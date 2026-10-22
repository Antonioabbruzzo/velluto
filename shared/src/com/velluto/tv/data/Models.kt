package com.velluto.tv.data

data class Account(val baseUrl: String, val username: String, val password: String)

data class Category(val id: String, val name: String)

/** Film o serie: tutto ciò che può comparire in una fila di locandine. */
sealed interface Title {
    val id: Int
    val name: String
    val image: String
    val backdrop: String
    val plot: String
    val rating: String
    val year: String
    val categoryId: String

    /** Chiave stabile basata sul nome: resta valida anche se il provider cambia gli ID. */
    val key: String
}

data class Movie(
    override val id: Int,
    override val name: String,
    override val image: String,
    override val categoryId: String,
    override val rating: String,
    val ext: String,
    val added: Long,
    override val backdrop: String = "",
    override val plot: String = "",
    override val year: String = "",
) : Title {
    // Calcolata una volta sola: con decine di migliaia di titoli ricalcolarla ogni volta rallentava l'app.
    override val key: String by lazy(LazyThreadSafetyMode.PUBLICATION) { movieKey(name) }
}

data class Series(
    override val id: Int,
    override val name: String,
    override val image: String,
    override val categoryId: String,
    override val rating: String,
    override val plot: String,
    override val year: String,
    override val backdrop: String,
    val lastModified: Long,
    /** Generi indicati dal provider, es. "Dramma, Mistero" (può essere vuoto). */
    val genre: String = "",
) : Title {
    override val key: String by lazy(LazyThreadSafetyMode.PUBLICATION) { seriesKey(name) }
}

data class LiveChannel(
    val id: Int,
    val num: Int,
    val name: String,
    val icon: String,
    val categoryId: String,
)

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val ext: String,
    val plot: String,
    val image: String,
    val durationSecs: Int,
)

data class SeriesDetail(
    val plot: String,
    val backdrop: String,
    val genre: String,
    val cast: String,
    val year: String,
    val rating: String,
    val seasons: Map<Int, List<Episode>>,
) {
    val allEpisodes: List<Episode> by lazy {
        seasons.keys.sorted().flatMap { s -> seasons[s].orEmpty().sortedBy { it.number } }
    }
}

data class MovieDetail(
    val plot: String,
    val backdrop: String,
    val genre: String,
    val cast: String,
    val year: String,
    val duration: String,
    val ext: String,
)

data class EpgEntry(val title: String, val start: Long, val end: Long)

data class AccountInfo(val expiresAt: Long?, val maxConnections: String, val status: String)

data class Catalog(
    val liveCategories: List<Category>,
    val live: List<LiveChannel>,
    val movieCategories: List<Category>,
    val movies: List<Movie>,
    val seriesCategories: List<Category>,
    val series: List<Series>,
) {
    val liveByCategory: Map<String, List<LiveChannel>> by lazy { live.groupBy { it.categoryId } }
    val moviesByCategory: Map<String, List<Movie>> by lazy { movies.groupBy { it.categoryId } }
    val seriesByCategory: Map<String, List<Series>> by lazy { series.groupBy { it.categoryId } }
    private val moviesByKey: Map<String, Movie> by lazy { movies.associateBy { it.key } }
    private val seriesByKey: Map<String, Series> by lazy { series.associateBy { it.key } }
    private val moviesById: Map<Int, Movie> by lazy { movies.associateBy { it.id } }
    private val seriesById: Map<Int, Series> by lazy { series.associateBy { it.id } }
    private val movieCategoryNames: Map<String, String> by lazy { movieCategories.associate { it.id to it.name } }
    private val seriesCategoryNames: Map<String, String> by lazy { seriesCategories.associate { it.id to it.name } }

    /** Nome della categoria del provider a cui appartiene il titolo. */
    fun categoryName(t: Title): String = when (t) {
        is Movie -> movieCategoryNames[t.categoryId]
        is Series -> seriesCategoryNames[t.categoryId]
    }.orEmpty()

    /** Ritrova un titolo salvato: prima per nome (stabile), poi per ID. */
    fun resolve(key: String, id: Int): Title? = when {
        key.startsWith("series:") -> seriesByKey[key] ?: seriesById[id]
        key.startsWith("movie:") -> moviesByKey[key] ?: moviesById[id]
        else -> null
    }
}

private val prefixRegex = Regex("""^\s*(\[[^\]]*]|\|[^|]*\||[A-Z]{2,4}\s*[-:|])\s*""")
private val nonAlnum = Regex("""[^\p{L}\p{N}]+""")

/** Normalizza un nome: toglie prefissi tipo "IT - " o "|IT|", maiuscole e simboli. */
fun normalizeName(name: String): String {
    var n = name
    repeat(3) { n = n.replace(prefixRegex, "") }
    return n.lowercase().replace(nonAlnum, " ").trim()
}

private val episodeCodeRegex = Regex("""(?i)\bS\d{1,2}\s*[ ._:-]?\s*E\d{1,3}\b|\b\d{1,2}x\d{1,3}\b""")
private const val TITLE_SEPARATORS = " -–—|:._"

/**
 * Titolo leggibile di un episodio. I provider spesso scrivono "IT - Nome Serie - S01E03 - Titolo":
 * teniamo solo "Titolo". Se non resta niente usiamo "Episodio N".
 */
fun cleanEpisodeTitle(raw: String, seriesName: String, number: Int): String {
    var t = raw.trim()
    val code = episodeCodeRegex.find(t)
    if (code != null) {
        t = t.substring(code.range.last + 1)
    } else {
        val name = seriesName.trim()
        val i = if (name.isNotEmpty()) t.indexOf(name, ignoreCase = true) else -1
        if (i in 0..8) t = t.substring(i + name.length)
    }
    t = t.trim { it in TITLE_SEPARATORS }
    // Titoli che sono solo un numero non aggiungono informazioni.
    if (t.isBlank() || t.all { it.isDigit() }) return "Episodio $number"
    return t
}

fun movieKey(name: String) = "movie:" + normalizeName(name)
fun seriesKey(name: String) = "series:" + normalizeName(name)
