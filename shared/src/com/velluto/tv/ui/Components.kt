package com.velluto.tv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.velluto.tv.platform.RemoteImage
import com.velluto.tv.data.Title
import kotlinx.coroutines.delay

/** Una locandina nelle file orizzontali. */
data class RowItem(
    val id: String,
    val title: Title,
    val image: String = title.image,
    val progress: Float? = null,
    val label: String? = null,
    val onClick: () -> Unit,
)

/** Evidenziato: selezionato col telecomando/tastiera oppure con il mouse sopra (Windows). */
@Composable
fun MutableInteractionSource.isHighlighted(): Boolean {
    val focused by collectIsFocusedAsState()
    val hovered by collectIsHoveredAsState()
    return focused || hovered
}

fun Modifier.optionalFocusRequester(fr: FocusRequester?): Modifier =
    if (fr != null) this.focusRequester(fr) else this

/**
 * Chiede il focus appena l'elemento è pronto (le liste lazy compongono con un frame di ritardo).
 * La richiesta viene ripetuta qualche volta: al primo frame l'elemento può non essere ancora focalizzabile.
 */
@Composable
fun RequestFocus(fr: FocusRequester, key: Any? = Unit) {
    LaunchedEffect(key) {
        var granted = 0
        repeat(30) {
            if (runCatching { fr.requestFocus() }.isSuccess && ++granted >= 4) return@LaunchedEffect
            delay(50)
        }
    }
}

