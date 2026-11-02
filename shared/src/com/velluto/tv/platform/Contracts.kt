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
 * e i caratteri (file in shared/res/font):
 *   val VellutoDisplayFont: FontFamily   (titoli)
 *   val VellutoBodyFont: FontFamily      (testo)
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

    /** Scarica la nuova versione in sottofondo e restituisce il file pronto da installare. */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File

    /** Installa subito il file scaricato (Firestick: apre l'installer di Android; PC: installa e riapre Velluto). */
    fun install(file: File)

    /** True se la piattaforma installa da sola, in silenzio, quando chiudi l'app (PC). */
    val installsOnExit: Boolean get() = false

    /** Prepara l'installazione silenziosa alla chiusura dell'app (solo dove [installsOnExit]). */
    fun installOnExit(file: File) {}
}

class PlatformServices(
    val accountStore: AccountStore,
    val updater: Updater,
    val dataDir: File,
    val versionName: String,
) {
    /** Attiva/disattiva lo schermo intero della finestra (solo Windows; null sulla Fire TV). */
    var toggleFullscreen: (() -> Unit)? = null

    /** Canali che il dispositivo regge insieme nel multiscreen (2 sulla Firestick, 4 sul PC). */
    var maxMultiScreens: Int = 2
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
