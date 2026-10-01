package com.example.music.repository

import androidx.room.withTransaction
import com.example.music.database.FolderDao
import com.example.music.database.MusicDatabase
import com.example.music.database.SongDao
import com.example.music.database.SongFolderCrossRefDao
import com.example.music.model.Folder
import com.example.music.model.Song
import com.example.music.model.SongFolderCrossRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class SongRepository(
    private val database: MusicDatabase,
    private val songDao: SongDao,
    private val folderDao: FolderDao,
    private val crossRefDao: SongFolderCrossRefDao
) {
    // --- SONG OPERATIONS ---
    fun getAllSongs(): Flow<List<Song>> = songDao.getAllSongs()

    fun getFavoriteSongs(): Flow<List<Song>> = songDao.getFavoriteSongs()

    fun searchSongs(query: String): Flow<List<Song>> = songDao.searchSongs(query)

    suspend fun insertSongs(songs: List<Song>) = songDao.insertSongs(songs)

    /**
     * Inserta canciones descartando las que ya están guardadas.
     *
     * A diferencia de [insertSongs], compara por metadatos y no sólo por `uri`: una canción
     * indexada por MediaStore y la misma importada por SAF tienen URIs distintas, así que el
     * filtro por URI dejaba la biblioteca con duplicados de cada pista.
     */
    suspend fun insertSongsDeduplicated(songs: List<Song>) = database.withTransaction {
        if (songs.isEmpty()) return@withTransaction
        val existing = songDao.getSongsBySource(Song.SOURCE_MEDIASTORE) +
            songDao.getSongsBySource(Song.SOURCE_SAF)
        val fresh = filterNewSongs(songs, existing)
        if (fresh.isNotEmpty()) songDao.insertSongs(fresh)
    }

    // --- ESCANEO ---
    /**
     * Sincroniza la base con los resultados de un escaneo de MediaStore, mediante diff por `uri`.
     *
     * Antes esta función hacía `deleteAll` + reinsert, lo que destruía en cada arranque:
     *   - la marca de favorito (el scanner siempre devuelve `isFavorite = false`);
     *   - las canciones importadas por SAF, que no aparecen en el escaneo de MediaStore;
     *   - la pertenencia a carpetas, porque al reinsertar se renumeran los `id`.
     *
     * La `uri` es la clave de identidad real: las canciones de SAF entran con `id = 0`
     * (autoGenerate) y reciben un id distinto en cada escaneo, así que comparar por `id` no sirve.
     *
     * Sólo se borran las filas de origen MediaStore cuya `uri` ya no está en el dispositivo;
     * las de SAF quedan intactas para que sobrevivan al relanzamiento de la app.
     */
    suspend fun refreshFromScan(songs: List<Song>) =
        refreshFromScan(songs, presentUris = null)

    /**
     * Igual que [refreshFromScan], pero con el conjunto de uris que el scanner ha visto
     * en MediaStore aunque no haya podido medirlas (metadatos sin indexar).
     *
     * Sin ese dato, una canción con `duration IS NULL` se descarta del escaneo y el diff la
     * da por desaparecida. Con él, la fila se conserva intacta hasta que el indexador termine.
     */
    suspend fun refreshFromScan(songs: List<Song>, presentUris: Set<String>?) = database.withTransaction {
        // Un escaneo vacío casi nunca significa "el usuario borró toda su música": significa
        // que el permiso se denegó, que MediaStore falló o que el dispositivo acaba de
        // arrancar. Purgar en ese caso borraría la biblioteca entera de golpe.
        if (songs.isEmpty()) return@withTransaction

        val existing = songDao.getSongsBySource(Song.SOURCE_MEDIASTORE)
        val favoriteUris = songDao.getFavoriteUris().toHashSet()

        val diff = computeScanDiff(existing, songs, favoriteUris, presentUris)

        if (diff.removedIds.isNotEmpty()) {
            songDao.deleteSongsByIds(diff.removedIds)
        }

        // Limpia referencias a canciones que ya no existen (evita carpetas cruzadas al renumerar ids).
        crossRefDao.deleteOrphanCrossRefs()

        if (diff.toInsert.isNotEmpty()) {
            songDao.insertSongs(diff.toInsert)
        }
        // Las actualizaciones van por @Update y NO por insert(REPLACE): REPLACE es
        // DELETE + INSERT, y el ON DELETE CASCADE de song_folder_cross borraría la
        // pertenencia a carpetas aunque la canción siga existiendo con el mismo id.
        for (song in diff.toUpdate) {
            songDao.updateSong(song)
        }
    }

    suspend fun updateSong(song: Song) = songDao.updateSong(song)

    suspend fun toggleFavorite(songId: Long, isFavorite: Boolean) =
        songDao.updateFavoriteStatus(songId, isFavorite)

    suspend fun deleteSong(song: Song) = songDao.deleteSong(song)

    // --- FOLDER OPERATIONS ---
    fun getAllFolders(): Flow<List<Folder>> = folderDao.getAllFolders()

    suspend fun getFolderById(id: Long): Folder? = folderDao.getFolderById(id)

    suspend fun insertFolder(folder: Folder): Long = folderDao.insertFolder(folder)

    suspend fun updateFolder(folder: Folder) = folderDao.updateFolder(folder)

    suspend fun deleteFolder(folder: Folder) = folderDao.deleteFolder(folder)

    // --- CROSS REF (song <-> folder) ---
    suspend fun addSongToFolder(songId: Long, folderId: Long) {
        // Quitar de todas las carpetas primero (una canción = una carpeta principal por ahora)
        crossRefDao.removeSongFromAllFolders(songId)
        crossRefDao.insert(SongFolderCrossRef(songId, folderId))
    }

    suspend fun removeSongFromFolder(songId: Long) {
        crossRefDao.removeSongFromAllFolders(songId)
    }

    suspend fun getFolderIdsForSong(songId: Long): List<Long> =
        crossRefDao.getFoldersForSong(songId)

    suspend fun getFolderIdForSong(songId: Long): Long? =
        crossRefDao.getFoldersForSong(songId).firstOrNull()

    fun getSongsForFolder(folderId: Long): Flow<List<Long>> =
        crossRefDao.getSongsForFolder(folderId)
}

