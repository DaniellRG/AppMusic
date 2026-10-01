package com.example.music.ui.preview

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.example.music.model.Song
import com.example.music.ui.screens.HomeScreen
import com.example.music.ui.components.VinylArtwork
import com.example.music.ui.theme.*

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    val sampleSongs = listOf(
        Song(title = "Bohemian Rhapsody", artist = "Queen", album = "A Night at the Opera", durationMs = 354000, uri = "", genre = "Rock", isFavorite = true),
        Song(title = "Shape of You", artist = "Ed Sheeran", album = "Ã· (Divide)", durationMs = 233000, uri = "", genre = "Pop", isFavorite = false),
        Song(title = "Despacito", artist = "Luis Fonsi", album = "Despacito", durationMs = 228000, uri = "", genre = "Reggaeton", isFavorite = true),
        Song(title = "Take Five", artist = "Dave Brubeck", album = "Time Out", durationMs = 324000, uri = "", genre = "Jazz", isFavorite = false),
        Song(title = "Clair de Lune", artist = "Debussy", album = "PrÃ©ludes", durationMs = 312000, uri = "", genre = "ClÃ¡sico", isFavorite = false)
    )
    val sampleFavorites = sampleSongs.filter { it.isFavorite }

    MusicTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BackgroundDark
        ) {
            HomeScreen(
                songs = sampleSongs,
                favorites = sampleFavorites,
                onSongClick = { },
                onSearchClick = { },
                onFavoritesClick = { },
                onFoldersClick = { },
                onSettingsClick = { }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PlayerScreenPreview() {
    val sampleSong = Song(
        title = "Bohemian Rhapsody",
        artist = "Queen",
        album = "A Night at the Opera",
        durationMs = 354000,
        uri = "",
        genre = "Rock",
        isFavorite = true
    )

    MusicTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BackgroundDark
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Vista previa simplificada
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(getGenreColor(sampleSong.genre))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = sampleSong.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = sampleSong.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        IconButton(
                            onClick = { },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = null,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        IconButton(
                            onClick = { },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.padding(14.dp),
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = { },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = null,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Previews de VinylArtwork: con carÃ¡tula (model no nula) y sin carÃ¡tula (model == null),
 * sonando y pausado. Verifican el giro, el brillo especular, los surcos y el fallback
 * de gÃ©nero en ambos estados.
 */
@Preview(showBackground = true, name = "VinylArtwork con carÃ¡tula, sonando")
@Composable
fun VinylArtworkWithCoverPreview() {
    MusicTheme {
        Box(Modifier.size(180.dp)) {
            VinylArtwork(
                model = Color(0xFF4A90E2),          // color sÃ³lido simula "carÃ¡tula cargada"
                title = "Bohemian Rhapsody",
                isPlaying = true,
                fallbackColor = getGenreColor("Rock"),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Preview(showBackground = true, name = "VinylArtwork sin carÃ¡tula, pausado")
@Composable
fun VinylArtworkNoCoverPreview() {
    MusicTheme {
        Box(Modifier.size(180.dp)) {
            VinylArtwork(
                model = null,
                title = "Sin carÃ¡tula",
                isPlaying = false,
                fallbackColor = getGenreColor("Otros"),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
