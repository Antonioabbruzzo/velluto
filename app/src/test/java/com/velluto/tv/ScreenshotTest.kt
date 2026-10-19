package com.velluto.tv

import android.app.Application
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult
import com.github.takahirom.roborazzi.captureRoboImage
import com.velluto.tv.data.Account
import com.velluto.tv.data.AccountInfo
import com.velluto.tv.data.Catalog
import com.velluto.tv.data.Category
import com.velluto.tv.data.Episode
import com.velluto.tv.data.LiveChannel
import com.velluto.tv.data.Movie
import com.velluto.tv.data.MovieDetail
import com.velluto.tv.data.Progress
import com.velluto.tv.data.Series
import com.velluto.tv.data.SeriesDetail
import com.velluto.tv.ui.AppViewModel
import com.velluto.tv.ui.HomeScreen
import com.velluto.tv.ui.LoginScreen
import com.velluto.tv.ui.MovieDetailScreen
import com.velluto.tv.ui.Palette
import com.velluto.tv.ui.Pill
import com.velluto.tv.ui.PlayRequest
import com.velluto.tv.ui.PlayerOverlay
import com.velluto.tv.ui.Section
import com.velluto.tv.ui.SeriesDetailScreen
import com.velluto.tv.ui.VellutoTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Genera gli screenshot dell'app con dati finti, a risoluzione Fire TV (1920x1080).
 * Le immagini finiscono in app/screenshots/.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: Application
    private lateinit var vm: AppViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        File(app.filesDir, "library.json").delete()
        Coil.setImageLoader(
            ImageLoader.Builder(app)
                .components { add(FakeImages(app.resources)) }
                .dispatcher(Dispatchers.Unconfined)
                .build()
        )
        vm = AppViewModel(app)
        vm.catalog = Fake.catalog
        vm.account = Account("http://mio-provider.example:8080", "mario.rossi", "password")
        vm.accountInfo = AccountInfo(System.currentTimeMillis() + 200L * 24 * 3600 * 1000, "2", "Active")
    }

    private fun seedLibrary() {
        val now = System.currentTimeMillis()
        val s0 = Fake.series[0]
        val s3 = Fake.series[13]
        val m4 = Fake.movies[4]
        vm.library.saveProgress(progress(s0, 1, 1, 2_700_000, 2_700_000, now - 50_000))
        vm.library.saveProgress(progress(s0, 1, 2, 1_100_000, 2_700_000, now - 10_000))
        vm.library.saveProgress(progress(s3, 2, 5, 1_900_000, 2_700_000, now - 30_000))
        vm.library.saveProgress(
            Progress(m4.key, m4.key, m4.id, m4.name, m4.image, "", 0, 0, "", 3_300_000, 6_000_000, now - 20_000)
        )
        vm.library.toggleFavorite(Fake.series[2])
        vm.library.toggleFavorite(Fake.movies[21])
        vm.library.toggleFavorite(Fake.series[22])
        vm.library.toggleFavorite(Fake.movies[11])
        vm.library.toggleFavorite(Fake.series[6])
    }

    private fun progress(s: Series, season: Int, ep: Int, pos: Long, dur: Long, at: Long) = Progress(
        key = Progress.episodeKey(s.key, season, ep), titleKey = s.key, titleId = s.id,
        name = s.name, image = s.image, episodeId = "$season$ep", season = season, episode = ep,
        episodeTitle = "", positionMs = pos, durationMs = dur, updatedAt = at,
    )

    @OptIn(ExperimentalComposeUiApi::class)
    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            // Sulla Fire TV si naviga col telecomando: simuliamo la modalità "tastiera" per avere il focus.
            val inputMode = LocalInputModeManager.current
            SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
            VellutoTheme {
                Box(Modifier.fillMaxSize().background(Palette.Background)) { content() }
            }
        }
        settle()
    }

    private fun settle() {
        repeat(6) {
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
        }
    }

    private fun capture(name: String) {
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @OptIn(ExperimentalTestApi::class)
    private fun press(key: Key, times: Int = 1) {
        repeat(times) { compose.onRoot().performKeyInput { pressKey(key) } }
        settle()
    }

    @Test
    fun login() {
        vm.account = null
        show { LoginScreen(vm) }
        capture("01-login")
    }

    @Test
    fun home() {
        seedLibrary()
        show { HomeScreen(vm, Fake.catalog) }
        capture("02-home")
    }

    @Test
    fun homeScrolled() {
        seedLibrary()
        show { HomeScreen(vm, Fake.catalog) }
        press(Key.DirectionDown, 2)
        press(Key.DirectionRight, 2)
        capture("03-home-navigazione")
    }

    @Test
    fun menu() {
        seedLibrary()
        show { HomeScreen(vm, Fake.catalog) }
        press(Key.DirectionLeft)
        capture("04-menu-laterale")
    }

    @Test
    fun seriesSection() {
        vm.section = Section.SERIES
        show { HomeScreen(vm, Fake.catalog) }
        capture("05-serie-tv")
    }

    @Test
    fun seriesDetail() {
        seedLibrary()
        val s = Fake.series[0]
        show { SeriesDetailScreen(vm, s, Fake.seriesDetail(s)) }
        capture("06-scheda-serie")
    }

    @Test
    fun movieDetail() {
        seedLibrary()
        show { MovieDetailScreen(vm, Fake.movies[4], Fake.movieDetail) }
        capture("07-scheda-film")
    }

    @Test
    fun myList() {
        seedLibrary()
        vm.section = Section.MY_LIST
        show { HomeScreen(vm, Fake.catalog) }
        capture("08-la-mia-lista")
    }

    @Test
    fun live() {
        vm.section = Section.LIVE
        show { HomeScreen(vm, Fake.catalog) }
        press(Key.DirectionRight)
        press(Key.DirectionDown, 2)
        capture("09-tv-live")
    }

    @Test
    fun player() {
        val s = Fake.series[0]
        val eps = Fake.seriesDetail(s).allEpisodes
        show {
            Box(Modifier.fillMaxSize()) {
                Image(
                    bitmap = Fake.backdropBitmap(7, 1280, 720).asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                PlayerOverlay(
                    req = PlayRequest.EpisodeReq(s, eps, 1, 0),
                    isPlaying = false,
                    positionMs = 1_634_000,
                    durationMs = 2_700_000,
                    epg = emptyList(),
                    error = null,
                )
            }
        }
        capture("10-player")
    }

    @Test
    fun playerNextEpisode() {
        val s = Fake.series[0]
        val eps = Fake.seriesDetail(s).allEpisodes
        show {
            Box(Modifier.fillMaxSize()) {
                Image(
                    bitmap = Fake.backdropBitmap(19, 1280, 720).asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Pill("▶  Premi OK per il prossimo episodio", Modifier.align(Alignment.BottomEnd).padding(40.dp))
            }
        }
        capture("11-prossimo-episodio")
    }

    @Test
    fun settings() {
        vm.section = Section.SETTINGS
        show { HomeScreen(vm, Fake.catalog) }
        capture("12-impostazioni")
    }
}

/** Restituisce immagini generate al volo per gli indirizzi "fake://…". */
private class FakeImages(private val res: Resources) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data as? String
        val bmp = data?.let { Fake.bitmapFor(it) } ?: return chain.proceed(chain.request)
        return SuccessResult(
            drawable = BitmapDrawable(res, bmp),
            request = chain.request,
            dataSource = DataSource.MEMORY,
        )
    }
}

