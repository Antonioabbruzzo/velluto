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
import com.velluto.tv.data.CatalogCache
import kotlinx.coroutines.CancellationException
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

    init {
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

    /**
     * Carica il catalogo senza far aspettare:
     * 1. mostra subito la copia salvata sul dispositivo (al primo avvio: solo le categorie, in pochi secondi);
     * 2. canali, serie e film arrivano in sottofondo, ognuno appena è pronto, e vengono salvati.
     * Con provider lenti (elenchi completi da diversi minuti) l'app resta usabile da subito.
     */
    fun loadCatalog() {
        val c = client ?: return
        val acc = account ?: return
        loadJob?.cancel()
        loadingParts.clear()
        catalogError = null
        loadState = LoadState.Loading("Collegamento al provider…")
        loadJob = scope.launch {
            try {
                if (accountInfo == null) accountInfo = runCatching { c.accountInfo() }.getOrNull()
                val cached = withContext(Dispatchers.IO) { catalogCache.load(acc) }
                if (cached != null) {
                    catalog = cached
                    loadState = LoadState.Idle
                } else {
                    loadState = LoadState.Loading("Caricamento delle categorie…")
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
                refreshLists(c, acc)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                val msg = t.message ?: "Errore durante il caricamento"
                if (catalog == null) loadState = LoadState.Error(msg)
                else catalogError = "Aggiornamento del catalogo non riuscito ($msg). Riprova da Impostazioni."
            }
        }
    }

    /** Scarica canali, serie e film in parallelo; ognuno aggiorna l'app e la copia salvata appena arriva. */
    private suspend fun refreshLists(c: XtreamClient, acc: Account) = coroutineScope {
        val failed = mutableListOf<String>()
        suspend fun <T> part(name: String, fetch: suspend ((Int) -> Unit) -> List<T>, apply: (Catalog, List<T>) -> Catalog) {
            try {
                val list = retryOnce { fetch { n -> loadingParts[name] = n } }
                catalog = catalog?.let { apply(it, list) }
                saveCache(acc)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                failed += name
            } finally {
                loadingParts.remove(name)
            }
        }
        listOf("Canali", "Serie", "Film").forEach { loadingParts[it] = 0 }
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
        withContext(Dispatchers.IO + NonCancellable) {
            cacheLock.withLock { runCatching { catalogCache.save(acc, snapshot) } }
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
