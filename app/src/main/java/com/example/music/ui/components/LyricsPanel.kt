package com.example.music.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Panel de letra con efecto glassmorphism.
 *
 * Diseño: fondo semitransparente + borde sutil → da profundidad sin oscurecer
 * la partitura/debajo que hay (el vinilo, el álbum). El blur REAL se aplica con
 * RenderEffect.createBlurEffect solo en API 31+ (S); en <31 se degrada gracefulmente
 * a fondo semitransp (el colorScheme sigue funcionando y el texto sigue legible).
 *
 * Recibe datos planos (text, isLoading, error) → 0 acupo con el ViewModel.
 * PlayerScreen (zona opencode) lo incrusta pasando:
 *   text = (LyricsUi.Success.text), isLoading = (LyricsUi.Loading), error = (LyricsUi.Error).
 * Previews: con letra y sin letra (placeholder).
 */
@Composable
fun LyricsPanel(
    // El Modifier va primero entre los parámetros opcionales: es la convención de Compose
    // (lint: ModifierParameter) y evita que un llamada añada banderas antes que el modificador.
    modifier: Modifier = Modifier,
    text: String? = null,
    isLoading: Boolean = false,
    error: String? = null
) {
    // Modifier.blur() sólo tiene efecto real en API 31+; por debajo queda el degradado
    // de fondo, que es el fallback visual. No hace falta el check porque el propio
    // Modifier ya es un no-op en versiones antiguas en vez de fallar.

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.verticalGradient(
                    0f to Color(0x33FFFFFF),
                    1f to Color(0x14000000)
                )
            )
            .border(width = 1.dp, color = Color(0x33FFFFFF), shape = RoundedCornerShape(20.dp))
            .blur(28.dp)
    ) {
        when {
            isLoading -> {
                Text(
                    "Cargando letra…",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            error != null -> {
                Text(
                    error ?: "Error cargando la letra",
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            !text.isNullOrBlank() -> {
                // SelectionContainer → ↑↓ para scroll + selección de texto.
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                // text == null && !isLoading && error == null → estado idle (sin letra todavía).
                Text(
                    "Toca el ícono de nota para ver la letra de esta canción",
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
