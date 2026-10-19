package com.velluto.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import coil.compose.AsyncImage
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
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, label = "posterScale")
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier
            .width(width)
            .aspectRatio(aspect)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .optionalFocusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .clip(shape)
            .background(Palette.Surface)
            .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
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
            AsyncImage(
                model = item.image,
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

@Composable
fun ContentRow(
    title: String,
    items: List<RowItem>,
    onItemFocused: (RowItem) -> Unit,
    focusId: String?,
    focusRequester: FocusRequester,
    cardWidth: Dp = 120.dp,
    aspect: Float = 2f / 3f,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            modifier = Modifier.padding(start = 40.dp, top = 6.dp),
            color = Palette.Text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(items) { _, item ->
                PosterCard(
                    item = item,
                    onFocused = { onItemFocused(item) },
                    width = cardWidth,
                    aspect = aspect,
                    focusRequester = if (item.id == focusId) focusRequester else null,
                )
            }
        }
    }
}

/** Banner grande in alto con lo sfondo del titolo selezionato. */
@Composable
fun Hero(title: Title?, modifier: Modifier = Modifier, extraLine: String? = null) {
    Box(modifier.fillMaxWidth()) {
        if (title != null) {
            AsyncImage(
                model = title.backdrop.ifBlank { title.image },
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
        if (title != null) {
            Column(Modifier.align(Alignment.BottomStart).padding(start = 40.dp, bottom = 12.dp).fillMaxWidth(0.55f)) {
                Text(
                    title.name,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 34.sp,
                )
                Spacer(Modifier.height(6.dp))
                MetaLine(year = title.year, rating = title.rating, extra = extraLine)
                if (title.plot.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        title.plot,
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
    val focused by interaction.collectIsFocusedAsState()
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
