package com.example.music.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,            // URI de la canción en el dispositivo
    val coverUri: String? = null, // URI de la portada del álbum (opcional)
    val genre: String = "Sin género",
    val isFavorite: Boolean = false,
    val source: String = SOURCE_MEDIASTORE // de dónde salió: MediaStore o carpeta SAF
) {
    companion object {
        const val SOURCE_MEDIASTORE = "mediastore"
        const val SOURCE_SAF = "saf"
    }
}
