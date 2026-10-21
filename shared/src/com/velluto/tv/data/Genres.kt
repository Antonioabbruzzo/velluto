package com.velluto.tv.data

import java.util.Locale

/*
 * Generi: i provider chiamano le categorie in mille modi ("FILM | COMMEDIA", "IT - Commedie",
 * "SERIE TV DRAMMATICHE"...). Qui le riduciamo a un genere comune, così film e serie dello
 * stesso genere finiscono insieme e i suggerimenti funzionano tra film e serie.
 */

/** Sinonimi -> genere canonico. Si confronta con le singole parole del nome della categoria. */
private val genreSynonyms: Map<String, String> = buildMap {
    fun g(canonical: String, vararg words: String) {
        put(canonical, canonical)
        words.forEach { put(it, canonical) }
    }
    g("commedia", "commedie", "comedy", "comico", "comici", "comiche", "sitcom")
    g("dramma", "drammi", "drama", "drammatico", "drammatici", "drammatiche", "drammatica")
    g("azione", "action")
    g("avventura", "avventure", "adventure")
    g("thriller", "suspense")
    g("horror", "orrore", "paura")
    g("fantascienza", "scifi", "sci", "fantascientifico", "fantascientifici")
    g("fantasy", "fantastico", "fantastici")
    g("animazione", "animation", "animati", "animato", "cartoni", "cartoon", "cartoons", "anime")
    g("bambini", "kids", "ragazzi", "famiglia", "family", "junior")
    g("documentari", "documentario", "documentary", "documentaries", "docu")
    g("romantico", "romantici", "romantica", "romantiche", "romance", "sentimentale", "sentimentali")
    g("crime", "crimine", "criminale", "poliziesco", "polizieschi", "gialli", "giallo", "noir")
    g("mistero", "mystery", "misteri")
    g("guerra", "war", "bellico", "bellici")
    g("western")
    g("musical", "musica", "music", "musicale")
    g("biografico", "biografia", "biografici", "biography")
    g("storico", "storia", "storici", "history")
    g("sport", "sportivo", "sportivi")
    g("reality")
    g("natale", "natalizio", "natalizi", "christmas")
}

/** Parole che non dicono nulla sul genere. */
private val genreNoise = setOf(
    "film", "films", "movie", "movies", "vod", "serie", "series", "tv", "hd", "fhd", "uhd",
    "4k", "sd", "ita", "it", "eng", "en", "sub", "nuovi", "nuove", "ultime", "uscite", "di", "e", "and", "i", "le", "la",
)

private val splitRegex = Regex("""[^\p{L}\p{N}]+""")
private val genreCache = java.util.concurrent.ConcurrentHashMap<String, String>()

/** Chiave del genere a partire dal nome di una categoria o di un genere ("" se non ricavabile). */
fun genreKey(raw: String): String = genreCache.getOrPut(raw) { computeGenreKey(raw) }

private fun computeGenreKey(raw: String): String {
    val words = normalizeName(raw).split(splitRegex).filter { it.isNotBlank() }
    words.firstNotNullOfOrNull { genreSynonyms[it] }?.let { return it }
    return words.filter { it !in genreNoise }.joinToString(" ")
}

/** Generi di un titolo: dalla categoria del provider e, per le serie, dal campo "genre". */
fun genreKeysOf(t: Title, catalog: Catalog): Set<String> {
    val keys = mutableSetOf<String>()
    genreKey(catalog.categoryName(t)).takeIf { it.isNotBlank() }?.let(keys::add)
    if (t is Series && t.genre.isNotBlank()) {
        t.genre.split(',', '/', '&', '|').forEach { part ->
            genreKey(part).takeIf { it.isNotBlank() }?.let(keys::add)
        }
    }
    return keys
}

private val categoryPrefixRegex =
    Regex("""(?i)^\s*(\[[^\]]*]|\|[^|]*\||[A-Z]{2,3}\s*[-:|]|(film|films|movies?|vod|serie\s*tv|series|serie|tv)\s*[-:|])\s*""")

/** Nome leggibile di una categoria: "IT | FILM - COMMEDIA" diventa "Commedia". */
fun cleanCategoryName(raw: String): String {
    var n = raw.trim()
    repeat(4) { n = n.replace(categoryPrefixRegex, "") }
    n = n.trim { it in " -|:._" }.ifBlank { raw.trim() }
    // Nomi tutti in maiuscolo diventano "Prima lettera maiuscola".
    if (n == n.uppercase()) n = n.lowercase().replaceFirstChar { it.titlecase(Locale.ITALIAN) }
    return n
}

/** Un genere (categoria del provider) di film oppure di serie, con tutti i suoi titoli. */
data class GenreEntry(val id: String, val name: String, val isSeries: Boolean, val titles: List<Title>)

