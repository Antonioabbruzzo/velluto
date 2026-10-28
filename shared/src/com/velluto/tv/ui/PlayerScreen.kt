package com.velluto.tv.ui

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.velluto.tv.data.Title
import com.velluto.tv.data.Series
import com.velluto.tv.data.Recommender
import com.velluto.tv.data.Movie
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.input.pointer.PointerEventType
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
/** Negli ultimi minuti di un film compaiono i titoli consigliati (di solito sono i titoli di coda). */
private const val POST_PLAY_MS = 180_000L

@Composable
fun PlayerScreen(vm: AppViewModel, request: PlayRequest) {
    val client = vm.client ?: return
    val engine = rememberVideoEngine()

    var req by remember { mutableStateOf(request) }
    // Formato dei canali live: si parte da quello che ha funzionato l'ultima volta (lo ricorda il ViewModel).
    var liveExt by remember { mutableStateOf(vm.liveExt) }
    var liveFallbackTried by remember { mutableStateOf(false) }
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
        liveExt = vm.liveExt
        liveFallbackTried = false
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

    // Fine film: titoli consigliati in base a questo film, calcolati in sottofondo appena parte.
    var similar by remember { mutableStateOf<List<Title>>(emptyList()) }
    var movieEnded by remember { mutableStateOf(false) }
    var postPlayDismissed by remember { mutableStateOf(false) }
    var postPlaySelected by remember { mutableIntStateOf(0) }
    LaunchedEffect(req) {
        val r = req
        similar = emptyList()
        movieEnded = false
        postPlayDismissed = false
        postPlaySelected = 0
        val catalog = vm.catalog
        if (r is PlayRequest.MovieReq && catalog != null) {
            val lib = vm.library.state.value
            similar = withContext(Dispatchers.Default) {
                runCatching { Recommender.similarTo(catalog, r.movie, lib) }.getOrDefault(emptyList())
            }
        }
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
                    r is PlayRequest.MovieReq && similar.isNotEmpty() -> {
                        movieEnded = true
                        postPlayDismissed = false
                    }
                    r !is PlayRequest.Live -> vm.back()
                }
            }

            override fun onError(message: String) {
                if (req is PlayRequest.Live && !liveFallbackTried) {
                    // Alcuni provider accettano solo HLS (o solo TS): si prova l'altro formato e, se va,
                    // dal canale successivo si parte direttamente da quello, senza riaspettare l'errore.
                    liveFallbackTried = true
                    liveExt = if (liveExt == "ts") "m3u8" else "ts"
                    vm.liveExt = liveExt
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
    val postPlay = r is PlayRequest.MovieReq && similar.isNotEmpty() && !postPlayDismissed &&
        (movieEnded || (durationMs > POST_PLAY_MS * 4 && durationMs - positionMs in 1..POST_PLAY_MS))

    // Apre il titolo consigliato: si esce dal player e si va alla sua scheda.
    fun openSimilar(i: Int) {
        val t = similar.getOrNull(i) ?: return
        countdown = null
        vm.back()
        when (t) {
            is Series -> vm.navigate(Screen.SeriesDetail(t))
            is Movie -> vm.navigate(Screen.MovieDetail(t))
        }
    }

    fun seekBy(deltaMs: Long) {
        val max = if (durationMs > 0) durationMs - 1_000 else Long.MAX_VALUE
        engine.seekTo((engine.positionMs + deltaMs).coerceIn(0L, max))
        showOverlay()
    }

    // Barra dei controlli: in diretta canale precedente/successivo e volume; per film e serie anche
    // ±10 s, pausa ed episodio precedente/successivo. Su Windows anche "Schermo intero".
    val controls: List<PlayerControl> = buildList {
        if (r is PlayRequest.Live) {
            add(PlayerControl("⏮", "Canale prec.", r.channels.size > 1, PlayerIcon.PREV) { zap(-1) })
            add(PlayerControl("⏭", "Canale succ.", r.channels.size > 1, PlayerIcon.NEXT) { zap(+1) })
        } else {
            val episode = r as? PlayRequest.EpisodeReq
            if (episode != null) {
                add(PlayerControl("⏮", "Episodio prec.", episode.index > 0, PlayerIcon.PREV) { goToEpisode(-1, finished = false) })
            }
            add(PlayerControl("«", "Indietro 10 s", true, PlayerIcon.BACK10) { seekBy(-10_000) })
            add(
                PlayerControl(
                    if (isPlaying) "❚❚" else "▶",
                    if (isPlaying) "Pausa" else "Riproduci",
                    true,
                    if (isPlaying) PlayerIcon.PAUSE else PlayerIcon.PLAY,
                    primary = true,
                ) { togglePlay() }
            )
            add(PlayerControl("»", "Avanti 10 s", true, PlayerIcon.FWD10) { seekBy(10_000) })
            if (episode != null) {
                add(PlayerControl("⏭", "Episodio succ.", hasNext(episode), PlayerIcon.NEXT) { goToEpisode(+1, finished = false) })
            }
        }
        add(
            PlayerControl("", if (muted) "Audio" else "Muto", true, if (muted) PlayerIcon.MUTED else PlayerIcon.VOLUME, ControlGroup.VOLUME) {
                toggleMute()
            }
        )
        add(PlayerControl("−", "Volume −", true, PlayerIcon.MINUS, ControlGroup.VOLUME) { volumeDown() })
        add(PlayerControl("+", "Volume +", true, PlayerIcon.PLUS, ControlGroup.VOLUME) { volumeUp() })
        if (vm.platform.toggleFullscreen != null) {
            add(PlayerControl("□", "Schermo intero", true, PlayerIcon.FULLSCREEN, ControlGroup.WINDOW) { toggleFullscreen() })
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
        if (postPlay && !controlsMode) {
            when (key) {
                Key.DirectionLeft -> postPlaySelected = (postPlaySelected - 1).coerceAtLeast(0)
                Key.DirectionRight -> postPlaySelected = (postPlaySelected + 1).coerceAtMost(similar.lastIndex)
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> openSimilar(postPlaySelected)
                Key.DirectionUp -> postPlayDismissed = true
                Key.DirectionDown -> Unit
                else -> null
            }?.let { return true }
        }
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
            // Col mouse (Windows): muovendolo compaiono i comandi; un clic mette in pausa/riprende
            // (in diretta mostra i comandi), doppio clic = schermo intero.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        if (awaitPointerEvent().type == PointerEventType.Move) showOverlay()
                    }
                }
            }
            .pointerInput(r is PlayRequest.Live) {
                detectTapGestures(
                    onDoubleTap = { toggleFullscreen() },
                    onTap = { if (r is PlayRequest.Live) showOverlay() else togglePlay() },
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
            visible = (overlayVisible && !postPlay) || errorText != null,
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
                volume = volume,
                muted = muted,
                onSeek = { f ->
                    if (durationMs > 0) {
                        engine.seekTo((f * durationMs).toLong().coerceIn(0L, durationMs - 1_000))
                        positionMs = (f * durationMs).toLong()
                    }
                    showOverlay()
                },
                onVolumeChange = { v ->
                    applyVolume(v, mute = false)
                    showOverlay()
                },
                onBack = {
                    countdown = null
                    vm.back()
                },
                onTogglePlay = { togglePlay() },
            )
        }

        if (nearEnd) {
            Pill("▶  Premi OK per il prossimo episodio", Modifier.align(Alignment.BottomEnd).padding(40.dp))
        }

        AnimatedVisibility(
            visible = postPlay,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            val movie = (r as? PlayRequest.MovieReq)?.movie
            PostPlayPanel(
                source = movie?.name.orEmpty(),
                titles = similar,
                selected = postPlaySelected,
                ended = movieEnded,
                onOpen = { openSimilar(it) },
                onDismiss = { postPlayDismissed = true },
            )
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

