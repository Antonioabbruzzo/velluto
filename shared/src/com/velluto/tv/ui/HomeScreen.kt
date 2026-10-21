package com.velluto.tv.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velluto.tv.data.Catalog
import com.velluto.tv.data.GenreEntry
import com.velluto.tv.data.LibraryState
import com.velluto.tv.data.Movie
import com.velluto.tv.data.Progress
import com.velluto.tv.data.Recommender
import com.velluto.tv.data.Series
import com.velluto.tv.data.Title
import com.velluto.tv.data.buildGenreEntries
import com.velluto.tv.data.cleanCategoryName
import com.velluto.tv.platform.PlatformBackHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class MenuEntry(val section: Section, val label: String, val icon: ImageVector)

private val menu = listOf(
    MenuEntry(Section.SEARCH, "Cerca", Icons.Filled.Search),
    MenuEntry(Section.HOME, "Home", Icons.Filled.Home),
    MenuEntry(Section.FOR_YOU, "Per te", Icons.Filled.ThumbUp),
    MenuEntry(Section.SERIES, "Serie TV", Icons.Filled.List),
    MenuEntry(Section.MOVIES, "Film", Icons.Filled.Star),
    MenuEntry(Section.LIVE, "TV in diretta", Icons.Filled.DateRange),
    MenuEntry(Section.MY_LIST, "La mia lista", Icons.Filled.Favorite),
    MenuEntry(Section.SETTINGS, "Impostazioni", Icons.Filled.Settings),
)

/** La voce di menu "madre" di una sezione (i generi stanno sotto Serie TV / Film). */
private fun Section.menuSection(): Section = when (this) {
    Section.SERIES_GENRE -> Section.SERIES
    Section.MOVIES_GENRE -> Section.MOVIES
    else -> this
}

@Composable
fun HomeScreen(vm: AppViewModel, catalog: Catalog) {
    val lib by vm.library.state.collectAsState()
    var menuHasFocus by remember { mutableStateOf(false) }
    val seriesGenres = remember(catalog) { buildGenreEntries(catalog, series = true) }
    val movieGenres = remember(catalog) { buildGenreEntries(catalog, series = false) }

    // Indietro: da un genere torna alla sezione Serie TV/Film, da una sezione torna alla Home.
    PlatformBackHandler(enabled = vm.section != Section.HOME) {
        vm.section = when (vm.section) {
            Section.SERIES_GENRE -> Section.SERIES
            Section.MOVIES_GENRE -> Section.MOVIES
            else -> Section.HOME
        }
    }

    Row(Modifier.fillMaxSize()) {
        SideMenu(
            current = vm.section,
            openGenre = vm.openGenre,
            seriesGenres = seriesGenres,
            movieGenres = movieGenres,
            expanded = menuHasFocus,
            onFocusChange = { menuHasFocus = it },
            // OK su una voce apre la sezione come sempre. I generi compaiono sotto Serie TV / Film
            // quando riapri il menu mentre sei in quella sezione: sono una scorciatoia facoltativa.
            onSelect = { section -> vm.section = section },
            onSelectGenre = { genre ->
                vm.openGenre = genre
                vm.section = if (genre.isSeries) Section.SERIES_GENRE else Section.MOVIES_GENRE
            },
        )
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (vm.section) {
                Section.HOME -> BrowseSection(vm, "home", remember(catalog, lib) { homeRows(vm, catalog, lib) })
                Section.FOR_YOU -> ForYouSection(vm, catalog, lib)
                Section.SERIES -> BrowseSection(vm, "series", remember(catalog) { seriesRows(vm, catalog) })
                Section.MOVIES -> BrowseSection(vm, "movies", remember(catalog) { movieRows(vm, catalog) })
                Section.SERIES_GENRE, Section.MOVIES_GENRE -> {
                    val g = vm.openGenre
                    if (g != null) GenreSection(vm, g) else EmptyMessage("Scegli un genere dal menu")
                }
                Section.MY_LIST -> MyListSection(vm, catalog, lib)
                Section.LIVE -> LiveSection(vm, catalog)
                Section.SEARCH -> SearchSection(vm, catalog)
                Section.SETTINGS -> SettingsSection(vm)
            }
            UpdateBanner(vm, Modifier.align(Alignment.TopEnd))
        }
    }
}

