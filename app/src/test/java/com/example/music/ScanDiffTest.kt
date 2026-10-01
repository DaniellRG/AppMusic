package com.example.music

import com.example.music.model.Song
import com.example.music.repository.computeScanDiff
import com.example.music.repository.filterNewSongs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del diff de escaneo. Es lÃ³gica pura, asÃ­ que corre en el host sin Room ni emulador.
 *
 * Contexto: antes, `refreshFromScan` hacÃ­a `deleteAll` + reinsert y por eso se perdÃ­an los
 * favoritos y las canciones importadas por SAF en cada arranque de la app.
 */
class ScanDiffTest {

    private fun song(
        id: Long = 0,
        uri: String,
        title: String = "titulo",
        artist: String = "artista",
        album: String = "album",
        durationMs: Long = 100_000,
        coverUri: String? = null,
        genre: String = "Sin gÃ©nero",
        isFavorite: Boolean = false,
        source: String = Song.SOURCE_MEDIASTORE
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        uri = uri,
        coverUri = coverUri,
        genre = genre,
        isFavorite = isFavorite,
        source = source
    )

    @Test
    fun `no escribe nada cuando el escaneo no trae cambios`() {
        val stored = song(id = 7, uri = "content://a", title = "igual")
        val scanned = song(id = 7, uri = "content://a", title = "igual")

        val diff = computeScanDiff(listOf(stored), listOf(scanned), emptySet())

        assertTrue("no debe reescribir filas sin cambios", diff.toWrite.isEmpty())
        assertTrue(diff.removedIds.isEmpty())
    }

    @Test
    fun `conserva el favorito cuando el scanner devuelve isFavorite false`() {
        val stored = song(id = 7, uri = "content://a", isFavorite = true)
        val scanned = song(id = 7, uri = "content://a", isFavorite = false, title = "metadato nuevo")

        val diff = computeScanDiff(listOf(stored), listOf(scanned), setOf("content://a"))

        assertEquals(1, diff.toWrite.size)
        assertTrue("el favorito no debe perderse", diff.toWrite.first().isFavorite)
    }

    /**
     * RegresiÃ³n: `insertSongs` usa REPLACE (= DELETE + INSERT), y el `ON DELETE CASCADE` de
     * `song_folder_cross` borraba la pertenencia a carpetas en cada arranque. Como el scanner
     * siempre devuelve `isFavorite = false`, la favorita se reescribÃ­a siempre. Si los metadatos
     * del dispositivo no han cambiado, no debe escribirse nada.
     */
    @Test
    fun `no reescribe una favorita cuando el dispositivo no aporto cambios`() {
        val stored = song(id = 7, uri = "content://a", isFavorite = true, genre = "Rock")
        val scanned = song(id = 7, uri = "content://a", isFavorite = false, genre = "Sin gÃ©nero")

        val diff = computeScanDiff(listOf(stored), listOf(scanned), setOf("content://a"))

        assertTrue(
            "reescribir dispararÃ­a el CASCADE y perderÃ­a la carpeta",
            diff.toWrite.isEmpty()
        )
    }

    @Test
    fun `tampoco reescribe por un cambio de genero hecho por el usuario`() {
        val stored = song(id = 7, uri = "content://a", genre = "Reggaeton")
        val scanned = song(id = 7, uri = "content://a", genre = "Sin gÃ©nero")

        val diff = computeScanDiff(listOf(stored), listOf(scanned), emptySet())

        assertTrue(diff.toWrite.isEmpty())
    }

    @Test
    fun `conserva el id previo para no romper la referencia desde las carpetas`() {
        val stored = song(id = 42, uri = "content://a")
        val scanned = song(id = 0, uri = "content://a", title = "otro titulo")

        val diff = computeScanDiff(listOf(stored), listOf(scanned), emptySet())

        assertEquals(42L, diff.toWrite.first().id)
    }

    @Test
    fun `marca como favorita una cancion nueva cuya uri ya estaba marcada`() {
        val scanned = song(uri = "content://nueva", isFavorite = false)

        val diff = computeScanDiff(emptyList(), listOf(scanned), setOf("content://nueva"))

        assertTrue(diff.toWrite.first().isFavorite)
    }

