package com.velluto.tv.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.velluto.tv.BuildConfig
import com.velluto.tv.data.Releases
import java.io.File

/**
 * Aggiornamenti della Fire TV: l'APK si scarica da GitHub Releases in sottofondo, poi si apre
 * l'installer di Android. Fire OS non permette a un'app installata fuori dall'Appstore di
 * aggiornarsi senza conferma: l'ultimo "Installa" lo preme sempre l'utente.
 */
class AndroidUpdater(private val context: Context) : Updater {

    override suspend fun check(): UpdateInfo? =
        Releases.latest(BuildConfig.UPDATE_REPO, ".apk", BuildConfig.VERSION_CODE)

    override suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates")
        // Le versioni scaricate in passato non servono più.
        dir.listFiles()?.forEach { it.delete() }
        val part = Releases.download(info.downloadUrl, File(dir, "velluto-${info.versionCode}.part"), onProgress)
        val apk = File(dir, "velluto-${info.versionCode}.apk")
        part.renameTo(apk)
        return apk
    }

    override fun install(file: File) {
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
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
