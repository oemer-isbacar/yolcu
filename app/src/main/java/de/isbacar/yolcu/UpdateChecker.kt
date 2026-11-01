package de.isbacar.yolcu

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Fragt das neueste GitHub-Release ab. Updates werden über den Browser geladen und drüber installiert. */
object UpdateChecker {
    data class Update(val version: String, val downloadUrl: String, val notes: String)

    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 8_000
                readTimeout = 8_000
            }
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val version = json.getString("tag_name").removePrefix("v")
                if (!isNewer(version, BuildConfig.VERSION_NAME)) return@runCatching null
                val assets = json.getJSONArray("assets")
                val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                    .firstOrNull { it.getString("name").endsWith(".apk") }
                    ?.getString("browser_download_url")
                Update(version, apk ?: json.getString("html_url"), json.optString("body"))
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /** Vergleicht Versionen wie 1.2.10 > 1.2.9. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
