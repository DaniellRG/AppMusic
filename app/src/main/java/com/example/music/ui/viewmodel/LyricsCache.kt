package com.example.music.ui.viewmodel

import android.content.Context
import com.example.music.model.Song
import java.io.File
import java.util.Locale

/**
 * Letras en disco, para no pedirlas otra vez.
 *
 * Sin esto la letra se busca en lrclib cada vez que abres la pista: se pierde al cerrar
 * la app, gastas datos sin enterarte y, sobre todo, dependes de que haya red. Con el
 * fichero en disco la letra se lee al instante y además funciona sin conexión, que es
 * justo lo que se le pidió.
 *
 * Dónde: `filesDir/lyrics`. No en el almacenamiento público, porque es un dato interno
 * de la app y no tiene que aparecer en la galería ni en la lista de ficheros del
 * móvil. Android borra esta carpeta solo si el usuario limpia los datos de la app.
 *
 * Una letra por canción, en un fichero cuyo nombre es el hash de la URI. Con el título
 * los nombres chocan ("x" e "x (1)") y hay títulos que no valen como nombre de fichero
 * en FAT32, que es donde se guardan las cosas en un Android: la almohadilla, las comas
 * japonesas y los dos puntos se rompen o se cambian sin avisar.
 */
object LyricsCache {

    private const val DIR = "lyrics"

    /**
     * Palabras que detrás de un guion indican una versión, no un artista.
     *
     * Sin esta lista no hay forma de distinguir "Canción - Live" de "Artista - Canción",
     * que tienen exactamente la misma forma. Se incluyen las abreviaturas habituales en
     * los nombres de fichero MP3.
     */
    private const val VERSIONES =
        "live|remaster|remastered|remix|version|edit|mix|mixdown|acoustic|instrumental|" +
            "demo|radio|single|album|deluxe|bonus|mono|stereo|digital|explicit|clean|" +
            "extended|unplugged|reprise|rework|bootleg|original|karaoke|sped|reverb|" +
            "preview|trailer|rerecorded|re-recorded"

    /**
     * Clave del fichero. Se usa la URI porque identifica la canción en este móvil de
     * forma única y estable, y no cambia aunque el usuario renombre el fichero.
     */
    private fun archivo(context: Context, song: Song): File =
        File(File(context.filesDir, DIR), clave(song))

    private fun clave(song: Song): String {
        val uriHash = song.uri.hashCode().toUInt().toString(16)
        val tituloHash = song.title.hashCode().toUInt().toString(16)
        return "${song.id}_${uriHash}_${tituloHash}.lrc"
    }

    /** Lee la letra guardada, o null si no está o si el fichero salió corrupto. */
    fun leer(context: Context, song: Song): String? {
        val f = archivo(context, song)
        if (!f.exists()) return null
        return try {
            val texto = f.readText()
            // Una letra vacía o de un solo salto de línea no sirve de nada y ocuparía el
            // sitio como si fuera buena, así que se considera que no está.
            if (texto.isBlank()) null else texto
        } catch (_: Exception) {
            null
        }
    }

