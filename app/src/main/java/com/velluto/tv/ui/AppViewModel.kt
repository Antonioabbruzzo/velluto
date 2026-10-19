package com.velluto.tv.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.velluto.tv.data.Account
import com.velluto.tv.data.AccountInfo
import com.velluto.tv.data.Catalog
import com.velluto.tv.data.Episode
import com.velluto.tv.data.Library
import com.velluto.tv.data.LiveChannel
import com.velluto.tv.data.Movie
import com.velluto.tv.data.Prefs
import com.velluto.tv.data.Series
import com.velluto.tv.data.UpdateInfo
import com.velluto.tv.data.Updater
import com.velluto.tv.data.XtreamClient
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

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

enum class Section { SEARCH, HOME, SERIES, MOVIES, LIVE, MY_LIST, SETTINGS }

sealed interface LoadState {
    data object Idle : LoadState
    data class Loading(val message: String) : LoadState
    data class Error(val message: String) : LoadState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    val library = Library(app)

    var account by mutableStateOf(prefs.loadAccount())
        private set
    var client: XtreamClient? = account?.let { XtreamClient(it) }
        private set
    var accountInfo by mutableStateOf<AccountInfo?>(null)
        private set
    var catalog by mutableStateOf<Catalog?>(null)
        private set
    var loadState by mutableStateOf<LoadState>(LoadState.Idle)
        private set
    var update by mutableStateOf<UpdateInfo?>(null)
        private set

    var section by mutableStateOf(Section.HOME)
    val backStack = mutableStateListOf<Screen>(Screen.Home)

    /** Ultima locandina selezionata per ogni sezione, per ritrovare il punto quando si torna indietro. */
    val lastFocus = mutableStateMapOf<String, String>()

    init {
        if (account != null) loadCatalog()
        checkForUpdate()
    }

    fun login(url: String, user: String, pass: String, onError: (String) -> Unit) {
        val acc = Account(XtreamClient.normalizeBaseUrl(url), user.trim(), pass.trim())
        loadState = LoadState.Loading("Accesso in corso…")
        viewModelScope.launch {
            try {
                val c = XtreamClient(acc)
                accountInfo = c.accountInfo()
                prefs.saveAccount(acc)
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
        prefs.clearAccount()
        account = null
        client = null
        catalog = null
        accountInfo = null
        loadState = LoadState.Idle
        backStack.clear()
        backStack.add(Screen.Home)
        section = Section.HOME
    }

    fun loadCatalog() {
        val c = client ?: return
        loadState = LoadState.Loading("Caricamento del catalogo…")
        viewModelScope.launch {
            try {
                if (accountInfo == null) accountInfo = runCatching { c.accountInfo() }.getOrNull()
                val liveCats = async { c.liveCategories() }
                val live = async { c.liveStreams() }
                val movieCats = async { c.movieCategories() }
                val movies = async { c.movies() }
                val seriesCats = async { c.seriesCategories() }
                val series = async { c.series() }
                catalog = Catalog(
                    liveCategories = liveCats.await(),
                    live = live.await(),
                    movieCategories = movieCats.await(),
                    movies = movies.await(),
                    seriesCategories = seriesCats.await(),
                    series = series.await(),
                )
                loadState = LoadState.Idle
            } catch (e: Exception) {
                loadState = LoadState.Error(e.message ?: "Errore durante il caricamento")
            }
        }
    }

    fun checkForUpdate(onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            update = Updater.check()
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
}