private object Fake {
    private val seriesNames = listOf(
        "Il Faro", "Notte a Milano", "Le Onde", "Codice Rosso", "La Villa", "Ombre sul Lago",
        "Il Mercante", "Vento del Sud", "Fuori Orario", "La Torre",
        "Distretto 9", "Il Patto", "Vicolo Cieco", "Sangue Freddo", "L'Infiltrato", "Doppio Gioco",
        "La Talpa", "Zona Grigia", "Ultima Fermata", "Il Testimone",
        "Coinquilini", "Che Famiglia!", "Il Bar Sotto Casa", "Vacanze Forzate", "Due Cuori e un Garage",
        "Lunedì Nero", "Ufficio 404", "Gli Zii", "Il Matrimonio", "Pizza e Fichi",
    )
    private val movieNames = listOf(
        "Corsa Contro il Tempo", "Punto di Rottura", "L'Ultimo Colpo", "Fuoco Incrociato", "Senza Ritorno",
        "Missione Alba", "Il Corriere", "Tempesta d'Acciaio", "Zona Rossa", "Caccia all'Uomo",
        "Il Silenzio", "La Stanza 7", "Doppia Identità", "Nebbia", "Il Sospetto",
        "Linea d'Ombra", "La Chiave", "Sotto Pressione", "Il Ritorno", "Specchi",
        "Il Piccolo Drago", "Avventura tra le Stelle", "La Volpe e il Gufo", "Robottino", "Il Regno di Ghiaccio",
        "Pinguini in Fuga", "La Foresta Magica", "Super Nonno", "Il Gatto Pirata", "Viaggio sotto il Mare",
    )
    private val channelNames = listOf(
        "Rete Uno HD", "Rete Due HD", "Rete Tre", "Canale Cinque", "Italia Più", "Quarta Rete",
        "Cinema HD", "Serie Club", "Sport Uno", "Sport Due", "Calcio Live", "Motori TV",
        "News 24", "Meteo Italia", "Mondo News", "Economia TV",
    )
    private val plot = "Una storia avvincente tra segreti di famiglia, scelte difficili e colpi di scena. " +
        "Quando il passato torna a bussare, niente sarà più come prima."