    /** Guarda la letra. Devuelve true si se escribió. */
    fun guardar(context: Context, song: Song, letra: String): Boolean {
        val texto = letra.trim()
        if (texto.isBlank()) return false
        return try {
            val f = archivo(context, song)
            f.parentFile?.mkdirs()
            f.writeText(texto)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun existe(context: Context, song: Song): Boolean = archivo(context, song).exists()

    /**
     * Borra la letra de una canción.
     *
     * Existe para el caso de "esta letra está mal, la quito y busco otra": si no se
     * puede volver a buscar, el usuario se queda enganchado a una letra equivocada
     * para siempre.
     */
    fun borrar(context: Context, song: Song): Boolean {
        val f = archivo(context, song)
        return if (f.exists()) {
            try { f.delete() } catch (_: Exception) { false }
        } else {
            false
        }
    }

    /** Cuántas letras hay en disco. Solo informativo. */
    fun contar(context: Context): Int =
        try { File(context.filesDir, DIR).listFiles()?.size ?: 0 } catch (_: Exception) { 0 }

    /**
     * Normaliza un título para buscar la letra.
     *
     * Los ficheros de audio traen la letra de la canción con la que viene en la
     * descarga: "Song (Remastered 2011)", "Song - Live", "Song feat. X". Buscar eso tal
     * cual en lrclib falla casi siempre, porque allí está como "Song". Es la causa más
     * común de "no encuentra letra" en canciones que sí la tienen.
     */
    fun tituloParaBuscar(titulo: String): String {
        var t = titulo.lowercase(Locale.ROOT)

        // Puntos y guiones bajos primero, pero SOLO entre letras. Un punto delante o
        // detrás ("Cancion.-.Remaster") es decoración del nombre del fichero y borrarlo
        // entero se lleva por delante medio título. Con \B no se tocan.
        t = t.replace(Regex("\\B[_.]+|[_.]+\\B"), " ")

        // Sufijos entre paréntesis, corchetes y llaves. Todas las reglas de aquí abajo
        // pasan por quitaSiQuedaAlgo: un título como "(Live)" es la canción en sí, y
        // quitárselo dejaría la búsqueda vacía, que es peor que no quitarlo. lrclib con
        // una cadena vacía devuelve la lista entera y la app enseñaría otra canción.
        t = quitaSiQuedaAlgo(t, Regex("\\s*[\\(\\[{][^)\\]}]*[)\\]}]\\s*$"), Regex("[()\\[\\]{}]"))
        t = quitaSiQuedaAlgo(t, Regex("\\s*[\\(\\[{][^)\\]}]*[)\\]}]\\s*"), Regex("[()\\[\\]{}]"))

        // "feat. X", "ft X", "featuring X" con el artista de más colgando. Va ANTES que
        // la regla del guion por una razón concreta: en "Weezer - Buddy Holly feat. X"
        // hay que quitar primero "feat. X" para que la regla del guion vea que lo que
        // queda tras el guion es un nombre y no una versión.
        t = quitaSiQuedaAlgo(
            t, Regex("(?i)\\s*\\b(feat|ft|featuring|with)\\b\\.?,?\\s.*$")
        )

        // " - Live", " - Remix", " - 2011 Remaster".
        //
        // Solo se quita si lo que va tras el guion parece una versión. No vale "cualquier
        // cosa tras el guion": en "Weezer - Buddy Holly" lo que hay tras el guion es el
        // artista, y quitándolo se acaba buscando "weezer", que sí existe en el
        // catálogo pero es otra canción con otra letra. Esa confusión es peor que no
        // encontrar nada, porque el usuario no ve que ha fallado.
        t = quitaSiQuedaAlgo(t, Regex("(?i)\\s+-\\s+(${VERSIONES}|\\d{4})\\b.*$"))

        // Un guion suelto que haya quedado por el camino.
        t = t.replace(Regex("\\s*[-–—]+\\s*$"), " ")

        return t.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Quita lo que casa con [regex], salvo que el resultado se quede sin nada.
     *
     * Se prefiere una búsqueda con el título feo antes que una búsqueda vacía: con la
     * vacía, lrclib responde con resultados cualesquiera y la app muestra la letra de
     * una canción que no es la que suena.
     *
     * @param soloMarcas segundo intento, ya sin el texto del bloque y dejando solo los
     *        signos. Con un título como "(Live)" quitar el bloque entero lo deja vacío,
     *        pero sí conviene quitar los paréntesis: buscar "(live)" no encuentra nada
     *        y buscar "live" sí.
     */
    private fun quitaSiQuedaAlgo(texto: String, regex: Regex, soloMarcas: Regex? = null): String {
        val limpio = texto.replace(regex, " ").trim()
        if (limpio.isNotBlank()) return limpio
        val sinMarcas = soloMarcas?.let { texto.replace(it, " ").trim() }
        return if (!sinMarcas.isNullOrBlank()) sinMarcas else texto
    }
}
