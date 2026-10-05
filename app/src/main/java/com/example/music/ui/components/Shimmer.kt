package com.example.music.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/**
 * Brillo que se desliza de lado a lado, para ocupar los huecos mientras carga algo.
 *
 * Es un degradado móvil, no un opacidad pulsante: se nota el "cargando" sin resultar molesto en
 * una lista con seis elementos a la vez. La duración es de 1200 ms porque por debajo de 1000 el
 * barrido se lee como un parpadeo y por encima se nota demasiado la pausa entre pasada y pasada.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape,
    baseColor: Color = Color(0xFF1C1C1C),
    highlightColor: Color = Color(0xFF2E2E2E)
) {
    val transicion = rememberInfiniteTransition(label = "shimmer")
    val progreso by transicion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgreso"
    )

    // El ancho del brillo es 3 veces el contenedor: al multiplicar por 3 se sale entera por los
    // dos lados y el salto del reinicio queda fuera de la vista.
    Box(
        modifier = modifier
            .clip(shape)
            .background(baseColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            highlightColor,
                            Color.Transparent
                        ),
                        start = Offset(progreso * 900f - 450f, 0f),
                        end = Offset(progreso * 900f + 450f, 450f)
                    )
                )
        )
    }
}

/**
 * Marca de carga con la forma y el tamaño de una portada.
 *
 * Es el caso particular de [ShimmerBox] que más se ve en la app: cada canción de la lista trae
 * su carátula, así que sin esto la pantalla tarda en arrancar mostrando once squares grises.
 */
@Composable
fun ArtworkShimmer(size: Dp, shape: Shape) {
    ShimmerBox(
        modifier = Modifier.size(size),
        shape = shape
    )
}