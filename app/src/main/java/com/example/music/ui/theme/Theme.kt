package com.example.music.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.music.settings.AppSettings

/**
 * Construye el esquema de color a partir de un único acento.
 *
 * Antes el tema era un [androidx.compose.material3.ColorScheme] fijo compilado en el código,
 * así que cambiar el acento no se podía. Ahora todo se deriva de [accent], que es lo que hace
 * que la app pueda tener un color propio sin rehacer el tema entero.
 */
private fun musicColorScheme(accent: Color, pureBlack: Boolean): androidx.compose.material3.ColorScheme {
    // Negro puro en OLED: cada píxel apagado no enciende, y además el reproductor ya es negro
    // de fondo, así que las transiciones no dan un salto de gris.
    val background = if (pureBlack) Color.Black else Color(0xFF121014)
    val surface = if (pureBlack) Color(0xFF0B0B0D) else Color(0xFF1B1A20)
    val surfaceVariant = if (pureBlack) Color(0xFF17171B) else Color(0xFF26242C)

    // El acento se usa tal cual como primary y, aclarado, como tertiary: da contraste para los
    // estados activos (repetir, aleatorio, favorito) sin inventar una segunda paleta.
    val tertiary = Color(
        red = (accent.red + 0.25f).coerceAtMost(1f),
        green = (accent.green + 0.25f).coerceAtMost(1f),
        blue = (accent.blue + 0.25f).coerceAtMost(1f),
        alpha = 1f
    )
    val secondary = Color(
        red = accent.red * 0.72f,
        green = accent.green * 0.72f,
        blue = accent.blue * 0.72f,
        alpha = 1f
    )

    return darkColorScheme(
        primary = accent,
        onPrimary = readableOn(accent),
        primaryContainer = secondary,
        onPrimaryContainer = background,
        secondary = secondary,
        onSecondary = readableOn(secondary),
        secondaryContainer = accent,
        onSecondaryContainer = readableOn(accent),
        tertiary = tertiary,
        onTertiary = readableOn(tertiary),
        background = background,
        onBackground = OnBackgroundDark,
        surface = surface,
        onSurface = OnSurfaceDark,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = OnSurfaceVariant,
        error = ErrorRed,
        onError = OnBackgroundDark
    )
}

/**
 * Negro o blanco según el fondo, para que el texto del botón principal nunca se perda.
 *
 * Se usa luminancia perceived en vez de un if/else de brillo: el rosa del reproductor
 * (rgb 227,42,118) tiene brillo alto pero ojo humano bajo, y con un if naïve salía texto
 * negro sobre rosa y no se leía.
 */
private fun readableOn(color: Color): Color {
    // Contraste real (WCAG >= 4.5:1). El blanco aporta >= 4.5:1 solo cuando el acento es
    // muy oscuro (L < ~0.18); por encima el negro es siempre más legible (9:1+).
    // El umbral viejo (0.55) dejaba texto blanco sobre acentos medios como rosa, fénix,
    // esmeralda o rojo a 2.0-2.3:1: ilegible. Verificado con las 10 paletas de AppSettings.
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return if (luminance < 0.18f) Color.White else Color(0xFF101014)
}

@Composable
fun MusicTheme(
    accentColor: Int = AppSettings.DEFAULT_ACCENT,
    pureBlack: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = musicColorScheme(Color(accentColor), pureBlack)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // No se tocan statusBarColor/navigationBarColor: están deprecated desde la API 35 y
            // la app ya usa enableEdgeToEdge(), que hace que las barras se dibujen transparentes.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}