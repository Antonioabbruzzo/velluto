package com.velluto.tv.platform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import com.velluto.tv.data.Account
import com.velluto.tv.data.Releases
import com.velluto.tv.data.XtreamClient
import kotlinx.coroutines.isActive
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.json.JSONObject
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.io.File
import java.nio.ByteBuffer
import javax.swing.SwingUtilities
import kotlin.math.min
import kotlin.system.exitProcess

// ======================= Servizi =======================

fun desktopDataDir(): File =
    System.getenv("APPDATA")?.let { File(it, "Velluto") } ?: File(System.getProperty("user.home"), ".velluto")

fun desktopServices(): PlatformServices {
    val dir = desktopDataDir().apply { mkdirs() }
    return PlatformServices(
        accountStore = DesktopAccountStore(dir),
        updater = DesktopUpdater(),
        dataDir = dir,
        versionName = BuildInfo.VERSION_NAME,
    )
}

/** Credenziali salvate in %APPDATA%\Velluto\account.json (solo su questo PC). */
class DesktopAccountStore(dir: File) : AccountStore {
    private val file = File(dir, "account.json")

    override fun load(): Account? = try {
        if (!file.exists()) null else {
            val o = JSONObject(file.readText())
            Account(o.getString("url"), o.getString("user"), o.getString("pass"))
        }
    } catch (e: Exception) {
        null
    }

    override fun save(account: Account) {
        file.writeText(
            JSONObject()
                .put("url", account.baseUrl)
                .put("user", account.username)
                .put("pass", account.password)
                .toString()
        )
    }

    override fun clear() {
        file.delete()
    }
}

/** Aggiornamenti per Windows: scarica il nuovo .msi da GitHub Releases e lo avvia. */
class DesktopUpdater : Updater {
    override suspend fun check(): UpdateInfo? =
        Releases.latest(BuildInfo.UPDATE_REPO, ".msi", BuildInfo.VERSION_CODE)

    override suspend fun install(info: UpdateInfo, onProgress: (Float) -> Unit) {
        val msi = Releases.download(
            info.downloadUrl,
            File(System.getProperty("java.io.tmpdir"), "Velluto-update.msi"),
            onProgress,
        )
        ProcessBuilder("msiexec", "/i", msi.absolutePath).start()
        // L'installer deve poter sostituire i file dell'app: chiudiamo Velluto.
        exitProcess(0)
    }
}

// ======================= Tasto Indietro (Esc) =======================

class BackEntry {
    var enabled: Boolean = true
    var onBack: () -> Unit = {}
}

/** Come il tasto Indietro di Android: vince l'ultimo gestore attivo registrato. */
class BackDispatcher {
    private val entries = mutableListOf<BackEntry>()

    fun add(e: BackEntry) {
        entries += e
    }

    fun remove(e: BackEntry) {
        entries -= e
    }

    fun dispatch(): Boolean {
        val e = entries.lastOrNull { it.enabled } ?: return false
        e.onBack()
        return true
    }
}

val LocalBackDispatcher = staticCompositionLocalOf { BackDispatcher() }

@Composable
fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val dispatcher = LocalBackDispatcher.current
    val entry = remember { BackEntry() }
    SideEffect {
        entry.enabled = enabled
        entry.onBack = onBack
    }
    DisposableEffect(dispatcher, entry) {
        dispatcher.add(entry)
        onDispose { dispatcher.remove(entry) }
    }
}

// ======================= Immagini =======================

@Composable
fun RemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
) {
    AsyncImage(
        model = url.ifBlank { null },
        contentDescription = contentDescription,
        modifier = modifier,
        alignment = alignment,
        contentScale = contentScale,
    )
}

// ======================= Video (VLC) =======================

/**
 * Player per Windows basato su VLC (libreria vlcj). VLC decodifica il video e ci passa i
 * fotogrammi, che disegniamo in Compose: così i controlli possono stare sopra il video.
 */
class VlcEngine : VideoEngine {
    private val factory = MediaPlayerFactory(NativeDiscovery(), "--quiet", "--no-video-title-show")
    private val player: EmbeddedMediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer()

    override var listener: VideoEngine.Listener? = null

    private val lock = Any()
    private var pixels: ByteArray? = null
    private var width = 0
    private var height = 0
    private var newFrame = false
    @Volatile private var released = false

    // Due bitmap: quella mostrata e la precedente (liberata solo quando sicuramente non serve più).
    private var current: Bitmap? = null
    private var previous: Bitmap? = null

    init {
        val formatCallback = object : BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                synchronized(lock) {
                    width = sourceWidth
                    height = sourceHeight
                    pixels = ByteArray(sourceWidth * sourceHeight * 4)
                }
                return RV32BufferFormat(sourceWidth, sourceHeight)
            }

