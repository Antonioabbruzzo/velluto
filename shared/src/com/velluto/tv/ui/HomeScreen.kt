package com.velluto.tv.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import com.velluto.tv.data.Category
import com.velluto.tv.data.GenreEntry
import com.velluto.tv.data.LibraryState
import com.velluto.tv.data.Movie
import com.velluto.tv.data.Progress
import com.velluto.tv.data.Series
import com.velluto.tv.data.Title
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

/**
 * Menu laterale, sempre visibile accanto alla Home e alle schede di serie e film.
 * Scegliendo una voce da una scheda si torna alla Home su quella sezione.
 */
@Composable
fun AppSideMenu(vm: AppViewModel) {
    var menuHasFocus by remember { mutableStateOf(false) }
    SideMenu(
        current = vm.section,
        expanded = menuHasFocus,
        onFocusChange = { menuHasFocus = it },
        onSelect = { section -> vm.openSection(section) },
    )
}

@Composable
fun HomeScreen(vm: AppViewModel, catalog: Catalog) {
    val lib by vm.library.state.collectAsState()
    // Generi, novità e suggerimenti sono calcolati in sottofondo dal ViewModel.
    val derived = vm.derived
    val suggestions = vm.suggestions
    val seriesGenres = derived?.seriesGenres.orEmpty()
    val movieGenres = derived?.movieGenres.orEmpty()
    // Elenco dei generi aperto a tutto schermo: true = generi delle serie, false = dei film.
    var genrePicker by remember { mutableStateOf<Boolean?>(null) }
    // Chiudendo l'elenco con Indietro il cursore torna sul pulsante "Generi".
    val genresButton = remember { FocusRequester() }
    var restoreGenresFocus by remember { mutableStateOf(false) }
    LaunchedEffect(restoreGenresFocus) {
        if (restoreGenresFocus) {
            delay(50)
            runCatching { genresButton.requestFocus() }
            restoreGenresFocus = false
        }
    }

    // Indietro: da un genere/categoria torna dove eri (Serie TV, Film o Home), da una sezione torna alla Home.
    PlatformBackHandler(enabled = vm.section != Section.HOME) {
        vm.section = when (vm.section) {
            Section.SERIES_GENRE, Section.MOVIES_GENRE -> vm.genreReturnSection
            else -> Section.HOME
        }
    }

    // Il menu laterale non sta qui ma in AppRoot (AppSideMenu): così resta visibile anche
    // nelle schede di serie e film.
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                when (vm.section) {
                    Section.HOME -> rememberRows(catalog, lib, derived, suggestions) {
                        homeRows(vm, catalog, lib, derived, suggestions)
                    }?.let { BrowseSection(vm, "home", it) } ?: LoadingRows()
                    Section.FOR_YOU -> ForYouSection(vm, suggestions)
                    Section.SERIES -> rememberCachedRows(vm, "series", catalog) { seriesRows(vm, catalog) }?.let {
                        BrowseSection(
                            vm, "series", it,
                            header = { PageHeader("Serie TV", null, genresButton) { genrePicker = true } },
                        )
                    } ?: LoadingRows()
                    Section.MOVIES -> rememberCachedRows(vm, "movies", catalog) { movieRows(vm, catalog) }?.let {
                        BrowseSection(
                            vm, "movies", it,
                            header = { PageHeader("Film", null, genresButton) { genrePicker = false } },
                        )
                    } ?: LoadingRows()
                    Section.SERIES_GENRE, Section.MOVIES_GENRE -> {
                        val g = vm.openGenre
                        if (g != null) GenreSection(vm, g, genresButton, onChangeGenre = { genrePicker = g.isSeries })
                        else EmptyMessage("Scegli un genere")
                    }
                    Section.MY_LIST -> MyListSection(vm, catalog, lib)
                    Section.LIVE -> LiveSection(vm, catalog)
                    Section.SEARCH -> SearchSection(vm, catalog)
                    Section.SETTINGS -> SettingsSection(vm)
                }
                UpdateBanner(vm, Modifier.align(Alignment.TopEnd))
                CatalogStatusBanner(vm, Modifier.align(Alignment.BottomEnd))
            }
        }

        val pickerSeries = genrePicker
        if (pickerSeries != null) {
            val current = vm.openGenre?.takeIf {
                it.isSeries == pickerSeries && vm.section.menuSection() == (if (pickerSeries) Section.SERIES else Section.MOVIES) &&
                    (vm.section == Section.SERIES_GENRE || vm.section == Section.MOVIES_GENRE)
            }
            GenrePicker(
                allLabel = if (pickerSeries) "Tutte le serie" else "Tutti i film",
                genres = if (pickerSeries) seriesGenres else movieGenres,
                selected = current,
                onPick = { g ->
                    genrePicker = null
                    val target = when {
                        g == null -> if (pickerSeries) Section.SERIES else Section.MOVIES
                        g.isSeries -> Section.SERIES_GENRE
                        else -> Section.MOVIES_GENRE
                    }
                    // Se la pagina non cambia (stesso genere) il cursore torna sul pulsante "Generi".
                    if (target == vm.section && (g == null || g.id == vm.openGenre?.id)) restoreGenresFocus = true
                    if (g != null) vm.openGenre = g
                    // Dal pulsante "Generi" Indietro riporta a tutte le serie / tutti i film.
                    vm.genreReturnSection = if (pickerSeries) Section.SERIES else Section.MOVIES
                    vm.section = target
                },
                onDismiss = {
                    genrePicker = null
                    restoreGenresFocus = true
                },
            )
        }
    }
}

