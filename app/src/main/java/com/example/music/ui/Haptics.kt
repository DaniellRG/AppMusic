package com.example.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Vibraciones de la interfaz.
 *
 * Se agrupan aquí en vez de repetir `LocalHapticFeedback.current` en cada pantalla, y sobre todo
 * para que los tipos no se inventen uno a uno: `LongPress` suena fuerte y se reserva para pulsar
 * el play (la acción que más se repite), mientras que el resto usa `TextHandleMove`, que es un
 * toque casi imperceptible y no cansa al marcar muchas canciones seguidas.
 */
class Haptics(private val feedback: HapticFeedback) {

    /** Acción principal: play/pausa. Temblor marcado. */
    fun playPause() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)

    /** Cambio de canción, saltar, cerrar hoja. Toque corto. */
    fun light() = feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    /** Marcar favorito, cambiar opción de un menú. Toque corto. */
    fun selection() = feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}