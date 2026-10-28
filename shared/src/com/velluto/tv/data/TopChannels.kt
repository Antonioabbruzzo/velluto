package com.velluto.tv.data

/** Un gruppo della sezione "Top canali" (es. "Rai e Mediaset") con i canali già scelti. */
data class TopGroup(val title: String, val channels: List<LiveChannel>)

/**
 * Selezione dei canali italiani più visti, presi fra tutti quelli del provider: per ogni canale
 * dell'elenco tiene una sola versione (FHD, altrimenti HD, altrimenti le altre) e scarta le
 * versioni 4K (troppo pesanti), i timeshift (+1, +24), le categorie per connessioni lente,
 * quelle per adulti e i canali stranieri.
 *
 * Ogni voce è un elenco di nomi equivalenti, già "normalizzati" (vedi [normalizeChannelName]).
 */
private val TOP_LIST: List<Pair<String, List<List<String>>>> = listOf(
    "Rai, Mediaset e La7" to listOf(
        listOf("rai 1", "rai uno", "rai1"),
        listOf("rai 2", "rai due", "rai2"),
        listOf("rai 3", "rai tre", "rai3"),
        listOf("rete 4", "rete4", "retequattro"),
        listOf("canale 5", "canale5"),
        listOf("italia 1", "italia1", "italia uno"),
        listOf("la7", "la 7"),
        listOf("tv8", "tv 8"),
        listOf("nove", "discovery nove", "nove tv"),
        listOf("rai 4", "rai4"),
        listOf("rai 5", "rai5"),
        listOf("rai movie"),
        listOf("rai premium"),
        listOf("iris"),
        listOf("la5", "la 5"),
        listOf("cine34", "cine 34"),
        listOf("twentyseven", "27 twentyseven"),
        listOf("canale 20", "20 mediaset"),
        listOf("italia 2", "italia2"),
        listOf("mediaset extra", "extra"),
        listOf("top crime", "topcrime"),
        listOf("focus"),
        listOf("cielo"),
        listOf("la7 cinema", "la7d", "la7 d"),
        listOf("rai storia"),
    ),
    "Sky e intrattenimento" to listOf(
        listOf("sky uno"),
        listOf("sky atlantic"),
        listOf("sky serie"),
        listOf("sky investigation"),
        listOf("sky crime"),
        listOf("sky collection"),
        listOf("sky documentaries"),
        listOf("sky nature"),
        listOf("sky adventure"),
        listOf("sky arte"),
        listOf("real time", "discovery real time"),
        listOf("dmax", "discovery dmax"),
        listOf("giallo", "discovery giallo"),
        listOf("discovery channel", "discovery"),
        listOf("history channel", "history"),
        listOf("food network"),
        listOf("comedy central"),
        listOf("sky mtv italia", "mtv"),
    ),
    "Cinema" to listOf(
        listOf("sky cinema uno"),
        listOf("sky cinema due"),
        listOf("sky cinema stories"),
        listOf("sky cinema collection"),
        listOf("sky cinema action"),
        listOf("sky cinema comedy"),
        listOf("sky cinema drama"),
        listOf("sky cinema family"),
        listOf("sky cinema romance"),
        listOf("sky cinema suspense"),
    ),
    "Sport" to listOf(
        listOf("sky sport 24"),
        listOf("sky sport uno", "sky sport 1"),
        listOf("sky sport calcio"),
        listOf("sky sport max"),
        listOf("sky sport arena"),
        listOf("sky sport mix"),
        listOf("sky sport f1", "sky sport formula 1"),
        listOf("sky sport motogp"),
        listOf("sky sport tennis"),
        listOf("sky sport basket"),
        listOf("sky sport legend"),
        listOf("dazn 1", "dazn1"),
        listOf("zona dazn", "dazn channel"),
        listOf("eurosport 1", "eurosport"),
        listOf("eurosport 2"),
        listOf("rai sport", "rai sport +"),
        listOf("supertennis", "super tennis"),
    ),
    "News" to listOf(
        listOf("rai news 24", "rainews 24", "rai news"),
        listOf("sky tg24", "sky tg 24"),
        listOf("tgcom 24", "tgcom24"),
        listOf("euronews"),
    ),
    "Ragazzi" to listOf(
        listOf("rai yoyo", "rai yo yo"),
        listOf("rai gulp"),
        listOf("boing"),
        listOf("cartoonito"),
        listOf("super!", "super"),
        listOf("frisbee"),
        listOf("k2"),
        listOf("dea kids"),
        listOf("nickelodeon"),
        listOf("cartoon network"),
    ),
)

