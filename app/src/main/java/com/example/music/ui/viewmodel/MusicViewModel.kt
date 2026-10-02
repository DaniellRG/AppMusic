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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.music.network.CoverApi
import com.example.music.network.LrcLine
import com.example.music.network.LyricsApi

sealed interface LyricsUi {
    object Idle : LyricsUi
    object Loading : LyricsUi
    data class Success(
        val text: String,
        val source: String,
        // Líneas con tiempo si la respuesta venía sincronizada (LRC). Vacío = letra en
        // texto plano, que se pinta tal cual. Se parsea una vez aquí, no en cada recomposición.
        val synced: List<LrcLine> = emptyList()
    ) : LyricsUi
    data class Error(val message: String) : LyricsUi
}

/**
 * Espera entre canción y canción al rellenar portadas.
 *
 * Con 0 la app golpea la API pública de iTunes tan rápido como puede; con una biblioteca
 * grande son cientos de peticiones seguidas y empieza a devolver errores o a cortar la
 * conexión, con lo que la mitad de las canciones se quedan sin portada justo cuando
 * parecía que funcionaba. 350 ms mantiene el proceso dentro de unos minutos y sin fallar.
 */
private const val COVER_FETCH_DELAY_MS = 350L

/**
 * ¿Esta canción necesita que le busquemos la portada?
 *
 * `isNullOrBlank()` y no `== null` a propósito. El escaneo de SAF deja `coverUri` a null,
 * pero otras rutas lo guardan como "" cuando el álbum no tiene arte; con la comparación
 * estricta esa canción se daba por resuelta y se quedaba con la nota musical para
 * siempre, sin volver a intentarlo nunca.
 */
internal fun necesitaPortada(song: Song): Boolean = song.coverUri.isNullOrBlank()

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
    // Guardas de "solo la última petición cuenta": si se salta rápido entre canciones, una
    // respuesta lenta de la anterior no debe pisar el estado de la actual. Ver GenerationGate.
