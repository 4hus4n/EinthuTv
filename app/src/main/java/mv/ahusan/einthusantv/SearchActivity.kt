package mv.ahusan.einthusantv

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Search every language at once; results shown as one row per language. */
class SearchActivity : Activity() {

    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var rows: LinearLayout
    private var found = 0
    private var lastQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        input = findViewById(R.id.searchInput)
        status = findViewById(R.id.searchStatus)
        rows = findViewById(R.id.searchRows)

        val home = findViewById<Button>(R.id.btnHome)
        home.setOnClickListener { Nav.goHome(this) }
        Nav.stopUp(home)
        findViewById<Button>(R.id.btnSearch).setOnClickListener { run() }

        input.setOnEditorActionListener { _, action, ev ->
            val enter = ev?.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN
            if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE || enter) { run(); true } else false
        }

        getSharedPreferences("einthutv", MODE_PRIVATE).getString("last_search", "")?.let {
            if (it.isNotBlank()) { input.setText(it); input.setSelection(it.length) }
        }
        input.requestFocus()
        input.postDelayed({
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 300)
    }

    private fun run() {
        val q = input.text.toString().trim()
        if (q.length < 2) { status.visibility = View.VISIBLE; status.text = getString(R.string.search_too_short); return }
        lastQuery = q
        getSharedPreferences("einthutv", MODE_PRIVATE).edit().putString("last_search", q).apply()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)

        rows.removeAllViews()
        found = 0
        status.visibility = View.VISIBLE
        status.text = getString(R.string.search_running, q)

        SiteSearch.search(q, onLang = { lang, movies ->
            if (isFinishing || isDestroyed || movies.isEmpty()) return@search
            addRow(lang, movies)
        }, onDone = { failed ->
            if (isFinishing || isDestroyed) return@search
            status.text = when {
                found > 0 -> getString(R.string.search_done, found, q)
                failed == VersionFinder.LANGS.size -> getString(R.string.search_failed)
                else -> getString(R.string.search_none, q)
            }
        })
    }

    private fun addRow(lang: String, movies: List<Movie>) {
        val section = LayoutInflater.from(this).inflate(R.layout.row_section, rows, false)
        section.findViewById<TextView>(R.id.rowTitle).text =
            getString(R.string.search_row, Nav.langLabel(lang), movies.size)
        val strip = section.findViewById<LinearLayout>(R.id.rowItems)
        movies.forEach { m -> strip.addView(Nav.movieCard(this, strip, m, lang)) }
        // Keep rows in a fixed language order no matter which comes back first
        val order = VersionFinder.LANGS.indexOf(lang)
        section.tag = order
        var at = rows.childCount
        for (i in 0 until rows.childCount) {
            if (((rows.getChildAt(i).tag as? Int) ?: 99) > order) { at = i; break }
        }
        rows.addView(section, at)
        found += movies.size
        status.text = getString(R.string.search_progress, found, lastQuery)
    }

    override fun onDestroy() {
        SiteSearch.cancel()
        super.onDestroy()
    }
}
