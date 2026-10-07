package com.example.music.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.music.settings.AppSettings
import material.DynamicScheme
import material.Hct
import material.MaterialDynamicColors
import material.SchemeTonalSpot

/**
 * Tema de la app.
 *
 * Todo el color sale de una única semilla y se deriva con las paletas tonales de Material (HCT),
 * en vez de calcular los colores Channels a mano. La razón práctica es que HCT mantiene la
 * luminancia perceptiva: el morado de marca queda igual de legible sobre negro que un rosa o un
 * lima, y los estados activo/inactivo no se descuadran al cambiar de tema.
 *
 * Nota: Kuro usa `MaterialExpressiveTheme` con `MotionScheme.expressive()`, pero esas APIs son
 * `internal` en material3 1.4.0 (la versión del Compose BOM de este proyecto) y solo se hicieron
 * públicas en 1.5. Se usa [MaterialTheme] para no subir el BOM a ciegas: el color y la tipografía,
 * que es lo que define el aspecto, sí son los del tema de referencia.
 */
@Composable
fun MusicTheme(
    accentColor: Int = AppSettings.DEFAULT_ACCENT,
    pureBlack: Boolean = true,
    content: @Composable () -> Unit
) {
    // El acento por portada (AppSettings.coverAccent) tiene prioridad sobre el de Ajustes: así el
    // reproductor y el reproductor compacto comparten el color de la carátula.
    val coverAccent by AppSettings.getInstance(LocalContext.current).coverAccent.collectAsState()
    val semilla: Color = coverAccent ?: Color(accentColor)

    val colorScheme = remember(semilla, pureBlack) {
        tonalColorScheme(semilla).negroPuro(pureBlack)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // No se tocan statusBarColor/navigationBarColor: están deprecated desde la API 35 y la
            // app usa enableEdgeToEdge(), que deja las barras transparentes.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KuroTypography,
        content = content
    )
}

/**
 * Genera el esquema tonal desde la semilla, en modo oscuro.
 *
 * Se usa [SchemeTonalSpot] con contraste 0.0 (el estándar de Material), que reparte el color en
 * tonos 0-100 y garantiza que los contenedores siempre contrasten con su contenido.
 */
private fun tonalColorScheme(semilla: Color): ColorScheme {
    val scheme = SchemeTonalSpot(Hct.fromInt(semilla.toArgb()), true, 0.0)
    val m = MaterialDynamicColors()
    return darkColorScheme(
        primary = m.primary().getArgb(scheme).toColor(),
        onPrimary = m.onPrimary().getArgb(scheme).toColor(),
        primaryContainer = m.primaryContainer().getArgb(scheme).toColor(),
        onPrimaryContainer = m.onPrimaryContainer().getArgb(scheme).toColor(),
        // secondary y tertiary se derivan de la semilla igual que el resto. Antes estaban
        // fijos al rosa de marca mientras secondaryContainer/tertiaryContainer sí se
        // derivaban: al cambiar el acento en Ajustes los contenedores cambiaban pero los
        // texto/iconos sobre ellos seguían rosas, y el par no contrastaba entre sí.
        secondary = m.secondary().getArgb(scheme).toColor(),
        onSecondary = m.onSecondary().getArgb(scheme).toColor(),
        secondaryContainer = m.secondaryContainer().getArgb(scheme).toColor(),
        onSecondaryContainer = m.onSecondaryContainer().getArgb(scheme).toColor(),
        tertiary = m.tertiary().getArgb(scheme).toColor(),
        onTertiary = m.onTertiary().getArgb(scheme).toColor(),
        tertiaryContainer = m.tertiaryContainer().getArgb(scheme).toColor(),
        onTertiaryContainer = m.onTertiaryContainer().getArgb(scheme).toColor(),
        error = m.error().getArgb(scheme).toColor(),
        onError = m.onError().getArgb(scheme).toColor(),
        errorContainer = m.errorContainer().getArgb(scheme).toColor(),
        onErrorContainer = m.onErrorContainer().getArgb(scheme).toColor(),
        background = m.background().getArgb(scheme).toColor(),
        onBackground = m.onBackground().getArgb(scheme).toColor(),
        surface = m.surface().getArgb(scheme).toColor(),
        onSurface = m.onSurface().getArgb(scheme).toColor(),
        surfaceVariant = m.surfaceVariant().getArgb(scheme).toColor(),
        onSurfaceVariant = m.onSurfaceVariant().getArgb(scheme).toColor(),
        outline = m.outline().getArgb(scheme).toColor(),
        outlineVariant = m.outlineVariant().getArgb(scheme).toColor(),
        surfaceContainerLowest = m.surfaceContainerLowest().getArgb(scheme).toColor(),
        surfaceContainerLow = m.surfaceContainerLow().getArgb(scheme).toColor(),
        surfaceContainer = m.surfaceContainer().getArgb(scheme).toColor(),
        surfaceContainerHigh = m.surfaceContainerHigh().getArgb(scheme).toColor(),
        surfaceContainerHighest = m.surfaceContainerHighest().getArgb(scheme).toColor(),
        inverseSurface = m.inverseSurface().getArgb(scheme).toColor(),
        inverseOnSurface = m.inverseOnSurface().getArgb(scheme).toColor(),
        inversePrimary = m.inversePrimary().getArgb(scheme).toColor()
    )
}

/**
 * Negro OLED: aplana todas las superficies a negro puro.
 *
 * No basta con poner `background` a negro. El reproductor y las hojas modales usan
 * `surfaceContainer*`, y si esas se quedan en gris se ve un rectángulo claro flotando sobre el
 * fondo negro. Por eso se aplanan también.
 */
private fun ColorScheme.negroPuro(apply: Boolean): ColorScheme =
    if (apply) {
        copy(
            surface = Color.Black,
            background = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainer = Color.Black,
            surfaceContainerHigh = Color.Black,
            surfaceContainerHighest = Color.Black
        )
    } else {
        this
    }

/** Convierte un int ARGB de material-color-utilities a [Color]. */
private fun Int.toColor(): Color = Color(this)