@Composable
fun PosterCard(
    item: RowItem,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 120.dp,
    aspect: Float = 2f / 3f,
    focusRequester: FocusRequester? = null,
    /** Mouse sopra la card (Windows). Se null fa come [onFocused]. */
    onHovered: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.isHighlighted()
    // Il passaggio del mouse è separato dalla selezione col telecomando: non deve far scorrere la pagina.
    val hovered by interaction.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) (onHovered ?: onFocused)() }
    val shape = RoundedCornerShape(6.dp)

    // Nessun ingrandimento: la card selezionata ha solo un bordo bianco, così niente si sposta.
    Box(
        modifier
            .width(width)
            .aspectRatio(aspect)
            .optionalFocusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .clip(shape)
            .background(Palette.Surface)
            .border(3.dp, if (focused) Color.White else Color.Transparent, shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = item.onClick),
    ) {
        Text(
            item.title.name,
            modifier = Modifier.align(Alignment.Center).padding(8.dp),
            color = Palette.TextDim,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.image.isNotBlank()) {
            RemoteImage(
                url = item.image,
                contentDescription = item.title.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (item.label != null) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(item.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        if (item.progress != null) {
            ProgressBar(item.progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(4.dp).background(Color(0x66FFFFFF))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(Palette.Accent))
    }
}

/**
 * Titolo della fila; se la fila è una categoria c'è "Vedi tutti ›": cliccabile col mouse e,
 * col telecomando, selezionabile con ↑ dalla fila in cui sei ([selectable] = fila attiva).
 * Le altre file non lo rendono selezionabile, così scendendo con ↓ si va dritti alle card.
 */
@Composable
internal fun RowTitle(title: String, onOpenAll: (() -> Unit)?, selectable: Boolean = true) {
    val titleInteraction = remember { MutableInteractionSource() }
    val titleHovered by titleInteraction.collectIsHoveredAsState()
    Row(
        Modifier.padding(start = 40.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = if (onOpenAll != null) {
                Modifier
                    .hoverable(titleInteraction)
                    .pointerInput(onOpenAll) { detectTapGestures { onOpenAll() } }
            } else Modifier,
            color = if (titleHovered) Color.White else Palette.Text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        if (onOpenAll != null) {
            Spacer(Modifier.width(10.dp))
            val interaction = remember { MutableInteractionSource() }
            val highlighted = interaction.isHighlighted() || titleHovered
            Text(
                "Vedi tutti  ›",
                modifier = Modifier
                    .focusProperties { canFocus = selectable }
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (highlighted) Color.White else Color.Transparent)
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null, onClick = onOpenAll)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                color = if (highlighted) Color.Black else Palette.TextDim,
                fontSize = 13.sp,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/** Ultima card della fila: apre tutti i titoli della categoria (col telecomando: → fino in fondo e OK). */
@Composable
internal fun SeeAllCard(total: Int, width: Dp, aspect: Float, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.isHighlighted()
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .width(width)
            .aspectRatio(aspect)
            .clip(shape)
            .background(if (focused) Color.White else Palette.SurfaceHigh)
            .border(3.dp, if (focused) Color.White else Color.Transparent, shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("›", color = if (focused) Color.Black else Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text("Vedi tutti", color = if (focused) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("$total titoli", color = if (focused) Color.DarkGray else Palette.TextDim, fontSize = 12.sp)
        }
    }
}

/** Freccia per scorrere una fila col mouse (non selezionabile col telecomando). */
@Composable
internal fun RowArrow(symbol: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .padding(horizontal = 6.dp)
            .size(width = 44.dp, height = 96.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Banner grande in alto con lo sfondo del titolo selezionato.
 * Il blocco di testo ha altezza fissa e il cambio di titolo è in dissolvenza:
 * titoli o trame di lunghezza diversa non fanno "saltare" niente.
 */
@Composable
fun Hero(
    title: Title?,
    modifier: Modifier = Modifier,
    extraLine: String? = null,
    /** Trama/genere/durata scaricati a parte, per i titoli che il provider manda senza trama (i film). */
    extraFor: (Title) -> TitleExtra? = { null },
) {
    Crossfade(
        targetState = title to extraLine,
        animationSpec = tween(durationMillis = 350),
        modifier = modifier.fillMaxWidth(),
        label = "hero",
    ) { (t, extra) ->
        Box(Modifier.fillMaxSize()) {
            if (t != null) {
                RemoteImage(
                    url = t.backdrop.ifBlank { t.image },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.75f },
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to Palette.Background, 0.45f to Palette.Background.copy(alpha = 0.6f), 1f to Color.Transparent,
                    )
                )
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to Palette.Background)
                )
            )
            if (t != null) {
                val more = extraFor(t)
                val plot = t.plot.ifBlank { more?.plot.orEmpty() }
                val meta = listOfNotNull(
                    extra,
                    more?.genre?.takeIf { it.isNotBlank() },
                    more?.duration?.takeIf { it.isNotBlank() },
                ).joinToString("  ·  ")
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 40.dp, bottom = 8.dp)
                        .fillMaxWidth(0.55f)
                        .height(132.dp),
                ) {
                    Text(
                        t.name,
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 36.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    MetaLine(year = t.year.ifBlank { more?.year.orEmpty() }, rating = t.rating, extra = meta)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        plot,
                        color = Palette.Text,
                        fontSize = 13.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun MetaLine(year: String, rating: String, extra: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val score = rating.toDoubleOrNull()
        if (score != null && score > 0) {
            Text("${(score * 10).toInt()}% voto", color = Palette.Good, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        if (year.isNotBlank()) Text(year, color = Palette.TextDim, fontSize = 13.sp)
        if (!extra.isNullOrBlank()) Text(extra, color = Palette.TextDim, fontSize = 13.sp, maxLines = 1)
    }
}

/** Pulsante "← Indietro" in alto a sinistra (categorie, generi, schede di serie e film). */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvButton(text = "←  Indietro", onClick = onClick, modifier = modifier)
}

/** Pulsante in stile TV: bianco quando selezionato. */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    primary: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.isHighlighted()
    val bg = when {
        focused -> Color.White
        primary -> Color.White.copy(alpha = 0.25f)
        else -> Color.White.copy(alpha = 0.12f)
    }
    val fg = if (focused) Color.Black else Color.White
    Row(
        modifier
            .optionalFocusRequester(focusRequester)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun WatchedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(Palette.Accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Check, contentDescription = "Visto", tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
