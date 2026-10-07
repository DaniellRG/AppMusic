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
val BackgroundDark = Color(0xFF000000)
val SurfaceDark = Color(0xFF0A0A0A)
val SurfaceVariant = Color(0xFF151515)
val OnBackgroundDark = Color(0xFFE8E8E8)
val OnSurfaceDark = Color(0xFFE0E0E0)
val OnSurfaceVariant = Color(0xFFA0A0A0)

// --- ACENTO MORADO KURO ---
// El tema arranca en morado, no en lima: es el color de marca del proyecto de referencia y
// además aguanta mejor el degradado del reproductor, donde un lima saturado sobre carátulas
// claras se quemaba.
// Legacy Kuro-specific accent colors kept for compatibility but theme now uses
// Material colorScheme. No longer used as "magical" fixed values.
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