private val lyricsGate = GenerationGate()
private val coverGate = GenerationGate()

    /**
     * Pone la letra de la canción en pantalla, y la deja guardada en disco.
     *
     * El orden importa y es lo que pedía el usuario:
     *
     *  1. Si ya hay letra en disco, se usa y no se toca la red. Es el caso normal a
     *     partir de la segunda vez, y también el único que funciona sin cobertura.
     *  2. Si no hay, se busca en lrclib. Antes esto era TODO lo que hacía la app: la
     *     letra se perdía al cerrar, gastaba datos cada vez que abrías la pista y sin
     *     internet no salía nada.
     *  3. Lo que venga de la red se guarda, para que los próximos pasos apliquen.
     *
     * Si la búsqueda falla no se guarda nada, y así el siguiente intento puede volver a
     * intentarlo: guardar el fallo convertiría una canción sin letra en una que ya no
     * vuelve a buscar nunca.
     */
    fun fetchLyrics(song: Song?) {
        val s = song ?: return
        if (fetchedLyricsUri == s.uri && _lyrics.value !is LyricsUi.Idle) return
        fetchedLyricsUri = s.uri
        val gen = lyricsGate.next()
        // Antes se ponía Loading dentro del launch, o sea en el siguiente frame: mientras
        // tanto seguía en pantalla la letra de la canción anterior. fetchCover sí lo
        // limpiaba de forma síncrona, así que se iguala.
        _lyrics.value = LyricsUi.Loading
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()

                // 1. Disco primero. Sin red y sin gastar datos.
                val guardada = withContext(Dispatchers.IO) { LyricsCache.leer(ctx, s) }
                if (guardada != null) {
                    if (lyricsGate.isCurrent(gen)) _lyrics.value = LyricsUi.Success(
                        text = guardada,
                        source = "descargada",
                        synced = LyricsApi.parseLrc(guardada)
                    )
                    return@launch
                }

                // 2. Red.
                buscarYGuardarLetra(s, gen)
            } catch (e: Exception) {
                if (lyricsGate.isCurrent(gen)) _lyrics.value = LyricsUi.Error(e.localizedMessage ?: "Error de red")
            }
        }
    }

    /**
     * Fuerza la descarga de la letra de una canción, ignorando la que hubiera en disco.
     *
     * Es el botón "Descargar letra". Se le pasa `forzar = true` a propósito: si no,
     * una letra ya guardada no volvería a buscarse nunca y el botón no haría nada, que
     * es justo lo que se quejó el usuario.
     */
    fun descargarLetra(song: Song?, forzar: Boolean = true) {
        val s = song ?: return
        val ctx = getApplication<Application>()
        val gen = lyricsGate.next()
        fetchedLyricsUri = s.uri
        _lyrics.value = LyricsUi.Loading
        viewModelScope.launch {
            try {
                if (forzar) withContext(Dispatchers.IO) { LyricsCache.borrar(ctx, s) }
                val (artist, title) = withContext(Dispatchers.IO) { extractMetadata(s) }
                val res = withContext(Dispatchers.IO) { LyricsApi.fetch(artist, title, s.album) }
                if (!lyricsGate.isCurrent(gen)) return@launch
                val texto = res.syncedLyrics ?: res.plainLyrics
                if (texto == null) {
                    _lyrics.value = LyricsUi.Error("Letra no encontrada")
                    return@launch
                }
                withContext(Dispatchers.IO) { LyricsCache.guardar(ctx, s, texto) }
                if (lyricsGate.isCurrent(gen)) _lyrics.value = LyricsUi.Success(
                    text = texto,
                    source = res.source,
                    synced = res.syncedLyrics?.let { LyricsApi.parseLrc(it) }.orEmpty()
                )
            } catch (e: Exception) {
                if (lyricsGate.isCurrent(gen)) _lyrics.value = LyricsUi.Error(e.localizedMessage ?: "Error de red")
            }
        }
    }

    /**
     * Descarta la letra guardada y la vuelve a buscar.
     *
     * Es lo que hace el botón de refrescar de la sección de Letras. Necesario porque
     * lrclib a veces devuelve la letra de OTRA canción con nombre parecido: sin esto el
     * usuario se queda con la letra equivocada y no hay forma de salir de ella, ya que
     * la app da por buena la que tiene en disco.
     */
    fun borrarLetra(song: Song?) {
        val s = song ?: return
        val ctx = getApplication<Application>()
        fetchedLyricsUri = null
        // El borrado va en la MISMA corrutina que la relectura. Antes se lanzaba aparte y
        // la relectura podía encontrar el fichero justo antes de que desapareciera,
        // dejando la letra vieja en pantalla.
        viewModelScope.launch {
            val gen = lyricsGate.next()
            _lyrics.value = LyricsUi.Loading
            withContext(Dispatchers.IO) { LyricsCache.borrar(ctx, s) }
            if (!lyricsGate.isCurrent(gen)) return@launch
            buscarYGuardarLetra(s, gen)
        }
    }

    /** Busca la letra en la red y la guarda. Separado para que `borrarLetra` la reutilice. */
    private suspend fun buscarYGuardarLetra(s: Song, gen: Int) {
        val ctx = getApplication<Application>()
        try {
            val (artist, title) = withContext(Dispatchers.IO) { extractMetadata(s) }
            val res = withContext(Dispatchers.IO) { LyricsApi.fetch(artist, title, s.album) }
            if (!lyricsGate.isCurrent(gen)) return
            val texto = res.syncedLyrics ?: res.plainLyrics
            if (texto == null) {
                _lyrics.value = LyricsUi.Error("Letra no encontrada")
                return
            }
            withContext(Dispatchers.IO) { LyricsCache.guardar(ctx, s, texto) }
            if (lyricsGate.isCurrent(gen)) _lyrics.value = LyricsUi.Success(
                text = texto,
                source = res.source,
                synced = res.syncedLyrics?.let { LyricsApi.parseLrc(it) }.orEmpty()
            )
        } catch (e: Exception) {
            if (lyricsGate.isCurrent(gen)) _lyrics.value = LyricsUi.Error(e.localizedMessage ?: "Error de red")
        }
    }

    /** Si la letra de esta canción ya está en disco, para pintar el botón distinto. */
    fun letraGuardada(song: Song?): Boolean {
        val s = song ?: return false
        return LyricsCache.existe(getApplication(), s)
    }

    fun fetchCover(song: Song?) {
        val s = song ?: return
        if (fetchedCoverUri == s.uri && (_coverUrl.value != null || _coverError.value != null)) return
        fetchedCoverUri = s.uri
        _coverUrl.value = null   // limpiar: no mostrar la portada de la canción previa mientras carga
        val gen = coverGate.next()
        viewModelScope.launch {
            _coverLoading.value = true
            _coverError.value = null
            try {
                val (artist, title) = withContext(Dispatchers.IO) { extractMetadata(s) }
                val url = withContext(Dispatchers.IO) { CoverApi.fetchUrl(artist, title) } ?: s.coverUri
                if (coverGate.isCurrent(gen)) {
                    _coverUrl.value = url
                    if (url == null) _coverError.value = "Portada no encontrada"
                }
            } catch (e: Exception) {
                if (coverGate.isCurrent(gen)) {
                    _coverUrl.value = s.coverUri
                    _coverError.value = e.localizedMessage ?: "Error de red"
                }
            } finally {
                if (coverGate.isCurrent(gen)) _coverLoading.value = false
            }
        }
    }

    fun downloadCover(song: Song?, url: String?) {
        val s = song ?: return
        val u = url ?: s.coverUri ?: return
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { CoverApi.downloadBytes(u) }
                val guardado = withContext(Dispatchers.IO) { saveCoverFile(s, bytes) }
                // Antes la descarga se perdía: la imagen se metía en la galería y la fila
                // seguía sin portada en el próximo escaneo. Ahora se guarda el URI en la
                // propia canción, así la lista, los favoritos y el reproductor la ven.
                if (guardado != null) persistirPortada(s.id, guardado)
            } catch (_: Exception) {
                // Silencioso: la descarga es opcional.
            }
        }
    }

    /**
     * Escribe la portada en el `coverUri` de la canción.
     *
     * Se relee la canción antes de escribir, no se reutiliza la copia que tenía el
     * llamante: entre medias el usuario puede haber marcado esa canción como favorita,
     * y escribir el objeto viejo lo borraría. Es la razón de usar updateSong y no un
     * UPDATE directo sobre la columna.
     */
    private suspend fun persistirPortada(songId: Long, coverUri: String) {
        val actual = songDao.getSongById(songId) ?: return
        if (actual.coverUri == coverUri) return
        songDao.updateSong(actual.copy(coverUri = coverUri))
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
            // Lo que viene del ID3 solo se usa si es de verdad un nombre.
            //
            // El fallo era que `isNullOrBlank()` no basta: cuando un MP3 no tiene
            // etiquetas, MediaMetadataRetriever no devuelve null sino "<unknown>". Como
            // eso no está en blanco, se usaba como artista, la búsqueda de portada y de
            // letra salía con artist_name="<unknown>" y lrclib e iTunes no devolvían
            // nada. Justo con los ficheros descargados, que es donde más se nota.
            val artistaReal = artist.takeUnless { esGenerico(it) } ?: pa.ifEmpty { "unknown" }
            val tituloReal = title.takeUnless { esGenerico(it) } ?: pt.ifEmpty { song.title }
            return Pair(artistaReal, tituloReal)
        } catch (e: Exception) {
            val (pa, pt) = parseArtistTitle(song.title)
            return Pair(pa.ifEmpty { "unknown" }, pt.ifEmpty { song.title })
        } finally {
            try { mm.release() } catch (_: Exception) { }
        }
    }

    /**
     * ¿Este "nombre" es en realidad la ausencia de un nombre?
     *
     * Cada programa que escribe etiquetas tiene su manera propia de decir "no lo sé", y
     * las tres llegan hasta aquí: el reproductor de Android pone "<unknown>", otros
     * reproductores lo dejan vacío y algunos programas escriben literalmente
     * "Desconocido", que es además lo que deja el escáner de la app. Se tratan todas
     * igual, y en todos los casos es preferible sacarlo del nombre del fichero.
     */
    private fun esGenerico(valor: String?): Boolean {
        if (valor == null) return true
        val v = valor.trim()
        if (v.isEmpty()) return true
        return v.lowercase() in GENERICOS
    }

    private val GENERICOS = setOf(
        "<unknown>", "unknown", "desconocido", "sin artista", "null", "none",
        "<none>", "<null>", "artista desconocido", "n/a", "?"
    )

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

    /**
     * Escribe los bytes de la portada en la galería y devuelve su URI, o null si no se pudo.
     *
     * Se guarda en Pictures/Music Covers: en Android 10+ insertar por MediaStore no pide
     * WRITE_EXTERNAL_STORAGE y por eso no hay que escribir en /sdcard/Music.
     *
     * El nombre lleva el hash del URI de la canción en vez del título. Con el título
     * había dos fallos: dos canciones distintas con el mismo nombre se pisaban la
     * imagen, y al reintentar con el mismo nombre, MediaStore creaba "cover (1).jpg" y
     * devolvía una copia distinta cada vez.
     */
    private suspend fun saveCoverFile(song: Song, bytes: ByteArray): String? {
        val ctx = getApplication() as android.content.Context
        val safeTitle = song.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(40)
        val sufijo = song.uri.hashCode().toUInt().toString(16)
        val name = (if (safeTitle.isBlank()) "cover" else safeTitle) + "_$sufijo.jpg"
        return try {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Music Covers")
            }
            val uri = ctx.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                null
            } else {
                // Antes se devolvía el URI siempre, incluso si `openOutputStream` devolvía
                // null o fallaba al escribir: la canción quedaba guardada con un `coverUri`
                // que no apunta a ninguna imagen, y ya no se reintentaba nunca porque
                // `necesitaPortada` da por buena cualquier URI no vacía.
                val escrito = try {
                    // `use` devuelve el resultado de la última expresión, y `write` devuelve
                    // un Int: hay que convertirlo a Boolean explícitamente.
                    val os = ctx.contentResolver.openOutputStream(uri)
                    if (os == null) false else { os.use { it.write(bytes) }; true }
                } catch (_: Exception) {
                    false
                }
                if (escrito) uri.toString() else {
                    // La entrada de MediaStore ya existe aunque el write haya fallado:
                    // hay que borrarla o se quedan carátulas de 0 bytes en la galería.
                    runCatching { ctx.contentResolver.delete(uri, null, null) }
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Completa las portadas que falten: pide la de iTunes y la guarda en la canción.
     *
     * Esto es lo que hacía que las listas siguieran mostrando la nota musical: la portada
     * sólo se descargaba si el usuario tocaba el botón de descarga, y el resultado se
     * perdía al re-escanear. Ahora, al abrir la app, cada canción sin `coverUri` intenta
     * conseguirla una vez y queda guardada para siempre.
     *
     * Se serializa y con espera entre canciones: `fetchUrl` pega a la API pública de
     * iTunes, y lanzar 300 peticiones seguidas desde el escaneo la hace fallar o
     * limitar la tasa de peticiones. Va de una en una, en segundo plano, y es cancelable.
     *
     * @param soloLasQueFaltan si es false, reintenta también las que ya tienen portada
     *        local (útil cuando la guardada en el móvil es de 100x100).
     */
    fun completarPortadas(songs: List<Song>, soloLasQueFaltan: Boolean = true) {
        if (_completandoPortadas.value) return
        val pendientes = if (soloLasQueFaltan) songs.filter { necesitaPortada(it) } else songs
        if (pendientes.isEmpty()) return

        _completandoPortadas.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                for (song in pendientes) {
                    val (artista, titulo) = extractMetadata(song)
                    val url = CoverApi.fetchUrl(artista, titulo) ?: continue
                    val bytes = CoverApi.downloadBytes(url)
                    val uriGuardado = saveCoverFile(song, bytes)
                    if (uriGuardado != null) persistirPortadaPorUri(song.uri, uriGuardado)
                    // iTunes es público pero no ilimitado. Este respiro mantiene el
                    // escaneo dentro de lo razonable y no levanta sospechas.
                    delay(COVER_FETCH_DELAY_MS)
                }
            } catch (_: Exception) {
                // Un fallo de red no puede tirar el resto del lote.
            } finally {
                withContext(Dispatchers.Main) { _completandoPortadas.value = false }
            }
        }
    }

    /** true mientras se está rellenando portada en segundo plano. */
    private val _completandoPortadas = MutableStateFlow(false)
    val completandoPortadas: StateFlow<Boolean> = _completandoPortadas.asStateFlow()

    // --- Flows de datos (provienen de la base tras escanear) ---
    val allSongs = songDao.getAllSongs()
    val favoriteSongs = songDao.getFavoriteSongs()
    val allFolders = folderDao.getAllFolders()

    /**
     * Guarda la portada usando la URI como clave, no el `id` de la fila.
     *
     * El `id` que trae el escáner es el de MediaStore, pero `songs.id` es el de Room, y no
     * son el mismo número: `refreshFromScan` acaba de insertar esas filas y la base les ha
     * asignado ids propios. Con `persistirPortada(song.id, ...)` se escribía la portada en
     * una fila ajena o en ninguna, y como la canción se quedaba con `coverUri` a null se
     * volvía a pedir en el siguiente arranque, para siempre. La URI sí es la misma en
     * ambos lados.
     */
    private suspend fun persistirPortadaPorUri(uriCancion: String, coverUri: String) {
        songDao.updateCoverByUri(uriCancion, coverUri)
    }

    /**
     * Canciones de cada carpeta, indexadas por `folderId`.
     *
     * Se construye con las consultas que ya existían (`getSongsForFolder` por carpeta) más
     * el índice de la biblioteca, para no tocar la capa de base de datos, que es
     * compartida.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val songsByFolder: StateFlow<Map<Long, List<Song>>> =
        combine(allFolders, allSongs) { folders, songs -> folders to songs.associateBy { it.id } }
            .flatMapLatest { (folders, songsById) ->
                if (folders.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(
                        folders.map { folder ->
                            // Una carpeta puede apuntar a una cancion que ya no esta: se
                            // descarta con mapNotNull en vez de dejar un hueco en la lista.
                            crossRefDao.getSongsForFolder(folder.id)
                                .map { ids -> folder.id to ids.mapNotNull(songsById::get) }
                        }
                    ) { perFolder -> perFolder.toMap() }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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
                // Las portadas que falten se rellenan en segundo plano, después de
                // terminar el escaneo: si fuera aquí, la lista aparecería congelada
                // esperando a la red. Las que ya trae MediaStore no se tocan.
                completarPortadas(scanned)
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
                val nuevas = scanner.scanSafTree(folderUri)
                repository.insertSongsDeduplicated(nuevas)
                // Las carpetas SAF no traen portada, así que aquí sí hace falta buscarla.
                completarPortadas(nuevas)
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