    val titles = mutableMapOf<String, String>()

    val series: List<Series> = seriesNames.mapIndexed { i, n ->
        titles["s$i"] = n
        Series(
            id = 100 + i, name = n, image = "fake://poster/s$i", categoryId = "sc${i / 10}",
            rating = listOf("8.4", "7.9", "9.1", "7.2", "8.8")[i % 5], plot = plot,
            year = "${2016 + i % 9}", backdrop = "fake://backdrop/$i", lastModified = (1000L - i * 7) % 300,
        )
    }

    val movies: List<Movie> = movieNames.mapIndexed { i, n ->
        titles["m$i"] = n
        Movie(
            id = 500 + i, name = n, image = "fake://poster/m$i", categoryId = "mc${i / 10}",
            rating = listOf("7.6", "8.2", "6.9", "8.9", "7.4")[i % 5], ext = "mkv",
            added = (2000L - i * 13) % 500, backdrop = "fake://backdrop/${i + 30}", plot = plot,
            year = "${2015 + i % 10}",
        )
    }

    val live: List<LiveChannel> = channelNames.mapIndexed { i, n ->
        titles["c$i"] = n
        LiveChannel(id = 900 + i, num = i + 1, name = n, icon = "fake://logo/c$i", categoryId = "lc${i / 6}")
    }

    val catalog = Catalog(
        liveCategories = listOf(Category("lc0", "Generalisti"), Category("lc1", "Cinema e Sport"), Category("lc2", "Informazione")),
        live = live,
        movieCategories = listOf(Category("mc0", "Azione"), Category("mc1", "Thriller"), Category("mc2", "Animazione")),
        movies = movies,
        seriesCategories = listOf(Category("sc0", "Drammi"), Category("sc1", "Crime"), Category("sc2", "Commedie")),
        series = series,
    )

    private val episodeTitles = listOf(
        "Il ritorno", "Segreti", "La scelta", "Sotto copertura", "Il prezzo", "Fiducia", "Resa dei conti", "Nuovo inizio",
    )

