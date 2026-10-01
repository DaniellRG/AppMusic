package com.example.music.ui.viewmodel

/**
 * Guarda de "solo la última petición cuenta".
 *
 * El problema que resuelve: si el usuario salta rápido entre canciones, la respuesta de
 * la anterior puede llegar DESPUÉS de la nueva. Sin esta guarda, una letra o una portada
 * lenta se quedaba pintada encima de la canción que suena ahora.
 *
 * Cada petición nueva pide un token; al responder, solo el token vigente puede tocar el
 * estado. Los tokens viejos se descartan con [isCurrent] y no tienen efecto.
 *
 * Deliberadamente NO lleva `synchronized` ni es `volatile`: todas las llamadas ocurren en el
 * hilo principal (los `fetch*` son `suspend` sobre `viewModelScope`, y el `launch` Main
 * immediate corre sin cambiar de hilo), así que un `int` desnudo basta y no hace falta
 * añadir sincronización que no se necesita.
 *
 * Va en su propio fichero (y no dentro del ViewModel) para poder testearlo en la JVM: el
 * ViewModel es un AndroidViewModel y necesitaría Robolectric para instanciarlo.
 */
internal class GenerationGate {

    private var generation = 0

    /** Registra una petición nueva y devuelve el token que debe validar su respuesta. */
    fun next(): Int = ++generation

    /** true si [token] sigue siendo la petición más reciente; si no, su respuesta se tira. */
    fun isCurrent(token: Int): Boolean = token == generation
}