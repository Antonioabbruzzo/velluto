package com.velluto.tv.ui

import com.velluto.tv.platform.PlatformBackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.velluto.tv.data.Catalog
import com.velluto.tv.data.LibraryState
import com.velluto.tv.data.Movie
import com.velluto.tv.data.Progress
import com.velluto.tv.data.Series
import com.velluto.tv.data.Title

private data class MenuEntry(val section: Section, val label: String, val icon: ImageVector)

private val menu = listOf(
    MenuEntry(Section.SEARCH, "Cerca", Icons.Filled.Search),
    MenuEntry(Section.HOME, "Home", Icons.Filled.Home),
    MenuEntry(Section.SERIES, "Serie TV", Icons.Filled.List),
    MenuEntry(Section.MOVIES, "Film", Icons.Filled.Star),
    MenuEntry(Section.LIVE, "TV in diretta", Icons.Filled.DateRange),
    MenuEntry(Section.MY_LIST, "La mia lista", Icons.Filled.Favorite),
    MenuEntry(Section.SETTINGS, "Impostazioni", Icons.Filled.Settings),
)

@Composable
fun HomeScreen(vm: AppViewModel, catalog: Catalog) {
    val lib by vm.library.state.collectAsState()
    var menuHasFocus by remember { mutableStateOf(false) }

    PlatformBackHandler(enabled = vm.section != Section.HOME) { vm.section = Section.HOME }

    Row(Modifier.fillMaxSize()) {
        SideMenu(
            current = vm.section,
            expanded = menuHasFocus,
            onFocusChange = { menuHasFocus = it },
            onSelect = { vm.section = it },
        )
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (vm.section) {
                Section.HOME -> BrowseSection(vm, "home", remember(catalog, lib) { homeRows(vm, catalog, lib) })
                Section.SERIES -> BrowseSection(vm, "series", remember(catalog) { seriesRows(vm, catalog) })
                Section.MOVIES -> BrowseSection(vm, "movies", remember(catalog) { movieRows(vm, catalog) })
                Section.MY_LIST -> MyListSection(vm, catalog, lib)
                Section.LIVE -> LiveSection(vm, catalog)
                Section.SEARCH -> SearchSection(vm, catalog)
                Section.SETTINGS -> SettingsSection(vm)
            }
            UpdateBanner(vm, Modifier.align(Alignment.TopEnd))
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SideMenu(
    current: Section,
    expanded: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onSelect: (Section) -> Unit,
) {
    // Il menu si apre col telecomando (focus) o passandoci sopra col mouse.
    val menuInteraction = remember { MutableInteractionSource() }
    val hovered by menuInteraction.collectIsHoveredAsState()
    val open = expanded || hovered
    val width by animateDpAsState(if (open) 210.dp else 64.dp, label = "menuWidth")
    val requesters = remember { menu.associate { it.section to FocusRequester() } }
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(
                if (open) Color.Black.copy(alpha = 0.95f) else Palette.Background
            )
            .hoverable(menuInteraction)
            .onFocusChanged { onFocusChange(it.hasFocus) }
            // Entrando nel menu il cursore va sulla sezione in cui ti trovi, non sulla voce più vicina.
            .focusProperties { enter = { requesters[current] ?: FocusRequester.Default } }
            .focusGroup()
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        menu.forEach { entry ->
            MenuItem(
                entry,
                selected = entry.section == current,
                expanded = open,
                focusRequester = requesters.getValue(entry.section),
                onClick = { onSelect(entry.section) },
            )
        }
    }
}

@Composable
private fun MenuItem(
    entry: MenuEntry,
    selected: Boolean,
    expanded: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.isHighlighted()
    val color = when {
        focused -> Color.Black
        selected -> Color.White
        else -> Palette.TextDim
    }
    Row(
        Modifier
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            entry.icon,
            contentDescription = entry.label,
            tint = if (selected && !focused) Palette.Accent else color,
            modifier = Modifier.size(20.dp),
        )
        if (expanded) {
            Spacer(Modifier.width(14.dp))
            Text(
                entry.label,
                color = color,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

// ---------- Sezioni con hero + file di locandine ----------

data class BrowseRow(val title: String, val items: List<RowItem>, val wide: Boolean = false)

@Composable
private fun BrowseSection(vm: AppViewModel, sectionKey: String, rows: List<BrowseRow>) {
    val visibleRows = rows.filter { it.items.isNotEmpty() }
    val savedId = vm.lastFocus[sectionKey]
    val allIds = remember(visibleRows) { visibleRows.flatMap { r -> r.items.map { it.id } }.toSet() }
    val focusId = savedId?.takeIf { it in allIds } ?: visibleRows.firstOrNull()?.items?.firstOrNull()?.id
    var focused by remember { mutableStateOf<RowItem?>(null) }
    var hero by remember { mutableStateOf<RowItem?>(null) }
    var focusedRow by remember { mutableIntStateOf(-1) }
    val fr = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Il banner cambia solo quando ti fermi su una card, non a ogni passaggio veloce.
    LaunchedEffect(focused) {
        val f = focused ?: return@LaunchedEffect
        if (hero != null) delay(450)
        hero = f
    }
    // La fila selezionata resta sempre in alto, nella stessa posizione.
    LaunchedEffect(focusedRow) {
        if (focusedRow >= 0) listState.animateScrollToItem(focusedRow)
    }

    if (visibleRows.isEmpty()) {
        EmptyMessage("Nessun contenuto disponibile")
        return
    }

    Column(Modifier.fillMaxSize()) {
        val current = hero ?: visibleRows.first().items.first()
        Hero(
            current.title,
            Modifier.height(250.dp),
            extraLine = current.label,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 300.dp),
        ) {
            itemsIndexed(visibleRows) { rowIndex, row ->
                ContentRow(
                    title = row.title,
                    items = row.items,
                    onItemFocused = {
                        focused = it
                        focusedRow = rowIndex
                        vm.lastFocus[sectionKey] = it.id
                    },
                    focusId = focusId,
                    focusRequester = fr,
                    cardWidth = if (row.wide) 220.dp else 120.dp,
                    aspect = if (row.wide) 16f / 9f else 2f / 3f,
                )
            }
        }
    }
    RequestFocus(fr, sectionKey)
}

private fun openTitle(vm: AppViewModel, title: Title) {
    when (title) {
        is Series -> vm.navigate(Screen.SeriesDetail(title))
        is Movie -> vm.navigate(Screen.MovieDetail(title))
    }
}

private fun titleItems(vm: AppViewModel, prefix: String, list: List<Title>, limit: Int = 60): List<RowItem> =
    list.take(limit).mapIndexed { i, t ->
        RowItem(id = "$prefix/$i/${t.id}", title = t, onClick = { openTitle(vm, t) })
    }

private fun continueItems(vm: AppViewModel, catalog: Catalog, lib: LibraryState): List<RowItem> =
    lib.continueWatching.mapNotNull { p: Progress ->
        val t = catalog.resolve(p.titleKey, p.titleId) ?: return@mapNotNull null
        val label = if (t is Series) "S${p.season}:E${p.episode}" else null
        val shownProgress = if (p.finished) null else p.fraction
        RowItem(
            id = "cw/${p.titleKey}",
            title = t,
            progress = shownProgress,
            label = label,
            onClick = { openTitle(vm, t) },
        )
    }

private fun myListItems(vm: AppViewModel, catalog: Catalog, lib: LibraryState): List<RowItem> =
    lib.favorites.values.sortedByDescending { it.addedAt }.mapNotNull { f ->
        val t = catalog.resolve(f.key, f.id) ?: return@mapNotNull null
        RowItem(id = "fav/${f.key}", title = t, onClick = { openTitle(vm, t) })
    }

private fun homeRows(vm: AppViewModel, catalog: Catalog, lib: LibraryState): List<BrowseRow> {
    val rows = mutableListOf<BrowseRow>()
    rows += BrowseRow("Continua a guardare", continueItems(vm, catalog, lib))
    rows += BrowseRow("La mia lista", myListItems(vm, catalog, lib))
    rows += BrowseRow(
        "Serie TV aggiornate di recente",
        titleItems(vm, "newseries", catalog.series.sortedByDescending { it.lastModified }, 40),
    )
    rows += BrowseRow(
        "Film aggiunti di recente",
        titleItems(vm, "newmovies", catalog.movies.sortedByDescending { it.added }, 40),
    )
    // Alterna qualche categoria di serie e di film, come le file della home di Netflix.
    val sc = catalog.seriesCategories.filter { catalog.seriesByCategory[it.id].orEmpty().isNotEmpty() }.take(5)
    val mc = catalog.movieCategories.filter { catalog.moviesByCategory[it.id].orEmpty().isNotEmpty() }.take(5)
    for (i in 0 until maxOf(sc.size, mc.size)) {
        sc.getOrNull(i)?.let { c ->
            rows += BrowseRow("Serie · ${c.name}", titleItems(vm, "hs-${c.id}", catalog.seriesByCategory[c.id].orEmpty()))
        }
        mc.getOrNull(i)?.let { c ->
            rows += BrowseRow("Film · ${c.name}", titleItems(vm, "hm-${c.id}", catalog.moviesByCategory[c.id].orEmpty()))
        }
    }
    return rows
}

private fun seriesRows(vm: AppViewModel, catalog: Catalog): List<BrowseRow> =
    listOf(
        BrowseRow("Novità", titleItems(vm, "s-new", catalog.series.sortedByDescending { it.lastModified }, 40))
    ) + catalog.seriesCategories.map { c ->
        BrowseRow(c.name, titleItems(vm, "s-${c.id}", catalog.seriesByCategory[c.id].orEmpty(), 200))
    }

private fun movieRows(vm: AppViewModel, catalog: Catalog): List<BrowseRow> =
    listOf(
        BrowseRow("Novità", titleItems(vm, "m-new", catalog.movies.sortedByDescending { it.added }, 40))
    ) + catalog.movieCategories.map { c ->
        BrowseRow(c.name, titleItems(vm, "m-${c.id}", catalog.moviesByCategory[c.id].orEmpty(), 200))
    }

// ---------- La mia lista (griglia) ----------

@Composable
private fun MyListSection(vm: AppViewModel, catalog: Catalog, lib: LibraryState) {
    val items = myListItems(vm, catalog, lib)
    if (items.isEmpty()) {
        EmptyMessage("La tua lista è vuota.\nApri una serie o un film e premi \"La mia lista\".")
        return
    }
    TitleGrid("La mia lista", items, vm, "mylist")
}

@Composable
fun TitleGrid(header: String, items: List<RowItem>, vm: AppViewModel, sectionKey: String) {
    val fr = remember { FocusRequester() }
    val ids = remember(items) { items.map { it.id }.toSet() }
    val focusId = vm.lastFocus[sectionKey]?.takeIf { it in ids } ?: items.firstOrNull()?.id
    Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
        Text(
            header,
            modifier = Modifier.padding(start = 40.dp, bottom = 8.dp),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(130.dp),
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(items) { _, item ->
                PosterCard(
                    item = item,
                    onFocused = { vm.lastFocus[sectionKey] = item.id },
                    width = 130.dp,
                    focusRequester = if (item.id == focusId) fr else null,
                )
            }
        }
    }
    if (focusId != null) RequestFocus(fr, sectionKey)
}

@Composable
fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Palette.TextDim, fontSize = 18.sp, lineHeight = 26.sp)
    }
}

@Composable
private fun UpdateBanner(vm: AppViewModel, modifier: Modifier) {
    val upd = vm.update ?: return
    Box(
        modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Palette.Accent)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            "Nuova versione disponibile (${upd.versionName}) · vai su Impostazioni",
            color = Color.White,
            fontSize = 13.sp,
        )
    }
}