    @Test
    fun `una cancion nueva sin marca previa no nace favorita`() {
        val scanned = song(uri = "content://nueva", isFavorite = false)

        val diff = computeScanDiff(emptyList(), listOf(scanned), emptySet())

        assertEquals(false, diff.toWrite.first().isFavorite)
    }

    @Test
    fun `marca para borrar la cancion que desaparecio del dispositivo`() {
        val stored = listOf(
            song(id = 1, uri = "content://sigue"),
            song(id = 2, uri = "content://desaparecida")
        )
        val scanned = listOf(song(id = 1, uri = "content://sigue"))

        val diff = computeScanDiff(stored, scanned, emptySet())

        assertEquals(listOf(2L), diff.removedIds)
    }

    @Test
    fun `conserva el origen SAF al actualizar una cancion`() {
        val stored = song(id = 9, uri = "content://saf/x", source = Song.SOURCE_SAF)
        val scanned = song(id = 0, uri = "content://saf/x", source = Song.SOURCE_SAF, title = "nuevo")

        val diff = computeScanDiff(listOf(stored), listOf(scanned), emptySet())

        assertEquals(Song.SOURCE_SAF, diff.toWrite.first().source)
    }

    @Test
    fun `conserva el genero editado por el usuario`() {
        val stored = song(id = 7, uri = "content://a", genre = "Reggaeton", title = "viejo")
        val scanned = song(id = 7, uri = "content://a", genre = "Sin gÃ©nero", title = "t")

        val diff = computeScanDiff(listOf(stored), listOf(scanned), emptySet())

        assertEquals("Reggaeton", diff.toWrite.first().genre)
    }

    @Test
    fun `refresca la portada cuando el dispositivo ya no la tiene`() {
        val stored = song(id = 7, uri = "content://a", coverUri = "content://portada/vieja")
        val scanned = song(id = 7, uri = "content://a", coverUri = null, title = "t")

        val diff = computeScanDiff(listOf(stored), listOf(scanned), emptySet())

        assertEquals(1, diff.toWrite.size)
    }

    /**
     * Un escaneo vacÃ­o se interpreta como fallo (permiso denegado, MediaStore caÃ­do), no
     * como "el usuario borrÃ³ su mÃºsica". Purgar aquÃ­ vaciaba la biblioteca entera y, en
     * cascada, las carpetas y favoritos con ella.
     */
    @Test
    fun `un escaneo vacio no borra nada`() {
        val stored = listOf(song(id = 1, uri = "content://a"))

        val diff = computeScanDiff(stored, emptyList(), emptySet())

        assertTrue("un escaneo vacÃ­o no debe purgar la biblioteca", diff.removedIds.isEmpty())
        assertTrue(diff.toWrite.isEmpty())
    }

    /**
     * RegresiÃ³n del CASCADE: una canciÃ³n existente cuyos metadatos cambian debe ir por
     * `toUpdate` (UPDATE in-place), nunca por `toInsert`. Si fuera una inserciÃ³n con
     * REPLACE, el `ON DELETE CASCADE` de `song_folder_cross` le borrarÃ­a las carpetas
     * aunque su `id` se conserve.
     */
    @Test
    fun `una cancion existente se actualiza in-place y no se reinserta`() {
        val stored = song(id = 42, uri = "content://a", title = "viejo")
        val scanned = song(id = 0, uri = "content://a", title = "nuevo")

        val diff = computeScanDiff(listOf(stored), listOf(scanned), emptySet())

        assertTrue("no debe pasar por INSERT/REPLACE", diff.toInsert.isEmpty())
        assertEquals(1, diff.toUpdate.size)
        assertEquals(42L, diff.toUpdate.first().id)
    }

    @Test
    fun `una cancion nueva va a toInsert y no a toUpdate`() {
        val diff = computeScanDiff(emptyList(), listOf(song(uri = "content://nueva")), emptySet())

        assertEquals(1, diff.toInsert.size)
        assertTrue(diff.toUpdate.isEmpty())
    }

    // --- uri presente pero sin metadatos: el indexador va con retraso ---

