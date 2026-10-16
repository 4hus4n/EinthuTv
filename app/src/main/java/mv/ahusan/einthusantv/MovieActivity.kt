package mv.ahusan.einthusantv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Movie details: Watch in this language, plus buttons for other-language versions. */
class MovieActivity : Activity() {

    companion object {
        const val EXTRA_MOVIE = "movie"
        const val EXTRA_LANG = "lang"
    }

    private lateinit var movie: Movie
    private lateinit var lang: String
    private lateinit var versionsBox: LinearLayout
    private lateinit var versionsStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_movie)

        @Suppress("DEPRECATION")
        movie = intent.getSerializableExtra(EXTRA_MOVIE) as? Movie ?: run { finish(); return }
        lang = intent.getStringExtra(EXTRA_LANG) ?: "malayalam"

        findViewById<TextView>(R.id.movieTitle).text = movie.title
        findViewById<TextView>(R.id.movieMeta).text = meta(movie.year, lang)
        findViewById<TextView>(R.id.movieBadges).text = if (movie.subtitles) " HD   CC " else " HD "
        findViewById<TextView>(R.id.movieDesc).text = movie.synopsis
        ImageLoader.load(movie.poster, findViewById<ImageView>(R.id.moviePoster))
        ImageLoader.load(movie.poster, findViewById<ImageView>(R.id.movieBg))

        val watch = findViewById<Button>(R.id.btnWatch)
        watch.text = getString(R.string.watch_in, Nav.langLabel(lang))
        watch.setOnClickListener { play(movie.url, movie.title, movie.poster, lang) }
        findViewById<Button>(R.id.btnHome).setOnClickListener { Nav.goHome(this) }
        watch.requestFocus()

        versionsBox = findViewById(R.id.versions)
        versionsStatus = findViewById(R.id.versionsStatus)
        loadVersions()
    }

    private fun loadVersions() {
        val section = findViewById<View>(R.id.versionsSection)
        if (!VersionFinder.canSearch(movie)) { section.visibility = View.GONE; return }
        versionsStatus.text = getString(R.string.versions_checking)
        versionsStatus.visibility = View.VISIBLE

        VersionFinder.find(movie, lang) { list ->
            if (isFinishing || isDestroyed) return@find
            versionsBox.removeAllViews()
            when {
                list == null -> versionsStatus.text = getString(R.string.versions_failed)
                list.isEmpty() -> versionsStatus.text = getString(R.string.versions_none, Nav.langLabel(lang))
                else -> {
                    versionsStatus.visibility = View.GONE
                    list.forEach { v -> versionsBox.addView(langButton(v)) }
                }
            }
        }
    }

    private fun langButton(v: Version): View {
        val b = LayoutInflater.from(this).inflate(R.layout.item_lang_button, versionsBox, false) as Button
        val differentTitle = v.title.isNotBlank() && !v.title.equals(movie.title, ignoreCase = true)
        b.text = if (differentTitle) "▶  ${Nav.langLabel(v.lang)}  ·  ${v.title}" else "▶  ${Nav.langLabel(v.lang)}"
        b.setOnClickListener { play(v.url, v.title.ifBlank { movie.title }, v.poster.ifBlank { movie.poster }, v.lang) }
        return b
    }

    private fun play(url: String, title: String, poster: String, l: String) {
        startActivity(
            Intent(this, BrowserActivity::class.java)
                .putExtra(BrowserActivity.EXTRA_URL, url)
                .putExtra(BrowserActivity.EXTRA_TITLE, title)
                .putExtra(BrowserActivity.EXTRA_POSTER, poster)
                .putExtra(BrowserActivity.EXTRA_DESC, movie.synopsis)
                .putExtra(BrowserActivity.EXTRA_META, meta(movie.year, l))
        )
    }

    private fun meta(year: String, l: String) =
        listOf(year, Nav.langLabel(l)).filter { it.isNotBlank() }.joinToString("  •  ")
}
