package com.example.music

import com.example.music.model.Song
import com.example.music.ui.screens.OrdenBiblioteca
import com.example.music.ui.screens.ordenarBiblioteca
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Orden de la biblioteca en Inicio.
 *
 * Lo importante aquí no es que "titulo" ordene bien, sino que el orden sea DETERMINISTA:
 * si dos canciones comparten artista o álbum y el desempate no es estable, las filas se
 * reordenan solas al recargar y el usuario ve saltos.
 */
class OrdenBibliotecaTest {

    private fun cancion(
        titulo: String,
        artista: String = "Artista",
        album: String = "Album",
        duracion: Long = 100_000L,
        id: Long = 0L
    ) = Song(
        id = id,
        title = titulo,
        artist = artista,
        album = album,
        durationMs = duracion,
        uri = "content://x/$titulo"
    )

    private val biblioteca = listOf(
        cancion("Zeta", artista = "Beta", album = "Uno", duracion = 300_000L, id = 3),
        cancion("alfa", artista = "Alfa", album = "Dos", duracion = 100_000L, id = 1),
        cancion("Media", artista = "Gamma", album = "Uno", duracion = 200_000L, id = 2)
    )

    private fun titulos(orden: OrdenBiblioteca) =
        ordenarBiblioteca(biblioteca, orden).map { it.title }

    @Test
    fun `por titulo ordena alfabeticamente y sin distinguir mayusculas`() {
        // "alfa" va antes que "Media" y que "Zeta": si fuera sensible a mayúsculas,
        // las mayúsculas irían todas primero y la lista quedaría Zeta/Media/alfa.
        assertEquals(listOf("alfa", "Media", "Zeta"), titulos(OrdenBiblioteca.TITULO))
    }

    @Test
    fun `por artista agrupa por artista`() {
        assertEquals(listOf("alfa", "Zeta", "Media"), titulos(OrdenBiblioteca.ARTISTA))
    }

    @Test
    fun `por album agrupa por album`() {
        // Dos va antes que Uno (alfabeticamente), y dentro de Uno el desempate por
        // título deja Media antes que Zeta. Si el desempate no existiera, este orden
        // dependería de cómo venga la lista de la base.
        assertEquals(listOf("alfa", "Media", "Zeta"), titulos(OrdenBiblioteca.ALBUM))
    }

    @Test
    fun `por duracion va de mas corta a mas larga`() {
        assertEquals(listOf("alfa", "Media", "Zeta"), titulos(OrdenBiblioteca.DURACION))
    }

    @Test
    fun `por recientes usa el id, el mas nuevo primero`() {
        assertEquals(listOf("Zeta", "Media", "alfa"), titulos(OrdenBiblioteca.RECIENTE))
    }

    @Test
    fun `el desempate por titulo evita que las filas salten`() {
        // Mismo artista y mismo album: solo cambia el titulo. Si no hubiera desempate,
        // el orden de estas dos dependeria del algoritmo de ordenacion.
        val empatadas = listOf(
            cancion("Zeta", artista = "Mismo", album = "Mismo"),
            cancion("alfa", artista = "Mismo", album = "Mismo")
        )
        assertEquals(
            listOf("alfa", "Zeta"),
            ordenarBiblioteca(empatadas, OrdenBiblioteca.ARTISTA).map { it.title }
        )
        assertEquals(
            listOf("alfa", "Zeta"),
            ordenarBiblioteca(empatadas, OrdenBiblioteca.ALBUM).map { it.title }
        )
    }

    @Test
    fun `el orden es estable aunque se aplique dos veces`() {
        OrdenBiblioteca.entries.forEach { orden ->
            val una = ordenarBiblioteca(biblioteca, orden).map { it.title }
            val dos = ordenarBiblioteca(biblioteca, orden).map { it.title }
            assertEquals("el orden $orden no es estable", una, dos)
        }
    }

    @Test
    fun `no pierde ni duplica canciones en ningun criterio`() {
        OrdenBiblioteca.entries.forEach { orden ->
            val res = ordenarBiblioteca(biblioteca, orden)
            assertEquals("con $orden", biblioteca.size, res.size)
            assertEquals("con $orden", biblioteca.toSet(), res.toSet())
        }
    }

    @Test
    fun `no modifica la lista original`() {
        val original = biblioteca.map { it.title }
        OrdenBiblioteca.entries.forEach { ordenarBiblioteca(biblioteca, it) }
        assertEquals(original, biblioteca.map { it.title })
    }

    @Test
    fun `biblioteca vacia o de una sola pista no revienta`() {
        assertTrue(ordenarBiblioteca(emptyList(), OrdenBiblioteca.TITULO).isEmpty())
        assertEquals(1, ordenarBiblioteca(listOf(cancion("solo")), OrdenBiblioteca.ALBUM).size)
    }
}