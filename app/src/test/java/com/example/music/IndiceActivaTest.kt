package com.example.music

import com.example.music.network.LrcLine
import com.example.music.network.indiceActiva
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Qué línea de la letra está sonando en cada momento.
 *
 * Es la lógica que decide el resaltado, y estaba metida dentro del composable, sin forma
 * de testearla. Ahora es `List<LrcLine>.indiceActiva(posicionMs)`, código puro.
 */
class IndiceActivaTest {

    private val letra = listOf(
        LrcLine(1_000L, "primera"),
        LrcLine(5_000L, "segunda"),
        LrcLine(9_000L, "tercera"),
        LrcLine(12_000L, "cuarta")
    )

    @Test
    fun `antes de la primera linea no hay ninguna activa`() {
        assertEquals(-1, letra.indiceActiva(0L))
        assertEquals(-1, letra.indiceActiva(999L))
    }

    @Test
    fun `una posicion negativa no activa la primera linea`() {
        // Una posición negativa por un reloj que va justo no debe encender la primera.
        assertEquals(-1, letra.indiceActiva(-5_000L))
    }

    @Test
    fun `en el instante exacto se activa esa linea`() {
        // El ">=" es importante: si fuera ">", la línea se retrasaría hasta el tick siguiente.
        assertEquals(0, letra.indiceActiva(1_000L))
        assertEquals(1, letra.indiceActiva(5_000L))
        assertEquals(3, letra.indiceActiva(12_000L))
    }

    @Test
    fun `entre dos lineas sigue sonando la anterior`() {
        assertEquals(0, letra.indiceActiva(4_999L))
        assertEquals(1, letra.indiceActiva(8_999L))
        assertEquals(2, letra.indiceActiva(11_999L))
    }

    @Test
    fun `pasada la ultima linea se queda en la ultima`() {
        // La letra no se repite ni se apaga al acabar: se mantiene la última frase.
        assertEquals(3, letra.indiceActiva(12_001L))
        assertEquals(3, letra.indiceActiva(600_000L))
    }

    @Test
    fun `una lista vacia no revienta`() {
        assertEquals(-1, emptyList<LrcLine>().indiceActiva(1_000L))
    }

    @Test
    fun `una sola linea`() {
        val una = listOf(LrcLine(500L, "solo"))
        assertEquals(-1, una.indiceActiva(499L))
        assertEquals(0, una.indiceActiva(500L))
    }

    @Test
    fun `lineas con el mismo tiempo se quedarian con la ultima`() {
        // Puede pasar con LRC编辑 a mano duplicando el timestamp.
        val repetidas = listOf(
            LrcLine(1_000L, "a"),
            LrcLine(1_000L, "b")
        )
        assertEquals(1, repetidas.indiceActiva(1_000L))
    }

    @Test
    fun `el indice avanza de forma monotona al avanzar el reloj`() {
        var anterior = -1
        var ms = 0L
        while (ms <= 15_000L) {
            val ahora = letra.indiceActiva(ms)
            assertTrueIndex(anterior, ahora, ms)
            anterior = ahora
            ms += 250L
        }
    }

    private fun assertTrueIndex(anterior: Int, ahora: Int, ms: Long) {
        assert(ahora >= anterior) { "el indice retrocedio en $ms ms: $anterior -> $ahora" }
    }
}