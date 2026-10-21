package com.velluto.tv.data

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString.Companion.decodeBase64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Client per l'API Xtream Codes (player_api.php). */
class XtreamClient(val account: Account) {

    private val base = normalizeBaseUrl(account.baseUrl)
    private val user = account.username
    private val pass = account.password

    private fun apiUrl(action: String? = null, extra: String = ""): String {
        val u = URLEncoder.encode(user, "UTF-8")
        val p = URLEncoder.encode(pass, "UTF-8")
        val a = if (action != null) "&action=$action" else ""
        return "$base/player_api.php?username=$u&password=$p$a$extra"
    }

    fun liveUrl(id: Int, ext: String = "ts") = "$base/live/$user/$pass/$id.$ext"
    fun movieUrl(id: Int, ext: String) = "$base/movie/$user/$pass/$id.${ext.ifBlank { "mp4" }}"
    fun episodeUrl(id: String, ext: String) = "$base/series/$user/$pass/$id.${ext.ifBlank { "mp4" }}"

    // ---- Autenticazione / account ----

    suspend fun accountInfo(): AccountInfo = withContext(Dispatchers.IO) {
        val json = JSONObject(getText(apiUrl()))
        val info = json.optJSONObject("user_info") ?: throw IOException("Risposta del server non valida")
        if (info.optString("auth") != "1") throw IOException("Utente o password errati")
        AccountInfo(
            expiresAt = info.optString("exp_date").toLongOrNull()?.times(1000),
            maxConnections = info.optString("max_connections"),
            status = info.optString("status"),
        )
    }

    // ---- Cataloghi (parsing in streaming: i cataloghi possono essere enormi) ----

    suspend fun liveCategories() = categories("get_live_categories")
    suspend fun movieCategories() = categories("get_vod_categories")
    suspend fun seriesCategories() = categories("get_series_categories")

    private suspend fun categories(action: String) = readList(apiUrl(action)) { m ->
        val id = m["category_id"] ?: return@readList null
        Category(id, m["category_name"].orEmpty())
    }

    suspend fun liveStreams(onProgress: ((Int) -> Unit)? = null) = readList(apiUrl("get_live_streams"), onProgress) { m ->
        val id = m["stream_id"]?.toIntOrNull() ?: return@readList null
        LiveChannel(
            id = id,
            num = m["num"]?.toIntOrNull() ?: 0,
            name = m["name"].orEmpty(),
            icon = m["stream_icon"].orEmpty(),
            categoryId = m["category_id"].orEmpty(),
        )
    }

    suspend fun movies(onProgress: ((Int) -> Unit)? = null) = readList(apiUrl("get_vod_streams"), onProgress) { m ->
        val id = m["stream_id"]?.toIntOrNull() ?: return@readList null
        Movie(
            id = id,
            name = m["name"].orEmpty(),
            image = m["stream_icon"].orEmpty(),
            categoryId = m["category_id"].orEmpty(),
            rating = m["rating"].orEmpty(),
            ext = m["container_extension"].orEmpty(),
            added = m["added"]?.toLongOrNull() ?: 0L,
        )
    }

    suspend fun series(onProgress: ((Int) -> Unit)? = null) = readList(apiUrl("get_series"), onProgress) { m ->
        val id = m["series_id"]?.toIntOrNull() ?: return@readList null
        Series(
            id = id,
            name = m["name"].orEmpty(),
            image = m["cover"].orEmpty(),
            categoryId = m["category_id"].orEmpty(),
            rating = m["rating"].orEmpty(),
            plot = m["plot"].orEmpty(),
            year = (m["releaseDate"] ?: m["release_date"]).orEmpty().take(4),
            backdrop = m["backdrop_path"].orEmpty(),
            lastModified = m["last_modified"]?.toLongOrNull() ?: 0L,
            genre = m["genre"].orEmpty(),
        )
    }

    // ---- Dettagli ----

    suspend fun seriesDetail(seriesId: Int): SeriesDetail = withContext(Dispatchers.IO) {
        val json = JSONObject(getText(apiUrl("get_series_info", "&series_id=$seriesId")))
        val info = json.optJSONObject("info") ?: JSONObject()
        val seasons = mutableMapOf<Int, MutableList<Episode>>()

        fun addEpisode(o: JSONObject, seasonHint: Int) {
            val epInfo = o.optJSONObject("info")
            val season = o.optInt("season", seasonHint).takeIf { it > 0 } ?: seasonHint
            val number = o.optInt("episode_num", 0)
            seasons.getOrPut(season) { mutableListOf() } += Episode(
                id = o.optString("id"),
                season = season,
                number = number,
                title = cleanEpisodeTitle(o.optString("title"), info.optString("name"), number),
                ext = o.optString("container_extension", "mp4"),
                plot = epInfo?.optString("plot").orEmpty(),
                image = epInfo?.optString("movie_image").orEmpty(),
                durationSecs = epInfo?.optInt("duration_secs", 0) ?: 0,
            )
        }

        when (val eps = json.opt("episodes")) {
            is JSONObject -> eps.keys().forEach { k ->
                val arr = eps.optJSONArray(k) ?: return@forEach
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { addEpisode(it, k.toIntOrNull() ?: 1) }
            }
            is JSONArray -> for (i in 0 until eps.length()) {
                val arr = eps.optJSONArray(i) ?: continue
                for (j in 0 until arr.length()) arr.optJSONObject(j)?.let { addEpisode(it, i + 1) }
            }
        }

        SeriesDetail(
            plot = info.optString("plot"),
            backdrop = firstString(info.opt("backdrop_path")),
            genre = info.optString("genre"),
            cast = info.optString("cast"),
            year = (info.optString("releaseDate").ifBlank { info.optString("release_date") }).take(4),
            rating = info.optString("rating"),
            seasons = seasons.mapValues { (_, v) -> v.sortedBy { it.number } },
        )
    }

