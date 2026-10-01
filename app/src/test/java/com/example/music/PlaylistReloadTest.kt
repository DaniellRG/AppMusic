package com.example.music

import com.example.music.model.Song
import com.example.music.player.playlistNeedsReload
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La cola de ExoPlayer sólo debe reconstruirse cuando cambia algo que afecta a lo que suena.
 *
 * Motivo: [com.example.music.player.MusicManager.setPlayList] se llama en cada emisión del
 * Flow `allSongs`, y reconstruir la cola es un `setMediaItems` + `prepare()`, es decir un corte
 * de audio audible. Como `allSongs` reemite con cualquier escritura en la base, sin este filtro
 * marcar un favorito o recibir un rescan cortaba la música.
 */
class PlaylistReloadTest {

    private fun song(
        id: Long = 0,
        uri: String = "content://media/$id",
        title: String = "titulo",
        artist: String = "artista",
        album: String = "album",
        durationMs: Long = 100_000,
        genre: String = "Sin género",
        isFavorite: Boolean = false
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        uri = uri,
        coverUri = null,
        genre = genre,
        isFavorite = isFavorite,
        source = Song.SOURCE_MEDIASTORE
    )

    // --- lo que NO debe recargar la cola ---

    @Test
    fun `una lista identica no recarga`() {
        val cola = listOf(song(id = 1), song(id = 2))

        assertFalse(playlistNeedsReload(cola, cola.map { it }))
    }

    @Test
    fun `marcar un favorito no recarga la cola`() {
        val actual = listOf(song(id = 1, isFavorite = false), song(id = 2, isFavorite = false))
        val nuevo = listOf(song(id = 1, isFavorite = true), song(id = 2, isFavorite = false))

        assertFalse(playlistNeedsReload(actual, nuevo))
    }

    @Test
    fun `cambiar favoritos de varias a la vez no recarga`() {
        val actual = listOf(song(id = 1), song(id = 2), song(id = 3))
        val nuevo = listOf(
            song(id = 1, isFavorite = true),
            song(id = 2, isFavorite = true),
            song(id = 3, isFavorite = true)
        )

        assertFalse(playlistNeedsReload(actual, nuevo))
    }

    // --- lo que SÍ debe recargarla ---

    @Test
    fun `una cancion nueva recarga`() {
        val actual = listOf(song(id = 1))
        val nuevo = listOf(song(id = 1), song(id = 2))

        assertTrue(playlistNeedsReload(actual, nuevo))
    }

    @Test
    fun `una cancion borrada recarga`() {
        val actual = listOf(song(id = 1), song(id = 2))
        val nuevo = listOf(song(id = 1))

        assertTrue(playlistNeedsReload(actual, nuevo))
    }

    @Test
    fun `reordenar la biblioteca recarga aunque sean los mismos ids`() {
        val actual = listOf(song(id = 1), song(id = 2))
        val nuevo = listOf(song(id = 2), song(id = 1))

        assertTrue(playlistNeedsReload(actual, nuevo))
    }

    @Test
    fun `cambiar la duracion recarga`() {
        val actual = listOf(song(id = 1, durationMs = 100_000))
        val nuevo = listOf(song(id = 1, durationMs = 200_000))

        assertTrue(playlistNeedsReload(actual, nuevo))
    }

    @Test
    fun `cambiar el titulo recarga porque la UI lo lee de la cola`() {
        val actual = listOf(song(id = 1, title = "Viejo"))
        val nuevo = listOf(song(id = 1, title = "Nuevo"))

        assertTrue(playlistNeedsReload(actual, nuevo))
    }

    @Test
    fun `cambiar la uri recarga`() {
        val actual = listOf(song(id = 1, uri = "content://a"))
        val nuevo = listOf(song(id = 1, uri = "content://b"))

        assertTrue(playlistNeedsReload(actual, nuevo))
    }

    @Test
    fun `cambiar el genero recarga`() {
        val actual = listOf(song(id = 1, genre = "Sin género"))
        val nuevo = listOf(song(id = 1, genre = "Pop"))

        assertTrue(playlistNeedsReload(actual, nuevo))
    }

    @Test
    fun `cargar la cola vacia siempre recarga`() {
        assertTrue(playlistNeedsReload(emptyList(), listOf(song(id = 1))))
    }
}