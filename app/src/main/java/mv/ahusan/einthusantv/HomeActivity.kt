package mv.ahusan.einthusantv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar

class HomeActivity : Activity() {

    private val languages = listOf(
        "Malayalam", "Tamil", "Hindi", "Telugu",
        "Kannada", "Bengali", "Marathi", "Punjabi"
    )
    private val thisYear = Calendar.getInstance().get(Calendar.YEAR)

    private lateinit var loader: CatalogLoader
    private lateinit var btnLang: Button
    private lateinit var btnYear: Button
    private lateinit var rows: LinearLayout
    private lateinit var status: TextView

    private lateinit var heroBg: ImageView
    private lateinit var heroPoster: ImageView
    private lateinit var heroTitle: TextView
    private lateinit var heroMeta: TextView
    private lateinit var heroBadges: TextView
    private lateinit var heroDesc: TextView
    private lateinit var btnPlay: Button
    private lateinit var btnSeeAll: Button

    private var lang = "malayalam"
    private var year = thisYear
    private var heroMovie: Movie? = null
    private var firstCard: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val prefs = getSharedPreferences("einthutv", MODE_PRIVATE)
        lang = prefs.getString("lang", "malayalam") ?: "malayalam"
        year = prefs.getInt("year", 2026)

        loader = CatalogLoader(findViewById<WebView>(R.id.loaderWebView))
        btnLang = findViewById(R.id.btnLang)
        btnYear = findViewById(R.id.btnYear)
        rows = findViewById(R.id.rows)
        status = findViewById(R.id.status)
        heroBg = findViewById(R.id.heroBg)
        heroPoster = findViewById(R.id.heroPoster)
        heroTitle = findViewById(R.id.heroTitle)
        heroMeta = findViewById(R.id.heroMeta)
        heroBadges = findViewById(R.id.heroBadges)
        heroDesc = findViewById(R.id.heroDesc)
        btnPlay = findViewById(R.id.btnPlay)
        btnSeeAll = findViewById(R.id.btnSeeAll)

        findViewById<Button>(R.id.tabHome).setOnClickListener { reload() }
        findViewById<Button>(R.id.tabMovies).setOnClickListener { open(resultsUrl(year, 1)) }
        findViewById<Button>(R.id.tabBrowse).setOnClickListener { open("https://einthusan.tv/movie/browse/?lang=$lang") }
        findViewById<Button>(R.id.tabAccount).setOnClickListener { open("https://einthusan.tv/account/") }

        btnLang.setOnClickListener { pickLanguage() }
        btnYear.setOnClickListener { pickYear() }
        btnPlay.setOnClickListener { heroMovie?.let { open(it.url) } }
        btnSeeAll.setOnClickListener { open(resultsUrl(year, 1)) }
        status.setOnClickListener { reload() }