// ---------- Menu laterale con sottomenu dei generi ----------

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SideMenu(
    current: Section,
    openGenre: GenreEntry?,
    seriesGenres: List<GenreEntry>,
    movieGenres: List<GenreEntry>,
    expanded: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onSelect: (Section) -> Unit,
    onSelectGenre: (GenreEntry) -> Unit,
) {
    // Il menu si apre col telecomando (focus) o passandoci sopra col mouse.
    val menuInteraction = remember { MutableInteractionSource() }
    val hovered by menuInteraction.collectIsHoveredAsState()
    val open = expanded || hovered
    val width by animateDpAsState(if (open) 240.dp else 64.dp, label = "menuWidth")
    val requesters = remember { menu.associate { it.section to FocusRequester() } }
    val parent = current.menuSection()
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(if (open) Color.Black.copy(alpha = 0.95f) else Palette.Background)
            .hoverable(menuInteraction)
            .onFocusChanged { onFocusChange(it.hasFocus) }
            // Entrando nel menu il cursore va sulla sezione in cui ti trovi, non sulla voce più vicina.
            .focusProperties { enter = { requesters[parent] ?: FocusRequester.Default } }
            .focusGroup()
            .verticalScroll(rememberScrollState())
            .padding(top = 56.dp, bottom = 24.dp),
    ) {
        menu.forEach { entry ->
            MenuItem(
                entry,
                selected = entry.section == parent,
                expanded = open,
                focusRequester = requesters.getValue(entry.section),
                onClick = { onSelect(entry.section) },
            )
            // Sottomenu dei generi, visibile a menu aperto sotto la sezione in cui ti trovi.
            val genres = when {
                entry.section == Section.SERIES && parent == Section.SERIES -> seriesGenres
                entry.section == Section.MOVIES && parent == Section.MOVIES -> movieGenres
                else -> emptyList()
            }
            if (open && genres.isNotEmpty()) {
                genres.forEach { g ->
                    GenreMenuItem(
                        genre = g,
                        selected = openGenre?.id == g.id && openGenre?.isSeries == g.isSeries &&
                            (current == Section.SERIES_GENRE || current == Section.MOVIES_GENRE),
                        onClick = { onSelectGenre(g) },
                    )
                }
            }
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

@Composable
private fun GenreMenuItem(genre: GenreEntry, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.isHighlighted()
    Row(
        Modifier
            .padding(start = 44.dp, end = 10.dp, top = 1.dp, bottom = 1.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            genre.name,
            color = when {
                focused -> Color.Black
                selected -> Color.White
                else -> Palette.TextDim
            },
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${genre.titles.size}",
            color = if (focused) Color.DarkGray else Palette.TextDim.copy(alpha = 0.6f),
            fontSize = 11.sp,
        )
    }
}

// ---------- Sezioni con hero + file di locandine ----------

data class BrowseRow(val title: String, val items: List<RowItem>, val wide: Boolean = false)

/** Banner che segue la card selezionata, aggiornato solo quando ti fermi, con la trama caricata se manca. */
@Composable
private fun rememberHero(vm: AppViewModel, focused: RowItem?, fallback: RowItem?): RowItem? {
    var hero by remember { mutableStateOf<RowItem?>(null) }
    LaunchedEffect(focused) {
        val f = focused ?: return@LaunchedEffect
        if (hero != null) delay(450)
        hero = f
    }
    val current = hero ?: fallback
    LaunchedEffect(current?.title?.key) {
        current?.title?.let { vm.loadExtra(it) }
    }
    return current
}

@Composable
private fun BrowseSection(vm: AppViewModel, sectionKey: String, rows: List<BrowseRow>) {
    val visibleRows = rows.filter { it.items.isNotEmpty() }
    val savedId = vm.lastFocus[sectionKey]
    val allIds = remember(visibleRows) { visibleRows.flatMap { r -> r.items.map { it.id } }.toSet() }
    val focusId = savedId?.takeIf { it in allIds } ?: visibleRows.firstOrNull()?.items?.firstOrNull()?.id
    var focused by remember { mutableStateOf<RowItem?>(null) }
    var focusedRow by remember { mutableIntStateOf(-1) }
    val fr = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val hero = rememberHero(vm, focused, visibleRows.firstOrNull()?.items?.firstOrNull())

    // La fila selezionata resta sempre in alto, nella stessa posizione.
    LaunchedEffect(focusedRow) {
        if (focusedRow >= 0) listState.animateScrollToItem(focusedRow)
    }

    if (visibleRows.isEmpty() || hero == null) {
        EmptyMessage("Nessun contenuto disponibile")
        return
    }

    Column(Modifier.fillMaxSize()) {
        Hero(
            hero.title,
            Modifier.height(250.dp),
            extraLine = hero.label,
            extraFor = { vm.extras[it.key] },
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

/** Tutte le serie (o tutti i film) di un genere, in griglia, con il banner e la trama in alto. */
@Composable
private fun GenreSection(vm: AppViewModel, genre: GenreEntry) {
    val sectionKey = "genre-${if (genre.isSeries) "s" else "m"}-${genre.id}"
    val items = remember(genre) {
        genre.titles.mapIndexed { i, t -> RowItem(id = "$sectionKey/$i/${t.id}", title = t, onClick = { openTitle(vm, t) }) }
    }
    val ids = remember(items) { items.map { it.id }.toSet() }
    val focusId = vm.lastFocus[sectionKey]?.takeIf { it in ids } ?: items.firstOrNull()?.id
    var focused by remember(genre) { mutableStateOf<RowItem?>(null) }
    val hero = rememberHero(vm, focused, items.firstOrNull())
    val fr = remember(genre) { FocusRequester() }

    if (hero == null) {
        EmptyMessage("Nessun titolo in questo genere")
        return
    }
    Column(Modifier.fillMaxSize()) {
        Hero(hero.title, Modifier.height(230.dp), extraFor = { vm.extras[it.key] })
        Text(
            "${if (genre.isSeries) "Serie TV" else "Film"} · ${genre.name}",
            modifier = Modifier.padding(start = 40.dp, top = 4.dp),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "${items.size} ${if (genre.isSeries) "serie" else "film"}",
            modifier = Modifier.padding(start = 40.dp),
            color = Palette.TextDim,
            fontSize = 12.sp,
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 12.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(items) { _, item ->
                PosterCard(
                    item = item,
                    onFocused = {
                        focused = item
                        vm.lastFocus[sectionKey] = item.id
                    },
                    width = 120.dp,
                    focusRequester = if (item.id == focusId) fr else null,
                )
            }
        }
    }
    RequestFocus(fr, sectionKey)
}

// ---------- Per te (suggerimenti) ----------

@Composable
private fun ForYouSection(vm: AppViewModel, catalog: Catalog, lib: LibraryState) {
    // Il calcolo scorre tutto il catalogo: lo facciamo fuori dal thread dell'interfaccia.
    val rows by produceState<List<BrowseRow>?>(null, catalog, lib) {
        value = withContext(Dispatchers.Default) { forYouRows(vm, catalog, lib) }
    }
    val r = rows
    when {
        r == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Palette.Accent)
        }
        r.isEmpty() -> EmptyMessage(
            "Qui troverai i suggerimenti scelti per te.\nGuarda qualche serie o film, oppure aggiungili a \"La mia lista\"."
        )
        else -> BrowseSection(vm, "foryou", r)
    }
}

private fun forYouRows(vm: AppViewModel, catalog: Catalog, lib: LibraryState): List<BrowseRow> {
    val rows = mutableListOf<BrowseRow>()
    val suggested = Recommender.forYou(catalog, lib, limit = 40)
    if (suggested.isNotEmpty()) rows += BrowseRow("Suggeriti per te", titleItems(vm, "fy", suggested, 40))
    Recommender.becauseYouWatched(catalog, lib).forEachIndexed { i, (source, list) ->
        rows += BrowseRow("Perché hai guardato \"${source.name}\"", titleItems(vm, "byw$i", list, 30))
    }
    return rows
}

// ---------- Contenuti delle file ----------

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
    rows += BrowseRow("Suggeriti per te", titleItems(vm, "home-fy", Recommender.forYou(catalog, lib, limit = 30), 30))
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
            rows += BrowseRow("Serie · ${cleanCategoryName(c.name)}", titleItems(vm, "hs-${c.id}", catalog.seriesByCategory[c.id].orEmpty()))
        }
        mc.getOrNull(i)?.let { c ->
            rows += BrowseRow("Film · ${cleanCategoryName(c.name)}", titleItems(vm, "hm-${c.id}", catalog.moviesByCategory[c.id].orEmpty()))
        }
    }
    return rows
}

private fun seriesRows(vm: AppViewModel, catalog: Catalog): List<BrowseRow> =
    listOf(
        BrowseRow("Novità", titleItems(vm, "s-new", catalog.series.sortedByDescending { it.lastModified }, 40))
    ) + catalog.seriesCategories.map { c ->
        BrowseRow(cleanCategoryName(c.name), titleItems(vm, "s-${c.id}", catalog.seriesByCategory[c.id].orEmpty(), 200))
    }

private fun movieRows(vm: AppViewModel, catalog: Catalog): List<BrowseRow> =
    listOf(
        BrowseRow("Novità", titleItems(vm, "m-new", catalog.movies.sortedByDescending { it.added }, 40))
    ) + catalog.movieCategories.map { c ->
        BrowseRow(cleanCategoryName(c.name), titleItems(vm, "m-${c.id}", catalog.moviesByCategory[c.id].orEmpty(), 200))
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
