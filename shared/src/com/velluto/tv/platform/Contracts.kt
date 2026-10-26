package com.velluto.tv.platform

import com.velluto.tv.data.Account
import java.io.File

/*
 * Cosa ogni piattaforma (Fire TV / Windows) deve fornire al codice condiviso.
 *
 * Oltre a queste interfacce, ogni piattaforma definisce nel package com.velluto.tv.platform
 * anche queste funzioni, con la stessa firma:
 *   @Composable fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
 *   @Composable fun RemoteImage(url: String, contentDescription: String?, modifier: Modifier,
 *                               contentScale: ContentScale = ContentScale.Crop, alignment: Alignment = Alignment.Center)
 *   @Composable fun rememberVideoEngine(): VideoEngine
 *   @Composable fun VideoSurface(engine: VideoEngine, modifier: Modifier)
 */

data class UpdateInfo(val versionCode: Int, val versionName: String, val downloadUrl: String)

/** Dove vengono salvate le credenziali del provider (solo sul dispositivo). */
interface AccountStore {
    fun load(): Account?
    fun save(account: Account)
    fun clear()
}

interface Updater {
    /** La nuova versione per questa piattaforma, o null se sei già aggiornato. */
    suspend fun check(): UpdateInfo?

    /** Scarica la nuova versione e avvia l'installazione. */
    suspend fun install(info: UpdateInfo, onProgress: (Float) -> Unit)
}

class PlatformServices(
    val accountStore: AccountStore,
    val updater: Updater,
    val dataDir: File,
    val versionName: String,
) {
    /** Attiva/disattiva lo schermo intero della finestra (solo Windows; null sulla Fire TV). */
    var toggleFullscreen: (() -> Unit)? = null
}

/** Il player video: ExoPlayer sulla Fire TV, VLC su Windows. */
interface VideoEngine {
    val positionMs: Long
    val durationMs: Long
    val isPlaying: Boolean
    var listener: Listener?

    /** Volume del player, da 0 (muto) a 1 (massimo). Sulla Fire TV si somma al volume del televisore. */
    var volume: Float

    fun load(url: String, startMs: Long)
    fun play()
    fun pause()
    fun seekTo(ms: Long)
    fun release()

    interface Listener {
        fun onPlayingChanged(playing: Boolean)
        fun onBufferingChanged(isBuffering: Boolean)
        fun onEnded()
        fun onError(message: String)
    }
}
