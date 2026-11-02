package com.velluto.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velluto.tv.data.LiveChannel
import com.velluto.tv.data.XtreamClient
import com.velluto.tv.platform.PlatformBackHandler
import com.velluto.tv.platform.RemoteImage
import com.velluto.tv.platform.VideoEngine
import com.velluto.tv.platform.VideoSurface
import com.velluto.tv.platform.rememberVideoEngine
import kotlinx.coroutines.delay

/**
 * Quanti canali si possono guardare insieme: 2 sulla Firestick, 4 sul PC. Ogni riquadro usa una
 * connessione del provider: se l'abbonamento non ne ha abbastanza, quel riquadro resta "non disponibile".
 */
fun AppViewModel.multiScreenSlots(): Int = platform.maxMultiScreens

/**
 * Multiscreen: 2 o 4 canali live insieme. Si sente l'audio solo del riquadro selezionato.
 * ◀ ▶ ▲ ▼ scelgono il riquadro, OK cambia il suo canale, Indietro torna a schermo pieno sul
 * canale del riquadro selezionato.
 */
@Composable
fun MultiScreen(vm: AppViewModel, channels: List<LiveChannel>, startIndex: Int) {
    val client = vm.client ?: return
    val count = remember { vm.multiScreenSlots().coerceAtLeast(2) }
    val cols = 2
    // Canale di ogni riquadro (indice in [channels]); il primo è quello che stavi guardando.
    val slots = remember { mutableStateListOf<Int?>().apply { add(startIndex); repeat(count - 1) { add(null) } } }
    var selected by remember { mutableIntStateOf(1) }
    // Si parte già scegliendo il canale del secondo riquadro.
    var picking by remember { mutableStateOf(true) }
    val fr = remember { FocusRequester() }

    PlatformBackHandler {
        if (picking) {
            picking = false
        } else {
            val idx = slots[selected] ?: slots[0] ?: startIndex
            vm.back()
            vm.navigate(Screen.Player(PlayRequest.Live(channels, idx)))
        }
    }

    fun move(dx: Int, dy: Int) {
        val rows = (count + cols - 1) / cols
        val row = (selected / cols + dy).coerceIn(0, rows - 1)
        val col = (selected % cols + dx).coerceIn(0, cols - 1)
        selected = (row * cols + col).coerceAtMost(count - 1)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { e ->
                if (picking || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> move(-1, 0)
                    Key.DirectionRight -> move(1, 0)
                    Key.DirectionUp -> move(0, -1)
                    Key.DirectionDown -> move(0, 1)
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> picking = true
                    else -> return@onKeyEvent false
                }
                true
            }
            .focusRequester(fr)
            .focusable(),
    ) {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            for (row in 0 until (count + cols - 1) / cols) {
                Row(
                    Modifier.fillMaxWidth().let { if (count > cols) it.weight(1f) else it },
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    for (col in 0 until cols) {
                        val i = row * cols + col
                        if (i >= count) continue
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            MultiSlot(
                                client = client,
                                vm = vm,
                                channel = slots[i]?.let { channels.getOrNull(it) },
                                active = i == selected,
                                modifier = Modifier.aspectRatio(16f / 9f),
                                onTap = { selected = i },
                                onDoubleTap = {
                                    selected = i
                                    picking = true
                                },
                            )
                        }
                    }
                }
            }
        }

        if (!picking) {
            Text(
                "◀ ▶ ▲ ▼  scegli riquadro   ·   OK  cambia canale   ·   Indietro  schermo pieno",
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                color = Palette.TextDim, fontSize = 12.sp,
            )
        }

        if (picking) {
            ChannelPicker(
                channels = channels,
                current = slots[selected],
                slotNumber = selected + 1,
                modifier = Modifier.align(Alignment.CenterEnd),
                onPick = { idx ->
                    slots[selected] = idx
                    picking = false
                },
            )
        }
    }
    // Focus: all'elenco canali quando è aperto (ci pensa ChannelPicker), altrimenti ai riquadri.
    if (!picking) RequestFocus(fr, "multi-grid")
}

