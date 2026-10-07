package com.example.music.database

import androidx.room.*
import com.example.music.model.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Long): Song?

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'")
    fun searchSongs(query: String): Flow<List<Song>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: Song): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)

    @Update
    suspend fun updateSong(song: Song)

    /**
     * Nº de filas guardadas.
     *
     * Lo usa el arranque para decidir si merece la pena escanear: si la base ya tiene filas y la
     * huella de MediaStore no ha cambiado, no hay nada nuevo que traer.
     */
    @Query("SELECT COUNT(*) FROM songs")
    suspend fun count(): Int

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :songId")
    suspend fun updateFavoriteStatus(songId: Long, isFavorite: Boolean)

    @Delete
    suspend fun deleteSong(song: Song)

    @Query("DELETE FROM songs")
    suspend fun deleteAllSongs()

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteSongsByIds(ids: List<Long>)

    /** Canciones ya guardadas de un origen concreto, para calcular el diff del escaneo. */
    @Query("SELECT * FROM songs WHERE source = :source")
    suspend fun getSongsBySource(source: String): List<Song>

    @Query("SELECT uri FROM songs")
    suspend fun getAllUris(): List<String>

    /** URIs marcadas como favorito; usado para re-aplicar la marca tras un re-escaneo. */
    @Query("SELECT uri FROM songs WHERE isFavorite = 1")
    suspend fun getFavoriteUris(): List<String>

    /**
     * Guarda la portada por URI y no por id.
     *
     * El id que devuelve el escáner es el de MediaStore y no coincide con el `id` de Room,
     * que es el que tiene la fila. La URI sí es la misma en los dos lados.
     */
    @Query("UPDATE songs SET coverUri = :coverUri WHERE uri = :songUri")
    suspend fun updateCoverByUri(songUri: String, coverUri: String?): Int

    @Query("SELECT * FROM songs WHERE uri = :uri LIMIT 1")
    suspend fun getSongByUri(uri: String): Song?
}
