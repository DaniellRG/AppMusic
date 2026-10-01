package com.example.music

import com.example.music.model.Song
import com.example.music.ui.viewmodel.necesitaPortada
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Qué canciones disparan una búsqueda de portada en iTunes.
 *
* Cada fallo aquí significa una descarga de red innecesaria, y en una biblioteca grande
 * se multiplica: son cientos de peticiones a un API público que acaba limitando la tasa.
 */
class NecesitaPortadaTest {

    private fun cancion(coverUri: String? = null, id: Long = 1L) = Song(
        id = id,
        title = "Faouzia",
        artist = "Artista",
        album = "Album",
        durationMs = 100_000L,
        uri = "content://x/$id",
        coverUri = coverUri
    )

    @Test
    fun `sin portada hay que buscarla`() {
        assertTrue(necesitaPortada(cancion(coverUri = null)))
    }

    @Test
    fun `con portada ya resuelta no hay que buscarla`() {
        assertFalse(
            necesitaPortada(cancion(coverUri = "content://media/external/audio/albumart/12"))
        )
    }

    /**
     * El caso que motivó la función. Con `coverUri == null` en vez de `isNullOrBlank()`,
     * esta canción contaba como resuelta y se quedaba con la nota musical para siempre.
     */
    @Test
    fun `cadena vacia cuenta como falta de portada`() {
        assertTrue(necesitaPortada(cancion(coverUri = "")))
    }

    @Test
    fun `solo espacios cuentan como falta de portada`() {
        assertTrue(necesitaPortada(cancion(coverUri = "   ")))
    }

    /**
     * El escaneo de SAF deja el URI a null aunque la canción traiga arte embebido en el
     * fichero, así que la búsqueda tiene que hacerse igual. Si esto devolviera false,
     * las canciones importadas por carpeta se quedarían siempre sin carátula.
     */
    @Test
    fun `una cancion de SAF con uri vacio si se busca`() {
        val deSaf = cancion(coverUri = null, id = 99L)
        assertTrue(necesitaPortada(deSaf))
    }
}