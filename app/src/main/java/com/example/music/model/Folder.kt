package com.example.music.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,        // Nombre de la carpeta / categoría
    val genre: String,       // Género asociado (ej: "Rock", "Reggaeton", "Jazz")
    val colorHex: String = "#6C5CE7" // Color identificativo
)