@Composable
private fun MultiSlot(
    client: XtreamClient,
    vm: AppViewModel,
    channel: LiveChannel?,
    active: Boolean,
    modifier: Modifier,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .clip(shape)
            .background(Palette.Surface)
            .border(if (active) 3.dp else 1.dp, if (active) Color.White else Palette.Stroke, shape)
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onDoubleTap = { onDoubleTap() }) },
        contentAlignment = Alignment.Center,
    ) {
        if (channel == null) {
            Text(
                if (active) "OK  per scegliere un canale" else "Riquadro libero",
                color = Palette.TextDim, fontSize = 14.sp, textAlign = TextAlign.Center,
            )
        } else {
            // Un player nuovo per ogni canale del riquadro: niente stato del canale precedente.
            key(channel.id) { SlotPlayer(client, vm, channel, active) }
        }
    }
}

@Composable
private fun SlotPlayer(client: XtreamClient, vm: AppViewModel, channel: LiveChannel, active: Boolean) {
    val engine = rememberVideoEngine()
    var started by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var ext by remember { mutableStateOf(vm.liveExt) }
    var fallbackTried by remember { mutableStateOf(false) }
    // Tentativi dopo un errore: entrando nel multiscreen il provider può tenere occupata per qualche
    // secondo la connessione del canale appena chiuso.
    var retry by remember { mutableIntStateOf(0) }
    val isActive by rememberUpdatedState(active)
    // Audio solo dal riquadro selezionato. Si riapplica anche quando lo stream parte: VLC (PC)
    // riporta il volume al massimo a ogni nuovo stream.
    fun applyVolume() {
        engine.volume = if (isActive && !vm.playerMuted) vm.playerVolume else 0f
    }

    DisposableEffect(engine) {
        engine.listener = object : VideoEngine.Listener {
            override fun onPlayingChanged(playing: Boolean) {
                if (playing) {
                    started = true
                    applyVolume()
                }
            }
            override fun onBufferingChanged(isBuffering: Boolean) = Unit
            override fun onEnded() = Unit
            override fun onError(message: String) {
                if (!fallbackTried) {
                    fallbackTried = true
                    ext = if (ext == "ts") "m3u8" else "ts"
                } else {
                    failed = true
                }
            }
        }
        onDispose {
            engine.listener = null
            engine.release()
        }
    }
    LaunchedEffect(failed) {
        if (failed && retry < 2) {
            delay(3_000)
            retry++
        }
    }
    LaunchedEffect(ext, retry) {
        started = false
        failed = false
        if (retry > 0) fallbackTried = true // nei nuovi tentativi niente cambio di formato: era la connessione
        applyVolume()
        engine.load(client.liveUrl(channel.id, ext), 0L)
    }
    LaunchedEffect(active) { applyVolume() }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        VideoSurface(engine, Modifier.fillMaxSize())
        if (!started || failed) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (channel.icon.isNotBlank()) {
                    RemoteImage(
                        url = channel.icon, contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.size(width = 120.dp, height = 68.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    if (failed) "${channel.name} · non disponibile" else channel.name,
                    color = if (failed) Palette.TextDim else Color.White, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Nome del canale in basso a sinistra, più evidente sul riquadro che si sente.
        Text(
            (if (active) "🔊  " else "") + channel.name,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            color = if (active) Color.White else Palette.TextDim,
            fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChannelPicker(
    channels: List<LiveChannel>,
    current: Int?,
    slotNumber: Int,
    modifier: Modifier,
    onPick: (Int) -> Unit,
) {
    val start = (current ?: 0).coerceIn(0, (channels.size - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (start - 3).coerceAtLeast(0))
    val firstFr = remember { FocusRequester() }
    Column(
        modifier
            .fillMaxHeight()
            .width(420.dp)
            .background(Palette.Background.copy(alpha = 0.94f))
            .padding(top = 24.dp),
    ) {
        Text(
            "Canale per il riquadro $slotNumber",
            modifier = Modifier.padding(horizontal = 20.dp),
            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        )
        Text(
            "OK  sceglie   ·   Indietro  annulla",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            color = Palette.TextDim, fontSize = 12.sp,
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            itemsIndexed(channels) { i, ch ->
                Box(if (i == start) Modifier.focusRequester(firstFr) else Modifier) {
                    ChannelEntry(channel = ch, onFocused = {}, onClick = { onPick(i) })
                }
            }
        }
    }
    RequestFocus(firstFr, "picker-$slotNumber")
}
