package com.velluto.tv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.roundToInt

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

    // Volume: resta uguale cambiando canale o episodio (lo ricorda il ViewModel).
    var volume by remember { mutableFloatStateOf(vm.playerVolume) }
    var muted by remember { mutableStateOf(vm.playerMuted) }
    var volumeShownAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(engine) { engine.volume = if (muted) 0f else volume }
    LaunchedEffect(volumeShownAt) {
        if (volumeShownAt > 0) {
            delay(1_500)
            volumeShownAt = 0L
        }
    }

    fun applyVolume(v: Float, mute: Boolean) {
        volume = v.coerceIn(0f, 1f)
        muted = mute
        vm.playerVolume = volume
        vm.playerMuted = mute
        engine.volume = if (mute) 0f else volume
        volumeShownAt = System.currentTimeMillis()
    }

    fun volumeUp() = applyVolume(((volume * 10).roundToInt() + 1) / 10f, mute = false)
    fun volumeDown() = applyVolume(((volume * 10).roundToInt() - 1) / 10f, mute = false)
    fun toggleMute() = applyVolume(volume, mute = !muted)
    fun toggleFullscreen() {
        vm.platform.toggleFullscreen?.invoke()
    }

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

    /** Dove riprendere un episodio: dal punto salvato se era a metà, altrimenti dall'inizio. */
    fun resumePosition(r: PlayRequest.EpisodeReq, index: Int): Long {
        val ep = r.episodes[index]
        val p = vm.library.state.value.progress[Progress.episodeKey(r.series.key, ep.season, ep.number)]
        return if (p != null && !p.finished) p.positionMs else 0L
    }

    /**
     * Passa a un altro episodio. [finished] = true quando l'episodio attuale è finito davvero
     * (fine naturale o conto alla rovescia): solo allora viene segnato come visto.
     */
    fun goToEpisode(delta: Int, finished: Boolean) {
        val r = req as? PlayRequest.EpisodeReq ?: return
        val target = r.index + delta
        if (target !in r.episodes.indices) return
        saveProgress(forceFinished = finished)
        countdown = null
        req = r.copy(index = target, startMs = if (finished) 0L else resumePosition(r, target))
    }

    fun goNext() = goToEpisode(+1, finished = true)

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

    // Barra dei controlli (si apre con la freccia giù): precedente, ±10 s, pausa, successivo.
    var controlsMode by remember { mutableStateOf(false) }
    var selectedControl by remember { mutableIntStateOf(0) }

    // Nasconde i controlli dopo qualche secondo (restano visibili in pausa).
    LaunchedEffect(overlayTick, isPlaying, controlsMode) {
        overlayVisible = true
        delay(if (controlsMode) 6_000 else 4_000)
        if (isPlaying) {
            overlayVisible = false
            controlsMode = false
        }
    }

    LaunchedEffect(countdown) {
        val c = countdown ?: return@LaunchedEffect
        if (c <= 0) goNext() else {
            delay(1_000)
            if (countdown == c) countdown = c - 1
        }
    }

    PlatformBackHandler {
        if (controlsMode) {
            controlsMode = false
            showOverlay()
        } else {
            countdown = null
            vm.back()
        }
    }

    val r = req
    val nearEnd = hasNext(r) && countdown == null && durationMs > 0 && durationMs - positionMs in 1..NEAR_END_MS

    fun seekBy(deltaMs: Long) {
        val max = if (durationMs > 0) durationMs - 1_000 else Long.MAX_VALUE
        engine.seekTo((engine.positionMs + deltaMs).coerceIn(0L, max))
        showOverlay()
    }

    // Barra dei controlli: in diretta canale precedente/successivo e volume; per film e serie anche
    // ±10 s, pausa ed episodio precedente/successivo. Su Windows anche "Schermo intero".
    val controls: List<PlayerControl> = buildList {
        if (r is PlayRequest.Live) {
            add(PlayerControl("⏮", "Canale prec.", r.channels.size > 1) { zap(-1) })
        } else {
            val episode = r as? PlayRequest.EpisodeReq
            if (episode != null) {
                add(PlayerControl("⏮", "Prec.", episode.index > 0) { goToEpisode(-1, finished = false) })
            }
            add(PlayerControl("«", "10 s", true) { seekBy(-10_000) })
            add(PlayerControl(if (isPlaying) "❚❚" else "▶", if (isPlaying) "Pausa" else "Riproduci", true) { togglePlay() })
            add(PlayerControl("»", "10 s", true) { seekBy(10_000) })
            if (episode != null) {
                add(PlayerControl("⏭", "Succ.", hasNext(episode)) { goToEpisode(+1, finished = false) })
            }
        }
        add(PlayerControl("−", "", true) { volumeDown() })
        add(PlayerControl("", if (muted) "Audio" else "Muto", true) { toggleMute() })
        add(PlayerControl("+", "", true) { volumeUp() })
        if (r is PlayRequest.Live) {
            add(PlayerControl("⏭", "Canale succ.", r.channels.size > 1) { zap(+1) })
        }
        if (vm.platform.toggleFullscreen != null) {
            add(PlayerControl("□", "Schermo intero", true) { toggleFullscreen() })
        }
    }
    // Aprendo la barra si parte da Pausa (film/serie) o da Muto (diretta).
    val defaultControl = controls.indexOfFirst { it.label == "Pausa" || it.label == "Riproduci" }
        .takeIf { it >= 0 } ?: controls.indexOfFirst { it.label == "Muto" || it.label == "Audio" }

    fun openControls() {
        controlsMode = true
        selectedControl = defaultControl.coerceAtLeast(0)
        showOverlay()
    }

    fun moveSelection(delta: Int) {
        var i = selectedControl + delta
        while (i in controls.indices && !controls[i].enabled) i += delta
        if (i in controls.indices) selectedControl = i
        showOverlay()
    }

    // Tenendo premuta una freccia l'avanzamento accelera.
    var heldKey by remember { mutableStateOf<Key?>(null) }
    var repeats by remember { mutableIntStateOf(0) }

    fun handleKey(key: Key): Boolean {
        if (controlsMode && controls.isNotEmpty()) {
            when (key) {
                Key.DirectionLeft -> moveSelection(-1)
                Key.DirectionRight -> moveSelection(+1)
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    controls.getOrNull(selectedControl)?.takeIf { it.enabled }?.action?.invoke()
                    showOverlay()
                }
                Key.DirectionUp -> {
                    controlsMode = false
                    showOverlay()
                }
                Key.DirectionDown -> showOverlay()
                else -> null
            }?.let { return true }
        }
        val step = if (repeats > 3) 60_000L else if (repeats > 0) 30_000L else 10_000L
        when (key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> when {
                countdown != null || nearEnd -> goNext()
                r is PlayRequest.Live -> openControls() // in diretta OK apre canali e volume
                else -> togglePlay()
            }
            // Tastiera del PC: + / − volume, M muto, F schermo intero.
            Key.Plus, Key.Equals, Key.NumPadAdd -> volumeUp()
            Key.Minus, Key.NumPadSubtract -> volumeDown()
            Key.M -> toggleMute()
            Key.F -> toggleFullscreen()
            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause, Key.Spacebar -> togglePlay()
            Key.DirectionLeft, Key.MediaRewind -> if (r is PlayRequest.Live) showOverlay() else seekBy(-step)
            Key.DirectionRight, Key.MediaFastForward -> if (r is PlayRequest.Live) showOverlay() else seekBy(step)
            Key.DirectionUp, Key.ChannelUp -> if (r is PlayRequest.Live) zap(-1) else showOverlay()
            Key.DirectionDown -> if (r is PlayRequest.Live) zap(+1) else openControls()
            Key.ChannelDown -> if (r is PlayRequest.Live) zap(+1) else showOverlay()
            Key.MediaNext -> goToEpisode(+1, finished = false)
            Key.MediaPrevious -> goToEpisode(-1, finished = false)
            else -> return false
        }
        return true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Col mouse (Windows): un clic sul video mostra i controlli, doppio clic = schermo intero.
            .pointerInput(r is PlayRequest.Live) {
                detectTapGestures(
                    onDoubleTap = { toggleFullscreen() },
                    onTap = { openControls() },
                )
            }
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
                controls = controls,
                selectedControl = if (controlsMode) selectedControl else -1,
                onControlClick = { i ->
                    selectedControl = i
                    controls.getOrNull(i)?.takeIf { it.enabled }?.action?.invoke()
                    showOverlay()
                },
            )
        }

        if (nearEnd) {
            Pill("▶  Premi OK per il prossimo episodio", Modifier.align(Alignment.BottomEnd).padding(40.dp))
        }

        // Indicazione del volume quando lo cambi.
        if (volumeShownAt > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    if (muted) "Audio disattivato" else "Volume ${(volume * 100).roundToInt()}%",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
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

/** Un pulsante della barra dei controlli del player. */
internal class PlayerControl(
    val symbol: String,
    val label: String,
    val enabled: Boolean,
    val action: () -> Unit,
)

@Composable
private fun ControlButton(control: PlayerControl, selected: Boolean, onClick: () -> Unit) {
    val bg = when {
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.14f)
    }
    val fg = when {
        !control.enabled -> Color.White.copy(alpha = 0.3f)
        selected -> Color.Black
        else -> Color.White
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .clickable(enabled = control.enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Pulsanti compatti: solo simbolo (es. volume − / +) oppure solo testo (es. Muto).
        if (control.symbol.isNotEmpty()) {
            Text(control.symbol, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        if (control.symbol.isNotEmpty() && control.label.isNotEmpty()) Spacer(Modifier.width(8.dp))
        if (control.label.isNotEmpty()) {
            Text(control.label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
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
    controls: List<PlayerControl> = emptyList(),
    selectedControl: Int = -1,
    onControlClick: (Int) -> Unit = {},
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
            Modifier.fillMaxWidth().height(220.dp).align(Alignment.BottomCenter)
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
            if (controls.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                ) {
                    controls.forEachIndexed { i, c ->
                        ControlButton(c, selected = i == selectedControl, onClick = { onControlClick(i) })
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            if (req is PlayRequest.Live) {
                Text(
                    if (selectedControl >= 0) "◀ ▶  scegli  ·  OK  conferma  ·  ▲  chiudi"
                    else "▲ ▼  cambia canale  ·  OK  canali e volume",
                    color = Palette.TextDim,
                    fontSize = 13.sp,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
                Text(
                    if (selectedControl >= 0) "◀ ▶  scegli  ·  OK  conferma  ·  ▲  chiudi"
                    else "▼  controlli  ·  ◀ ▶  ±10 s (tieni premuto per andare più veloce)  ·  OK  pausa",
                    color = Palette.TextDim,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