    @Test
    fun `no borra una cancion que sigue en MediaStore aunque el scanner no la midio`() {
        val stored = listOf(
            song(id = 1, uri = "content://medida"),
            song(id = 7, uri = "content://sin-indexar")
        )
        // El escaneo NO va vacÃ­o: si lo fuera, lo cortarÃ­a la guarda-corpus y el test
        // pasarÃ­a por el motivo equivocado.
        val scanned = listOf(song(id = 1, uri = "content://medida"))

        // Sin el conjunto de presencia, "no vino en el escaneo" es indistinguible de
        // "ya no estÃ¡ en el mÃ³vil", y la canciÃ³n se perderÃ­a junto a su carpeta.
        val diff = computeScanDiff(
            existing = stored,
            scanned = scanned,
            favoriteUris = emptySet(),
            presentUris = setOf("content://medida", "content://sin-indexar")
        )

        assertTrue(diff.removedIds.isEmpty())
    }

    @Test
    fun `si sigue en el conjunto de presencia, la cancion ausente del escaneo no se toca`() {
        val stored = listOf(
            song(id = 1, uri = "content://medida"),
            song(id = 2, uri = "content://sin-medir")
        )
        val scanned = listOf(song(id = 1, uri = "content://medida"))

        val diff = computeScanDiff(
            existing = stored,
            scanned = scanned,
            favoriteUris = emptySet(),
            presentUris = setOf("content://medida", "content://sin-medir")
        )

        assertTrue(diff.removedIds.isEmpty())
        // No debe reinscribirla como nueva ni actualizarla con datos inventados.
        assertTrue(diff.toInsert.isEmpty())
        assertTrue(diff.toUpdate.isEmpty())
    }

    @Test
    fun `la ausencia del conjunto de presencia conserva el comportamiento anterior`() {
        val stored = listOf(song(id = 1, uri = "content://a"), song(id = 2, uri = "content://b"))
        val scanned = listOf(song(id = 1, uri = "content://a"))

        // presentUris = null: mismo criterio de siempre, uri ausente = eliminada.
        val diff = computeScanDiff(stored, scanned, emptySet(), presentUris = null)

        assertEquals(listOf(2L), diff.removedIds)
    }

    /**
     * Este test fallaba antes de arreglar `computeScanDiff`, y es el motivo de que las
     * portadas desapareciesen solas.
     *
     * La app descarga la portada de iTunes cuando el móvil no trae arte de álbum y la
     * guarda en `songs.coverUri`. Al siguiente arranque el escaneo veía la canción y
     * sobrescribía la fila con la portada que da MediaStore, que en este caso es null:
     * la canción volvía a la nota musical y había que volver a descargarla. Con cada
     * reescaneo, una y otra vez.
     */
    @Test
    fun `el reescaneo no borra la portada descargada de iTunes`() {
        val descargada = "content://media/external/audio/images/media/42"
        val enBase = song(
            id = 7,
            uri = "content://media/external/audio/media/99",
            coverUri = descargada,
            isFavorite = true
        )
        // MediaStore sigue sin saber de la carátula: llega con null.
        val delEscaner = song(id = 0, uri = "content://media/external/audio/media/99", coverUri = null)

        val diff = computeScanDiff(listOf(enBase), listOf(delEscaner), emptySet())

        assertEquals(1, diff.toUpdate.size)
        assertEquals(descargada, diff.toUpdate[0].coverUri)
    }

    /**
     * La portada que sí trae MediaStore debe ganar: si el móvil pasa a tener arte de
     * álbum, lo propio del sistema es mejor fuente que una descarga antigua de iTunes.
     */
    @Test
    fun `la portada de MediaStore tiene prioridad si el movil ya trae arte`() {
        val delMovil = "content://media/external/audio/albumart/1234"
        val enBase = song(
            id = 7,
            uri = "content://media/external/audio/media/99",
            coverUri = "content://media/external/audio/images/media/42"
        )
        val delEscaner = song(id = 0, uri = "content://media/external/audio/media/99", coverUri = delMovil)

        val diff = computeScanDiff(listOf(enBase), listOf(delEscaner), emptySet())

        assertEquals(delMovil, diff.toUpdate[0].coverUri)
    }

