package com.example.music.database

import androidx.room.*
import com.example.music.model.SongFolderCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface SongFolderCrossRefDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(crossRef: SongFolderCrossRef)

    @Delete
    suspend fun delete(crossRef: SongFolderCrossRef)

    @Query("SELECT folderId FROM song_folder_cross WHERE songId = :songId")
    suspend fun getFoldersForSong(songId: Long): List<Long>

    @Query("SELECT songId FROM song_folder_cross WHERE folderId = :folderId")
    fun getSongsForFolder(folderId: Long): Flow<List<Long>>

    @Query("DELETE FROM song_folder_cross WHERE songId = :songId")
    suspend fun removeSongFromAllFolders(songId: Long)

    @Query("DELETE FROM song_folder_cross")
    suspend fun deleteAllCrossRefs()

    /**
     * Elimina las referencias huérfanas: filas cuyo songId ya no existe en `songs`.
     * Necesario tras un re-escaneo, porque las canciones borradas dejan referencias colgantes
     * (y `songId` vuelve a numerarse al reinsertar, lo que cruzaría carpetas por error).
     */
    @Query("DELETE FROM song_folder_cross WHERE songId NOT IN (SELECT id FROM songs)")
    suspend fun deleteOrphanCrossRefs()
}