            override fun allocatedBuffers(buffers: Array<ByteBuffer>) = Unit
        }
        val renderCallback = object : RenderCallback {
            override fun display(mediaPlayer: MediaPlayer, nativeBuffers: Array<ByteBuffer>, bufferFormat: BufferFormat) {
                if (released) return
                val buf = nativeBuffers[0]
                synchronized(lock) {
                    val px = pixels ?: return
                    buf.rewind()
                    buf.get(px, 0, min(px.size, buf.remaining()))
                    newFrame = true
                }
            }
        }
        player.videoSurface().set(factory.videoSurfaces().newVideoSurface(formatCallback, renderCallback, true))
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) = ui {
                // VLC applica il volume solo con l'audio avviato: lo reimpostiamo a ogni nuovo contenuto.
                player.audio().setVolume((volume * 100).toInt())
                listener?.onBufferingChanged(false)
                listener?.onPlayingChanged(true)
            }

            override fun paused(mediaPlayer: MediaPlayer) = ui { listener?.onPlayingChanged(false) }
            override fun stopped(mediaPlayer: MediaPlayer) = ui { listener?.onPlayingChanged(false) }
            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) = ui {
                listener?.onBufferingChanged(newCache < 100f)
            }

            override fun finished(mediaPlayer: MediaPlayer) = ui {
                listener?.onPlayingChanged(false)
                listener?.onEnded()
            }

            override fun error(mediaPlayer: MediaPlayer) = ui { listener?.onError("errore di VLC") }
        })
    }

    /** Gli eventi di VLC arrivano su un thread suo: li riportiamo sul thread dell'interfaccia. */
    private fun ui(block: () -> Unit) {
        SwingUtilities.invokeLater { if (!released) block() }
    }

    override val positionMs: Long get() = if (released) 0 else player.status().time().coerceAtLeast(0)
    override val durationMs: Long get() = if (released) 0 else player.status().length().coerceAtLeast(0)
    override val isPlaying: Boolean get() = !released && player.status().isPlaying
    override var volume: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            if (!released) player.audio().setVolume((field * 100).toInt())
        }

    override fun load(url: String, startMs: Long) {
        // Canali live: cache più corta, così partono prima (film e serie restano a 1,5 s, più stabili).
        val caching = if ("/live/" in url) 1000 else 1500
        val options = mutableListOf(":http-user-agent=${XtreamClient.USER_AGENT}", ":network-caching=$caching")
        if (startMs > 0) options += ":start-time=${startMs / 1000.0}"
        listener?.onBufferingChanged(true)
        player.media().play(url, *options.toTypedArray())
    }

    override fun play() = player.controls().play()
    override fun pause() = player.controls().setPause(true)
    override fun seekTo(ms: Long) = player.controls().setTime(ms)

    override fun release() {
        released = true
        listener = null
        player.release()
        factory.release()
        synchronized(lock) {
            previous?.close()
            current?.close()
            previous = null
            current = null
        }
    }

    /** Il fotogramma più recente, se ne è arrivato uno nuovo dall'ultima chiamata. */
    fun takeFrame(): ImageBitmap? {
        synchronized(lock) {
            if (!newFrame || released) return null
            newFrame = false
            val px = pixels ?: return null
            val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
            val bmp = Bitmap()
            if (!bmp.installPixels(info, px, width * 4)) {
                bmp.close()
                return null
            }
            previous?.close()
            previous = current
            current = bmp
            return bmp.asComposeImageBitmap()
        }
    }
}

/** Usato se VLC non è installato: mostra un messaggio invece di andare in crash. */
private class MissingVlcEngine(private val reason: String) : VideoEngine {
    override var listener: VideoEngine.Listener? = null
    override val positionMs = 0L
    override val durationMs = 0L
    override val isPlaying = false
    override var volume = 1f
    override fun load(url: String, startMs: Long) {
        listener?.onBufferingChanged(false)
        listener?.onError("installa VLC 64-bit da videolan.org e riapri Velluto · $reason")
    }
    override fun play() = Unit
    override fun pause() = Unit
    override fun seekTo(ms: Long) = Unit
    override fun release() = Unit
}

@Composable
fun rememberVideoEngine(): VideoEngine = remember {
    try {
        VlcEngine()
    } catch (t: Throwable) {
        MissingVlcEngine(t.message ?: t.javaClass.simpleName)
    }
}

@Composable
fun VideoSurface(engine: VideoEngine, modifier: Modifier) {
    val vlc = engine as? VlcEngine
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    if (vlc != null) {
        LaunchedEffect(vlc) {
            while (isActive) {
                withFrameNanos { }
                vlc.takeFrame()?.let { image = it }
            }
        }
    }
    Canvas(modifier.background(Color.Black)) {
        val img = image ?: return@Canvas
        val scale = min(size.width / img.width, size.height / img.height)
        val w = (img.width * scale).toInt()
        val h = (img.height * scale).toInt()
        drawImage(
            image = img,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()),
            dstSize = IntSize(w, h),
            filterQuality = FilterQuality.Medium,
        )
    }
}
