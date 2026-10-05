package com.example.music

import com.example.music.ui.viewmodel.debeEscanear
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la puerta de escaneo del arranque.
 *
 * Contexto: el autoescaneo se lanzaba en cada `LaunchedEffect` con permiso concedido, asi que
 * abrir la app recorria MediaStore entero y reabria cada pista sin duracion indexada para
 * terminar dejando exactamente la misma lista (unos ~5 s medidos en el emulador). Ahora se
 * compara una huella barata (COUNT(*) + MAX(DATE_MODIFIED)) contra la del ultimo escaneo.
 *
 * Es logica pura: corre en el host, sin Room, sin MediaStore y sin emulador.
 */
class ScanGateTest {

    // --- Se salta: la biblioteca no ha cambiado y ya hay filas. Es el arranque normal. ---

    @Test
    fun `sin cambios y base poblada no escanea`() {
        assertFalse(debeEscanear(forzar = false, yaIndexada = true, "42|1700000000", "42|1700000000"))
    }

    // --- Se escanea: casos que deben seguir funcionando sin dudarlo. ---

    @Test
    fun `forzar escanea aunque no haya cambios`() {
        // Boton "Escanear" de Ajustes: tiene que reindexar siempre.
        assertTrue(debeEscanear(forzar = true, yaIndexada = true, "42|1700000000", "42|1700000000"))
    }

    @Test
    fun `base vacia escanea aunque la huella coincida`() {
        // Primer arranque, o datos de la app borrados. Si aqui no escanea, la app se queda
        // vacia para siempre: es el fallo mas grave posible de esta optimizacion.
        assertTrue(debeEscanear(forzar = false, yaIndexada = false, "42|1700000000", "42|1700000000"))
    }

    @Test
    fun `huella distinta escanea`() {
        // Pistas nuevas, borradas o reetiquetadas.
        assertTrue(debeEscanear(forzar = false, yaIndexada = true, "43|1700000000", "42|1700000000"))
    }

    @Test
    fun `ultima modificacion distinta escanea aunque el numero sea igual`() {
        // Se reetiqueta una pista: COUNT(*) no cambia, pero DATE_MODIFIED si. Si solo se mirara
        // el numero, el cambio de portada/titulo no se indexaria nunca.
        assertTrue(debeEscanear(forzar = false, yaIndexada = true, "42|1800000000", "42|1700000000"))
    }

    @Test
    fun `huella guardada ausente escanea`() {
        // Primer arranque tras instalar: no hay nada guardado todavia.
        assertTrue(debeEscanear(forzar = false, yaIndexada = true, "42|1700000000", null))
    }

    @Test
    fun `huella vacia de un dispositivo sin pistas escanea siempre`() {
        // Regresion real que cambio la implementacion: si el usuario borra TODOS sus archivos,
        // MediaStore pasa a "0|0" y lo sigue estando, asi que comparar solo huellas daba
        // "nada que hacer". La base se quedaba con la biblioteca entera de antes: canciones
        // fantasma y favoritos de archivos inexistentes, sin forma de limpiarlos.
        assertTrue(debeEscanear(forzar = false, yaIndexada = true, "0|0", "0|0"))
        assertTrue(debeEscanear(forzar = false, yaIndexada = false, "0|0", "0|0"))
        assertTrue(debeEscanear(forzar = false, yaIndexada = true, "0|0", null))
    }

    @Test
    fun `huella con formato inesperado escanea por prudencia`() {
        // Ante un valor que no se puede interpretar, se escanea. Es lo seguro: un escaneo de mas
        // cuesta unos segundos, mientras que saltarselo deja la biblioteca desactualizada.
        assertTrue(debeEscanear(forzar = false, yaIndexada = true, "basura", "basura"))
    }
}