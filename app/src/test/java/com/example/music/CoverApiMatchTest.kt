package com.example.music

import com.example.music.network.CoverApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validación de las coincidencias de iTunes. El fallo que motivó esto: el artista se
 * comparaba en crudo contra la etiqueta ID3, así que "  Faouzia " no encontraba nada y el
 * disco se quedaba sin portada aunque la respuesta tuviera la correcta.
 */
class CoverApiMatchTest {

    @Test
    fun `encuentra el artista ignorando mayusculas`() {
        assertTrue(CoverApi.matches("Faouzia", "faouzia"))
        assertTrue(CoverApi.matches("FAOUZIA", "Faouzia"))
    }

    @Test
    fun `los espacios sobrantes del ID3 ya no rompen la busqueda`() {
        // El caso real: el ID3 traía espacios alrededor.
        assertTrue(CoverApi.matches("Faouzia", "  Faouzia  "))
    }

    @Test
    fun `los espacios multiples se colapsan`() {
        assertTrue(CoverApi.matches("Bad   Bunny", "bad bunny"))
        assertTrue(CoverApi.matches("Bad Bunny", "bad   bunny"))
    }

    @Test
    fun `los acentos no impiden el match`() {
        assertTrue(CoverApi.matches("Mamacita", "mamácita"))
        assertTrue(CoverApi.matches("Rosalía", "rosalia"))
        assertTrue(CoverApi.matches("Björk", "bjork"))
    }

    @Test
    fun `un artista distinto NO coincide`() {
        // iTunes a veces devuelve otro tema primero; esto es justo lo que hay que filtrar.
        assertFalse(CoverApi.matches("Ed Sheeran", "faouzia"))
        assertFalse(CoverApi.matches("Desconocido", "faouzia"))
    }

    @Test
    fun `una etiqueta generica no filtra, para no perder resultados validos`() {
        // Si no sabemos el artista, filtrar por él descartaría todas las respuestas.
        assertTrue(CoverApi.matches("Cualquier Artista", "unknown"))
        assertTrue(CoverApi.matches("Cualquier Artista", "Desconocido"))
        assertTrue(CoverApi.matches("Cualquier Artista", "<unknown>"))
    }

    @Test
    fun `una etiqueta vacia o nula no filtra`() {
        assertTrue(CoverApi.matches("Lo que sea", ""))
        assertTrue(CoverApi.matches("Lo que sea", "   "))
        assertTrue(CoverApi.matches("Lo que sea", null))
    }

    @Test
    fun `el titulo se valida igual que el artista`() {
        // "Unethical" debe encontrar "Unethical", no una canción que solo comparta palabras.
        assertTrue(CoverApi.matches("Unethical", "unethical"))
        assertFalse(CoverApi.matches("Unethical (Live)", "ethical remix 2024"))
    }
}