    suspend fun movieDetail(movieId: Int): MovieDetail = withContext(Dispatchers.IO) {
        val json = JSONObject(getText(apiUrl("get_vod_info", "&vod_id=$movieId")))
        val info = json.optJSONObject("info") ?: JSONObject()
        val data = json.optJSONObject("movie_data") ?: JSONObject()
        MovieDetail(
            plot = info.optString("plot").ifBlank { info.optString("description") },
            backdrop = firstString(info.opt("backdrop_path")),
            genre = info.optString("genre"),
            cast = info.optString("cast").ifBlank { info.optString("actors") },
            year = (info.optString("releasedate").ifBlank { info.optString("release_date") }).take(4),
            duration = info.optString("duration"),
            ext = data.optString("container_extension"),
        )
    }

    suspend fun shortEpg(streamId: Int): List<EpgEntry> = withContext(Dispatchers.IO) {
        val json = JSONObject(getText(apiUrl("get_short_epg", "&stream_id=$streamId&limit=3")))
        val arr = json.optJSONArray("epg_listings") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            EpgEntry(
                title = decodeB64(o.optString("title")),
                start = o.optString("start_timestamp").toLongOrNull()?.times(1000) ?: parseDate(o.optString("start")),
                end = (o.optString("stop_timestamp").ifBlank { o.optString("end_timestamp") })
                    .toLongOrNull()?.times(1000) ?: parseDate(o.optString("end")),
            )
        }
    }

    // ---- Helper HTTP / JSON ----

    private fun getText(url: String): String {
        http.newCall(request(url)).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Errore del server (${r.code})")
            return r.body?.string() ?: ""
        }
    }

    /**
     * Legge un array JSON di oggetti senza caricarlo tutto in memoria.
     * [onProgress] riceve ogni tanto il numero di elementi letti (per mostrare l'avanzamento).
     */
    private suspend fun <T> readList(
        url: String,
        onProgress: ((Int) -> Unit)? = null,
        map: (Map<String, String>) -> T?,
    ): List<T> =
        withContext(Dispatchers.IO) {
            http.newCall(request(url)).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Errore del server (${r.code})")
                val body = r.body ?: return@use emptyList()
                val out = ArrayList<T>()
                JsonReader(body.charStream()).use { reader ->
                    reader.isLenient = true
                    if (reader.peek() != JsonToken.BEGIN_ARRAY) return@use
                    reader.beginArray()
                    var read = 0
                    while (reader.hasNext()) {
                        if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                            map(readFlatObject(reader))?.let(out::add)
                        } else {
                            reader.skipValue()
                        }
                        if (++read % 1000 == 0) {
                            ensureActive() // se il caricamento viene annullato ci fermiamo subito
                            onProgress?.invoke(out.size)
                        }
                    }
                    reader.endArray()
                }
                out
            }
        }

    /** Legge un oggetto tenendo solo i valori semplici (degli array prende il primo elemento). */
    private fun readFlatObject(reader: JsonReader): Map<String, String> {
        val m = HashMap<String, String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            when (reader.peek()) {
                JsonToken.STRING, JsonToken.NUMBER -> m[name] = reader.nextString()
                JsonToken.BOOLEAN -> m[name] = reader.nextBoolean().toString()
                JsonToken.NULL -> reader.nextNull()
                JsonToken.BEGIN_ARRAY -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        if (!m.containsKey(name) && reader.peek() == JsonToken.STRING) {
                            m[name] = reader.nextString()
                        } else {
                            reader.skipValue()
                        }
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return m
    }

    companion object {
        const val USER_AGENT = "Velluto/1.0 (Linux; Android)"

        val http: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        fun request(url: String): Request =
            Request.Builder().url(url).header("User-Agent", USER_AGENT).build()

        /** Accetta "host:porta", "http://host:porta/", link con player_api.php o get.php. */
        fun normalizeBaseUrl(raw: String): String {
            var u = raw.trim()
            if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) u = "http://$u"
            val markers = listOf("/player_api.php", "/get.php", "/xmltv.php", "/panel_api.php")
            for (m in markers) {
                val i = u.indexOf(m, ignoreCase = true)
                if (i > 0) u = u.substring(0, i)
            }
            return u.trimEnd('/')
        }

        private fun firstString(v: Any?): String = when (v) {
            is JSONArray -> if (v.length() > 0) v.optString(0) else ""
            is String -> v
            else -> ""
        }

        private fun decodeB64(s: String): String = s.trim().decodeBase64()?.utf8() ?: s

        private fun parseDate(s: String): Long = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(s)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
