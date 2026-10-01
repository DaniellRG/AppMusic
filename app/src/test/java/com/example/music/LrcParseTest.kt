package com.example.music

import com.example.music.network.LyricsApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parseo de letras LRC sincronizadas.
 *
 * El motivo de que exista: sin parsear, la letra sincronizada de lrclib se mostraba tal
 * cual y el usuario leía "[00:32.50] Y esto es un amor" con los números dentro.
 */
class LrcParseTest {

    @Test
    fun `una linea con un solo tiempo`() {
        val lrc = LyricsApi.parseLrc("[00:12.34] Primera linea")
        assertEquals(1, lrc.size)
        assertEquals(12_340L, lrc[0].timeMs)
        assertEquals("Primera linea", lrc[0].text)
    }

    @Test
    fun `acepta centisegundos y milisegundos por igual`() {
        assertEquals(500L, LyricsApi.parseLrc("[00:00.50] x")[0].timeMs)
        assertEquals(500L, LyricsApi.parseLrc("[00:00.500] x")[0].timeMs)
        assertEquals(5L, LyricsApi.parseLrc("[00:00.005] x")[0].timeMs)
    }

    @Test
    fun `un digito de centisegundos vale por dos`() {
        // ".5" son 500 ms, no 5 ms: el separador decimal se come un digito.
        assertEquals(500L, LyricsApi.parseLrc("[00:00.5] x")[0].timeMs)
    }

    @Test
    fun `los segundos cuentan aparte de los centisegundos`() {
        // [00:01.5] es un minuto y medio de prueba de reloj: 1 s + 500 ms.
        assertEquals(1_500L, LyricsApi.parseLrc("[00:01.5] x")[0].timeMs)
        assertEquals(1_000L, LyricsApi.parseLrc("[00:01.00] x")[0].timeMs)
    }

    @Test
    fun `minutos y segundos se suman bien`() {
        assertEquals(62_000L, LyricsApi.parseLrc("[01:02.00] x")[0].timeMs)
        assertEquals(3_600_000L, LyricsApi.parseLrc("[60:00.00] x")[0].timeMs)
    }

    @Test
    fun `varios tiempos en la misma linea repiten el texto`() {
        // Es lo que hace que un estribillo suene dos veces en la misma LRC.
        val lrc = LyricsApi.parseLrc("[00:10.00][01:20.00] Estribillo")
        assertEquals(2, lrc.size)
        assertEquals(10_000L, lrc[0].timeMs)
        assertEquals(80_000L, lrc[1].timeMs)
        assertEquals("Estribillo", lrc[0].text)
        assertEquals("Estribillo", lrc[1].text)
    }

    @Test
    fun `descarta las etiquetas de metadatos`() {
        val crudo = """
            [ti:Un ethical]
            [ar:Faouzia]
            [al:Album]
            [length:03:21]
            [00:05.00] Primera linea real
        """.trimIndent()
        val lrc = LyricsApi.parseLrc(crudo)
        assertEquals(1, lrc.size)
        assertEquals("Primera linea real", lrc[0].text)
    }

    @Test
    fun `las lineas sin texto se conservan, para que el resaltado avance`() {
        val crudo = "[00:05.00] Canta\n[00:09.00]\n[00:12.00] Sigue"
        val lrc = LyricsApi.parseLrc(crudo)
        assertEquals(3, lrc.size)
        assertEquals("", lrc[1].text)
        assertEquals(9_000L, lrc[1].timeMs)
    }

    @Test
    fun `ordena por tiempo aunque venga desordenado`() {
        val lrc = LyricsApi.parseLrc("[00:30.00] Tercera\n[00:10.00] Primera\n[00:20.00] Segunda")
        assertEquals(listOf(10_000L, 20_000L, 30_000L), lrc.map { it.timeMs })
        assertEquals("Primera", lrc[0].text)
    }

    @Test
    fun `una letra de texto plano no tiene tiempos`() {
        val lrc = LyricsApi.parseLrc("Solo texto plano\nen dos lineas")
        assertTrue(lrc.isEmpty())
        assertFalse(LyricsApi.isSynced("Solo texto plano\nen dos lineas"))
    }

    @Test
    fun `texto vacio o basura no revienta`() {
        assertTrue(LyricsApi.parseLrc("").isEmpty())
        assertTrue(LyricsApi.parseLrc("\n\n\n").isEmpty())
        assertTrue(LyricsApi.parseLrc("[]\n[:.]\n[aa:bb]").isEmpty())
    }

    @Test
    fun `rechaza segundos imposibles`() {
        // 99 segundos no es un minuto: es basura, y colarse aqui correria el resaltado.
        assertTrue(LyricsApi.parseLrc("[00:99.00] x").isEmpty())
    }

    @Test
    fun `detecta si hay letra sincronizada`() {
        assertTrue(LyricsApi.isSynced("[00:01.00] hola"))
        assertFalse(LyricsApi.isSynced("hola"))
    }

    @Test
    fun `el texto tras el timestamp conserva los corchetes del interior`() {
        // El texto puede traer sus propios caracteres; solo se corta en el ultimo tiempo.
        val lrc = LyricsApi.parseLrc("[00:01.00] [corchete] al principio")
        assertEquals("[corchete] al principio", lrc[0].text)
    }

    @Test
    fun `payload real de lrclib se parsea bien`() {
        // Cortado de la respuesta real de lrclib.net para "Queen - Bohemian Rhapsody".
        // lrclib SIEMPRE manda centisegundos (2 digitos), asi que esto es el caso de verdad,
        // no uno inventado.
        val real = """
            [00:00.15] Is this the real life? Is this just fantasy?
            [00:07.13] Caught in a landslide, no escape from reality
            [00:25.37] I'm just a poor boy, I need no sympathy
        """.trimIndent()
        val lrc = LyricsApi.parseLrc(real)
        assertEquals(3, lrc.size)
        assertEquals(150L, lrc[0].timeMs)
        assertEquals(7_130L, lrc[1].timeMs)
        assertEquals(25_370L, lrc[2].timeMs)
        assertEquals("Is this the real life? Is this just fantasy?", lrc[0].text)
        assertTrue(LyricsApi.isSynced(real))
    }
}