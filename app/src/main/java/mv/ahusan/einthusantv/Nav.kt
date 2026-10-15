package mv.ahusan.einthusantv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

/** Shared navigation and card building for the native screens. */
object Nav {

    const val BASE = "https://einthusan.tv/movie/results/"

    fun langLabel(lang: String) = lang.replaceFirstChar { it.uppercase() }

    fun resultsUrl(lang: String, vararg params: Pair<String, String>): String {
        val b = Uri.parse(BASE).buildUpon()
        params.forEach { b.appendQueryParameter(it.first, it.second) }
        b.appendQueryParameter("lang", lang)
        return b.build().toString()
    }

    fun yearUrl(lang: String, year: Int) = resultsUrl(lang, "find" to "Year", "year" to year.toString())
    fun recentUrl(lang: String) = resultsUrl(lang, "find" to "Recent")
    fun trendingUrl(lang: String) = resultsUrl(lang, "find" to "Popularity", "ptype" to "view", "tp" to "l30d")
    fun staffPicksUrl(lang: String) = resultsUrl(lang, "find" to "StaffPick")

    /** Adds or replaces the page number in a results URL. */
    fun withPage(url: String, page: Int): String {
        val u = Uri.parse(url)
        val b = u.buildUpon().clearQuery()
        u.queryParameterNames.filter { it != "page" }.forEach { n ->
            u.getQueryParameters(n).forEach { v -> b.appendQueryParameter(n, v) }
        }
        b.appendQueryParameter("page", page.toString())
        return b.build().toString()
    }

    /** A readable title for any einthusan results URL. */
    fun titleFor(url: String): String {
        val u = Uri.parse(url)
        val lang = langLabel(u.getQueryParameter("lang") ?: "")
        return when (u.getQueryParameter("find")) {
            "Year" -> "$lang ${u.getQueryParameter("year") ?: ""}"
            "Decade" -> "$lang ${u.getQueryParameter("decade") ?: ""}s"
            "Alphabets" -> "$lang · ${u.getQueryParameter("alpha") ?: ""}"
            "Numbers" -> "$lang · 0–9"
            "Recent" -> "Recently Added · $lang"
            "StaffPick" -> "Staff Picks · $lang"
            "RegionalHit" -> "Regional Hits · $lang"
            "ComingSoon" -> "Coming Soon · $lang"
            "Popularity" -> when (u.getQueryParameter("ptype")?.lowercase()) {
                "rating" -> "Top Rated · $lang"
                "comment" -> "Most Discussed · $lang"
                else -> if (u.getQueryParameter("tp") == "alltime") "Most Watched · $lang" else "Trending · $lang"
            }
            "Cast" -> "Cast & Crew · $lang"
            else -> "$lang Movies"
        }
    }

    fun isResults(uri: Uri) = uri.path?.startsWith("/movie/results") == true
    fun isBrowse(uri: Uri) = uri.path?.startsWith("/movie/browse") == true

    fun openWatch(ctx: Context, url: String) {
        ctx.startActivity(Intent(ctx, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_URL, url))
    }

    /** Opens a movie's watch page with its info for the loading screen. */
    fun openMovie(ctx: Context, m: Movie, lang: String) {
        ctx.startActivity(
            Intent(ctx, BrowserActivity::class.java)
                .putExtra(BrowserActivity.EXTRA_URL, m.url)
                .putExtra(BrowserActivity.EXTRA_TITLE, m.title)
                .putExtra(BrowserActivity.EXTRA_POSTER, m.poster)
                .putExtra(BrowserActivity.EXTRA_DESC, m.synopsis)
                .putExtra(
                    BrowserActivity.EXTRA_META,
                    listOf(m.year, langLabel(lang)).filter { it.isNotBlank() }.joinToString("  •  ")
                )
        )
    }

    fun openGrid(ctx: Context, url: String, title: String = titleFor(url)) {
        ctx.startActivity(
            Intent(ctx, GridActivity::class.java)
                .putExtra(GridActivity.EXTRA_URL, url)
                .putExtra(GridActivity.EXTRA_TITLE, title)
        )
    }

    fun openBrowse(ctx: Context) {
        ctx.startActivity(Intent(ctx, BrowseActivity::class.java))
    }

    fun goHome(act: Activity) {
        act.startActivity(
            Intent(act, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        act.finish()
    }

    // ---------- Cards ----------

    fun focusScale(v: View) {
        val old = v.onFocusChangeListener
        v.setOnFocusChangeListener { view, has ->
            view.animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f).setDuration(150).start()
            view.elevation = if (has) 12f else 0f
            old?.onFocusChange(view, has)
        }
    }

    fun movieCard(
        act: Activity,
        parent: ViewGroup,
        m: Movie,
        lang: String,
        onFocus: ((Movie) -> Unit)? = null
    ): View {
        val v = LayoutInflater.from(act).inflate(R.layout.item_card, parent, false)
        v.findViewById<TextView>(R.id.cardTitle).text = m.title
        v.findViewById<TextView>(R.id.cardMeta).text =
            listOf(m.year, langLabel(lang)).filter { it.isNotBlank() }.joinToString("  •  ")
        v.findViewById<TextView>(R.id.cardBadges).text = if (m.subtitles) "HD   CC" else "HD"
        ImageLoader.load(m.poster, v.findViewById<ImageView>(R.id.cardPoster))
        v.setOnClickListener { openMovie(act, m, lang) }
        if (onFocus != null) v.setOnFocusChangeListener { _, has -> if (has) onFocus(m) }
        focusScale(v)
        return v
    }

    fun seeAllCard(act: Activity, parent: ViewGroup, label: String, onClick: () -> Unit): View {
        val v = LayoutInflater.from(act).inflate(R.layout.item_see_all, parent, false)
        v.findViewById<TextView>(R.id.seeAllText).text = label
        v.setOnClickListener { onClick() }
        focusScale(v)
        return v
    }
}
