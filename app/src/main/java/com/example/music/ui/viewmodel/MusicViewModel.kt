package com.example.music.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.music.database.FolderDao
import com.example.music.database.MusicDatabase
import com.example.music.database.SongDao
import com.example.music.database.SongFolderCrossRefDao
import com.example.music.model.Folder
import com.example.music.model.Song
import com.example.music.player.MusicManager
import com.example.music.player.PlaybackState
import com.example.music.player.RepeatMode
import com.example.music.repository.SongRepository
import com.example.music.scanner.MusicScanner
import com.example.music.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.music.network.CoverApi
import com.example.music.network.LyricsApi

sealed interface LyricsUi {
    object Idle : LyricsUi
    object Loading : LyricsUi
    data class Success(val text: String, val source: String) : LyricsUi
    data class Error(val message: String) : LyricsUi
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val database: MusicDatabase = MusicDatabase.getDatabase(application)
    private val songDao: SongDao = database.songDao()
    private val folderDao: FolderDao = database.folderDao()
    private val crossRefDao: SongFolderCrossRefDao = database.songFolderCrossRefDao()
    private val repository: SongRepository = SongRepository(database, songDao, folderDao, crossRefDao)
    // Singleton: el PlaybackService envuelve este mismo ExoPlayer para la notificación.
    private val musicManager = MusicManager.getInstance(application)
    private val scanner = MusicScanner(application)

    /** Ajustes persistidos. Se expone para que Ajustes y el reproductor lean lo mismo. */
    val settings: AppSettings = AppSettings.getInstance(application)

    /**
     * Segundos que quedan para que se corte la música (0 = apagado).
     *
     * Lo lleva la cuenta [MusicManager.setSleepTimer] y sólo se lee aquí, para que el
     * temporizador sobreviva aunque la pantalla esté cerrada.
     */
    val sleepTimerSeconds: StateFlow<Int> = settings.sleepTimer

    init {
        // Los ajustes que viven dentro de ExoPlayer (saltar silencios, fundido) se aplican una
        // vez al abrir la app; el resto los lee la UI directamente del StateFlow.
        musicManager.applySettings()
    }

    fun setSleepTimer(seconds: Int) = musicManager.setSleepTimer(seconds)

    // --- Estado de reproducción (se expone a la UI) ---
    val playbackState: StateFlow<PlaybackState> = musicManager.playbackState
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()
    val musicManagerPublic: MusicManager get() = musicManager

    // --- Estado de escaneo de música real ---
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    // --- Permiso de almacenamiento (gestionado por la Activity) ---
    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()
    fun setPermissionGranted(granted: Boolean) {
        _permissionGranted.value = granted
    }

    // --- Letras + portada online (red: lrclib + iTunes) ---
    private val _lyrics = MutableStateFlow<LyricsUi>(LyricsUi.Idle)
    val lyrics: StateFlow<LyricsUi> = _lyrics.asStateFlow()
    private val _coverUrl = MutableStateFlow<String?>(null)
    val coverUrl: StateFlow<String?> = _coverUrl.asStateFlow()
    private val _coverLoading = MutableStateFlow(false)
    val coverLoading: StateFlow<Boolean> = _coverLoading.asStateFlow()
    private val _coverError = MutableStateFlow<String?>(null)
    val coverError: StateFlow<String?> = _coverError.asStateFlow()

    // Cache: una letra/portada por canción mientras dura la sesión (claves separadas,
    // porque fetchedSongUri era compartida entre fetchLyrics y fetchCover y la caché
    // casi nunca acertaba).
    private var fetchedLyricsUri: String? = null
    private var fetchedCoverUri: String? = null
    // Generación: si se salta rápido entre canciones, una respuesta lenta de la
    // anterior no debe pisar el estado de la actual (condición de carrera).
    private var lyricsGeneration = 0
    private var coverGeneration = 0

    fun fetchLyrics(song: Song?) {
        val s = song ?: return
        if (fetchedLyricsUri == s.uri && _lyrics.value !is LyricsUi.Idle) return
        fetchedLyricsUri = s.uri
        val gen = ++lyricsGeneration
        viewModelScope.launch {
            _lyrics.value = LyricsUi.Loading
            try {
                val (artist, title) = withContext(Dispatchers.IO) { extractMetadata(s) }
                val res = withContext(Dispatchers.IO) { LyricsApi.fetch(artist, title, s.album) }
                if (gen != lyricsGeneration) return@launch   // canción cambiada: descartar
                _lyrics.value = if (res.plainLyrics != null || res.syncedLyrics != null)
                    LyricsUi.Success(res.syncedLyrics ?: res.plainLyrics ?: "", res.source)
                else
                    LyricsUi.Error("Letra no encontrada")
            } catch (e: Exception) {
                if (gen == lyricsGeneration) _lyrics.value = LyricsUi.Error(e.localizedMessage ?: "Error de red")
            }
        }
    }