// ---------- Generi in stile Netflix: pulsante in cima alla pagina + elenco a tutto schermo ----------

/** Titolo della pagina con il pulsante "Generi ▾", in alto a sinistra sopra il banner. */
@Composable
private fun PageHeader(
    title: String,
    genre: String?,
    buttonFocus: FocusRequester,
    /** Se presente mostra "← Indietro" prima del titolo (pagine di genere/categoria). */
    onBack: (() -> Unit)? = null,
    onGenres: () -> Unit,
) {
    Row(
        Modifier.padding(start = 40.dp, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            BackButton(onBack)
            Spacer(Modifier.width(20.dp))
        }
        Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (genre != null) {
            Text("  ›  $genre", color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Spacer(Modifier.width(24.dp))
        TvButton(text = "Generi  ▾", onClick = onGenres, focusRequester = buttonFocus)
    }
}

/** Elenco dei generi a tutto schermo, sopra la pagina scurita. Indietro lo chiude. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun GenrePicker(
    allLabel: String,
    genres: List<GenreEntry>,
    selected: GenreEntry?,
    onPick: (GenreEntry?) -> Unit,
    onDismiss: () -> Unit,
) {
    PlatformBackHandler(onBack = onDismiss)
    val selectedIndex = if (selected == null) 0 else genres.indexOfFirst { it.id == selected.id } + 1
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 3).coerceAtLeast(0))
    val fr = remember { FocusRequester() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            // Un clic fuori dall'elenco (mouse) lo chiude; i clic non passano alla pagina sotto.
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(460.dp)
                // Il cursore resta nell'elenco finché non scegli o premi Indietro.
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup(),
            contentPadding = PaddingValues(vertical = 90.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                PickerItem(
                    allLabel,
                    selected = selected == null,
                    focusRequester = if (selectedIndex == 0) fr else null,
                    onClick = { onPick(null) },
                )
            }
            itemsIndexed(genres) { i, g ->
                PickerItem(
                    g.name,
                    selected = selected?.id == g.id,
                    focusRequester = if (selectedIndex == i + 1) fr else null,
                    onClick = { onPick(g) },
                )
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(28.dp)) {
            TvButton(text = "✕", onClick = onDismiss)
        }
    }
    RequestFocus(fr, "genre-picker")
}

@Composable
private fun PickerItem(text: String, selected: Boolean, focusRequester: FocusRequester?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.isHighlighted()
    Text(
        text,
        modifier = Modifier
            .optionalFocusRequester(focusRequester)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 24.dp),
        color = when {
            focused -> Color.White
            selected -> Palette.Accent
            else -> Palette.TextDim
        },
        fontSize = if (focused) 26.sp else 21.sp,
        fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// ---------- Menu laterale ----------

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

data class BrowseRow(
    val title: String,
    val items: List<RowItem>,
    val wide: Boolean = false,
    /** Se la fila è una categoria: tutti i suoi titoli, per "Vedi tutti". */
    val openAll: GenreEntry? = null,
)

/** Fila di una categoria del provider: mostra i primi titoli e permette di aprirla tutta. */
private fun categoryRow(vm: AppViewModel, title: String, prefix: String, category: Category, all: List<Title>, isSeries: Boolean) =
    BrowseRow(
        title = title,
        items = titleItems(vm, prefix, all, ROW_PREVIEW),
        openAll = GenreEntry(category.id, cleanCategoryName(category.name), isSeries, all),
    )

/** Titoli mostrati in una fila di categoria; gli altri si vedono con "Vedi tutti". */
private const val ROW_PREVIEW = 40

/**
 * Calcola le file fuori dal thread dell'interfaccia (con cataloghi da decine di migliaia di titoli
 * ordinarli richiede tempo). Quando il catalogo si aggiorna restano visibili le file precedenti.
 */
@Composable
private fun rememberRows(vararg keys: Any?, compute: () -> List<BrowseRow>): List<BrowseRow>? {
    val rows by produceState<List<BrowseRow>?>(null, *keys) {
        value = withContext(Dispatchers.Default) { compute() }
    }
    return rows
}

/**
 * Come [rememberRows], ma il risultato resta in memoria nel ViewModel finché il catalogo non cambia:
 * rientrando in Serie TV / Film le file sono già pronte, senza ricostruirle.
 */
@Composable
private fun rememberCachedRows(
    vm: AppViewModel,
    cacheKey: String,
    catalog: Catalog,
    compute: () -> List<BrowseRow>,
): List<BrowseRow>? {
    @Suppress("UNCHECKED_CAST")
    val cached = vm.rowCache[cacheKey]?.takeIf { it.first === catalog }?.second as? List<BrowseRow>
    val rows by produceState(cached, catalog) {
        if (cached == null) {
            val computed = withContext(Dispatchers.Default) { compute() }
            vm.rowCache[cacheKey] = catalog to computed
            value = computed
        }
    }
    return rows
}

@Composable
private fun LoadingRows() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Palette.Accent)
    }
}

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
private fun BrowseSection(
    vm: AppViewModel,
    sectionKey: String,
    rows: List<BrowseRow>,
    /** Intestazione sopra il banner (es. "Serie TV" + pulsante Generi). */
    header: (@Composable () -> Unit)? = null,
) {
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
        EmptyMessage(
            if (vm.loadingParts.isNotEmpty()) "Contenuti in arrivo dal provider…\nLa prima volta può richiedere qualche minuto."
            else "Nessun contenuto disponibile"
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.height(250.dp)) {
            Hero(
                hero.title,
                Modifier.fillMaxSize(),
                extraLine = hero.label,
                extraFor = { vm.extras[it.key] },
            )
            header?.invoke()
        }
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
                    // Mouse sopra una card: aggiorna solo il banner (niente scorrimento della pagina).
                    onItemHovered = { focused = it },
                    focusId = focusId,
                    focusRequester = fr,
                    cardWidth = if (row.wide) 220.dp else 120.dp,
                    aspect = if (row.wide) 16f / 9f else 2f / 3f,
                    onOpenAll = row.openAll?.let { g -> { vm.openGenreEntry(g) } },
                    totalCount = row.openAll?.titles?.size ?: row.items.size,
                )
            }
        }
    }
    RequestFocus(fr, sectionKey)
}

