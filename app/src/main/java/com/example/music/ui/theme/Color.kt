package com.example.music.ui.theme

import androidx.compose.ui.graphics.Color

// --- PALETA PRINCIPAL ---
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6C5CE7)
val PurpleGrey40 = Color(0xFF6C5CE7)
val Pink40 = Color(0xFFEC4899)

// --- COLORES DE LA APP --
val BackgroundDark = Color(0xFF0F0F0F)
val SurfaceDark = Color(0xFF1A1A1A)
val SurfaceVariant = Color(0xFF252525)
val OnBackgroundDark = Color(0xFFE8E8E8)
val OnSurfaceDark = Color(0xFFE0E0E0)
val OnSurfaceVariant = Color(0xFFA0A0A0)

// --- ACENTO LIMA (dark theme) --
val AccentPrimary = Color(0xFF32FF7E)   // lima principal: play, nav activa, Favorito, progreso
val AccentSecondary = Color(0xFF69F0AE) // verde lima claro (primaryContainer)
val AccentTertiary = Color(0xFF00E5FF)  // cian suave (tertiary)
val SuccessGreen = Color(0xFF00B894)
val WarningOrange = Color(0xFFFDCB6E)
val ErrorRed = Color(0xFFD63031)

// --- COLORES PARA CATEGORÍAS / GÉNEROS ---
val GenreColors = mapOf(
    "Rock" to Color(0xFFE17055),
    "Pop" to Color(0xFF00CEC9),
    "Reggaeton" to Color(0xFFFF7675),
    "Jazz" to Color(0xFF6C5CE7),
    "Clsico" to Color(0xFF00B894),
    "Electrnica" to Color(0xFFA29BFE),
    "Rap/Hip Hop" to Color(0xFFFDCB6E),
    "Indie" to Color(0xFFFD79A8),
    "Cumbia" to Color(0xFFE17055),
    "Salsa" to Color(0xFFFF7675),
    "Sin gnero" to Color(0xFF636E72),
    "Otros" to Color(0xFF636E72)
)

fun getGenreColor(genre: String): Color {
    return GenreColors[genre] ?: GenreColors["Otros"]!!
}
