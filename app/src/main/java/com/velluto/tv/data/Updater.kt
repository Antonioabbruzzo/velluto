package com.velluto.tv.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.velluto.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String)

/** Controlla le GitHub Releases del repository e installa la nuova versione. */
object Updater {

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val repo = BuildConfig.UPDATE_REPO
        if (repo.isBlank()) return@withContext null
        try {
            val req = XtreamClient.request("https://api.github.com/repos/$repo/releases/latest")
            XtreamClient.http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val json = JSONObject(r.body?.string() ?: return@withContext null)
                val code = json.optString("tag_name").filter { it.isDigit() }.toIntOrNull() ?: return@withContext null
                if (code <= BuildConfig.VERSION_CODE) return@withContext null
                val assets = json.optJSONArray("assets") ?: return@withContext null
                val apk = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk") } ?: return@withContext null
                UpdateInfo(code, json.optString("name"), apk.optString("browser_download_url"))
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "velluto.apk")
            XtreamClient.http.newCall(XtreamClient.request(info.apkUrl)).execute().use { r ->
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

    fun install(context: Context, apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            } catch (e: ActivityNotFoundException) {
                // Alcune Fire TV non hanno questa schermata: proviamo comunque l'installazione.
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
