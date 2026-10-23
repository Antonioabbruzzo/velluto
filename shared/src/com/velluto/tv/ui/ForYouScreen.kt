package com.velluto.tv.ui

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velluto.tv.data.Series
import com.velluto.tv.platform.RemoteImage
import kotlinx.coroutines.delay

private val CARD_HEIGHT = 200.dp
private val CARD_WIDTH = 133.dp // 2:3, locandina verticale
private val CARD_WIDTH_EXPANDED = 356.dp // 16:9, immagine panoramica

/**
 * Pagina "Per te" nello stile del nuovo Netflix per TV: niente banner in alto, la card selezionata
 * si allarga in formato panoramico e sotto la fila compaiono genere, anno e trama.
 */
@Composable
fun ForYouNetflixStyle(vm: AppViewModel, rows: List<BrowseRow>) {
    val visibleRows = rows.filter { it.items.isNotEmpty() }
    val listState = rememberLazyListState()
    var activeRow by remember { mutableIntStateOf(0) }
    var activeItem by remember { mutableStateOf(visibleRows.firstOrNull()?.items?.firstOrNull()) }
    val fr = remember { FocusRequester() }
    val savedId = vm.lastFocus["foryou"]
    val focusId = savedId?.takeIf { id -> visibleRows.any { r -> r.items.any { it.id == id } } }
        ?: visibleRows.firstOrNull()?.items?.firstOrNull()?.id

    // La fila selezionata resta sempre in alto, come nelle altre pagine.
    LaunchedEffect(activeRow) { listState.animateScrollToItem(activeRow) }
    // Trama dei film scaricata quando ti fermi su una card.
    LaunchedEffect(activeItem?.title?.key) {
        val t = activeItem?.title ?: return@LaunchedEffect
        delay(300)
        vm.loadExtra(t)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 28.dp, bottom = 420.dp),
    ) {
        itemsIndexed(visibleRows) { rowIndex, row ->
            Column(Modifier.fillMaxWidth()) {
                Text(
                    row.title,
                    modifier = Modifier.padding(start = 40.dp, bottom = 2.dp),
                    color = if (rowIndex == activeRow) Color.White else Palette.TextDim,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 40.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(row.items) { _, item ->
                        ExpandingCard(
                            item = item,
                            focusRequester = if (item.id == focusId) fr else null,
                            onFocused = {
                                activeRow = rowIndex
                                activeItem = item
                                vm.lastFocus["foryou"] = item.id
                            },
                            onHovered = {
                                activeRow = rowIndex
                                activeItem = item
                            },
                        )
                    }
                }
                if (rowIndex == activeRow) {
                    ItemInfo(vm, activeItem)
                }
            }
        }
    }
    RequestFocus(fr, "foryou")
}

/** Card che, selezionata, si allarga in formato panoramico con il titolo sull'immagine. */
@Composable
private fun ExpandingCard(
    item: RowItem,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
    onHovered: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    // Si allarga solo con la selezione (telecomando/tastiera), non col passaggio del mouse:
    // così su PC le card non scappano da sotto il cursore.
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) onHovered() }
    val width by animateDpAsState(
        if (focused) CARD_WIDTH_EXPANDED else CARD_WIDTH,
        animationSpec = tween(220),
        label = "cardWidth",
    )
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .width(width)
            .height(CARD_HEIGHT)
            .optionalFocusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .clip(shape)
            .background(Palette.Surface)
            .border(3.dp, if (focused || hovered) Color.White else Color.Transparent, shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = item.onClick),
    ) {
        val t = item.title
        val expanded = width > CARD_WIDTH + 40.dp
        RemoteImage(
            url = if (expanded) t.backdrop.ifBlank { item.image } else item.image,
            contentDescription = t.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (expanded) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))
                )
            )
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Text(
                    if (t is Series) "SERIE" else "FILM",
                    color = Palette.Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    t.name,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 26.sp,
                )
            }
        }
        if (item.progress != null) {
            ProgressBar(item.progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
    }
}

/** Sotto la fila selezionata: genere · anno · voto · tipo, e la trama. */
@Composable
private fun ItemInfo(vm: AppViewModel, item: RowItem?) {
    val t = item?.title ?: return
    val extra = vm.extras[t.key]
    val genre = (t as? Series)?.genre?.takeIf { it.isNotBlank() } ?: extra?.genre.orEmpty()
    val year = t.year.ifBlank { extra?.year.orEmpty() }
    val score = t.rating.toDoubleOrNull()?.takeIf { it > 0 }?.let { "${(it * 10).toInt()}% voto" }
    val meta = listOfNotNull(
        genre.takeIf { it.isNotBlank() },
        year.takeIf { it.isNotBlank() },
        if (t is Series) "Serie TV" else extra?.duration?.takeIf { it.isNotBlank() } ?: "Film",
        score,
    ).joinToString("  •  ")
    val plot = t.plot.ifBlank { extra?.plot.orEmpty() }
    // Altezza fissa: trame di lunghezza diversa non fanno saltare le file sotto.
    Column(Modifier.padding(start = 40.dp, end = 40.dp, top = 2.dp, bottom = 18.dp).height(96.dp).fillMaxWidth(0.62f)) {
        Text(meta, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(
            plot.ifBlank { "Trama in arrivo…" },
            color = Palette.TextDim,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
