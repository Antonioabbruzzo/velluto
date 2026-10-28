package com.velluto.tv

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import coil.Coil
import coil.ImageLoader
import coil.memory.MemoryCache
import com.velluto.tv.platform.AndroidAccountStore
import com.velluto.tv.platform.AndroidUpdater
import com.velluto.tv.platform.PlatformServices
import com.velluto.tv.ui.AppRoot
import com.velluto.tv.ui.AppViewModel
import com.velluto.tv.ui.Palette
import com.velluto.tv.ui.VellutoTheme

/** Tiene in vita lo stato dell'app anche se Android ricrea l'activity. */
class AppHolder(app: Application) : AndroidViewModel(app) {
    val vm = AppViewModel(androidServices(app))

    override fun onCleared() {
        vm.dispose()
    }
}

fun androidServices(app: Application) = PlatformServices(
    accountStore = AndroidAccountStore(app),
    updater = AndroidUpdater(app),
    dataDir = app.filesDir,
    versionName = BuildConfig.VERSION_NAME,
)

class MainActivity : ComponentActivity() {
    private val holder: AppHolder by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Immagini leggere per le Firestick con poca memoria: RGB565 (metà memoria, in TV non si nota
        // la differenza) e cache in RAM più piccola; le locandine restano comunque sul disco.
        Coil.setImageLoader {
            ImageLoader.Builder(applicationContext)
                .allowRgb565(true)
                .memoryCache { MemoryCache.Builder(applicationContext).maxSizePercent(0.15).build() }
                .build()
        }
        setContent {
            VellutoTheme {
                Box(Modifier.fillMaxSize().background(Palette.Background)) {
                    AppRoot(holder.vm)
                }
            }
        }
    }
}
