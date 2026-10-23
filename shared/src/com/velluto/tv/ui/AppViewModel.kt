package com.velluto.tv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.velluto.tv.data.Account
import com.velluto.tv.data.AccountInfo
import com.velluto.tv.data.Catalog
import com.velluto.tv.data.Episode
import com.velluto.tv.data.GenreEntry
import com.velluto.tv.data.Library
import com.velluto.tv.data.LiveChannel
import com.velluto.tv.data.Movie
import com.velluto.tv.data.Series
import com.velluto.tv.data.Title
import com.velluto.tv.data.XtreamClient
import com.velluto.tv.platform.PlatformServices
import com.velluto.tv.platform.UpdateInfo
import androidx.compose.runtime.snapshotFlow
import com.velluto.tv.data.CatalogCache
import com.velluto.tv.data.Recommender
import com.velluto.tv.data.buildGenreEntries
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

sealed interface Screen {
    val stateKey: String

    data object Home : Screen { override val stateKey = "home" }
    data class SeriesDetail(val series: Series) : Screen { override val stateKey = "series-${series.id}" }
    data class MovieDetail(val movie: Movie) : Screen { override val stateKey = "movie-${movie.id}" }
    data class Player(val request: PlayRequest, val n: Long = System.nanoTime()) : Screen {
        override val stateKey = "player-$n"
    }
}

sealed interface PlayRequest {
    data class Live(val channels: List<LiveChannel>, val index: Int) : PlayRequest
    data class MovieReq(val movie: Movie, val ext: String, val startMs: Long) : PlayRequest
    data class EpisodeReq(val series: Series, val episodes: List<Episode>, val index: Int, val startMs: Long) : PlayRequest
}

enum class Section { SEARCH, HOME, FOR_YOU, SERIES, SERIES_GENRE, MOVIES, MOVIES_GENRE, LIVE, MY_LIST, SETTINGS }

/** Informazioni in più su un titolo (trama, genere, durata) scaricate solo quando servono. */
data class TitleExtra(val plot: String, val year: String, val genre: String, val duration: String)

/** Il catalogo si riscarica al massimo una volta al giorno (o a mano da Impostazioni). */
private const val REFRESH_EVERY_MS = 24L * 60 * 60 * 1000

/** Dati ricavati dal catalogo che richiedono di scorrere decine di migliaia di titoli. */
data class CatalogDerived(
    val catalog: Catalog,
    val recentSeries: List<Series>,
    val recentMovies: List<Movie>,
    val seriesGenres: List<GenreEntry>,
    val movieGenres: List<GenreEntry>,
)

/** Suggerimenti per la Home e la sezione "Per te". */
data class Suggestions(val forYou: List<Title>, val becauseYouWatched: List<Pair<Title, List<Title>>>)

sealed interface LoadState {
    data object Idle : LoadState
    data class Loading(val message: String) : LoadState
    data class Error(val message: String) : LoadState
}

