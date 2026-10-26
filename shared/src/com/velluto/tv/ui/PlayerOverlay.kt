package com.velluto.tv.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velluto.tv.data.EpgEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Gruppo del pulsante nella barra: riproduzione a sinistra, volume e schermo intero a destra. */
internal enum class ControlGroup { PLAYBACK, VOLUME, WINDOW }

/** Un pulsante della barra dei controlli del player. */
internal class PlayerControl(
    val symbol: String,
    val label: String,
    val enabled: Boolean,
    val icon: PlayerIcon? = null,
    val group: ControlGroup = ControlGroup.PLAYBACK,
    /** Pulsante principale (play/pausa): più grande e sempre in evidenza. */
    val primary: Boolean = false,
    val action: () -> Unit,
)

/**
 * Interfaccia del player in stile classico: titolo e "indietro" in alto, al centro il grande
 * pulsante play quando è in pausa, in basso la barra di avanzamento (cliccabile e trascinabile)
 * e i comandi, con il volume a cursore. In diretta al posto della barra c'è "IN DIRETTA".
 */
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
    volume: Float = 1f,
    muted: Boolean = false,
    onSeek: ((Float) -> Unit)? = null,
    onVolumeChange: ((Float) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onTogglePlay: (() -> Unit)? = null,
) {
    val live = req is PlayRequest.Live
    val (title, subtitle) = when (req) {
        is PlayRequest.Live -> {
            val ch = req.channels[req.index]
            (if (ch.num > 0) "${ch.num}  ${ch.name}" else ch.name) to ""
        }
        is PlayRequest.MovieReq -> req.movie.name to ""
        is PlayRequest.EpisodeReq -> {
            val ep = req.episodes[req.index]
            req.series.name to "Stagione ${ep.season} · Episodio ${ep.number}  ·  ${ep.title}"
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Sfumature in alto e in basso per leggere bene testi e comandi sopra qualsiasi immagine.
        Box(
            Modifier.fillMaxWidth().height(170.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
        )
        Box(
            Modifier.fillMaxWidth().height(300.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
        )

        // ---- In alto: indietro + titolo ----
        Row(
            Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 24.dp, end = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                RoundIconButton(PlayerIcon.BACK, onClick = onBack, size = 46.dp, background = Color.White.copy(alpha = 0.14f))
                Spacer(Modifier.width(18.dp))
            }
            Column {
                Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = Palette.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // ---- Al centro: errore, oppure il grande play quando è in pausa ----
        if (error != null) {
            Box(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.85f)).padding(horizontal = 28.dp, vertical = 20.dp)
            ) {
                Text(error, color = Color.White, fontSize = 16.sp)
            }
        } else if (!live && !isPlaying && onTogglePlay != null) {
            RoundIconButton(
                PlayerIcon.PLAY,
                onClick = onTogglePlay,
                size = 92.dp,
                iconSize = 40.dp,
                background = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.align(Alignment.Center).border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            )
        }

        // ---- In basso: avanzamento (o IN DIRETTA) e comandi ----
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 40.dp, end = 40.dp, bottom = 22.dp)) {
            if (live) {
                LiveBadge(epg)
            } else {
                SeekBar(
                    fraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                    onSeek = onSeek,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                controls.forEachIndexed { i, c ->
                    if (c.group == ControlGroup.PLAYBACK) {
                        ControlIconButton(c, selected = i == selectedControl, onClick = { onControlClick(i) })
                        Spacer(Modifier.width(6.dp))
                    }
                }
                if (!live && durationMs > 0) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${formatTime(positionMs)}  /  ${formatTime(durationMs)}",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.weight(1f))
                controls.forEachIndexed { i, c ->
                    if (c.group == ControlGroup.VOLUME) {
                        ControlIconButton(c, selected = i == selectedControl, onClick = { onControlClick(i) }, size = 44.dp)
                        // Il cursore del volume (col mouse) sta subito dopo il pulsante "−".
                        if (c.icon == PlayerIcon.MINUS && onVolumeChange != null) {
                            VolumeSlider(if (muted) 0f else volume, onVolumeChange)
                        }
                    }
                }
                controls.forEachIndexed { i, c ->
                    if (c.group == ControlGroup.WINDOW) {
                        Spacer(Modifier.width(8.dp))
                        ControlIconButton(c, selected = i == selectedControl, onClick = { onControlClick(i) }, size = 44.dp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val selectedLabel = controls.getOrNull(selectedControl)?.label
            Text(
                when {
                    selectedLabel != null -> "$selectedLabel   ·   ◀ ▶ scegli  ·  OK conferma  ·  ▲ chiudi"
                    live -> "▲ ▼  cambia canale  ·  OK  canali e volume"
                    else -> "▼  controlli  ·  ◀ ▶  ±10 s (tieni premuto per andare più veloce)  ·  OK  pausa"
                },
                color = Palette.TextDim,
                fontSize = 12.sp,
            )
        }
    }
}

/** Pulsante tondo con icona (anche per il mouse). Non prende il focus: col telecomando si usa la barra. */
@Composable
private fun RoundIconButton(
    icon: PlayerIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = size * 0.46f,
    background: Color = Color.Transparent,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (hovered) Color.White.copy(alpha = 0.28f) else background)
            .focusProperties { canFocus = false }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PlayerIconView(icon, Color.White, iconSize)
    }
}

@Composable
private fun ControlIconButton(control: PlayerControl, selected: Boolean, onClick: () -> Unit, size: Dp = 48.dp) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val buttonSize = if (control.primary) size + 12.dp else size
    val bg = when {
        selected -> Color.White
        hovered -> Color.White.copy(alpha = 0.25f)
        control.primary -> Color.White.copy(alpha = 0.16f)
        else -> Color.Transparent
    }
    val fg = when {
        !control.enabled -> Color.White.copy(alpha = 0.3f)
        selected -> Color.Black
        else -> Color.White
    }
    Box(
        Modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(bg)
            .focusProperties { canFocus = false }
            .hoverable(interaction)
            .clickable(enabled = control.enabled, interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val icon = control.icon
        if (icon != null) {
            PlayerIconView(icon, fg, buttonSize * 0.5f)
        } else {
            Text(control.symbol.ifBlank { control.label }, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Barra di avanzamento: un clic salta a quel punto, trascinando si sceglie il punto e si salta al rilascio. */
@Composable
private fun SeekBar(fraction: Float, onSeek: ((Float) -> Unit)?) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val active = hovered || dragFraction != null
    val trackHeight by animateDpAsState(if (active) 7.dp else 4.dp, label = "seekTrack")
    val shown = (dragFraction ?: fraction).coerceIn(0f, 1f)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .hoverable(interaction)
            .then(
                if (onSeek != null) {
                    Modifier
                        .pointerInput(Unit) {
                            detectTapGestures { o -> onSeek((o.x / size.width).coerceIn(0f, 1f)) }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { o -> dragFraction = (o.x / size.width).coerceIn(0f, 1f) },
                                onDragEnd = {
                                    dragFraction?.let(onSeek)
                                    dragFraction = null
                                },
                                onDragCancel = { dragFraction = null },
                            ) { change, _ ->
                                change.consume()
                                dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            }
                        }
                } else Modifier
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        val w = maxWidth
        Box(Modifier.fillMaxWidth().height(trackHeight).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.28f)))
        Box(Modifier.width(w * shown).height(trackHeight).clip(RoundedCornerShape(4.dp)).background(Palette.Accent))
        val thumb = if (active) 16.dp else 0.dp
        if (thumb > 0.dp) {
            Box(
                Modifier
                    .offset(x = (w * shown - thumb / 2).coerceAtLeast(0.dp))
                    .size(thumb)
                    .clip(CircleShape)
                    .background(Palette.Accent)
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}

/** Cursore del volume (mouse): clic o trascinamento. */
@Composable
private fun VolumeSlider(value: Float, onChange: (Float) -> Unit) {
    BoxWithConstraints(
        Modifier
            .padding(horizontal = 6.dp)
            .width(110.dp)
            .height(28.dp)
            .pointerInput(Unit) { detectTapGestures { o -> onChange((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val w = maxWidth
        val v = value.coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.28f)))
        Box(Modifier.width(w * v).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
        Box(
            Modifier
                .offset(x = (w * v - 6.dp).coerceAtLeast(0.dp))
                .size(12.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
    Text("${(value * 100).roundToInt()}", color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.width(30.dp))
}

/** In diretta: bollino rosso, programma in onda e orari. */
@Composable
private fun LiveBadge(epg: List<EpgEntry>) {
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.ITALY) }
    val now = System.currentTimeMillis()
    val current = epg.firstOrNull { now in it.start until it.end }
    val next = epg.firstOrNull { it.start >= (current?.end ?: now) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.clip(RoundedCornerShape(4.dp)).background(Palette.Accent).padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                Spacer(Modifier.width(6.dp))
                Text("IN DIRETTA", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            if (current != null) {
                Text(
                    "${fmt.format(Date(current.start))} – ${fmt.format(Date(current.end))}   ${current.title}",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (current != null && current.end > current.start) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(0.5f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f))) {
                Box(
                    Modifier.fillMaxHeight()
                        .fillMaxWidth(((now - current.start).toFloat() / (current.end - current.start)).coerceIn(0f, 1f))
                        .background(Palette.Accent)
                )
            }
        }
        if (next != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "A seguire  ${fmt.format(Date(next.start))}  ${next.title}",
                color = Palette.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