    /** Conservar la portada no puede romper lo que ya funcionaba. */
    @Test
    fun `al conservar la portada tambien conserva id, favorito y genero`() {
        val descargada = "content://media/external/audio/images/media/42"
        val enBase = song(
            id = 7,
            uri = "content://media/external/audio/media/99",
            coverUri = descargada,
            isFavorite = true,
            genre = "Pop"
        )
        val delEscaner = song(id = 0, uri = "content://media/external/audio/media/99", coverUri = null)

        val diff = computeScanDiff(listOf(enBase), listOf(delEscaner), emptySet())

        val actualizada = diff.toUpdate[0]
        assertEquals(7L, actualizada.id)
        assertTrue(actualizada.isFavorite)
        assertEquals("Pop", actualizada.genre)
    }
}

/** Tests de la deduplicaciÃ³n MediaStore â†” SAF. */
class FilterNewSongsTest {

    private fun song(
        uri: String,
        title: String = "titulo",
        artist: String = "artista",
        durationMs: Long = 100_000,
        source: String = Song.SOURCE_MEDIASTORE
    ) = Song(
        id = 0,
        title = title,
        artist = artist,
        album = "album",
        durationMs = durationMs,
        uri = uri,
        coverUri = null,
        genre = "Sin gÃ©nero",
        isFavorite = false,
        source = source
    )

    /**
     * El bug: la misma pista indexada por MediaStore e importada por SAF tiene URIs distintas,
     * asÃ­ que el filtro por URI no la detectaba y la biblioteca mostraba la canciÃ³n dos veces.
     */
    @Test
    fun `descarta la misma cancion imported por SAF con otra uri`() {
        val existing = song(uri = "content://media/external/audio/media/1")
        val incoming = song(uri = "content://saf/music/track.mp3", source = Song.SOURCE_SAF)

        val result = filterNewSongs(listOf(incoming), listOf(existing))

        assertTrue("misma pista, no debe duplicarse", result.isEmpty())
    }

    @Test
    fun `descarta duplicados dentro del mismo lote entrante`() {
        val a = song(uri = "content://a")
        val b = song(uri = "content://b", source = Song.SOURCE_SAF)

        val result = filterNewSongs(listOf(a, b), emptyList())

        assertEquals(1, result.size)
        assertEquals("content://a", result.first().uri)
    }

    @Test
    fun `deja pasar una cancion distinta`() {
        val existing = song(uri = "content://a", title = "Una")
        val incoming = song(uri = "content://b", title = "Otra", artist = "Otro artista")

        val result = filterNewSongs(listOf(incoming), listOf(existing))

        assertEquals(1, result.size)
    }

    /** La comparaciÃ³n no debe ser sensible a mayÃºsculas ni a espacios sobrantes. */
    @Test
    fun `compara titulos sin distinguir mayusculas ni espacios`() {
        val existing = song(uri = "content://a", title = "Bohemian Rhapsody", artist = "Queen")
        val incoming = song(uri = "content://b", title = "  bohemian RHAPSODY ", artist = "queen")

        assertTrue(filterNewSongs(listOf(incoming), listOf(existing)).isEmpty())
    }

    /**
     * El mismo archivo puede reportar duraciones que difieren en milisegundos segÃºn el
     * extractor, asÃ­ que la clave redondea a 5 s.
     */
    @Test
    fun `tolera diferencias minimas de duracion`() {
        val existing = song(uri = "content://a", durationMs = 100_000)
        val incoming = song(uri = "content://b", durationMs = 101_500)

        assertTrue(filterNewSongs(listOf(incoming), listOf(existing)).isEmpty())
    }

    /** Canciones con la misma duraciÃ³n pero distinto tÃ­tulo NO son la misma pista. */
    @Test
    fun `no descarta canciones distintas con igual duracion`() {
        val existing = song(uri = "content://a", title = "Cancelled", artist = "Beach House")
        val incoming = song(uri = "content://b", title = "Space Song", artist = "Beach House")

        assertEquals(1, filterNewSongs(listOf(incoming), listOf(existing)).size)
    }

    @Test
    fun `descarta por uri aunque los metadatos no coincidan`() {
        val existing = song(uri = "content://a", title = "Titulo viejo")
        val incoming = song(uri = "content://a", title = "Titulo nuevo", artist = "Otro")

        assertTrue(filterNewSongs(listOf(incoming), listOf(existing)).isEmpty())
    }
}
