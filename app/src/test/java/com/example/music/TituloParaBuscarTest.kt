package com.example.music

import com.example.music.ui.viewmodel.LyricsCache
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Normalización del título antes de buscar la letra.
 *
 * El motivo de que exista: lrclib guarda las canciones por su título "de verdad"
 * ("Bohemian Rhapsody"), pero el fichero que tienes en el móvil se llama
 * "Bohemian Rhapsody - Remastered 2011". Buscando el nombre tal cual no hay ningún
 * resultado y la app responde "no encontrada" en canciones que sí tienen letra. Estos
 * casos son los que más se dan en la práctica.
 */
class TituloParaBuscarTest {

    @Test
    fun `un titulo normal no se toca`() {
        assertEquals("bohemian rhapsody", LyricsCache.tituloParaBuscar("Bohemian Rhapsody"))
    }

    @Test
    fun `quita el sufijo entre parentesis`() {
        assertEquals(
            "bohemian rhapsody",
            LyricsCache.tituloParaBuscar("Bohemian Rhapsody (Remastered 2011)")
        )
    }

    @Test
    fun `quita el remix entre parentesis`() {
        assertEquals("shape of you", LyricsCache.tituloParaBuscar("Shape of You (Official Video)"))
    }

    @Test
    fun `quita el final con guion`() {
        assertEquals("hotel california", LyricsCache.tituloParaBuscar("Hotel California - Live"))
    }

    /**
     * Lo más común en ficheros descargados: el artista se cuelga dentro del título.
     * Sin quitarlo, la búsqueda es "weezer buddy holly" y no aparece nada.
     */
    @Test
    fun `quita el nombre del artista agregado`() {
        assertEquals(
            "buddy holly",
            LyricsCache.tituloParaBuscar("Buddy Holly feat. Someone")
        )
    }

    @Test
    fun `quita feat sin punto`() {
        assertEquals("buddy holly", LyricsCache.tituloParaBuscar("Buddy Holly ft X"))
    }

    /**
     * Títulos con puntos y guiones bajos, típicos de lo que baja de fuentes de
     * descarga. "Bohemian_Rhapsody.-.Remastered" tiene que acabar en "bohemian
     * rhapsody", no en un título rarísimo que no existe en el catálogo.
     */
    @Test
    fun `limpia puntos y guiones bajos`() {
        assertEquals("bohemian rhapsody", LyricsCache.tituloParaBuscar("Bohemian_Rhapsody.-.Remastered"))
    }

    @Test
    fun `colapsa espacios sobrantes`() {
        assertEquals("a b", LyricsCache.tituloParaBuscar("  A   B  "))
    }

        /**
     * Un título con todo a la vez.
     *
     * Fíjate en que "weezer - buddy holly" conserva el guion y el artista: ese mashup
     * existe en el catálogo con ese nombre exacto, así que quitar el " - " convertiría
     * la búsqueda en "weezer", que también existe y es otra canción con otra letra.
     * Solo se quita lo que va tras un guion cuando es una palabra de versión.
     */
    @Test
    fun `un titulo con todo a la vez`() {
        assertEquals(
            "weezer - buddy holly",
            LyricsCache.tituloParaBuscar("Weezer - Buddy Holly (Remastered 2004) feat. X")
        )
    }

    /** El mashup con guion se queda; el sufijo de versión detrás de un guion, no. */
    @Test
    fun `el guion del mashup se conserva y el remix del final se quita`() {
        assertEquals(
            "weezer - buddy holly",
            LyricsCache.tituloParaBuscar("Weezer - Buddy Holly")
        )
        assertEquals(
            "hotel california",
            LyricsCache.tituloParaBuscar("Hotel California - 2011 Remaster")
        )
    }

    /**
     * Un título que es solo un sufijo no puede quedarse en cadena vacía: si se busca ""
     * lrclib devuelve la lista completa y la app enseñaría la letra de otra canción.
     */
    @Test
    fun `no devuelve cadena vacia`() {
        assertEquals("live", LyricsCache.tituloParaBuscar("(Live)"))
    }
}