/** Tutte le serie (o tutti i film) di un genere, in griglia, con il banner e la trama in alto. */
@Composable
private fun GenreSection(vm: AppViewModel, genre: GenreEntry, genresButton: FocusRequester, onChangeGenre: () -> Unit) {
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
        Box(Modifier.height(250.dp)) {
            Hero(hero.title, Modifier.fillMaxSize(), extraFor = { vm.extras[it.key] })
            PageHeader(
                if (genre.isSeries) "Serie TV" else "Film", genre.name, genresButton,
                onBack = { vm.section = vm.genreReturnSection },
                onGenres = onChangeGenre,
            )
        }
        Text(
            "${items.size} ${if (genre.isSeries) "serie" else "film"}",
            modifier = Modifier.padding(start = 40.dp, top = 2.dp),
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
private fun ForYouSection(vm: AppViewModel, suggestions: Suggestions?) {
    // I suggerimenti arrivano già calcolati (in sottofondo) dal ViewModel: qui si compongono solo le file.
    val r = remember(suggestions) { suggestions?.let { forYouRows(vm, it) } }
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

private fun forYouRows(vm: AppViewModel, s: Suggestions): List<BrowseRow> {
    val rows = mutableListOf<BrowseRow>()
    if (s.forYou.isNotEmpty()) rows += BrowseRow("Suggeriti per te", titleItems(vm, "fy", s.forYou, 40))
    s.becauseYouWatched.forEachIndexed { i, (source, list) ->
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

/**
 * Un titolo salvato (cronologia o lista) anche se il catalogo non è ancora arrivato:
 * nome, immagine e ID bastano per mostrarlo e aprirlo.
 */
private fun savedTitle(catalog: Catalog, key: String, id: Int, name: String, image: String): Title? =
    catalog.resolve(key, id) ?: when {
        key.startsWith("series:") -> Series(
            id = id, name = name, image = image, categoryId = "", rating = "", plot = "",
            year = "", backdrop = "", lastModified = 0L,
        )
        key.startsWith("movie:") -> Movie(id = id, name = name, image = image, categoryId = "", rating = "", ext = "", added = 0L)
        else -> null
    }

private fun continueItems(vm: AppViewModel, catalog: Catalog, lib: LibraryState): List<RowItem> =
    lib.continueWatching.mapNotNull { p: Progress ->
        val t = savedTitle(catalog, p.titleKey, p.titleId, p.name, p.image) ?: return@mapNotNull null
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
        val t = savedTitle(catalog, f.key, f.id, f.name, f.image) ?: return@mapNotNull null
        RowItem(id = "fav/${f.key}", title = t, onClick = { openTitle(vm, t) })
    }

/**
 * File della Home. Le parti pesanti (novità, suggerimenti) arrivano già calcolate in sottofondo:
 * aggiungere un titolo alla lista ricostruisce solo le file leggere.
 */
private fun homeRows(
    vm: AppViewModel,
    catalog: Catalog,
    lib: LibraryState,
    derived: CatalogDerived?,
    suggestions: Suggestions?,
): List<BrowseRow> {
    val rows = mutableListOf<BrowseRow>()
    rows += BrowseRow("Continua a guardare", continueItems(vm, catalog, lib))
    rows += BrowseRow("La mia lista", myListItems(vm, catalog, lib))
    rows += BrowseRow("Suggeriti per te", titleItems(vm, "home-fy", suggestions?.forYou.orEmpty(), 30))
    val d = derived?.takeIf { it.catalog === catalog }
    rows += BrowseRow("Serie TV aggiornate di recente", titleItems(vm, "newseries", d?.recentSeries.orEmpty(), 40))
    rows += BrowseRow("Film aggiunti di recente", titleItems(vm, "newmovies", d?.recentMovies.orEmpty(), 40))
    // Alterna qualche categoria di serie e di film, come le file della home di Netflix.
    val sc = catalog.seriesCategories.filter { catalog.seriesByCategory[it.id].orEmpty().isNotEmpty() }.take(5)
    val mc = catalog.movieCategories.filter { catalog.moviesByCategory[it.id].orEmpty().isNotEmpty() }.take(5)
    for (i in 0 until maxOf(sc.size, mc.size)) {
        sc.getOrNull(i)?.let { c ->
            rows += categoryRow(vm, "Serie · ${cleanCategoryName(c.name)}", "hs-${c.id}", c, catalog.seriesByCategory[c.id].orEmpty(), isSeries = true)
        }
        mc.getOrNull(i)?.let { c ->
            rows += categoryRow(vm, "Film · ${cleanCategoryName(c.name)}", "hm-${c.id}", c, catalog.moviesByCategory[c.id].orEmpty(), isSeries = false)
        }
    }
    return rows
}

private fun seriesRows(vm: AppViewModel, catalog: Catalog): List<BrowseRow> =
    listOf(
        BrowseRow("Novità", titleItems(vm, "s-new", catalog.series.sortedByDescending { it.lastModified }, 40))
    ) + catalog.seriesCategories.map { c ->
        categoryRow(vm, cleanCategoryName(c.name), "s-${c.id}", c, catalog.seriesByCategory[c.id].orEmpty(), isSeries = true)
    }

private fun movieRows(vm: AppViewModel, catalog: Catalog): List<BrowseRow> =
    listOf(
        BrowseRow("Novità", titleItems(vm, "m-new", catalog.movies.sortedByDescending { it.added }, 40))
    ) + catalog.movieCategories.map { c ->
        categoryRow(vm, cleanCategoryName(c.name), "m-${c.id}", c, catalog.moviesByCategory[c.id].orEmpty(), isSeries = false)
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

/** Scritta in basso a destra mentre il catalogo arriva in sottofondo (es. "Film 45.000…"). */
@Composable
private fun CatalogStatusBanner(vm: AppViewModel, modifier: Modifier) {
    val text = vm.catalogStatus ?: return
    val loading = vm.loadingParts.isNotEmpty()
    Row(
        modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(color = Palette.Accent, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = if (loading) Palette.Text else Palette.Accent, fontSize = 12.sp)
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
