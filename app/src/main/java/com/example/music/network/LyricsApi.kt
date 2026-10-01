package com.example.music.network

import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/** Resultado del fetch de letra. */
data class LyricsResult(
    val syncedLyrics: String? = null,   // LRC sincronizada (con timestamps)
    val plainLyrics: String? = null,    // texto plano
    val source: String = ""             // "lrclib-synced" | "lrclib" | "http-XXX"
)

/** Una línea de letra LRC con su instante en milisegundos. */
data class LrcLine(val timeMs: Long, val text: String)

/**
 * Letras vía lrclib.net — API pública, SIN api_key, con letras sincronizadas (LRC)
 * cuando el proveedor las tiene. Devuelve texto plano como respaldo.
 */
object LyricsApi {

    /**
     * Convierte una LRC cruda en líneas con tiempo.
     *
     * Hace falta porque si no se parsea, la letra sincronizada se muestra TAL CUAL: el
     * usuario ve "[00:32.50] Y esto es un amor..." como texto plano, con los corchetes y
     * los numeros dentro de la frase.
     *
     * Cosas que aguanta, todas ellas presentes en ficheros LRC reales:
     *  - `[mm:ss]`, `[mm:ss.xx` (centisegundos) y `[mm:ss.xxx` (milisegundos).
     *  - Varios tiempos en la misma línea (`[00:10.00][01:20.00] estribillo`), que es lo
     *    que hace un mismo texto sonar dos veces.
     *  - Etiquetas de metadatos (`[ti:...]`, `[ar:...]`, `[length:...]`): se descartan.
     *  - Líneas sin texto (silencios/ Instrumental), que se conservan con el texto vacío
     *    para que el resaltado avance igualmente.
     *
     * `internal` para poder testearla en la JVM sin red.
     */
    internal fun parseLrc(raw: String): List<LrcLine> {
        val out = mutableListOf<LrcLine>()
        for (line in raw.lineSequence()) {
            val marcas = TIMESTAMP.findAll(line).toList()
            // Sin timestamp no es una línea de letra: es metadato o basura.
            if (marcas.isEmpty()) continue
            val texto = line.substring(marcas.last().range.last + 1).trim()
            for (m in marcas) {
                val ms = aTimestampAMs(m.groupValues[1], m.groupValues[2], m.groupValues[3])
                if (ms != null) out += LrcLine(ms, texto)
            }
        }
        // lrclib devuelve los tiempos en orden, pero un LRC editado a mano puede no estarlo:
        // el resaltado recorre las líneas en secuencia, así que se ordena por tiempo.
        return out.sortedBy { it.timeMs }
    }

    /**
     * true si el texto trae alguna marca de tiempo válida. Decide si se pinta la letra
     * sincronizada o el texto plano: una letra sin timestamps se vería igual, pero wasting
     * el resaltado y el auto-scroll.
     */
    internal fun isSynced(raw: String): Boolean = TIMESTAMP.containsMatchIn(raw)

    /** `[mm:ss]`, `[mm:ss.xx]` o `[mm:ss.xxx]` → milisegundos. */
    private fun aTimestampAMs(m: String, s: String, frac: String?): Long? {
        val min = m.toIntOrNull() ?: return null
        val seg = s.toIntOrNull() ?: return null
        if (min < 0 || seg < 0 || seg > 59) return null
        val ms = when {
            frac == null || frac.isEmpty() -> 0L
            else -> frac.toLongOrNull() ?: return null
        }
        // ".5" son 500 ms y no 5 ms: los LRC usan 2 decimales (centisegundos), no 1.
        val factor = when (frac?.length) {
            null, 0 -> 0L
            1 -> 100L
            2 -> 10L
            else -> 1L
        }
        return min * 60_000L + seg * 1_000L + ms * factor
    }

    private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun fetch(
        artist: String,
        title: String,
        album: String? = null
    ): LyricsResult {
        val params = buildList {
            add("artist_name" to artist)
            add("track_name" to title)
            // album_name es opcional en lrclib y rompe el match cuando el tag ID3 es genérico
            // ("Music" por la carpeta, "<unknown>", etc.); se omite para un matching robusto.
        }
        val query = params.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, "UTF-8")}"
        }
        val url = "https://lrclib.net/api/get?$query"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MusicAndroid/1.0")
            .get()
            .build()

        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return LyricsResult(source = "http-${resp.code}")
            // Mismo fallo que en CoverApi: sin cuerpo, NPE. Aquí se devuelve un resultado
            // vacío con el código, que es lo que el llamante ya sabe manejar.
            val body = resp.body ?: return LyricsResult(source = "http-sin-cuerpo")
            val j = JSONObject(body.string())
            val synced = if (!j.isNull("syncedLyrics")) j.getString("syncedLyrics") else null
            val plain = if (!j.isNull("plainLyrics")) j.getString("plainLyrics") else null
            return LyricsResult(
                syncedLyrics = synced,
                plainLyrics = plain ?: synced,
                source = if (synced != null) "lrclib-synced" else "lrclib"
            )
        }
    }
}