/**
 * I generi per il sottomenu di "Serie TV" o di "Film", in ordine alfabetico.
 * Categorie con lo stesso nome (es. "IT | Commedia" e "Commedia") vengono unite.
 */
fun buildGenreEntries(catalog: Catalog, series: Boolean): List<GenreEntry> {
    val cats = if (series) catalog.seriesCategories else catalog.movieCategories
    val groups = LinkedHashMap<String, Triple<String, String, MutableList<Title>>>()
    for (c in cats) {
        val titles: List<Title> = if (series) catalog.seriesByCategory[c.id].orEmpty() else catalog.moviesByCategory[c.id].orEmpty()
        if (titles.isEmpty()) continue
        val name = cleanCategoryName(c.name)
        groups.getOrPut(name.lowercase()) { Triple(c.id, name, mutableListOf()) }.third += titles
    }
    return groups.values
        .map { (id, name, titles) -> GenreEntry(id, name, series, titles.distinctBy { it.key }) }
        .sortedBy { it.name.lowercase() }
}

/**
 * Suggerimenti calcolati sul dispositivo: dai titoli che hai guardato o messo in lista ricava
 * i tuoi generi preferiti e propone titoli degli stessi generi che non hai ancora visto.
 */
object Recommender {

    /** Titoli "tuoi" con un peso: visti di recente, molti episodi, in lista. Più recenti per primi. */
    private fun seen(catalog: Catalog, lib: LibraryState): List<Pair<Title, Double>> {
        val weights = LinkedHashMap<String, Pair<Title, Double>>()
        lib.progress.values
            .groupBy { it.titleKey }
            .entries
            .sortedByDescending { (_, list) -> list.maxOf { it.updatedAt } }
            .forEach { (key, list) ->
                val first = list.first()
                val t = catalog.resolve(key, first.titleId) ?: return@forEach
                val w = 1.0 + 0.3 * minOf(list.size, 5) + if (list.any { it.finished }) 0.5 else 0.0
                weights[t.key] = t to w
            }
        lib.favorites.values.sortedByDescending { it.addedAt }.forEach { f ->
            val t = catalog.resolve(f.key, f.id) ?: return@forEach
            val prev = weights[t.key]?.second ?: 0.0
            weights[t.key] = t to prev + 1.5
        }
        return weights.values.toList()
    }

    private fun score(c: Title, genreWeights: Map<String, Double>, catalog: Catalog): Double {
        var s = 0.0
        for (g in genreKeysOf(c, catalog)) s += genreWeights[g] ?: 0.0
        if (s == 0.0) return 0.0
        val rating = c.rating.toDoubleOrNull() ?: 0.0
        return s + rating / 10.0 * 0.5
    }

    private fun candidates(catalog: Catalog, exclude: Set<String>): Sequence<Title> =
        (catalog.series.asSequence() + catalog.movies.asSequence()).filter { it.key !in exclude }

    /** "Suggeriti per te": i titoli più vicini ai tuoi generi preferiti. */
    fun forYou(catalog: Catalog, lib: LibraryState, limit: Int = 40): List<Title> {
        val seen = seen(catalog, lib)
        if (seen.isEmpty()) return emptyList()
        val genreWeights = HashMap<String, Double>()
        for ((t, w) in seen) for (g in genreKeysOf(t, catalog)) genreWeights[g] = (genreWeights[g] ?: 0.0) + w
        val exclude = seen.map { it.first.key }.toSet()
        return candidates(catalog, exclude)
            .map { it to score(it, genreWeights, catalog) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .distinctBy { it.first.key }
            .take(limit)
            .map { it.first }
            .toList()
    }

    /** "Perché hai guardato X": per gli ultimi titoli visti, altri titoli degli stessi generi. */
    fun becauseYouWatched(catalog: Catalog, lib: LibraryState, rows: Int = 3, perRow: Int = 30): List<Pair<Title, List<Title>>> {
        val seen = seen(catalog, lib)
        val exclude = seen.map { it.first.key }.toSet()
        return seen.take(rows).mapNotNull { (source, _) ->
            val genres = genreKeysOf(source, catalog)
            if (genres.isEmpty()) return@mapNotNull null
            val weights = genres.associateWith { 1.0 }
            val list = candidates(catalog, exclude)
                .map { it to score(it, weights, catalog) + if (it.categoryId == source.categoryId && it::class == source::class) 0.5 else 0.0 }
                .filter { it.second > 0.0 }
                .sortedByDescending { it.second }
                .distinctBy { it.first.key }
                .take(perRow)
                .map { it.first }
                .toList()
            if (list.isEmpty()) null else source to list
        }
    }
}
