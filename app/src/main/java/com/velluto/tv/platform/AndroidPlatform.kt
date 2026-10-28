package com.velluto.tv.platform

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.velluto.tv.data.XtreamClient

@Composable
fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

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
        contentScale = contentScale,
        alignment = alignment,
        modifier = modifier,
    )
}

/** Player della Fire TV basato su ExoPlayer (Media3). */
class ExoEngine(val player: ExoPlayer) : VideoEngine {
    override var listener: VideoEngine.Listener? = null

    override val positionMs: Long get() = player.currentPosition
    override val durationMs: Long get() = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
    override val isPlaying: Boolean get() = player.isPlaying
    override var volume: Float
        get() = player.volume
        set(value) {
            player.volume = value.coerceIn(0f, 1f)
        }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            listener?.onPlayingChanged(isPlaying)
        }

        override fun onPlaybackStateChanged(state: Int) {
            listener?.onBufferingChanged(state == Player.STATE_BUFFERING)
            if (state == Player.STATE_ENDED) listener?.onEnded()
        }

        override fun onPlayerError(error: PlaybackException) {
            listener?.onError(error.errorCodeName)
        }
    }

    init {
        player.addListener(playerListener)
    }

    override fun load(url: String, startMs: Long) {
        player.setMediaItem(MediaItem.fromUri(url), startMs)
        player.prepare()
        player.playWhenReady = true
    }

    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun seekTo(ms: Long) = player.seekTo(ms)

    override fun release() {
        player.removeListener(playerListener)
        player.release()
    }
}

@Composable
fun rememberVideoEngine(): VideoEngine {
    val context = LocalContext.current
    val engine = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(XtreamClient.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(20_000)
        // Parte appena c'è 1 secondo di video (di default ExoPlayer ne aspetta 2,5: nei canali live,
        // che arrivano in tempo reale, si sentiva). Buffer massimo più piccolo: meno memoria sulle Firestick.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 40_000, 1_000, 2_500)
            .build()
        ExoEngine(
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
                .setLoadControl(loadControl)
                .build()
        )
    }
    // Se l'app va in background (tasto Home), mette in pausa: il codice condiviso salva la posizione.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) engine.pause()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    return engine
}

@Composable
fun VideoSurface(engine: VideoEngine, modifier: Modifier) {
    val exo = (engine as ExoEngine).player
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                keepScreenOn = true
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                player = exo
            }
        },
        modifier = modifier,
    )
}