/** Stato dell'app condiviso tra Fire TV e Windows. */
class AppViewModel(val platform: PlatformServices) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val accounts = platform.accountStore
    val library = Library(platform.dataDir)

    var account by mutableStateOf(accounts.load())
        internal set
    var client: XtreamClient? = account?.let { XtreamClient(it) }
        private set
    var accountInfo by mutableStateOf<AccountInfo?>(null)
        internal set
    var catalog by mutableStateOf<Catalog?>(null)
        internal set
    var loadState by mutableStateOf<LoadState>(LoadState.Idle)
        private set
    var update by mutableStateOf<UpdateInfo?>(null)
        private set

    private val catalogCache = CatalogCache(platform.dataDir)
    private val cacheLock = Mutex()
    private var loadJob: Job? = null

    /** Parti del catalogo ancora in arrivo, con quanti elementi sono già stati letti. */
    val loadingParts = mutableStateMapOf<String, Int>()

    /** Quando è stato scaricato l'ultima volta ogni elenco ("Canali", "Serie", "Film"). */
    private val updatedAt = mutableStateMapOf<String, Long>()
    var catalogError by mutableStateOf<String?>(null)
        private set

    /** Testo per la scritta "aggiornamento in corso" (null quando il catalogo è completo). */
    val catalogStatus: String?
        get() = when {
            loadingParts.isNotEmpty() -> "Aggiornamento catalogo · " + loadingParts.entries
                .sortedBy { it.key }
                .joinToString("  ·  ") { (part, n) -> if (n > 0) "$part ${"%,d".format(Locale.ITALY, n)}…" else "$part…" }
            else -> catalogError
        }

    var section by mutableStateOf(Section.HOME)

    /** Genere aperto dal sottomenu di "Serie TV" o "Film" (sezioni SERIES_GENRE / MOVIES_GENRE). */
    var openGenre by mutableStateOf<GenreEntry?>(null)

    val backStack = mutableStateListOf<Screen>(Screen.Home)

    /** Ultima locandina selezionata per ogni sezione, per ritrovare il punto quando si torna indietro. */
    val lastFocus = mutableStateMapOf<String, String>()

    /** Trama e dettagli dei titoli già mostrati nel banner (il provider non li manda per i film). */
    val extras = mutableStateMapOf<String, TitleExtra>()
    private val extrasLoading = mutableSetOf<String>()

    /** Scarica trama, genere e durata di un titolo, se mancano. Una sola volta per titolo. */
    fun loadExtra(t: Title) {
        if (t is Series && t.plot.isNotBlank()) return
        if (extras.containsKey(t.key) || !extrasLoading.add(t.key)) return
        val c = client ?: return
        scope.launch {
            val extra = runCatching {
                when (t) {
                    is Movie -> c.movieDetail(t.id).let { TitleExtra(it.plot, it.year, it.genre, it.duration) }
                    is Series -> c.seriesDetail(t.id).let { TitleExtra(it.plot, it.year, it.genre, "") }
                }
            }.getOrNull()
            if (extra != null) extras[t.key] = extra
            extrasLoading.remove(t.key)
        }
    }

    /**
     * I calcoli pesanti girano su un solo thread in sottofondo, così non rallentano mai
     * l'interfaccia né si contendono il processore (sulla Fire TV è poco potente).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val background = Dispatchers.Default.limitedParallelism(1)

    var derived by mutableStateOf<CatalogDerived?>(null)
        private set
    var suggestions by mutableStateOf<Suggestions?>(null)
        private set

    /** File delle sezioni Serie TV / Film già costruite, per non rifarle a ogni accesso. */
    internal val rowCache = HashMap<String, Pair<Catalog, Any>>()

    @OptIn(FlowPreview::class)
    private fun startBackgroundWork() {
        // Generi e novità: una volta per ogni versione del catalogo.
        scope.launch {
            snapshotFlow { catalog }.filterNotNull().collectLatest { c ->
                derived = withContext(background) {
                    // Prepara qui (non sul thread dell'interfaccia) gli indici del catalogo usati ovunque.
                    c.resolve("movie:", -1)
                    c.resolve("series:", -1)
                    c.moviesByCategory
                    c.seriesByCategory
                    c.liveByCategory
                    CatalogDerived(
                        catalog = c,
                        recentSeries = c.series.sortedByDescending { it.lastModified }.take(60),
                        recentMovies = c.movies.sortedByDescending { it.added }.take(60),
                        seriesGenres = buildGenreEntries(c, series = true),
                        movieGenres = buildGenreEntries(c, series = false),
                    )
                }
            }
        }
        // Suggerimenti: ricalcolati con calma (qualche secondo dopo) quando cambiano catalogo, lista o cronologia.
        scope.launch {
            combine(snapshotFlow { catalog }.filterNotNull(), library.state) { c, lib -> c to lib }
                .debounce(2_000)
                .collectLatest { (c, lib) ->
                    suggestions = withContext(background) {
                        Suggestions(
                            forYou = Recommender.forYou(c, lib, limit = 40),
                            becauseYouWatched = Recommender.becauseYouWatched(c, lib),
                        )
                    }
                }
        }
    }

    init {
        startBackgroundWork()
        if (account != null) loadCatalog()
        checkForUpdate()
    }

    fun login(url: String, user: String, pass: String, onError: (String) -> Unit) {
        val acc = Account(XtreamClient.normalizeBaseUrl(url), user.trim(), pass.trim())
        loadState = LoadState.Loading("Accesso in corso…")
        scope.launch {
            try {
                val c = XtreamClient(acc)
                accountInfo = c.accountInfo()
                accounts.save(acc)
                client = c
                account = acc
                loadCatalog()
            } catch (e: Exception) {
                loadState = LoadState.Idle
                onError(e.message ?: "Impossibile collegarsi al server")
            }
        }
    }

    fun logout() {
        // La libreria (preferiti e cronologia) NON viene cancellata.
        loadJob?.cancel()
        loadingParts.clear()
        updatedAt.clear()
        catalogError = null
        accounts.clear()
        account = null
        client = null
        catalog = null
        accountInfo = null
        loadState = LoadState.Idle
        backStack.clear()
        backStack.add(Screen.Home)
        section = Section.HOME
    }

    /** Data dell'ultimo aggiornamento completo del catalogo (null se non è mai stato completato). */
    val catalogUpdatedAt: Long?
        get() = CatalogCache.PARTS.map { updatedAt[it] ?: 0L }.minOrNull()?.takeIf { it > 0 }

    /**
     * Carica il catalogo senza far aspettare:
     * 1. mostra subito la copia salvata sul dispositivo (al primo avvio: solo le categorie, in pochi secondi);
     * 2. riscarica in sottofondo solo gli elenchi più vecchi di un giorno (o mancanti), ognuno appena è pronto.
     * [force] = true (da Impostazioni → Ricarica catalogo) riscarica tutto subito.
     */
    fun loadCatalog(force: Boolean = false) {
        val c = client ?: return
        val acc = account ?: return
        loadJob?.cancel()
        loadingParts.clear()
        catalogError = null
        if (catalog == null) loadState = LoadState.Loading("Collegamento al provider…")
        loadJob = scope.launch {
            try {
                if (accountInfo == null) accountInfo = runCatching { c.accountInfo() }.getOrNull()
                if (catalog == null) {
                    val cached = withContext(Dispatchers.IO) { catalogCache.load(acc) }
                    if (cached != null) {
                        catalog = cached.catalog
                        updatedAt.putAll(cached.updatedAt)
                        loadState = LoadState.Idle
                    } else {
                        loadState = LoadState.Loading("Caricamento delle categorie…")
                    }
                }
                val now = System.currentTimeMillis()
                val current = catalog
                fun isEmpty(part: String) = when (part) {
                    "Canali" -> current?.live.isNullOrEmpty()
                    "Serie" -> current?.series.isNullOrEmpty()
                    else -> current?.movies.isNullOrEmpty()
                }
                val toRefresh = CatalogCache.PARTS.filter { part ->
                    force || isEmpty(part) || now - (updatedAt[part] ?: 0L) > REFRESH_EVERY_MS
                }
                if (toRefresh.isEmpty()) {
                    loadState = LoadState.Idle
                    return@launch // catalogo aggiornato da meno di un giorno: niente da scaricare
                }
                // Le categorie arrivano in pochi secondi: al primo avvio bastano per aprire la Home.
                val (liveCats, movieCats, seriesCats) = coroutineScope {
                    val l = async { retryOnce { c.liveCategories() } }
                    val m = async { retryOnce { c.movieCategories() } }
                    val s = async { retryOnce { c.seriesCategories() } }
                    Triple(l.await(), m.await(), s.await())
                }
                val base = catalog ?: Catalog(liveCats, emptyList(), movieCats, emptyList(), seriesCats, emptyList())
                catalog = base.copy(liveCategories = liveCats, movieCategories = movieCats, seriesCategories = seriesCats)
                loadState = LoadState.Idle
                refreshLists(c, acc, toRefresh)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                val msg = t.message ?: "Errore durante il caricamento"
                if (catalog == null) loadState = LoadState.Error(msg)
                else catalogError = "Aggiornamento del catalogo non riuscito ($msg). Riprova da Impostazioni."
            }
        }
    }

    /** Scarica in parallelo gli elenchi indicati; ognuno aggiorna l'app e la copia salvata appena arriva. */
    private suspend fun refreshLists(c: XtreamClient, acc: Account, parts: List<String>) = coroutineScope {
        val failed = mutableListOf<String>()
        suspend fun <T> part(name: String, fetch: suspend ((Int) -> Unit) -> List<T>, apply: (Catalog, List<T>) -> Catalog) {
            if (name !in parts) return
            try {
                val list = retryOnce { fetch { n -> loadingParts[name] = n } }
                catalog = catalog?.let { apply(it, list) }
                updatedAt[name] = System.currentTimeMillis()
                saveCache(acc)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                failed += name
            } finally {
                loadingParts.remove(name)
            }
        }
        parts.forEach { loadingParts[it] = 0 }
        listOf(
            launch { part("Canali", { p -> c.liveStreams(p) }) { cat, l -> cat.copy(live = l) } },
            launch { part("Serie", { p -> c.series(p) }) { cat, l -> cat.copy(series = l) } },
            launch { part("Film", { p -> c.movies(p) }) { cat, l -> cat.copy(movies = l) } },
        ).joinAll()
        if (failed.isNotEmpty()) {
            catalogError = "Non è stato possibile scaricare: ${failed.joinToString(", ").lowercase()}. " +
                "Riprova da Impostazioni → Ricarica catalogo."
        }
    }

    private suspend fun saveCache(acc: Account) {
        val snapshot = catalog ?: return
        val times = updatedAt.toMap()
        withContext(Dispatchers.IO + NonCancellable) {
            cacheLock.withLock { runCatching { catalogCache.save(acc, snapshot, times) } }
        }
    }

    /** Riprova una volta dopo qualche secondo: i provider lenti a volte chiudono la connessione. */
    private suspend fun <T> retryOnce(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        delay(3_000)
        block()
    }

    fun checkForUpdate(onResult: ((Boolean) -> Unit)? = null) {
        scope.launch {
            update = runCatching { platform.updater.check() }.getOrNull()
            onResult?.invoke(update != null)
        }
    }

    /** Sezione a cui tornare con Indietro da una pagina di genere/categoria. */
    var genreReturnSection: Section = Section.SERIES

    /** Apre tutti i titoli di una categoria ("Vedi tutti"); Indietro riporta alla sezione di partenza. */
    fun openGenreEntry(g: GenreEntry) {
        if (section != Section.SERIES_GENRE && section != Section.MOVIES_GENRE) genreReturnSection = section
        openGenre = g
        section = if (g.isSeries) Section.SERIES_GENRE else Section.MOVIES_GENRE
    }

    /** Apre una sezione dal menu laterale; se si è in una scheda (serie/film) si torna prima alla Home. */
    fun openSection(s: Section) {
        if (backStack.size > 1) {
            backStack.clear()
            backStack.add(Screen.Home)
        }
        section = s
    }

    fun navigate(screen: Screen) {
        backStack.add(screen)
    }

    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    fun replaceTop(screen: Screen) {
        backStack[backStack.lastIndex] = screen
    }

    fun dispose() {
        scope.cancel()
    }
}
