package mv.ahusan.einthusantv

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Poster grid for any einthusan results list (year, decade, A–Z, trending…), page by page. */
class GridActivity : Activity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        private const val COLUMNS = 5
        private const val SITE_PAGES_PER_SCREEN = 2
    }

    private lateinit var loader: CatalogLoader
    private lateinit var grid: LinearLayout
    private lateinit var status: TextView
    private lateinit var pageLabel: TextView
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button
    private lateinit var scroll: ScrollView

    private lateinit var baseUrl: String
    private lateinit var lang: String
    private var page = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid)

        baseUrl = intent.getStringExtra(EXTRA_URL) ?: Nav.yearUrl("malayalam", 2026)
        val uri = Uri.parse(baseUrl)
        lang = uri.getQueryParameter("lang") ?: "malayalam"
        val sitePage = uri.getQueryParameter("page")?.toIntOrNull() ?: 1
        page = ((sitePage - 1) / SITE_PAGES_PER_SCREEN + 1).coerceAtLeast(1)

        loader = CatalogLoader(findViewById<WebView>(R.id.loaderWebView))
        grid = findViewById(R.id.grid)
        status = findViewById(R.id.status)
        pageLabel = findViewById(R.id.pageLabel)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        scroll = findViewById(R.id.scroll)

        findViewById<TextView>(R.id.gridTitle).text = intent.getStringExtra(EXTRA_TITLE) ?: Nav.titleFor(baseUrl)
        findViewById<Button>(R.id.btnHome).setOnClickListener { Nav.goHome(this) }
        findViewById<Button>(R.id.btnBrowse).setOnClickListener { Nav.openBrowse(this) }
        Nav.stopUp(findViewById(R.id.btnHome), findViewById(R.id.btnBrowse))
        btnPrev.setOnClickListener { if (page > 1) { page--; loadPage() } }
        btnNext.setOnClickListener { page++; loadPage() }
        status.setOnClickListener { loadPage() }

        loadPage()
    }

    private fun loadPage() {
        loader.cancelAll()
        grid.removeAllViews()
        scroll.scrollTo(0, 0)
        pageLabel.text = getString(R.string.page_n, page)
        btnPrev.isEnabled = page > 1
        btnPrev.alpha = if (page > 1) 1f else 0.4f
        btnNext.isEnabled = false
        btnNext.alpha = 0.4f
        status.visibility = View.VISIBLE
        status.text = getString(R.string.loading)

        val all = mutableListOf<Movie>()
        val first = (page - 1) * SITE_PAGES_PER_SCREEN + 1
        val last = first + SITE_PAGES_PER_SCREEN - 1
        for (p in first..last) {
            loader.load(Nav.withPage(baseUrl, p)) { movies ->
                val fresh = movies.filter { m -> all.none { it.url == m.url } }
                all.addAll(fresh)
                fresh.forEach { addToGrid(it) }
                if (p == last) done(all.size)
            }
        }
    }

    private fun addToGrid(m: Movie) {
        var row = if (grid.childCount > 0) grid.getChildAt(grid.childCount - 1) as LinearLayout else null
        if (row == null || row.childCount >= COLUMNS) {
            row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                setPadding(0, dp(10), 0, dp(10))
            }
            grid.addView(row)
        }
        val card = Nav.movieCard(this, row, m, lang)
        row.addView(card)
        if (grid.childCount == 1 && row.childCount == 1) card.requestFocus()
    }

    private fun done(count: Int) {
        if (count == 0) {
            status.visibility = View.VISIBLE
            status.text = getString(if (page == 1) R.string.grid_empty else R.string.grid_end)
            if (page > 1) btnPrev.requestFocus() else status.requestFocus()
        } else {
            status.visibility = View.GONE
            btnNext.isEnabled = true
            btnNext.alpha = 1f
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        loader.cancelAll()
        super.onDestroy()
    }
}