/** Resultado del diff entre lo ya guardado y lo que devuelve un escaneo. */
internal data class ScanDiff(
    /** Canciones nuevas: se insertan (no tienen id previo). */
    val toInsert: List<Song>,
    /** Canciones ya existentes cuyos metadatos cambiaron: se actualizan por id. */
    val toUpdate: List<Song>,
    /** Ids de las filas de MediaStore que ya no están en el dispositivo. */
    val removedIds: List<Long>
) {
    /** Todo lo que hay que escribir, sin distinguir altas de actualizaciones. */
    val toWrite: List<Song> get() = toInsert + toUpdate
}

/**
 * Calcula el diff entre las canciones de MediaStore ya guardadas y las de un escaneo nuevo.
 *
 * Función pura (no toca la base) para poder testearla sin Room ni emulador.
 *
 * Reglas:
 *  - La identidad de una canción es su `uri`, nunca su `id` (las de SAF entran con `id = 0`).
 *  - Una canción ya presente conserva su `id`, su `isFavorite` y su `genre`; sólo se refrescan
 *    los metadatos que vienen del dispositivo.
 *  - Sólo se purgan las que dejaron de aparecer en el escaneo.
 *  - Un escaneo vacío no purga nada (ver [refreshFromScan]).
 *
 * OJO con `isFavorite` y `genre`: NO entran en la comparación de "cambió". El scanner siempre
 * devuelve `isFavorite = false` y `genre = "Sin género"`, así que incluirlas haría que toda
 * canción favorite se reescribiera en cada arranque. Y como REPLACE es DELETE + INSERT, cada
 * reescritura dispara el `ON DELETE CASCADE` de `song_folder_cross` y borra la pertenencia a
 * carpetas. Por eso las actualizaciones van por `@Update` (que no borra la fila) y las filas
 * sin cambios no se tocan.
 */
