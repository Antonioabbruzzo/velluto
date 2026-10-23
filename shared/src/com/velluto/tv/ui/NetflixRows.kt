package com.velluto.tv.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.velluto.tv.data.Title
import com.velluto.tv.platform.RemoteImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CARD_HEIGHT = 200.dp
private val CARD_WIDTH = 133.dp // 2:3, locandina verticale
private val CARD_WIDTH_EXPANDED = 356.dp // 16:9, immagine panoramica
private const val EXPAND_MS = 220

/** Ritardo prima che il passaggio del mouse allarghi una card (evita il "lampeggio" passando sopra veloci). */
private const val HOVER_DELAY_MS = 250L

/**
 * Pagina a file nello stile del nuovo Netflix per TV (Home, Serie TV, Film, Per te):
 * niente banner in alto, la card selezionata si allarga in formato panoramico e sotto
 * la fila selezionata compaiono genere, anno e trama.
 */
@Composable
fun NetflixBrowse(
    vm: AppViewModel,
    sectionKey: String,
    rows: List<BrowseRow>,
    /** Intestazione fissa in alto (es. "Serie TV" + pulsante Generi). */
    header: (@Composable () -> Unit)? = null,
) {
    val visibleRows = rows.filter { it.items.isNotEmpty() }
    val listState = rememberLazyListState()
    // Fila e titolo selezionati col telecomando/tastiera: la trama compare sotto questa fila.
    var activeRow by remember { mutableIntStateOf(0) }
    var activeItem by remember { mutableStateOf(visibleRows.firstOrNull()?.items?.firstOrNull()) }
    val fr = remember { FocusRequester() }
    val savedId = vm.lastFocus[sectionKey]
    val allIds = remember(visibleRows) { visibleRows.flatMap { r -> r.items.map { it.id } }.toSet() }
    val focusId = savedId?.takeIf { it in allIds } ?: visibleRows.firstOrNull()?.items?.firstOrNull()?.id

    // La fila selezionata resta sempre in alto. Solo il telecomando/tastiera la sposta: il mouse no.
    LaunchedEffect(activeRow) { if (visibleRows.isNotEmpty()) listState.animateScrollToItem(activeRow) }
    // Trama dei film scaricata quando ti fermi su una card.
    LaunchedEffect(activeItem?.title?.key) {
        val t = activeItem?.title ?: return@LaunchedEffect
        delay(300)
        vm.loadExtra(t)
    }

    Column(Modifier.fillMaxSize()) {
        header?.invoke()
        if (visibleRows.isEmpty()) {
            EmptyMessage(
                if (vm.loadingParts.isNotEmpty()) "Contenuti in arrivo dal provider…\nLa prima volta può richiedere qualche minuto."
                else "Nessun contenuto disponibile"
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = if (header == null) 28.dp else 8.dp, bottom = 420.dp),
            ) {
                itemsIndexed(visibleRows) { rowIndex, row ->
                    NetflixRow(
                        vm = vm,
                        row = row,
                        isActive = rowIndex == activeRow,
                        activeItem = activeItem,
                        focusId = focusId,
                        focusRequester = fr,
                        onItemFocused = { item ->
                            activeRow = rowIndex
                            activeItem = item
                            vm.lastFocus[sectionKey] = item.id
                        },
                    )
                }
            }
        }
    }
    if (visibleRows.isNotEmpty()) RequestFocus(fr, sectionKey)
}

@Composable
private fun NetflixRow(
    vm: AppViewModel,
    row: BrowseRow,
    isActive: Boolean,
    activeItem: RowItem?,
    focusId: String?,
    focusRequester: FocusRequester,
    onItemFocused: (RowItem) -> Unit,
) {
    val rowState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rowInteraction = remember { MutableInteractionSource() }
    val rowHovered by rowInteraction.collectIsHoveredAsState()

    // Una sola card allargata per fila: quella selezionata col telecomando oppure, col mouse,
    // l'ultima su cui ti sei fermato. Quando passi a un'altra, la vecchia si chiude nello stesso
    // istante in cui la nuova si apre: così la card sotto il cursore non "scappa".
    var focusedId by remember { mutableStateOf<String?>(null) }
    var hoverCandidate by remember { mutableStateOf<String?>(null) }
    var hoverId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hoverCandidate) {
        val c = hoverCandidate ?: return@LaunchedEffect
        delay(HOVER_DELAY_MS)
        hoverId = c
    }
    LaunchedEffect(rowHovered) {
        if (!rowHovered) {
            delay(600)
            hoverCandidate = null
            hoverId = null
        }
    }
    val expandedId = focusedId ?: hoverId
    val openAll = row.openAll

    Column(Modifier.fillMaxWidth()) {
        RowTitle(row.title, openAll?.let { g -> { vm.openGenreEntry(g) } })
        Box(Modifier.hoverable(rowInteraction)) {
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(row.items) { _, item ->
                    ExpandingCard(
                        vm = vm,
                        item = item,
                        expanded = item.id == expandedId,
                        showDetailsInside = item.id == expandedId && focusedId == null,
                        focusRequester = if (item.id == focusId) focusRequester else null,
                        onFocusChange = { has ->
                            if (has) {
                                focusedId = item.id
                                onItemFocused(item)
                            } else if (focusedId == item.id) {
                                focusedId = null
                            }
                        },
                        onHoverStart = { hoverCandidate = item.id },
                    )
                }
                if (openAll != null) {
                    item {
                        SeeAllCard(openAll.titles.size, CARD_WIDTH, CARD_WIDTH / CARD_HEIGHT) { vm.openGenreEntry(openAll) }
                    }
                }
            }
            // Col mouse (Windows) le file non si scorrono con la rotellina: frecce ai lati.
            if (rowHovered) {
                Box(Modifier.matchParentSize()) {
                    RowArrow("‹", Modifier.align(Alignment.CenterStart)) {
                        scope.launch { rowState.animateScrollBy(-900f) }
                    }
                    RowArrow("›", Modifier.align(Alignment.CenterEnd)) {
                        scope.launch { rowState.animateScrollBy(900f) }
                    }
                }
            }
        }
        if (isActive) ItemInfo(vm, activeItem)
    }
}

