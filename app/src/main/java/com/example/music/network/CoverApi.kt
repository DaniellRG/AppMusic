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
            val j = JSONObject(resp.body!!.string())
            if (j.optInt("resultCount") == 0) return null
            val arr = j.getJSONArray("results")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val art = o.optString("artworkUrl100", null)
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
            return resp.body!!.byteStream().readBytes()
        }
    }

    /** true si value contiene needle (ignora mayúsculas/minúsculas). Si needle es
     * genérico (en blanco o "unknown") no filtra, para no rechazar resultados válidos. */
    private fun matches(value: String, needle: String?): Boolean =
        needle.isNullOrBlank() || needle == "unknown" || value.contains(needle, ignoreCase = true)
}
