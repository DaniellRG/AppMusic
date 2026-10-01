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

/**
 * Letras vía lrclib.net — API pública, SIN api_key, con letras sincronizadas (LRC)
 * cuando el proveedor las tiene. Devuelve texto plano como respaldo.
 */
object LyricsApi {

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
            val j = JSONObject(resp.body!!.string())
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