    fun seriesDetail(s: Series) = SeriesDetail(
        plot = s.plot, backdrop = s.backdrop, genre = "Dramma, Mistero", cast = "Giulia Bianchi, Marco Neri, Sara Verdi",
        year = s.year, rating = s.rating,
        seasons = (1..3).associateWith { season ->
            (1..8).map { n ->
                Episode(
                    id = "$season$n", season = season, number = n, title = episodeTitles[n - 1], ext = "mkv",
                    plot = "Dopo gli eventi della scorsa puntata, una nuova pista rimette tutto in discussione.",
                    image = "fake://ep/${season * 10 + n}", durationSecs = 2700,
                )
            }
        },
    )

    val movieDetail = MovieDetail(
        plot = plot, backdrop = "fake://backdrop/34", genre = "Azione, Thriller",
        cast = "Luca Romano, Elena Conti", year = "2023", duration = "1h 52m", ext = "mkv",
    )

    // ---------- Grafica generata ----------

    fun bitmapFor(url: String): Bitmap? {
        if (!url.startsWith("fake://")) return null
        val parts = url.removePrefix("fake://").split("/")
        val kind = parts.getOrNull(0) ?: return null
        val id = parts.getOrNull(1) ?: return null
        return when (kind) {
            "poster" -> poster(titles[id] ?: id, hueFor(id))
            "backdrop" -> backdropBitmap(id.toIntOrNull() ?: 0, 960, 540)
            "ep" -> backdropBitmap(id.toIntOrNull() ?: 0, 480, 270)
            "logo" -> logo(titles[id] ?: id, hueFor(id))
            else -> null
        }
    }

    private fun hueFor(id: String) = ((id.hashCode() and 0x7fffffff) % 360).toFloat()

    private fun hsv(h: Float, s: Float, v: Float, a: Int = 255) =
        android.graphics.Color.HSVToColor(a, floatArrayOf(((h % 360) + 360) % 360, s, v))

    private fun poster(title: String, hue: Float): Bitmap {
        val w = 300
        val h = 450
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), hsv(hue, 0.75f, 0.6f), hsv(hue + 50, 0.9f, 0.12f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        p.color = hsv(hue + 180, 0.5f, 0.95f, 70)
        c.drawCircle(w * 0.72f, h * 0.32f, 110f, p)
        p.color = hsv(hue + 20, 0.4f, 1f, 50)
        c.drawCircle(w * 0.2f, h * 0.55f, 70f, p)

        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val lines = wrap(title.uppercase(), tp, w - 36f)
        var y = h - 30f - (lines.size - 1) * 44f
        for (line in lines) {
            c.drawText(line, 18f, y, tp)
            y += 44f
        }
        p.color = hsv(0f, 0.9f, 0.76f)
        c.drawRect(18f, 22f, 52f, 28f, p)
        return bmp
    }

    fun backdropBitmap(seed: Int, w: Int, h: Int): Bitmap {
        val hue = (seed * 47f) % 360
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), hsv(hue, 0.6f, 0.55f), hsv(hue + 60, 0.8f, 0.15f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        // "Montagne" stilizzate e un sole, per dare profondità all'immagine.
        p.color = hsv(hue + 30, 0.5f, 0.9f, 90)
        c.drawCircle(w * 0.72f, h * 0.35f, h * 0.22f, p)
        val path = android.graphics.Path().apply {
            moveTo(0f, h.toFloat())
            lineTo(w * 0.25f, h * 0.5f)
            lineTo(w * 0.45f, h * 0.72f)
            lineTo(w * 0.7f, h * 0.42f)
            lineTo(w.toFloat(), h * 0.68f)
            lineTo(w.toFloat(), h.toFloat())
            close()
        }
        p.color = hsv(hue + 200, 0.5f, 0.25f, 200)
        c.drawPath(path, p)
        return bmp
    }

    private fun logo(name: String, hue: Float): Bitmap {
        val w = 240
        val h = 135
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hsv(hue, 0.7f, 0.75f) }
        c.drawRoundRect(RectF(10f, 10f, w - 10f, h - 10f), 24f, 24f, p)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase()
        c.drawText(initials, w / 2f, h / 2f + 19f, tp)
        return bmp
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        for (word in text.split(" ")) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) current = candidate
            else {
                lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }
}
