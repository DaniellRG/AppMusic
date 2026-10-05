package com.example.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.music.model.Song
import com.example.music.player.PlaybackState
import com.example.music.ui.rememberHaptics
import com.example.music.ui.screens.SongArtwork
import com.example.music.ui.theme.AccentPrimary
import com.example.music.ui.theme.SurfaceVariant
import com.example.music.ui.theme.rememberArtworkAccent

/**
 * Reproductor compacto que vive encima de la barra de navegación.
 *
 * El color del botón y del progreso sale de la carátula ([rememberArtworkAccent]), de modo que
 * la barra inferior cambia de tono con la canción sin dejar de leerse nunca: el icono
 * siempre lleva `Color.White` por encima.
 */
@Composable
fun MiniPlayer(
    currentSong: Song?,
    playbackState: PlaybackState,
    onPlayPauseClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentSong == null) return
    val haptics = rememberHaptics()
    val acento by rememberArtworkAccent(currentSong.coverUri)
    val progreso = if (playbackState.durationMs > 0L) {
        (playbackState.currentPositionMs.toFloat() / playbackState.durationMs.toFloat())
            .coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        // Semitransparente en vez de opaco: con el color de la canción, un negro plano
        // pesaba demasiado y rompía la sensación de "barra flotando" sobre el contenido.
        color = SurfaceVariant.copy(alpha = 0.92f),
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column {
            LinearProgressIndicator(
                progress = { progreso },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)),
                color = acento,
                trackColor = Color.Transparent,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SongArtwork(
                    song = currentSong,
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    iconSize = 20.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = currentSong.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentSong.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = {
                        haptics.playPause()
                        onPlayPauseClick()
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(acento, CircleShape)
                ) {
                    Icon(
                        if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pausar" else "Reproducir",
                        tint = Color.White
                    )
                }
            }
        }
    }
}