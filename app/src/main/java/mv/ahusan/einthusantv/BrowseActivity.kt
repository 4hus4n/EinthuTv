package mv.ahusan.einthusantv

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar

/** Native browse screen: curated lists, decades, years and A–Z for the chosen language. */
class BrowseActivity : Activity() {

    private val languages = listOf(
        "Malayalam", "Tamil", "Hindi", "Telugu",
        "Kannada", "Bengali", "Marathi", "Punjabi"
    )
    private val thisYear = Calendar.getInstance().get(Calendar.YEAR)

    private lateinit var sections: LinearLayout
    private lateinit var btnLang: Button
    private var lang = "malayalam"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browse)

        lang = getSharedPreferences("einthutv", MODE_PRIVATE).getString("lang", "malayalam") ?: "malayalam"
        sections = findViewById(R.id.sections)
        btnLang = findViewById(R.id.btnLang)

        findViewById<Button>(R.id.btnHome).setOnClickListener { Nav.goHome(this) }
        findViewById<Button>(R.id.btnSearch).setOnClickListener { Nav.openSearch(this) }
        Nav.stopUp(findViewById(R.id.btnHome), findViewById(R.id.btnSearch))
        btnLang.setOnClickListener { pickLanguage() }

        build()
    }

    private fun build() {
        sections.removeAllViews()
        btnLang.text = getString(R.string.filter_language, Nav.langLabel(lang))

        section(getString(R.string.sec_lists), listOf(
            getString(R.string.list_recent) to Nav.recentUrl(lang),
            getString(R.string.list_trending) to Nav.trendingUrl(lang),
            getString(R.string.list_most_watched) to Nav.resultsUrl(lang, "find" to "Popularity", "ptype" to "view", "tp" to "alltime"),
            getString(R.string.list_top_rated) to Nav.resultsUrl(lang, "find" to "Popularity", "ptype" to "rating", "tp" to "alltime"),
            getString(R.string.list_staff) to Nav.staffPicksUrl(lang),
            getString(R.string.list_regional) to Nav.resultsUrl(lang, "find" to "RegionalHit"),
            getString(R.string.list_coming) to Nav.resultsUrl(lang, "find" to "ComingSoon")
        ))

        section(getString(R.string.sec_years), (thisYear downTo 1961).map {
            it.toString() to Nav.yearUrl(lang, it)
        })

        section(getString(R.string.sec_decades), listOf(2020, 2010, 2000, 1990, 1980, 1970, 1960).map {
            "${it}s" to Nav.resultsUrl(lang, "find" to "Decade", "decade" to it.toString())
        })

        section(getString(R.string.sec_az),
            listOf("0–9" to Nav.resultsUrl(lang, "find" to "Numbers")) +
                ('A'..'Z').map { c -> c.toString() to Nav.resultsUrl(lang, "find" to "Alphabets", "alpha" to c.toString()) }
        )

        // Search and cast lists stay on the website
        val web = LayoutInflater.from(this).inflate(R.layout.row_chips, sections, false)
        web.findViewById<TextView>(R.id.chipsTitle).text = getString(R.string.sec_more)
        val strip = web.findViewById<LinearLayout>(R.id.chips)
        strip.addView(chip(strip, getString(R.string.search_on_site)) {
            Nav.openWatch(this, "https://einthusan.tv/movie/browse/?lang=$lang")
        })
        sections.addView(web)
    }

    private fun section(title: String, items: List<Pair<String, String>>) {
        val v = LayoutInflater.from(this).inflate(R.layout.row_chips, sections, false)
        v.findViewById<TextView>(R.id.chipsTitle).text = title
        val strip = v.findViewById<LinearLayout>(R.id.chips)
        items.forEach { (label, url) ->
            strip.addView(chip(strip, label) { Nav.openGrid(this, url) })
        }
        sections.addView(v)
        if (sections.childCount == 1) strip.getChildAt(0)?.requestFocus()
    }

    private fun chip(parent: LinearLayout, label: String, onClick: () -> Unit): Button {
        val b = LayoutInflater.from(this).inflate(R.layout.item_chip, parent, false) as Button
        b.text = label
        b.setOnClickListener { onClick() }
        return b
    }

    private fun pickLanguage() {
        val idx = languages.indexOfFirst { it.equals(lang, true) }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.choose_language)
            .setSingleChoiceItems(languages.toTypedArray(), idx) { d, which ->
                lang = languages[which].lowercase()
                getSharedPreferences("einthutv", MODE_PRIVATE).edit().putString("lang", lang).apply()
                d.dismiss()
                build()
            }
            .show()
    }
}
