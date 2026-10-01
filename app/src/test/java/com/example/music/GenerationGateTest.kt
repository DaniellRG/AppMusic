package com.example.music.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cubre la condición de carrera de letras y portadas: al saltar entre canciones, una
 * respuesta lenta de la anterior NO debe pisar el estado de la actual.
 */
class GenerationGateTest {

    @Test
    fun `la primera peticion es valida nada mas emitirla`() {
        val gate = GenerationGate()
        assertTrue(gate.isCurrent(gate.next()))
    }

    @Test
    fun `una respuesta vieja se descarta al pedir otra cosa`() {
        val gate = GenerationGate()
        val primera = gate.next()   // letra de la cancion A
        gate.next()                // el usuario salta a la cancion B

        assertFalse(gate.isCurrent(primera))
    }

    @Test
    fun `varias respuestas viejas siguen descartadas, la ultima manda`() {
        val gate = GenerationGate()
        val tokens = List(5) { gate.next() }
        val ultima = tokens.last()

        tokens.dropLast(1).forEach { assertFalse("token $it deberia estar caducado", gate.isCurrent(it)) }
        assertTrue(gate.isCurrent(ultima))
    }

    @Test
    fun `el token se emite una vez por peticion y en orden`() {
        val gate = GenerationGate()
        assertEquals(1, gate.next())
        assertEquals(2, gate.next())
        assertEquals(3, gate.next())
    }

    @Test
    fun `descartar una respuesta vieja no reactiva la peticion`() {
        val gate = GenerationGate()
        val primera = gate.next()
        val segunda = gate.next()

        // La respuesta de la A llega tarde y se descarta...
        assertFalse(gate.isCurrent(primera))
        // ...pero la de la B sigue siendo la buena: el descarte no libera nada.
        assertTrue(gate.isCurrent(segunda))
    }
}