        reload()
        btnPlay.requestFocus()
    }

    // ---------- Data ----------

    private fun resultsUrl(y: Int, page: Int) =
        "https://einthusan.tv/movie/results/?find=Year&lang=$lang&page=$page&year=$y"

    private fun langLabel() = lang.replaceFirstChar { it.uppercase() }

    private fun reload() {
        loader.cancelAll()
        rows.removeAllViews()
        heroMovie = null
        firstCard = null
        btnLang.text = getString(R.string.filter_language, langLabel())
        btnYear.text = getString(R.string.filter_year, year.toString())
        heroTitle.text = getString(R.string.loading)
        heroMeta.text = ""
        heroBadges.visibility = View.GONE
        heroDesc.text = ""
        heroPoster.setImageDrawable(null)
        heroBg.setImageDrawable(null)
        status.visibility = View.GONE

        addRow(getString(R.string.row_new, langLabel(), year.toString()), year, pages = 3, isMain = true)
        if (year - 1 >= 1950) addRow(getString(R.string.row_year, year - 1), year - 1, pages = 2, isMain = false)
        if (year - 2 >= 1950) addRow(getString(R.string.row_year, year - 2), year - 2, pages = 2, isMain = false)
    }

    private fun addRow(title: String, y: Int, pages: Int, isMain: Boolean) {
        val section = LayoutInflater.from(this).inflate(R.layout.row_section, rows, false)
        section.findViewById<TextView>(R.id.rowTitle).text = title
        val strip = section.findViewById<LinearLayout>(R.id.rowItems)
        val scroller = section.findViewById<HorizontalScrollView>(R.id.rowScroll)
        rows.addView(section)

        var count = 0
        for (p in 1..pages) {
            loader.load(resultsUrl(y, p)) { movies ->
                movies.forEach { m ->
                    strip.addView(makeCard(m))
                    count++
                    if (isMain && heroMovie == null) showHero(m)
                }
                if (p == pages) {
                    if (count == 0) {
                        if (isMain) {
                            status.visibility = View.VISIBLE
                            status.text = getString(R.string.load_failed)
                            heroTitle.text = getString(R.string.no_movies)
                        }
                        section.visibility = if (isMain) View.VISIBLE else View.GONE
                    }
                    strip.addView(makeSeeAllCard(y))
                }
            }
        }
        scroller.isFocusable = false
    }

    // ---------- Cards ----------

    private fun makeCard(m: Movie): View {
        val v = LayoutInflater.from(this).inflate(R.layout.item_card, rows, false)
        val poster = v.findViewById<ImageView>(R.id.cardPoster)
        v.findViewById<TextView>(R.id.cardTitle).text = m.title
        v.findViewById<TextView>(R.id.cardMeta).text = listOf(m.year, langLabel()).filter { it.isNotBlank() }.joinToString("  •  ")
        v.findViewById<TextView>(R.id.cardBadges).text = if (m.subtitles) "HD   CC" else "HD"
        ImageLoader.load(m.poster, poster)
        v.setOnClickListener { open(m.url) }
        v.setOnFocusChangeListener { view, has ->
            view.animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f).setDuration(150).start()
            view.elevation = if (has) 12f else 0f
            if (has) showHero(m)
        }
        if (firstCard == null) firstCard = v
        return v
    }

    private fun makeSeeAllCard(y: Int): View {
        val v = LayoutInflater.from(this).inflate(R.layout.item_see_all, rows, false)
        v.findViewById<TextView>(R.id.seeAllText).text = getString(R.string.see_all_year, y)
        v.setOnClickListener { open(resultsUrl(y, 1)) }
        v.setOnFocusChangeListener { view, has ->
            view.animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f).setDuration(150).start()
        }
        return v
    }

    private fun showHero(m: Movie) {
        heroMovie = m
        heroTitle.text = m.title
        heroMeta.text = listOf(m.year, langLabel()).filter { it.isNotBlank() }.joinToString("  •  ")
        heroBadges.visibility = View.VISIBLE
        heroBadges.text = if (m.subtitles) " HD   CC " else " HD "
        heroDesc.text = m.synopsis
        ImageLoader.load(m.poster, heroPoster)
        ImageLoader.load(m.poster, heroBg)
    }

    // ---------- Pickers ----------

    private fun pickLanguage() {
        val idx = languages.indexOfFirst { it.equals(lang, true) }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.choose_language)
            .setSingleChoiceItems(languages.toTypedArray(), idx) { d, which ->
                lang = languages[which].lowercase()
                save(); d.dismiss(); reload()
            }
            .show()
    }

    private fun pickYear() {
        val years = (thisYear downTo 1950).map { it.toString() }
        val idx = years.indexOf(year.toString())
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.choose_year)
            .setSingleChoiceItems(years.toTypedArray(), idx) { d, which ->
                year = years[which].toInt()
                save(); d.dismiss(); reload()
            }
            .show()
    }

    private fun save() {
        getSharedPreferences("einthutv", MODE_PRIVATE).edit()
            .putString("lang", lang).putInt("year", year).apply()
    }

    private fun open(url: String) {
        startActivity(Intent(this, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_URL, url))
    }

    override fun onDestroy() {
        loader.cancelAll()
        super.onDestroy()
    }
}
