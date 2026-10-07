package com.example.music.scanner

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.example.music.model.Song
import com.example.music.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Escanea la música real del dispositivo.
 *
 *  - [scanMediaStore]: índice de MediaStore.Audio (carpetas Music/, Alarms/, Podcasts/, …).
 *  - [scanSafTree]:   carpeta elegida por el usuario vía Storage Access Framework
 *    (útil para música en Download/ u otras rutas no indexadas por MediaStore).
 */
class MusicScanner(private val context: Context) {

    private val contentResolver: ContentResolver
        get() = context.contentResolver

    /**
     * Duración mínima en ms, leída de Ajustes.
     *
     * Se resuelve en cada escaneo y no como constante para que el filtro que se ofrece en la
     * pantalla de Ajustes sea el mismo que se aplica de verdad: si fuera fijo, el usuario
     * movería el chip y no cambiaría nada.
     */
    private val minDurationMs: Long
        get() = AppSettings.getInstance(context).minDurationSec.value.toLong() * 1000L

    /** Escanea la música que el sistema ha indexado en MediaStore.Audio. */
    suspend fun scanMediaStore(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )
        // Sólo audio con duración > MIN_DURATION_MS (descarta timbres/notificaciones cortas).
        //
        // OJO con el `OR duration IS NULL`: una canción recién copiada al dispositivo
        // aparece en MediaStore antes de que el escáner termine de indexarla, con la columna
        // DURATION a NULL. Con el filtro original (`DURATION > 30000`) esa fila NO entraba en
        // el cursor, así que la canción era invisible en la app... y peor: si algún día
        // estuviera guardada y su duración quedara a NULL de nuevo, `computeScanDiff` la
        // marcaría como "desaparecida del dispositivo" y la BORRARÍA de la base (con su
        // carpeta por cascada) por un simple retardo del indexador.
        val selection = (
            "(" +
            "${MediaStore.Audio.Media.DURATION} > ? OR ${MediaStore.Audio.Media.DURATION} IS NULL" +
            ") AND " +
            "${MediaStore.Audio.Media.MIME_TYPE} IN (?,?,?,?,?,?,?)"
        )
        val selectionArgs = arrayOf(
            minDurationMs.toString(),
            "audio/mpeg",      // MP3
            "audio/mp4",       // AAC/M4A
            "audio/aac",       // AAC
            "audio/ogg",       // OGG
            "audio/vorbis",    // OGG
            "audio/flac",      // FLAC
            "audio/wav",       // WAV
            "audio/x-wav"      // WAV
        )
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
            ?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iDuration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    // getLong sobre un NULL devuelve 0: no confundirlos con "duración cero".
                    val indexed = if (c.isNull(iDuration)) 0L else c.getLong(iDuration)
                    val mediaUri = Uri.withAppendedPath(collection, id.toString())
                    val duration = if (indexed > 0L) indexed else readDuration(mediaUri)
                    if (duration < minDurationMs) continue

                    val title = c.getString(iTitle)
                    val artist = c.getString(iArtist)
                    val album = c.getString(iAlbum)
                    val albumId = c.getLong(iAlbumId)

                    // Portada del álbum: content://media/external/audio/albumart/<albumId>
                    val coverUri = if (albumId > 0) {
                        "content://media/external/audio/albumart/$albumId"
                    } else null