internal fun computeScanDiff(
    existing: List<Song>,
    scanned: List<Song>,
    favoriteUris: Set<String>,
    presentUris: Set<String>? = null
): ScanDiff {
    // Guarda-corpus: un escaneo vacío casi siempre es un fallo (permiso denegado, MediaStore
    // no disponible) y no "el usuario borró su música". Si se purga aquí, se pierde todo.
    if (scanned.isEmpty()) return ScanDiff(emptyList(), emptyList(), emptyList())

    val scannedUris = scanned.mapTo(HashSet()) { it.uri }
    val existingByUri = existing.associateBy { it.uri }

    // Una canción está "presente" si la vimos en el escaneo Y, si el scanner nos pasó el
    // conjunto de filas que existen en MediaStore, si su uri sigue ahí. Sin lo segundo no hay
    // forma de distinguir "ya no está en el móvil" de "el indexador aún no ha rellenado sus
    // metadatos y el scanner la descartó por no poder medirla", y en el segundo caso borrarle
    // la fila sería perder la canción (y su carpeta) por un retardo del sistema.
    val removedIds = existing
        .filter { it.uri !in scannedUris && (presentUris == null || it.uri !in presentUris) }
        .map { it.id }

    val toInsert = mutableListOf<Song>()
    val toUpdate = mutableListOf<Song>()

    for (fresh in scanned) {
        val previous = existingByUri[fresh.uri]
        when {
            // Nueva: hereda la marca de favorito si su uri estaba marcada.
            previous == null -> toInsert.add(fresh.copy(isFavorite = fresh.uri in favoriteUris))

            // Sin cambios en los metadatos que aporta el dispositivo: no se toca.
            previous.title == fresh.title &&
                previous.artist == fresh.artist &&
                previous.album == fresh.album &&
                previous.durationMs == fresh.durationMs &&
                previous.coverUri == fresh.coverUri -> Unit

            // Actualización: conserva id, favorito, género y origen previos.
            else -> toUpdate.add(
                fresh.copy(
                    id = previous.id,
                    isFavorite = previous.isFavorite,
                    genre = previous.genre,
                    source = previous.source
                )
            )
        }
    }

    return ScanDiff(toInsert = toInsert, toUpdate = toUpdate, removedIds = removedIds)
}

/**
 * Descarta de [incoming] las canciones que ya existen en [existing].
 *
 * Compara por `uri` primero (barato y exacto) y, si no coincide, por metadatos: título,
 * artista y duración redondeada. La URI no basta porque MediaStore y SAF describen el mismo
 * archivo con URIs distintas, y sin esto la misma canción aparecía dos veces en la biblioteca.
 *
 * Función pura para poder testearla sin Room.
 */
internal fun filterNewSongs(incoming: List<Song>, existing: List<Song>): List<Song> {
    val existingUris = existing.mapTo(HashSet()) { it.uri }
    val existingKeys = existing.mapTo(HashSet()) { it.identityKey() }

    val result = mutableListOf<Song>()
    val seenKeys = HashSet<String>()
    for (song in incoming) {
        val key = song.identityKey()
        if (song.uri in existingUris) continue
        if (key in existingKeys) continue
        if (!seenKeys.add(key)) continue // duplicado dentro del propio lote
        result.add(song)
    }
    return result
}

/**
 * Clave de identidad por metadatos.
 *
 * La duración se redondea a 5 s porque el mismo archivo puede reportar duraciones que difieren
 * en unos milisegundos según el extractor (MediaStore vs MediaMetadataRetriever).
 */
private fun Song.identityKey(): String {
    val normalizedTitle = title.trim().lowercase()
    val normalizedArtist = artist.trim().lowercase()
    val roundedDuration = durationMs / 5_000L
    return "$normalizedTitle|$normalizedArtist|$roundedDuration"
}
