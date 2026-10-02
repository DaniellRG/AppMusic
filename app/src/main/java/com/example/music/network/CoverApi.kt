package com.example.music.network

import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Portadas vía iTunes Search API — pública, SIN api_key.
 * Devuelve artworkUrl100; se escalar a 600x600 reemplazando el sufijo.
 */
object CoverApi {

    fun fetchUrl(artist: String?, title: String?): String? {
        val terms = listOf(artist, title).filter { !it.isNullOrBlank() }
        if (terms.isEmpty()) return null
        val term = terms.joinToString(" ")
        val url = "https://itunes.apple.com/search?" +
            "term=${URLEncoder.encode(term, "UTF-8")}" +
            "&entity=song&limit=5"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MusicAndroid/1.0")
            .get()
            .build()

        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            // body!! reventaba con NPE si la respuesta venía sin cuerpo (204, o un corte a
            // mitad). bettero fallar limpiamente: no hay portada y ya.
            val body = resp.body ?: return null
            val j = JSONObject(body.string())
            if (j.optInt("resultCount") == 0) return null
            val arr = j.getJSONArray("results")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val art = o.optString("artworkUrl100")
                // Validar artista/título: iTunes puede devolver otro tema primero.
                if (!art.isNullOrBlank() &&
                    matches(o.optString("artistName", ""), artist) &&
                    matches(o.optString("trackName", ""), title)) {
                    return art.replace("100x100bb", "600x600bb")
                }
            }
            return null
        }
    }

    /** Descarga los bytes de una URL de portada (para guardarla localmente). */
    fun downloadBytes(url: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MusicAndroid/1.0")
            .get()
            .build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            // Antes: resp.body!!.byteStream() -> NPE en vez de un error legible.
            val body = resp.body ?: throw IOException("Respuesta sin cuerpo (HTTP ${resp.code})")
            return body.byteStream().readBytes()
        }
    }

    /**
     * true si [value] contiene [needle], sin distinguir mayúsculas, acentos ni espacios de sobra.
     *
     * El [needle] sale de las etiquetas ID3, que traen basura típica ("  Faouzia ", "MAPHRA
     * Vocal Cover"); comparar en crudo rechazaba resultados válidos y se quedaba sin
     * portada. Si [needle] es genérico no filtra, para no descartar respuestas buenas.
     *
     * `internal` (no private) para poder testearla en la JVM sin tocar la red.
     */
    internal fun matches(value: String, needle: String?): Boolean {
        if (needle.isNullOrBlank() || normalize(needle) in GENERIC_NEEDLES) return true
        return normalize(value).contains(normalize(needle))
    }

    /** Minúsculas, sin acentos y con espacios colapsados, para comparar texto de verdad. */
    private fun normalize(s: String): String =
        java.text.Normalizer
            .normalize(s, java.text.Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Etiquetas que no describen a nadie concreto: no sirven para descartar un resultado. */
    private val GENERIC_NEEDLES = setOf("unknown", "desconocido", "none", "null", "<unknown>")
}