/** Card che, selezionata, si allarga in formato panoramico con il titolo sull'immagine. */
@Composable
private fun ExpandingCard(
    vm: AppViewModel,
    item: RowItem,
    expanded: Boolean,
    /** Allargata col mouse: genere, anno e trama dentro la card (la riga sotto segue solo il telecomando). */
    showDetailsInside: Boolean,
    focusRequester: FocusRequester?,
    onFocusChange: (Boolean) -> Unit,
    onHoverStart: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) onHoverStart() }
    LaunchedEffect(showDetailsInside) { if (showDetailsInside) vm.loadExtra(item.title) }
    val width by animateDpAsState(
        if (expanded) CARD_WIDTH_EXPANDED else CARD_WIDTH,
        animationSpec = tween(EXPAND_MS),
        label = "cardWidth",
    )
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .width(width)
            .height(CARD_HEIGHT)
            .optionalFocusRequester(focusRequester)
            .onFocusChanged { onFocusChange(it.isFocused) }
            .clip(shape)
            .background(Palette.Surface)
            .border(3.dp, if (focused || hovered) Color.White else Color.Transparent, shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = item.onClick),
    ) {
        val t = item.title
        val wide = width > CARD_WIDTH + 40.dp
        Text(
            t.name,
            modifier = Modifier.align(Alignment.Center).padding(8.dp),
            color = Palette.TextDim,
            fontSize = 12.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        RemoteImage(
            url = if (wide) t.backdrop.ifBlank { item.image } else item.image,
            contentDescription = t.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (wide) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.9f))
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
                    maxLines = if (showDetailsInside) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 26.sp,
                )
                if (showDetailsInside) {
                    val extra = vm.extras[t.key]
                    val meta = metaLine(t, extra)
                    if (meta.isNotBlank()) {
                        Text(meta, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    val plot = t.plot.ifBlank { extra?.plot.orEmpty() }
                    if (plot.isNotBlank()) {
                        Text(plot, color = Palette.Text, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (item.label != null) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
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

/** "Dramma, Mistero  •  2023  •  Serie TV  •  91% voto" */
private fun metaLine(t: Title, extra: TitleExtra?): String {
    val genre = (t as? Series)?.genre?.takeIf { it.isNotBlank() } ?: extra?.genre.orEmpty()
    val year = t.year.ifBlank { extra?.year.orEmpty() }
    val score = t.rating.toDoubleOrNull()?.takeIf { it > 0 }?.let { "${(it * 10).toInt()}% voto" }
    return listOfNotNull(
        genre.takeIf { it.isNotBlank() },
        year.takeIf { it.isNotBlank() },
        if (t is Series) "Serie TV" else extra?.duration?.takeIf { it.isNotBlank() } ?: "Film",
        score,
    ).joinToString("  •  ")
}

/** Sotto la fila selezionata: genere · anno · tipo · voto, e la trama. */
@Composable
private fun ItemInfo(vm: AppViewModel, item: RowItem?) {
    val t = item?.title ?: return
    val extra = vm.extras[t.key]
    val plot = t.plot.ifBlank { extra?.plot.orEmpty() }
    // Altezza fissa: trame di lunghezza diversa non fanno saltare le file sotto.
    Column(Modifier.padding(start = 40.dp, end = 40.dp, top = 2.dp, bottom = 18.dp).height(96.dp).fillMaxWidth(0.62f)) {
        Text(metaLine(t, extra), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
