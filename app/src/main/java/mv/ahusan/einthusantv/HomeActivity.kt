package mv.ahusan.einthusantv

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import android.widget.Button
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
        findViewById<Button>(R.id.tabMovies).setOnClickListener { Nav.openGrid(this, Nav.yearUrl(lang, year)) }
        findViewById<Button>(R.id.tabBrowse).setOnClickListener { Nav.openBrowse(this) }
        findViewById<Button>(R.id.tabAccount).setOnClickListener { Nav.openWatch(this, "https://einthusan.tv/account/") }

        btnLang.setOnClickListener { pickLanguage() }
        btnYear.setOnClickListener { pickYear() }
        btnPlay.setOnClickListener { heroMovie?.let { Nav.openWatch(this, it.url) } }
        btnSeeAll.setOnClickListener { Nav.openGrid(this, Nav.yearUrl(lang, year)) }
        status.setOnClickListener { reload() }

        reload()
        btnPlay.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        // Language may have been changed on the Browse screen
        val saved = getSharedPreferences("einthutv", MODE_PRIVATE).getString("lang", lang) ?: lang
        if (saved != lang) { lang = saved; reload() }
    }

    // ---------- Data ----------

    private fun reload() {
        loader.cancelAll()
        rows.removeAllViews()
        heroMovie = null
        val label = Nav.langLabel(lang)
        btnLang.text = getString(R.string.filter_language, label)
        btnYear.text = getString(R.string.filter_year, year.toString())
        heroTitle.text = getString(R.string.loading)
        heroMeta.text = ""
        heroBadges.visibility = View.GONE
        heroDesc.text = ""
        heroPoster.setImageDrawable(null)
        heroBg.setImageDrawable(null)
        status.visibility = View.GONE

        addRow(getString(R.string.row_new, label, year.toString()), Nav.yearUrl(lang, year), pages = 3, isMain = true)
        addRow(getString(R.string.row_trending, label), Nav.trendingUrl(lang), pages = 2, isMain = false)
        addRow(getString(R.string.row_recent), Nav.recentUrl(lang), pages = 2, isMain = false)
        addRow(getString(R.string.row_staff), Nav.staffPicksUrl(lang), pages = 1, isMain = false)
    }

    private fun addRow(title: String, url: String, pages: Int, isMain: Boolean) {
        val section = LayoutInflater.from(this).inflate(R.layout.row_section, rows, false)
        section.findViewById<TextView>(R.id.rowTitle).text = title
        val strip = section.findViewById<LinearLayout>(R.id.rowItems)
        rows.addView(section)

        val seen = HashSet<String>()
        for (p in 1..pages) {
            loader.load(Nav.withPage(url, p)) { movies ->
                movies.filter { seen.add(it.url) }.forEach { m ->
                    strip.addView(Nav.movieCard(this, strip, m, lang) { showHero(it) })
                    if (isMain && heroMovie == null) showHero(m)
                }
                if (p == pages) {
                    if (seen.isEmpty()) {
                        if (isMain) {
                            status.visibility = View.VISIBLE
                            status.text = getString(R.string.load_failed)
                            heroTitle.text = getString(R.string.no_movies)
                        } else {
                            section.visibility = View.GONE
                        }
                    }
                    if (seen.isNotEmpty()) {
                        strip.addView(Nav.seeAllCard(this, strip, getString(R.string.see_all_arrow)) {
                            Nav.openGrid(this, url, title)
                        })
                    }
                }
            }
        }
    }

    private fun showHero(m: Movie) {
        heroMovie = m
        heroTitle.text = m.title
        heroMeta.text = listOf(m.year, Nav.langLabel(lang)).filter { it.isNotBlank() }.joinToString("  •  ")
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
        val years = (thisYear downTo 1961).map { it.toString() }
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

    override fun onDestroy() {
        loader.cancelAll()
        super.onDestroy()
    }
}
