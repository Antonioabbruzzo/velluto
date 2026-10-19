package com.velluto.tv.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Un titolo nella "La mia lista". */
data class Favorite(
    val key: String,
    val id: Int,
    val name: String,
    val image: String,
    val addedAt: Long,
)

/**
 * Punto di visione di un film o di un episodio.
 * [titleKey] è la chiave stabile del film/serie, [key] identifica il singolo episodio.
 */
data class Progress(
    val key: String,
    val titleKey: String,
    val titleId: Int,
    val name: String,
    val image: String,
    val episodeId: String,
    val season: Int,
    val episode: Int,
    val episodeTitle: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val finished: Boolean get() = durationMs > 0 && fraction >= FINISHED_AT

    companion object {
        const val FINISHED_AT = 0.92f
        fun episodeKey(seriesKey: String, season: Int, episode: Int) = "$seriesKey#s${season}e$episode"
    }
}

data class LibraryState(
    val favorites: Map<String, Favorite> = emptyMap(),
    val progress: Map<String, Progress> = emptyMap(),
) {
    /** L'ultimo episodio/film visto per ogni titolo, più recente per primo. */
    val continueWatching: List<Progress> by lazy {
        progress.values
            .groupBy { it.titleKey }
            .mapNotNull { (_, list) -> list.maxByOrNull { it.updatedAt } }
            .filter { !(it.titleKey.startsWith("movie:") && it.finished) }
            .sortedByDescending { it.updatedAt }
    }

    fun latestFor(titleKey: String): Progress? =
        progress.values.filter { it.titleKey == titleKey }.maxByOrNull { it.updatedAt }
}

/**
 * Preferiti e cronologia salvati in un file JSON nella memoria interna dell'app.
 * La memoria interna resta intatta quando l'app viene aggiornata (stessa firma),
 * e le chiavi basate sul nome reggono anche se il provider cambia gli ID.
 */
class Library(context: Context) {
    private val file = File(context.filesDir, "library.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    fun isFavorite(key: String) = _state.value.favorites.containsKey(key)

    fun toggleFavorite(title: Title) = update { s ->
        if (s.favorites.containsKey(title.key)) {
            s.copy(favorites = s.favorites - title.key)
        } else {
            val fav = Favorite(title.key, title.id, title.name, title.image, System.currentTimeMillis())
            s.copy(favorites = s.favorites + (title.key to fav))
        }
    }

    fun saveProgress(p: Progress) = update { s -> s.copy(progress = s.progress + (p.key to p)) }

    fun removeTitleProgress(titleKey: String) = update { s ->
        s.copy(progress = s.progress.filterValues { it.titleKey != titleKey })
    }

    private fun update(change: (LibraryState) -> LibraryState) {
        val newState = synchronized(this) {
            change(_state.value).also { _state.value = it }
        }
        scope.launch { writeLock.withLock { save(newState) } }
    }

    private fun save(s: LibraryState) {
        val json = JSONObject()
        json.put("version", 1)
        json.put("favorites", JSONArray().apply {
            s.favorites.values.forEach { f ->
                put(JSONObject().apply {
                    put("key", f.key); put("id", f.id); put("name", f.name)
                    put("image", f.image); put("addedAt", f.addedAt)
                })
            }
        })
        json.put("progress", JSONArray().apply {
            s.progress.values.forEach { p ->
                put(JSONObject().apply {
                    put("key", p.key); put("titleKey", p.titleKey); put("titleId", p.titleId)
                    put("name", p.name); put("image", p.image); put("episodeId", p.episodeId)
                    put("season", p.season); put("episode", p.episode); put("episodeTitle", p.episodeTitle)
                    put("positionMs", p.positionMs); put("durationMs", p.durationMs); put("updatedAt", p.updatedAt)
                })
            }
        })
        // Scrittura atomica: prima su un file temporaneo, poi rinomina.
        val tmp = File(file.parentFile, "library.json.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun load(): LibraryState = try {
        if (!file.exists()) LibraryState() else {
            val json = JSONObject(file.readText())
            val favs = json.optJSONArray("favorites") ?: JSONArray()
            val prog = json.optJSONArray("progress") ?: JSONArray()
            LibraryState(
                favorites = (0 until favs.length()).mapNotNull { favs.optJSONObject(it) }.associate { o ->
                    val f = Favorite(
                        o.optString("key"), o.optInt("id"), o.optString("name"),
                        o.optString("image"), o.optLong("addedAt"),
                    )
                    f.key to f
                },
                progress = (0 until prog.length()).mapNotNull { prog.optJSONObject(it) }.associate { o ->
                    val p = Progress(
                        key = o.optString("key"), titleKey = o.optString("titleKey"),
                        titleId = o.optInt("titleId"), name = o.optString("name"),
                        image = o.optString("image"), episodeId = o.optString("episodeId"),
                        season = o.optInt("season"), episode = o.optInt("episode"),
                        episodeTitle = o.optString("episodeTitle"), positionMs = o.optLong("positionMs"),
                        durationMs = o.optLong("durationMs"), updatedAt = o.optLong("updatedAt"),
                    )
                    p.key to p
                },
            )
        }
    } catch (e: Exception) {
        LibraryState()
    }
}
