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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Aggiornamenti della Fire TV: scarica l'APK da GitHub Releases e apre l'installer di Android. */
class AndroidUpdater(private val context: Context) : Updater {

    override suspend fun check(): UpdateInfo? =
        Releases.latest(BuildConfig.UPDATE_REPO, ".apk", BuildConfig.VERSION_CODE)

    override suspend fun install(info: UpdateInfo, onProgress: (Float) -> Unit) {
        val apk = Releases.download(info.downloadUrl, File(context.cacheDir, "updates/velluto.apk"), onProgress)
        withContext(Dispatchers.Main) { launchInstaller(apk) }
    }

    private fun launchInstaller(apk: File) {
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