                    songs.add(
                        Song(
                            id = id,
                            title = title ?: "Sin título",
                            artist = normalizeArtist(artist),
                            album = album ?: "Sin álbum",
                            durationMs = duration,
                            uri = mediaUri.toString(),
                            coverUri = coverUri,
                            genre = "Sin género",
                            isFavorite = false
                        )
                    )
                }
            }
        songs
    }

    /**
     * Uris de las filas de audio que MediaStore tiene ahora mismo, sin filtrar por duración.
     *
     * Sirve para separar "esta canción ya no está en el móvil" de "aquí está pero el
     * indexador todavía no le ha puesto la duración". Sólo se usa como red de seguridad: el
     * escaneo normal no cambia, pero una uri que aparezca aquí nunca se considera eliminada.
     */
    suspend fun mediaStorePresentUris(): Set<String> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val uri = mutableSetOf<String>()
        contentResolver.query(
            collection,
            arrayOf(MediaStore.Audio.Media._ID),
            null, null, null
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            while (c.moveToNext()) {
                uri.add(Uri.withAppendedPath(collection, c.getLong(iId).toString()).toString())
            }
        }
        uri
    }
    /**
     * Huella barata de MediaStore para decidir si hace falta escanear.
     *
     * Son DOS agregados: cuantas pistas hay y cual es la ultima modificacion. La consulta
     * devuelve una sola fila sin contenido, asi que es del orden de milisegundos, mientras que
     * [scanMediaStore] recorre todas las pistas y para las que aun no tienen duracion indexada
     * abre el archivo una a una con MediaMetadataRetriever. Eso ultimo es lo que se evitaba en
     * CADA arranque de la app.
     *
     * Si la huella coincide con la del ultimo escaneo guardado y la base ya tiene filas, no hay
     * nada nuevo que traer. El boton "Escanear" de Ajustes fuerza igualmente.
     */
    suspend fun mediaStoreFingerprint(): String = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        runCatching {
            contentResolver.query(
                collection,
                arrayOf("COUNT(*)", "MAX(${MediaStore.Audio.Media.DATE_MODIFIED})"),
                null, null, null
            )?.use { c ->
                if (!c.moveToFirst()) return@use "0|0"
                val total = if (c.isNull(0)) 0L else c.getLong(0)
                val ultima = if (c.isNull(1)) 0L else c.getLong(1)
                "$total|$ultima"
            } ?: "0|0"
        }.getOrDefault("0|0")
    }


    /**
     * Lee la duración real del archivo cuando MediaStore todavía no la ha indexado.
     *
     * Devuelve 0 si tampoco se puede leer: en ese caso la canción se descarta, pero como
     * es el mismo criterio que se aplicaba antes, no se inventa duración inventada.
     */
    private fun readDuration(uri: Uri): Long {
        val mm = MediaMetadataRetriever()
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                mm.setDataSource(pfd.fileDescriptor)
                mm.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            } ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try { mm.release() } catch (_: Exception) { }
        }
    }

    /**
     * Recorre una carpeta elegida vía SAF (treeUri de OpenDocumentTree) y devuelve
     * sus archivos de audio. Se persiste el permiso para poder reproducirlas después.
     */
    suspend fun scanSafTree(treeUri: Uri): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        takePersistableUriPermission(treeUri)
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext songs
        val queue: ArrayDeque<DocumentFile> = ArrayDeque()
        queue.addLast(root)
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            dir.listFiles()?.forEach { f ->
                when {
                    f.isDirectory -> queue.addLast(f)
                    f.isFile && isAudioFile(f.name) -> extractSongSaf(f)?.let { songs.add(it) }
                }
            }
        }
        songs
    }

    private fun isAudioFile(name: String?): Boolean {
        val n = (name ?: "").lowercase()
        return n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".flac") ||
            n.endsWith(".wav") || n.endsWith(".ogg") || n.endsWith(".aac") || n.endsWith(".wma")
    }

    private fun extractSongSaf(file: DocumentFile): Song? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, file.uri)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            if (dur == null || dur < minDurationMs) return null
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: file.name
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: UNKNOWN_ARTIST
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: "Sin álbum"
            Song(
                id = 0,
                title = title ?: "Sin título",
                artist = normalizeArtist(artist),
                album = album,
                durationMs = dur,
                uri = file.uri.toString(),
                coverUri = null,
                genre = "Sin género",
                isFavorite = false,
                source = Song.SOURCE_SAF
            )
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun takePersistableUriPermission(treeUri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Ya tomada o no soportada en este documento.
        }
    }

    private fun normalizeArtist(raw: String?): String {
        val a = (raw ?: "").trim()
        if (a.isEmpty() || a == "<unknown>") return UNKNOWN_ARTIST
        return a
    }

    companion object {
        const val MIN_DURATION_MS = 30_000L
        private const val UNKNOWN_ARTIST = "Desconocido"
    }
}