    fun fetchCover(song: Song?) {
        val s = song ?: return
        if (fetchedCoverUri == s.uri && (_coverUrl.value != null || _coverError.value != null)) return
        fetchedCoverUri = s.uri
        _coverUrl.value = null   // limpiar: no mostrar la portada de la canción previa mientras carga
        val gen = ++coverGeneration
        viewModelScope.launch {
            _coverLoading.value = true
            _coverError.value = null
            try {
                val (artist, title) = withContext(Dispatchers.IO) { extractMetadata(s) }
                val url = withContext(Dispatchers.IO) { CoverApi.fetchUrl(artist, title) } ?: s.coverUri
                if (gen == coverGeneration) {
                    _coverUrl.value = url
                    if (url == null) _coverError.value = "Portada no encontrada"
                }
            } catch (e: Exception) {
                if (gen == coverGeneration) {
                    _coverUrl.value = s.coverUri
                    _coverError.value = e.localizedMessage ?: "Error de red"
                }
            } finally {
                if (gen == coverGeneration) _coverLoading.value = false
            }
        }
    }

    fun downloadCover(song: Song?, url: String?) {
        val s = song ?: return
        val u = url ?: s.coverUri ?: return
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { CoverApi.downloadBytes(u) }
                withContext(Dispatchers.IO) { saveCoverFile(s, bytes) }
            } catch (e: Exception) {
                // Silencioso: la descarga es opcional.
            }
        }
    }

    /** Lee artista/título reales desde el ID3; si faltan, los extrae del nombre del archivo. */
    private fun extractMetadata(song: Song): Pair<String, String> {
        val mm = android.media.MediaMetadataRetriever()
        try {
            val ctx = getApplication() as android.content.Context
            android.net.Uri.parse(song.uri)?.let { uri ->
                ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    mm.setDataSource(pfd.fileDescriptor)
                }
            }
            val artist = mm.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val title = mm.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
            val (pa, pt) = parseArtistTitle(song.title)
            return Pair(
                if (artist.isNullOrBlank()) pa.ifEmpty { "unknown" } else artist,
                if (title.isNullOrBlank()) pt.ifEmpty { song.title } else title
            )
        } catch (e: Exception) {
            val (pa, pt) = parseArtistTitle(song.title)
            return Pair(pa.ifEmpty { "unknown" }, pt.ifEmpty { song.title })
        } finally {
            try { mm.release() } catch (_: Exception) { }
        }
    }

    private fun parseArtistTitle(name: String): Pair<String, String> {
        val cleaned = name.replace(Regex("[(){}\\[\\]]"), "").trim()
        val dash = cleaned.indexOfFirst { it == '-' }
        if (dash > 0) {
            val a = cleaned.substring(0, dash).trim()
            val t = cleaned.substring(dash + 1).trim()
            if (a.isNotBlank() && t.isNotBlank()) return Pair(a, t)
        }
        val parts = cleaned.split(Regex("[_\\-]")).filter { it.isNotBlank() }
        return if (parts.size >= 2)
            Pair(parts[0], parts.drop(1).joinToString(" "))
        else Pair("", cleaned)
    }

    private fun saveCoverFile(song: Song, bytes: ByteArray) {
        val ctx = getApplication() as android.content.Context
        val safeTitle = song.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(40)
        val name = (if (safeTitle.isBlank()) "cover" else safeTitle) + "_cover.jpg"
        // Android 10+ (Scoped Storage): insertar vía MediaStore no requiere WRITE_EXTERNAL_STORAGE
        // y no escribe en /sdcard/Music ( Pictures/Music Covers ).
        try {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Music Covers")
            }
            val uri = ctx.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
        } catch (_: Exception) {
            // Silencioso: la descarga es opcional.
        }
    }

    // --- Flows de datos (provienen de la base tras escanear) ---
    val allSongs = songDao.getAllSongs()
    val favoriteSongs = songDao.getFavoriteSongs()
    val allFolders = folderDao.getAllFolders()

    init {
        // La cola del reproductor se alimenta de la biblioteca, así que se mantiene
        // sincronizada con cada cambio de la base.
        viewModelScope.launch {
            allSongs.collect { songs -> musicManager.setPlayList(songs) }
        }
        // `_currentSong` era un estado paralelo que se quedaba obsoleto al cambiar de pista
        // (salto automático, siguiente, cola). Ahora deriva del reproductor, que es la
        // fuente de verdad.
        viewModelScope.launch {
            playbackState.collect { state ->
                val song = state.currentSong ?: return@collect
                if (_currentSong.value?.id != song.id) {
                    _currentSong.value = song
                }
            }
        }
    }

    // --- ESCANEO REAL ---
    /** Escanea MediaStore y refresca la base con la música del dispositivo. */
    fun scanDeviceMusic() {
        viewModelScope.launch {
            _isScanning.value = true
            _scanError.value = null
            try {
                val scanned = scanner.scanMediaStore()
                // Se pasa qué uris sigue viendo MediaStore aunque el scanner no haya podido
                // medirlas: sin esto, una canción recién copiada (DURATION a NULL hasta que el
                // indexador la rellena) parece "desaparecida" y se borra con su carpeta.
                repository.refreshFromScan(scanned, scanner.mediaStorePresentUris())
            } catch (e: SecurityException) {
                _scanError.value = "Permiso de almacenamiento denegado."
            } catch (e: Exception) {
                _scanError.value = "Error escaneando música: ${e.localizedMessage}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    // --- IMPORTAR CARPETA VÍA SAF ---
    fun importFolder(folderUri: android.net.Uri) {
        viewModelScope.launch {
            try {
                // `insertSongsDeduplicated` compara por metadatos: una pista ya indexada por
                // MediaStore tiene otra URI en SAF y se colaría como duplicado.
                repository.insertSongsDeduplicated(scanner.scanSafTree(folderUri))
            } catch (e: SecurityException) {
                _scanError.value = "Permiso de almacenamiento denegado."
            } catch (e: Exception) {
                _scanError.value = "Error importando carpeta: ${e.localizedMessage}"
            }
        }
    }

    // --- FUNCIONES DE REPRODUCTOR ---
    fun playSong(song: Song) {
        _currentSong.value = song
        musicManager.playSong(song)
    }

    fun pause() = musicManager.pause()
    fun resume() = musicManager.resume()
    fun togglePlayPause() = musicManager.togglePlayPause()

    /**
     * Avanza en la cola. La cola vive en ExoPlayer, así que ya no hace falta releer
     * la biblioteca de la base en cada salto.
     */
    fun skipNext() = musicManager.skipToNext()

    fun skipPrevious() = musicManager.skipToPrevious()

    fun setRepeatMode(mode: RepeatMode) = musicManager.setRepeatMode(mode)
    fun cycleRepeatMode() = musicManager.cycleRepeatMode()
    fun toggleShuffle() = musicManager.toggleShuffle()

    val hasNext: Boolean get() = musicManager.hasNext
    val hasPrevious: Boolean get() = musicManager.hasPrevious

    // --- FAVORITOS ---
    fun toggleFavorite(songId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(songId, isFavorite)
            // reflejar el cambio en la canción actual para que la UI actualice el icono de favorito
            val cur = _currentSong.value
            if (cur != null && cur.id == songId) {
                _currentSong.value = cur.copy(isFavorite = isFavorite)
            }
        }
    }

    // --- CARPETAS ---
    fun createFolder(name: String, genre: String, colorHex: String) {
        viewModelScope.launch { repository.insertFolder(Folder(name = name, genre = genre, colorHex = colorHex)) }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch { repository.deleteFolder(folder) }
    }

    fun addSongToFolder(songId: Long, folderId: Long) {
        viewModelScope.launch { repository.addSongToFolder(songId, folderId) }
    }

    fun removeSongFromFolder(songId: Long) {
        viewModelScope.launch { repository.removeSongFromFolder(songId) }
    }

    // --- CANCION ACTUAL ---
    suspend fun getCurrentSong(): Song? {
        val mediaId = musicManager.currentMediaItem?.mediaId?.toLongOrNull() ?: return null
        return songDao.getSongById(mediaId)
    }

    fun getCurrentSongIndex(songs: List<Song>): Int {
        val mediaId = musicManager.currentMediaItem?.mediaId?.toLongOrNull() ?: return -1
        return songs.indexOfFirst { it.id == mediaId }
    }

    fun getPlaybackState(): PlaybackState = musicManager.playbackState.value

    override fun onCleared() {
        super.onCleared()
        // No se libera el player aquí. MusicManager es un singleton compartido con
        // PlaybackService: si la Activity se destruye mientras suena (rotación, "sin
        // recientes", proceso recreation), liberar mataba el ExoPlayer y la notificación
        // del servicio, que sigue en primer plano, se quedaba controlando un player muerto.
        // El servicio y la app viven o mueren juntos con el proceso, no con la Activity.
    }
}

data class MusicUiState(
    val currentSong: Song? = null,
    val playbackState: PlaybackState = PlaybackState(),
    val currentSongIndex: Int = -1,
    val isPlayerVisible: Boolean = false
)
