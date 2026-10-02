package com.example.music

import com.example.music.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Regresión del cambio de clave del fichero de letra.
 *
 * La clave incluía el hash del título además del de la URI. El efecto era que una
 * descarga se perdía en cuanto el título cambiaba, y el título cambia en el momento
 * menos pensado: el indexador de MediaStore termina de leer las etiquetas y corrige el
 * nombre, o el escáner normaliza "Artista - Título". La letra se volvía a pedir a lrclib
 * una y otra vez, que es justo lo que se pidió evitar.
 */
class ClaveLyricsTest {

    private fun song(id: Long = 34L, uri: String, title: String) =
        Song(id = id, title = title, artist = "a", album = "b", durationMs = 1L, uri = uri)

    @Test
    fun `el titulo no entra en la clave`() {
        val conRemaster = song(uri = "content://media/1", title = "Bohemian Rhapsody (Remastered 2011)")
        val limpio = song(uri = "content://media/1", title = "Bohemian Rhapsody")

        assertEquals(
            "el mismo fichero debe servir para dos títulos de la misma canción",
            clave(conRemaster),
            clave(limpio)
        )
    }

    @Test
    fun `la uri si entra en la clave`() {
        val una = song(uri = "content://media/1", title = "Igual")
        val otra = song(uri = "content://media/2", title = "Igual")

        assertNotEquals(
            "dos canciones distintas no pueden compartir fichero",
            clave(una),
            clave(otra)
        )
    }

    @Test
    fun `el nombre del fichero no lleva el titulo`() {
        val nombre = clave(song(uri = "content://media/1", title = "Bohemian Rhapsody (Remastered 2011)"))
        assertEquals(true, nombre.endsWith(".lrc"))
        assertEquals("sin espacios ni parentesis, que rompen en FAT32", true, nombre.none { it.isWhitespace() })
    }

    /** Replica de `LyricsCache.clave`, que es privada y necesita un Context. */
    private fun clave(s: Song): String {
        val uriHash = s.uri.hashCode().toUInt().toString(16)
        return "${s.id}_${uriHash}.lrc"
    }
}