// Espressioni compilate una volta sola: con migliaia di canali ricompilarle per ognuno bloccava le Firestick più lente.
private val COUNTRY_PREFIX = Regex("""^\s*[\[(|]?\s*(it|ita|italia)\s*[\])|:\-]+\s*""")
private val BRACKETS = Regex("""[|\[\]()]""")
private val SPACES = Regex("""\s+""")
private val Q_FHD = Regex("""\bfhd\b|ꜰʜᴅ""")
private val Q_HD = Regex("""\bhd\b|ᴴᴰ""")
private val Q_SAT = Regex("""\bsat\b""")
private val Q_SD = Regex("""\bsd\b""")

private val QUALITY_TOKENS = setOf("hd", "fhd", "uhd", "4k", "sat", "sd", "hevc", "h265", "1080p", "720p", "ᴴᴰ", "ꜰʜᴅ", "⁴ᴷ")

/** Nome del canale senza prefissi di paese, qualità e simboli: "|IT| Rai 1 FHD" → "rai 1". */
fun normalizeChannelName(name: String): String {
    var s = name.lowercase()
        .replace(COUNTRY_PREFIX, "")
        .replace(BRACKETS, " ")
    s = s.split(SPACES).filter { it.isNotBlank() && it !in QUALITY_TOKENS }.joinToString(" ")
    // "Mediaset Canale 20" → anche "canale 20": il marchio davanti spesso c'è, spesso no.
    return s.removePrefix("mediaset ").trim()
}

/** Punteggio di qualità della versione: più alto = preferita. SAT è spesso instabile. */
private fun qualityScore(name: String): Int {
    val t = name.lowercase()
    return when {
        Q_FHD.containsMatchIn(t) -> 4
        Q_HD.containsMatchIn(t) -> 3
        Q_SAT.containsMatchIn(t) -> 1
        Q_SD.containsMatchIn(t) -> 0
        else -> 2
    }
}

private val TIMESHIFT = Regex("""\+\s*\d|\bplus\b""", RegexOption.IGNORE_CASE)
private val ULTRA_HD = Regex("""\b(4k|uhd|2160p)\b|⁴ᴷ""", RegexOption.IGNORE_CASE)
private val SLOW_CATEGORY =Regex("""\bsd\b|lent""", RegexOption.IGNORE_CASE)

/**
 * Costruisce i gruppi dei Top canali. Restano solo i gruppi con almeno un canale trovato:
 * con un provider senza canali italiani la sezione semplicemente non compare.
 */
fun buildTopChannels(live: List<LiveChannel>, categoryNames: Map<String, String>): List<TopGroup> {
    val byName = HashMap<String, MutableList<LiveChannel>>()
    // Le categorie si controllano una volta sola, non per ogni canale.
    val skippedCategory = HashMap<String, Boolean>()
    for (ch in live) {
        val skip = skippedCategory.getOrPut(ch.categoryId) {
            val category = categoryNames[ch.categoryId].orEmpty()
            SLOW_CATEGORY.containsMatchIn(category) || isAdultCategory(category)
        }
        if (skip) continue
        if (TIMESHIFT.containsMatchIn(ch.name) || ULTRA_HD.containsMatchIn(ch.name)) continue
        byName.getOrPut(normalizeChannelName(ch.name)) { mutableListOf() }.add(ch)
    }
    val used = HashSet<Int>()
    return TOP_LIST.mapNotNull { (title, entries) ->
        val channels = entries.mapNotNull { aliases ->
            aliases.asSequence()
                .flatMap { byName[it].orEmpty().asSequence() }
                .filter { it.id !in used }
                .maxByOrNull { qualityScore(it.name) }
                ?.also { used += it.id }
        }
        if (channels.isEmpty()) null else TopGroup(title, channels)
    }
}
