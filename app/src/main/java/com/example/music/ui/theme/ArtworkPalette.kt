package com.example.music.ui.theme

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Paleta de la carátula con el color que usa el reproductor.
 *
 * Solo guarda el acento: el reproductor pinta el play, el progreso y los secundarios. El fondo
 * dejó de teñirse con la portada (ya no hay capa difuminada), así que un segundo color oscuro
 * solo sería trabajo de decodificación sin destino.
 *
 * @param accent color dominante claro de la carátula.
 */
data class ArtworkPalette(
    val accent: Color
)

/**
 * Color de acento extraído de la carátula de la canción que suena.
 *
 * Se usa [AccentPrimary] como valor inicial en vez de `null`: así el reproductor nunca se queda
 * sin botón de play y, mientras la paleta carga, se ve el acento por defecto en lugar de un hueco.
 */
@Composable
fun rememberArtworkAccent(uri: String?, fallback: Color = Color(0xFF8B5CF6)): State<Color> {
    val context = LocalContext.current
    val loader = remember(context) { ImageLoader(context) }
    return produceState(initialValue = fallback, uri, fallback, loader) {
        if (uri.isNullOrBlank()) {
            value = fallback
        } else {
            value = leerPaleta(context, loader, uri)?.accent ?: fallback
        }
    }
}

/**
 * Paleta completa de la carátula, para el reproductor y su fondo degradado.
 *
 * Separate de [rememberArtworkAccent] porque el fondoimmersivo necesita dos colores y no sólo
 * el dominante.
 */
@Composable
fun rememberArtworkPalette(uri: String?): State<ArtworkPalette?> {
    val context = LocalContext.current
    val loader = remember(context) { ImageLoader(context) }
    return produceState<ArtworkPalette?>(initialValue = null, uri, loader) {
        value = if (uri.isNullOrBlank()) null else leerPaleta(context, loader, uri)
    }
}

/**
 * Lee la paleta de una imagen local o remota.
 *
 * Dos detalles que no son opcionales:
 *
 *  - `allowHardware(false)`: los bitmaps por hardware no se pueden leer con `getPixels`, que es
 *    justo lo que hace Palette. Sin esto el resultado siempre es null y el reproductor se queda
 *    con el acento por defecto para siempre.
 *  - `size(128)`: se pide una miniatura. Palette sólo necesita el color dominante, así que
 *    decodificar la carátula a resolución completa era gastar memoria para nada.
 *
 * Se cachea por `uri` porque `produceState` no relanza la carga mientras la clave no cambie, y
 * volver a decodificar en cada recomposición era el coste más visible al saltar de canción.
 */
private suspend fun leerPaleta(
    context: Context,
    loader: ImageLoader,
    uri: String
): ArtworkPalette? = withContext(Dispatchers.IO) {
    runCatching {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false)
            .size(128)
            .build()
        val result = loader.execute(request) as? SuccessResult ?: return@runCatching null
        // OJO: aquí NO se recycle()a nada. `drawable.toBitmap()` sobre un BitmapDrawable de
        // ARGB_8888 devuelve el MISMO bitmap, no una copia, y ese bitmap lo tiene la caché de
        // Coil. Liberarlo aquí dejaba en el caché un bitmap reciclado y la siguiente pantalla
        // que lo pintara (AsyncImage) petaba con "Canvas: trying to use a recycled bitmap".
        // La caché de Coil es quien decide cuándo liberar sus bitmaps; aquí solo se leen.
        paletaDeBitmap(result.drawable.toBitmap())
    }.getOrNull()
}

/** Convierte un bitmap ya decodificado en la paleta que usa la interfaz. */
internal fun paletaDeBitmap(bitmap: Bitmap): ArtworkPalette? = runCatching {
    val palette = Palette.from(bitmap).maximumColorCount(24).generate()
    val respaldo = Color(0xFF8B5CF6).toArgb()
    // El dominante a secas suele ser un tono apagado (el fondo de la carátula), así que se
    // prefiere el vibrante y sólo se cae al mutado si la imagen es casi monocroma. Sin esto
    // el botón de play salía gris en la mayoría de portadas.
    val accent = palette.getVibrantColor(palette.getMutedColor(palette.getDominantColor(respaldo)))
    ArtworkPalette(accent = Color(accent))
}.getOrNull()