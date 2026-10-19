package com.velluto.tv.data

import com.velluto.tv.platform.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Legge l'ultima release pubblicata su GitHub (repository pubblico degli installer). */
object Releases {

    /**
     * Restituisce la release più recente se è più nuova di [currentCode] e contiene
     * un file che termina con [assetSuffix] (".apk" per la Fire TV, ".msi" per Windows).
     */
    suspend fun latest(repo: String, assetSuffix: String, currentCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        if (repo.isBlank()) return@withContext null
        try {
            val req = XtreamClient.request("https://api.github.com/repos/$repo/releases/latest")
            XtreamClient.http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val json = JSONObject(r.body?.string() ?: return@withContext null)
                val code = json.optString("tag_name").filter { it.isDigit() }.toIntOrNull() ?: return@withContext null
                if (code <= currentCode) return@withContext null
                val assets = json.optJSONArray("assets") ?: return@withContext null
                val asset = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(assetSuffix, ignoreCase = true) }
                    ?: return@withContext null
                UpdateInfo(code, json.optString("name"), asset.optString("browser_download_url"))
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun download(url: String, out: File, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        out.parentFile?.mkdirs()
        XtreamClient.http.newCall(XtreamClient.request(url)).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Download non riuscito (${r.code})")
            val body = r.body ?: throw IOException("Download vuoto")
            val total = body.contentLength()
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress(read.toFloat() / total)
                    }
                }
            }
        }
        out
    }
}
