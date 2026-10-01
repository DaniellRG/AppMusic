package com.example.music.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.music.model.Song
import com.example.music.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Estado del reproductor.
 *
 * Es [Immutable] para que Compose pueda saltarse la recomposición: sin esta anotación, el
 * ticker de posición (que emite cada [MusicManager.POSITION_POLL_MS]) recompone la pantalla
 * entera —incluida la portada de Coil y la lista— dos veces por segundo.
 */
@Immutable
data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleMode: Boolean = false
)

enum class RepeatMode {
    OFF, ONE, ALL
}

/**
 * Reproductor basado en ExoPlayer.
 *
 * La cola vive DENTRO de ExoPlayer ([setPlayList]): antes se reproducía item a item
 * (`setMediaItem` en cada salto), lo que dejaba el aleatorio sin efecto —ExoPlayer no puede
 * barajar un único elemento— y hacía que "siguiente" al final de la lista no continuara solo.
 *
 * Es un **singleton a propósito**: [PlaybackService] tiene que envolver ESTE mismo ExoPlayer en
 * su MediaSession para la notificación y los controles de bloqueo. Si cada uno creara el suyo,
 * habría dos players y la notificación controlaría uno distinto del que suena.
 */
class MusicManager private constructor(context: Context) {

    private val appContext: Context = context.applicationContext

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        // Sin AudioAttributes ExoPlayer no pide foco de audio: una podcast o un juego con
        // música seguirían sonando encima, y la notificación del sistema tampoco se arma bien.
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            /* handleAudioFocus = */ true
        )
        // Desenchufar los auriculares no debe seguir reproduciendo a todo volumen.
        .setHandleAudioBecomingNoisy(true)
        .build()

    /** Player real, para que el MediaSession del servicio controle esta misma instancia. */
    val player: Player get() = exoPlayer

    /**
     * Segundos que quedan del temporizador de sueño; 0 si está apagado.
     *
     * Se expone aquí para que la UI pueda mostrar la cuenta atrás sin tener que recibir
     * [AppSettings] a través del grafo de navegación.
     */
    val sleepTimer: StateFlow<Int> get() = settings.sleepTimer

    /** Id de la sesión de audio, que es lo que necesita `Equalizer` para colgar el efecto. */
    val audioSessionId: Int get() = exoPlayer.audioSessionId

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Sondeo de la posición: ExoPlayer no notifica el avance del tiempo, sólo los cambios de estado. */
    private var positionTicker: Job? = null

    private val settings = AppSettings.getInstance(context)

    /** Rampa de volumen del fundido en curso, si la hay. */
    private var fadeJob: Job? = null

    /** Cuenta atrás del temporizador de sueño. */
    private var sleepJob: Job? = null

    /** Copia de la cola para resolver el índice de una canción sin preguntar a ExoPlayer. */
    private var playlist: List<Song> = emptyList()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                updateState()
                if (playbackState == Player.STATE_READY) startPositionTicker()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateState()
                if (isPlaying) startPositionTicker() else stopPositionTicker()
                // El servicio se levanta al empezar a sonar, no al abrir la app: arrancarlo
                // antes daría un foreground service sin notificación, que Android mata.
                if (isPlaying) ensurePlaybackService()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                updateState()
            }

            // Cubre el salto automático al acabar la pista, que antes dejaba la UI desincronizada.
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateState()
                // Sólo al avanzar solo. Si el usuario pulsa "siguiente" quiere oír la canción
                // ya, no esperar a que suba el volumen.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) startFadeIn()
            }
        })
    }

    // --- COLA ---

    /**
     * Carga [songs] como cola de reproducción conservando la posición de la canción en curso.
     *
     * Se llama cada vez que llega un escaneo nuevo. Si la canción actual sigue en la lista,
     * ExoPlayer reanuda en su índice; si ya no está, se queda en el principio.
     *
     * Sólo reanuda la reproducción si ya estaba sonando: cargar la cola no debe arrancar
     * audio por sorpresa al abrir la app.
     */
    fun setPlayList(songs: List<Song>, startSongId: Long? = null) {
        if (songs.isEmpty()) {
            playlist = emptyList()
            exoPlayer.clearMediaItems()
            updateState()
            return
        }

        // `allSongs` es un Flow: reemite con CUALQUIER escritura en la base, y esto se llama
        // en cada emisión. Recargar la cola es un `setMediaItems` + `prepare()`, o sea un corte
        // de audio audible. Marcar un favorito, o el broadcast de MediaStore que dispara un
        // rescan, no cambian la cola: para eso no hay que tocar el reproductor.
        if (!playlistNeedsReload(playlist, songs)) {
            playlist = songs
            updateState()
            return
        }

        val resumeId = startSongId ?: _playbackState.value.currentSong?.id
        val resumeIndex = songs.indexOfFirst { it.id == resumeId }

        val wasPlaying = exoPlayer.isPlaying
        val position = exoPlayer.currentPosition

        playlist = songs
        exoPlayer.setMediaItems(songs.map { it.toMediaItem() })
        exoPlayer.prepare()

        when {
            resumeIndex >= 0 -> exoPlayer.seekTo(resumeIndex, position)
            else -> exoPlayer.seekTo(0, 0L)
        }

        if (wasPlaying) exoPlayer.play() else exoPlayer.pause()
        updateState()
    }

    fun playSong(song: Song) {
        val index = playlist.indexOfFirst { it.id == song.id }
        if (index >= 0) {
            // Si el reproductor cayó a IDLE (un ítem anterior falló al cargar, el archivo se
            // borró, el dispositivo despertó), `play()` es un no-op silencioso: hay que
            // volver a preparar antes de Reproducir.
            if (exoPlayer.playbackState == Player.STATE_IDLE) {
                exoPlayer.prepare()
            }
            exoPlayer.seekTo(index, 0L)
        } else {
            // La canción no está en la cola (p. ej. acaba de llegar de un escaneo): se añade.
            playlist = playlist + song
            exoPlayer.addMediaItem(song.toMediaItem())
            // Sin prepare() el reproductor queda en IDLE y `play()` no arranca: ExoPlayer
            // no carga nada hasta que se le pide preparar.
            exoPlayer.prepare()
            exoPlayer.seekTo(playlist.lastIndex, 0L)
        }
        exoPlayer.play()
        updateState()
    }

    fun pause() {
        exoPlayer.pause()
        updateState()
    }

    fun resume() {
        exoPlayer.play()
        updateState()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) pause() else resume()
    }

    fun stop() {
        exoPlayer.stop()
        updateState()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        updateState()
    }

    /**
     * Avanza a la siguiente canción de la cola.
     *
     * @return el índice en el que quedó, o `null` si no hay a dónde ir
     *   (última canción y repeat distinto de ALL).
     */
    fun skipToNext(): Int? {
        if (playlist.size <= 1) {
            if (playbackState.value.repeatMode == RepeatMode.ALL && playlist.isNotEmpty()) {
                exoPlayer.seekTo(0, 0L)
                exoPlayer.play()
                updateState()
                return 0
            }
            return null
        }
        val hasNext = exoPlayer.hasNextMediaItem() ||
            playbackState.value.repeatMode == RepeatMode.ALL
        if (!hasNext) return null

        exoPlayer.seekToNextMediaItem()
        exoPlayer.play()
        updateState()
        return exoPlayer.currentMediaItemIndex
    }

    /** Retrocede a la canción anterior. Devuelve el índice, o `null` si no hay a dónde ir. */
    fun skipToPrevious(): Int? {
        if (playlist.size <= 1) {
            if (playbackState.value.repeatMode == RepeatMode.ALL && playlist.isNotEmpty()) {
                val last = playlist.lastIndex
                exoPlayer.seekTo(last, 0L)
                exoPlayer.play()
                updateState()
                return last
            }
            return null
        }

        // Comportamiento habitual de los reproductores: si llevas más de unos segundos
        // dentro de la pista, "anterior" reinicia la pista en vez de saltar hacia atrás.
        if (exoPlayer.currentPosition > RESTART_THRESHOLD_MS) {
            exoPlayer.seekTo(0L)
            updateState()
            return exoPlayer.currentMediaItemIndex
        }

        val hasPrev = exoPlayer.hasPreviousMediaItem() ||
            playbackState.value.repeatMode == RepeatMode.ALL
        if (!hasPrev) return null

        exoPlayer.seekToPreviousMediaItem()
        exoPlayer.play()
        updateState()
        return exoPlayer.currentMediaItemIndex
    }

    /** Índice de [songId] en la cola actual, o -1 si no está. */
    fun indexOf(songId: Long): Int = playlist.indexOfFirst { it.id == songId }

    /**
     * @deprecated La cola vive en ExoPlayer; usar [skipToNext]. Se mantiene hasta que la UI
     *   deixe de pasar la lista completa desde la pantalla.
     */
    @Deprecated("Usa skipToNext()", ReplaceWith("skipToNext()"))
    fun skipNext(songs: List<Song>, currentIndex: Int): Int? {
        setPlayListIfEmpty(songs, currentIndex)
        return skipToNext()
    }

    /** @deprecated Ver [skipNext]. */
    @Deprecated("Usa skipToPrevious()", ReplaceWith("skipToPrevious()"))
    fun skipPrevious(songs: List<Song>, currentIndex: Int): Int? {
        setPlayListIfEmpty(songs, currentIndex)
        return skipToPrevious()
    }

    /** Carga la cola sólo si aún está vacía, para no reiniciar la reproducción en curso. */
    private fun setPlayListIfEmpty(songs: List<Song>, currentIndex: Int) {
        if (playlist.isNotEmpty() || songs.isEmpty()) return
        setPlayList(songs, startSongId = songs.getOrNull(currentIndex)?.id)
    }

    // --- REPETIR / ALEATORIO ---

    fun setRepeatMode(mode: RepeatMode) {
        exoPlayer.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
        updateState()
    }

    fun cycleRepeatMode() {
        val next = when (playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        setRepeatMode(next)
    }

    /** El aleatorio sólo tiene efecto si ExoPlayer tiene una cola real con 2+ elementos. */
    fun toggleShuffle() {
        exoPlayer.shuffleModeEnabled = !exoPlayer.shuffleModeEnabled
        updateState()
    }

    val hasNext: Boolean
        get() = exoPlayer.hasNextMediaItem() ||
            (playbackState.value.repeatMode == RepeatMode.ALL && playlist.size > 1)

    val hasPrevious: Boolean
        get() = exoPlayer.hasPreviousMediaItem() ||
            (playbackState.value.repeatMode == RepeatMode.ALL && playlist.size > 1)

    val currentPosition: Long get() = exoPlayer.currentPosition
    val duration: Long get() = exoPlayer.duration
    val isPlaying: Boolean get() = exoPlayer.isPlaying
    val currentMediaItem: MediaItem? get() = exoPlayer.currentMediaItem

    fun release() {
        stopPositionTicker()
        scope.cancel()
        playlist = emptyList()
        exoPlayer.release()
    }

    // --- INTERNOS ---

    /**
     * Levanta [PlaybackService], que es quien publica la notificación y los controles de
     * bloqueo. Se llama sólo cuando ya hay audio sonando.
     */
    private fun ensurePlaybackService() {
        runCatching {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, PlaybackService::class.java)
            )
        }
    }

    private fun startPositionTicker() {
        if (positionTicker?.isActive == true) return
        positionTicker = scope.launch {
            while (isActive) {
                updateState()
                maybeStartFadeOut()
                delay(POSITION_POLL_MS)
            }
        }
    }

    private fun stopPositionTicker() {
        positionTicker?.cancel()
        positionTicker = null
    }

    /**
     * Aplica a ExoPlayer los ajustes que viven en él (no en la UI).
     *
     * Se llama al crear el ViewModel y cada vez que cambia un ajuste, para no tener al
     * reproductor leyendo [AppSettings] en cada_FRAME.
     */
    fun applySettings() {
        exoPlayer.skipSilenceEnabled = settings.skipSilence.value
        // Si el usuario apaga el fundido a mitad de canción, el volumen se queda donde estaba
        // a medias y la canción se oye baja: hay que restaurarlo.
        if (!settings.fade.value) {
            fadeJob?.cancel()
            fadeJob = null
            exoPlayer.volume = 1f
        }
    }

    // --- FUNDIDO ---
    //
    // Media3 no tiene crossfade (no existe el método en el 1.4.0) y no solapa pistas, así que
    // esto es un fundido de volumen, no un solapamiento: la pista nueva entra de 0 a 1 y la
    // vieja sale de 1 a 0 en sus últimos segundos. Suena mucho mejor que el corte seco, pero
    // conviene no llamarlo crossfade.

    private fun startFadeIn() {
        if (!settings.fade.value) return
        fadeJob?.cancel()
        fadeJob = scope.launch {
            exoPlayer.volume = 0f
            val steps = FADE_STEPS
            repeat(steps) { i ->
                if (!isActive) return@launch
                // Curva cuadrática: el volumen sube rápido al principio, que es lo que percibe
                // el oído como "entrada" y no como un corte.
                val t = (i + 1f) / steps
                exoPlayer.volume = t * t
                delay(FADE_STEP_MS)
            }
            exoPlayer.volume = 1f
        }
    }

    private fun maybeStartFadeOut() {
        if (!settings.fade.value) return
        if (fadeJob?.isActive == true) return
        val dur = exoPlayer.duration
        if (dur <= 0 || dur == C.TIME_UNSET) return
        val remaining = dur - exoPlayer.currentPosition
        if (remaining > FADE_OUT_MS || remaining <= 0) return
        fadeJob = scope.launch {
            val steps = FADE_STEPS
            val from = (remaining.toFloat() / FADE_OUT_MS).coerceIn(0f, 1f)
            repeat(steps) { i ->
                if (!isActive) return@launch
                val t = (i + 1f) / steps
                exoPlayer.volume = from * (1f - t)
                delay(FADE_STEP_MS)
            }
        }
    }

    // --- TEMPORIZADOR DE SUEÑO ---

    /**
     * Apaga la reproducción en [seconds]. Con 0 se cancela.
     *
     * El aviso se manda al usuario al iniciar y al cancelar, porque algo que para la música
     * sin que se note parece un fallo de la app.
     */
    fun setSleepTimer(seconds: Int) {
        sleepJob?.cancel()
        sleepJob = null
        settings.setSleepTimer(seconds)
        if (seconds <= 0) return
        sleepJob = scope.launch {
            var left = seconds
            while (left > 0 && isActive) {
                settings.setSleepTimer(left)
                delay(1_000L)
                left--
            }
            if (isActive) {
                exoPlayer.pause()
                settings.setSleepTimer(0)
            }
        }
    }

    private fun updateState() {
        val currentIndex = exoPlayer.currentMediaItemIndex
        val song = playlist.getOrNull(currentIndex)

        _playbackState.value = PlaybackState(
            // Antes copiaba el valor anterior de sí mismo, así que siempre era null: la UI
            // no tenía forma de saber qué sonaba tras un salto automático.
            currentSong = song,
            isPlaying = exoPlayer.isPlaying,
            currentPositionMs = exoPlayer.currentPosition,
            durationMs = exoPlayer.duration.takeIf { it > 0 } ?: song?.durationMs ?: 0L,
            repeatMode = when (exoPlayer.repeatMode) {
                Player.REPEAT_MODE_OFF -> RepeatMode.OFF
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            shuffleMode = exoPlayer.shuffleModeEnabled
        )
    }

    private fun Song.toMediaItem(): MediaItem {
        val extras = Bundle().apply {
            putLong(KEY_SONG_ID, id)
            putString(KEY_SONG_URI, uri)
            putString(KEY_SONG_TITLE, title)
        }
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setExtras(extras)
            .apply {
                // Sin esto la notificación y la pantalla de bloqueo salen sin portada, y en
                // Android Auto directamente no aparece el canción.
                if (!coverUri.isNullOrBlank()) setArtworkUri(Uri.parse(coverUri))
            }
            .build()
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(id.toString())
            .setMediaMetadata(metadata)
            .build()
    }

    companion object {
        /** 500 ms: suficiente para una barra de progreso fluida sin gastar CPU. */
        const val POSITION_POLL_MS = 500L

        /** Por encima de este tiempo, "anterior" reinicia la pista en vez de saltar atrás. */
        const val RESTART_THRESHOLD_MS = 3_000L

        /** Pasos del fundido. 20 a 20 ms = 400 ms, que es la duración de una entrada_listener decente. */
        const val FADE_STEPS = 20
        const val FADE_STEP_MS = 20L

        /** Últimos 1.5 s de la pista: aquí se baja el volumen antes del cambio. */
        const val FADE_OUT_MS = 1_500L

        const val KEY_SONG_ID = "songId"
        const val KEY_SONG_URI = "songUri"
        const val KEY_SONG_TITLE = "songTitle"

        @Volatile
        private var instance: MusicManager? = null

        fun getInstance(context: Context): MusicManager =
            instance ?: synchronized(this) {
                instance ?: MusicManager(context.applicationContext).also { instance = it }
            }
    }
}

/**
 * ¿Hay que reconstruir la cola de ExoPlayer?
 *
 * Función pura para poder testearla sin Android.
 *
 * Se comparan todos los campos que la UI o el reproductor leen de la cola, MENOS
 * [Song.isFavorite]: marcar un favorito reescribe la fila y por tanto vuelve a emitir
 * `allSongs`, pero no cambia un solo byte de lo que se está escuchando. Si se contara,
 * cada toque en el corazón cortaría el audio.
 *
 * El orden importa: una reordenación de la biblioteca sí debe recargar la cola, aunque
 * coincidan los ids.
 */
internal fun playlistNeedsReload(current: List<Song>, incoming: List<Song>): Boolean {
    if (current.size != incoming.size) return true
    return current.indices.any { i ->
        val a = current[i]
        val b = incoming[i]
        a.id != b.id ||
            a.uri != b.uri ||
            a.durationMs != b.durationMs ||
            a.title != b.title ||
            a.artist != b.artist ||
            a.album != b.album ||
            a.genre != b.genre
    }
}
