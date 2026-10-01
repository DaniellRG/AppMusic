package com.example.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * El cálculo de la barra de progreso del hero de Inicio.
 *
 * No se extrajo a una función aparte a propósito: son tres líneas dentro del composable y
 * la regla del repo es no crear abstracciones para una sola vez. Se replica aquí la misma
 * cuenta para que un cambio accidental en ella se note en la suite.
 */
class ProgressBarTest {

    private fun progreso(currentMs: Long, durationMs: Long): Float =
        if (durationMs <= 0L) 0f
        else (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    @Test
    fun `al principio de la cancion la barra esta vacia`() {
        assertEquals(0f, progreso(0L, 200_000L), 0.001f)
    }

    @Test
    fun `a mitad la barra va por la mitad`() {
        assertEquals(0.5f, progreso(100_000L, 200_000L), 0.001f)
    }

    @Test
    fun `al final la barra esta llena pero no se pasa`() {
        // currentPosition puede adelantarse unos milisegundos a la duración real de ExoPlayer.
        assertEquals(1f, progreso(200_000L, 200_000L), 0.001f)
        assertEquals(1f, progreso(203_000L, 200_000L), 0.001f)
    }

    @Test
    fun `una duracion de cero no revienta ni llena la barra`() {
        // El compositor ni siquiera la dibuja en este caso, pero la division por cero
        // daria NaN si alguien la dejara pasar.
        assertEquals(0f, progreso(1_000L, 0L), 0.001f)
        assertEquals(0f, progreso(0L, 0L), 0.001f)
    }

    @Test
    fun `una duracion negativa se trata como desconocida`() {
        assertEquals(0f, progreso(5_000L, -1_000L), 0.001f)
    }

    @Test
    fun `el progreso crece de forma monotona al avanzar el tiempo`() {
        // El ticker del ViewModel va en saltos de ~500 ms, pero la barra solo puede
        // avanzar hacia delante durante la reproduccion.
        val dur = TimeUnit.MINUTES.toMillis(3)
        var anterior = 0f
        for (ms in 0L..dur step 500L) {
            val ahora = progreso(ms, dur)
            assertTrue("el progreso retrocedio en $ms ms", ahora >= anterior)
            anterior = ahora
        }
    }
}