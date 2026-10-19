package com.velluto.tv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velluto.tv.data.EpgEntry
import com.velluto.tv.data.Progress
import com.velluto.tv.platform.PlatformBackHandler
import com.velluto.tv.platform.VideoEngine
import com.velluto.tv.platform.VideoSurface
import com.velluto.tv.platform.rememberVideoEngine
import kotlinx.coroutines.delay

private const val NEXT_EPISODE_COUNTDOWN = 10
private const val NEAR_END_MS = 45_000L

@Composable
fun PlayerScreen(vm: AppViewModel, request: PlayRequest) {
    val client = vm.client ?: return
    val engine = rememberVideoEngine()

    var req by remember { mutableStateOf(request) }
    var liveExt by remember { mutableStateOf("ts") }
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var overlayVisible by remember { mutableStateOf(true) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }
    var loadedAt by remember { mutableLongStateOf(0L) }
    val fr = remember { FocusRequester() }

    fun hasNext(r: PlayRequest) = r is PlayRequest.EpisodeReq && r.index + 1 < r.episodes.size

    fun saveProgress(forceFinished: Boolean = false) {
        val r = req
        val dur = engine.durationMs
        if (dur <= 0) return
        val pos = if (forceFinished) dur else engine.positionMs
        if (pos < 10_000) return // aperto per sbaglio: non lo mettiamo in "Continua a guardare"
        val now = System.currentTimeMillis()
        when (r) {
            is PlayRequest.MovieReq -> vm.library.saveProgress(
                Progress(
                    key = r.movie.key, titleKey = r.movie.key, titleId = r.movie.id,
                    name = r.movie.name, image = r.movie.image, episodeId = "",
                    season = 0, episode = 0, episodeTitle = "",
                    positionMs = pos, durationMs = dur, updatedAt = now,
                )
            )
            is PlayRequest.EpisodeReq -> {
                val ep = r.episodes[r.index]
                vm.library.saveProgress(
                    Progress(
                        key = Progress.episodeKey(r.series.key, ep.season, ep.number),
                        titleKey = r.series.key, titleId = r.series.id,
                        name = r.series.name, image = r.series.image, episodeId = ep.id,
                        season = ep.season, episode = ep.number, episodeTitle = ep.title,
                        positionMs = pos, durationMs = dur, updatedAt = now,
                    )
                )
            }
            is PlayRequest.Live -> Unit
        }
    }

    fun goNext() {
        val r = req as? PlayRequest.EpisodeReq ?: return
        if (r.index + 1 >= r.episodes.size) return
        saveProgress(forceFinished = true)
        countdown = null
        req = r.copy(index = r.index + 1, startMs = 0L)
    }

    fun zap(delta: Int) {
        val r = req as? PlayRequest.Live ?: return
        val n = r.channels.size
        if (n == 0) return
        liveExt = "ts"
        req = r.copy(index = ((r.index + delta) % n + n) % n)
    }

    fun showOverlay() {
        overlayTick++
    }

    fun togglePlay() {
        if (engine.isPlaying) engine.pause() else engine.play()
        showOverlay()
    }

    // Carica il contenuto ogni volta che cambia (episodio successivo, cambio canale, fallback m3u8).
    LaunchedEffect(req, liveExt) {
        errorText = null
        countdown = null
        val r = req
        val (url, start) = when (r) {
            is PlayRequest.Live -> client.liveUrl(r.channels[r.index].id, liveExt) to 0L
            is PlayRequest.MovieReq -> client.movieUrl(r.movie.id, r.ext) to r.startMs
            is PlayRequest.EpisodeReq -> {
                val ep = r.episodes[r.index]
                client.episodeUrl(ep.id, ep.ext) to r.startMs
            }
        }
        loadedAt = System.currentTimeMillis()
        engine.load(url, start)
        showOverlay()
    }

    // Guida TV del canale in onda.
    LaunchedEffect(req) {
        val r = req
        epg = emptyList()
        if (r is PlayRequest.Live) {
            epg = runCatching { client.shortEpg(r.channels[r.index].id) }.getOrNull().orEmpty()
        }
    }

    DisposableEffect(engine) {
        engine.listener = object : VideoEngine.Listener {
            override fun onPlayingChanged(playing: Boolean) {
                isPlaying = playing
                // In pausa o in background salva subito. Subito dopo un cambio di episodio/canale
                // il player può ancora riportare la posizione del contenuto precedente: in quel caso no.
                if (!playing && System.currentTimeMillis() - loadedAt > 3_000) saveProgress()
            }

            override fun onBufferingChanged(isBuffering: Boolean) {
                buffering = isBuffering
            }

            override fun onEnded() {
                val r = req
                saveProgress(forceFinished = true)
                when {
                    hasNext(r) -> countdown = NEXT_EPISODE_COUNTDOWN
                    r !is PlayRequest.Live -> vm.back()
                }
            }

            override fun onError(message: String) {
                if (req is PlayRequest.Live && liveExt == "ts") {
                    liveExt = "m3u8" // alcuni provider accettano solo HLS
                    return
                }
                errorText = "Impossibile riprodurre ($message)"
                overlayVisible = true
            }
        }
        onDispose {
            saveProgress()
            engine.listener = null
            engine.release()
        }
    }

    // Salva la posizione ogni 10 secondi e aggiorna la barra di avanzamento.
    LaunchedEffect(engine) {
        var ticks = 0
        while (true) {
            positionMs = engine.positionMs
            durationMs = engine.durationMs.coerceAtLeast(0L)
            if (++ticks % 20 == 0 && engine.isPlaying) saveProgress()
            delay(500)
        }
    }

    // Nasconde i controlli dopo 4 secondi (restano visibili in pausa).
    LaunchedEffect(overlayTick, isPlaying) {
        overlayVisible = true
        delay(4_000)
        if (isPlaying) overlayVisible = false
    }

    LaunchedEffect(countdown) {
        val c = countdown ?: return@LaunchedEffect
        if (c <= 0) goNext() else {
            delay(1_000)
            if (countdown == c) countdown = c - 1
        }
    }

    PlatformBackHandler {
        countdown = null
        vm.back()
    }

    val r = req
    val nearEnd = hasNext(r) && countdown == null && durationMs > 0 && durationMs - positionMs in 1..NEAR_END_MS

    // Tenendo premuta una freccia l'avanzamento accelera.
    var heldKey by remember { mutableStateOf<Key?>(null) }
    var repeats by remember { mutableIntStateOf(0) }

    fun handleKey(key: Key): Boolean {
        val step = if (repeats > 3) 60_000L else if (repeats > 0) 30_000L else 10_000L
        when (key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> when {
                countdown != null || nearEnd -> goNext()
                r is PlayRequest.Live -> showOverlay()
                else -> togglePlay()
            }
            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause, Key.Spacebar -> togglePlay()
            Key.DirectionLeft, Key.MediaRewind -> {
                if (r !is PlayRequest.Live) engine.seekTo((engine.positionMs - step).coerceAtLeast(0))
                showOverlay()
            }
            Key.DirectionRight, Key.MediaFastForward -> {
                if (r !is PlayRequest.Live) {
                    val max = if (durationMs > 0) durationMs - 1_000 else Long.MAX_VALUE
                    engine.seekTo((engine.positionMs + step).coerceAtMost(max))
                }
                showOverlay()
            }
            Key.DirectionUp, Key.ChannelUp -> if (r is PlayRequest.Live) zap(-1) else showOverlay()
            Key.DirectionDown, Key.ChannelDown -> if (r is PlayRequest.Live) zap(+1) else showOverlay()
            Key.MediaNext -> goNext()
            else -> return false
        }
        return true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { e ->
                when (e.type) {
                    KeyEventType.KeyDown -> {
                        if (heldKey == e.key) repeats++ else {
                            heldKey = e.key
                            repeats = 0
                        }
                        handleKey(e.key)
                    }
                    KeyEventType.KeyUp -> {
                        heldKey = null
                        repeats = 0
                        false
                    }
                    else -> false
                }
            }
            .focusRequester(fr)
            .focusable(),
    ) {
        VideoSurface(engine, Modifier.fillMaxSize())

        if (buffering && errorText == null) {
            CircularProgressIndicator(color = Palette.Accent, modifier = Modifier.align(Alignment.Center).size(56.dp))
        }

        AnimatedVisibility(
            visible = overlayVisible || errorText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            PlayerOverlay(
                req = r,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                epg = epg,
                error = errorText,
            )
        }

        if (nearEnd) {
            Pill("▶  Premi OK per il prossimo episodio", Modifier.align(Alignment.BottomEnd).padding(40.dp))
        }

        countdown?.let { c ->
            val next = (r as? PlayRequest.EpisodeReq)?.let { it.episodes.getOrNull(it.index + 1) }
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(20.dp),
            ) {
                Text("Prossimo episodio tra $c", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (next != null) {
                    Text(
                        "S${next.season}:E${next.number}  ${next.title}",
                        color = Palette.TextDim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(360.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("OK = guarda ora  ·  Indietro = esci", color = Palette.TextDim, fontSize = 12.sp)
            }
        }
    }

    RequestFocus(fr, Unit)
}

@Composable
internal fun Pill(text: String, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(text, color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun PlayerOverlay(
    req: PlayRequest,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    epg: List<EpgEntry>,
    error: String?,
) {
    val (title, subtitle) = when (req) {
        is PlayRequest.Live -> {
            val ch = req.channels[req.index]
            val now = System.currentTimeMillis()
            val current = epg.firstOrNull { now in it.start until it.end }
            (if (ch.num > 0) "${ch.num}  ${ch.name}" else ch.name) to (current?.title ?: "")
        }
        is PlayRequest.MovieReq -> req.movie.name to ""
        is PlayRequest.EpisodeReq -> {
            val ep = req.episodes[req.index]
            req.series.name to "S${ep.season}:E${ep.number}  ${ep.title}"
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().height(140.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
        )
        Box(
            Modifier.fillMaxWidth().height(160.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
        )
        Column(Modifier.align(Alignment.TopStart).padding(start = 40.dp, top = 28.dp, end = 40.dp)) {
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = Palette.TextDim, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (error != null) {
            Box(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.85f)).padding(24.dp)
            ) {
                Text(error, color = Color.White, fontSize = 16.sp)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 40.dp, vertical = 28.dp)) {
            if (req is PlayRequest.Live) {
                Text("▲ ▼  cambia canale", color = Palette.TextDim, fontSize = 13.sp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(if (isPlaying) "❚❚" else "▶", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(formatTime(positionMs), color = Color.White, fontSize = 13.sp)
                    ProgressBar(
                        if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                        Modifier.weight(1f),
                    )
                    Text(
                        if (durationMs > 0) "-${formatTime(durationMs - positionMs)}" else "",
                        color = Color.White, fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("◀ ▶  ±10 s (tieni premuto per andare più veloce)  ·  OK  pausa", color = Palette.TextDim, fontSize = 12.sp)
            }
        }
    }
}
