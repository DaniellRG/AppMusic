package com.example.music.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest

/** Una vuelta completa del disco. Lento a propósito: más rápido marea. */
private const val VINYL_PERIOD_MS = 20_000

/** La portada ocupa el centro del disco, como la etiqueta de un vinilo real. */
private const val LABEL_FRACTION = 0.62f

/**
 * Carátula que gira como un vinilo mientras suena.
 *
 * El disco negro es lo que gira; la portada queda encima, centrada, como la etiqueta. Es al
 * revés de lo obvio (girar la imagen square) por dos motivos:
 *
 *  1. Una imagen cuadrada al rotar deja las cuatro esquinas vacías y se ven como triángulos
 *     negros. Al ser cuadrada, sus esquinas sobresalen siempre del círculo, así que el disco
 *     queda redondo pase lo que pase.
 *  2. Muchas carátulas son degradados radiales: al rotarlas no cambia ni un píxel y el
 *     disco parece parado. Por eso el brillo especular y los surcos van en el disco, que sí
 *     se ven girar aunque la portada sea un color plano.
 *
 * El ángulo es un único valor en infiniteRepeatable: al pausar se congela solo sin guardar
 * estado a mano.
 */
@Composable
fun VinylArtwork(
    model: Any?,
    title: String,
    isPlaying: Boolean,
    fallbackColor: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "vinilo")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(VINYL_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "giro"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // El respaldo se dibuja SIEMPRE por debajo, no solo cuando model == null. Antes, si la
        // carga fallaba (permiso denegado, 404, red caída) el AsyncImage no pintaba nada y se
        // veía un cuadrado negro: un fallo de red parecía un disco parado.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(fallbackColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                tint = Color.White.copy(alpha = 0.35f)
            )
        }

        // Disco: gira con la música. El brillo es lo que hace visible el movimiento.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(if (isPlaying) angle else 0f)
                .clip(CircleShape)
                .background(Color(0xFF151515))
                .drawBehind {
                    val r = size.minDimension / 2f
                    val c = center

                    // Surcos concéntricos, con el hueco central de la etiqueta.
                    for (i in 1..13) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.05f),
                            radius = r * (LABEL_FRACTION / 2f + 0.036f * i),
                            center = c,
                            style = Stroke(width = 1.2f)
                        )
                    }

                    // Brillo especular: un arco desplazado del centro, asimétrico a propósito.
                    drawArc(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.22f),
                                Color.White.copy(alpha = 0.05f)
                            ),
                            start = Offset(c.x - r, c.y - r),
                            end = Offset(c.x + r, c.y + r)
                        ),
                        startAngle = -55f,
                        sweepAngle = 75f,
                        useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r),
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(width = r * 0.26f)
                    )
                }
        )

        // Portada encima, como la etiqueta. Si no hay portada, el disco se ve solo.
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxSize(LABEL_FRACTION)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/**
 * Color dominante de la carátula, para teñir la interfaz con el color del álbum.
 *
 * Se extrae con [Palette] en vez de inventar una paleta fija: es lo que hace que dos canciones
 * del mismo género se vean distintas al reproducirse. Devuelve null mientras carga o si no hay
 * portada, para que la UI pueda seguir con el acento global sin parpadear.
 */
@Composable
fun rememberArtworkAccent(model: Any?): Color? {
    val context = LocalContext.current
    var accent by remember(model) { mutableStateOf<Color?>(null) }

    LaunchedEffect(model) {
        if (model == null) {
            accent = null
            return@LaunchedEffect
        }
        val drawable: Drawable? = runCatching {
            val request = ImageRequest.Builder(context)
                .data(model)
                // Palette necesita leer los píxeles, y un bitmap de hardware no se puede leer.
                .allowHardware(false)
                .build()
            context.imageLoader.execute(request).drawable
        }.getOrNull()

        val bitmap = runCatching {
            drawable?.toBitmap()?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
        }.getOrNull()

        if (bitmap == null) {
            accent = null
            return@LaunchedEffect
        }

        accent = runCatching {
            // En palette-ktx 1.0.0 Palette no es Closeable, así que no se puede usar `use {}`.
            Palette.from(bitmap)
                .clearFilters()
                .maximumColorCount(24)
                .generate()
                .let { palette ->
                    // vibrantSwatch suele ser null en carátulas casi grises, por eso el
                    // respaldo a mutedSwatch; y si tampoco hay, null para no inventar color.
                    (palette.vibrantSwatch ?: palette.mutedSwatch)?.rgb?.let { Color(it) }
                }
        }.getOrNull()
    }

    return accent
}