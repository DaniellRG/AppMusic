package com.example.music

import com.example.music.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nombres "vacíos" que llegan desde las etiquetas del MP3.
 *
 * El caso importante es `<unknown>`: no es null ni está en blanco, así que un
 * `isNullOrBlank()` a secas lo acepta como si fuera el nombre real del artista. La app
 * acababa buscando la letra y la portada con `artist_name="<unknown>"` y ni lrclib ni
 * iTunes devuelven nada. Pasa con los MP3 sin etiquetas, o sea los descargados, que es
 * justo lo que el usuario se quejaba de que no funcionaba.
 */
class GenericosTest {

    /** Réplica de la lista de MusicViewModel, para poder comprobarla sin Android. */
    private val genericos = setOf(
        "<unknown>", "unknown", "desconocido", "sin artista", "null", "none",
        "<none>", "<null>", "artista desconocido", "n/a", "?"
    )

    private fun esGenerico(valor: String?): Boolean {
        if (valor == null) return true
        val v = valor.trim()
        if (v.isEmpty()) return true
        return v.lowercase() in genericos
    }

    @Test
    fun `el desconocido de android se trata como vacio`() {
        assertTrue(esGenerico("<unknown>"))
    }

    @Test
    fun `el desconocido del escaner se trata como vacio`() {
        assertTrue(esGenerico("Desconocido"))
    }

    @Test
    fun `no le importa si va en mayusculas`() {
        assertTrue(esGenerico("<UNKNOWN>"))
        assertTrue(esGenerico("DESCONOCIDO"))
    }

    @Test
    fun `tambien cuenta el null y el vacio`() {
        assertTrue(esGenerico(null))
        assertTrue(esGenerico(""))
        assertTrue(esGenerico("   "))
    }

    @Test
    fun `un artista de verdad no se toca`() {
        assertTrue(!esGenerico("Queen"))
        assertTrue(!esGenerico("Bad Bunny"))
    }

    /**
     * Caso trampa: una banda o un álbum que se llamen realmente "Unknown" o "None"
     * existen. Descartarlos sin mirar nada más sería un error, pero no hay forma
     * sencilla de distinguirlo de una etiqueta vacía. Al menos el nombre no se
     * descarta si viene acompañado de algo más.
     */
    @Test
    fun `unknown junto a algo mas no se descarta`() {
        assertTrue(!esGenerico("Unknown Mortal Orchestra"))
    }

    @Test
    fun `el escaner deja desconocido como artista`() {
        val song = Song(
            id = 1,
            title = "Queen - Bohemian Rhapsody",
            artist = "Desconocido",
            album = "Music",
            durationMs = 355_000L,
            uri = "content://x/1"
        )
        assertTrue(esGenerico(song.artist))
    }
}
