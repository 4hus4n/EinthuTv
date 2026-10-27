package mv.ahusan.einthusantv

import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.Executors

/**
 * Searches einthusan.tv in every language at once and reports results per language.
 * Also holds a Jsoup version of the movie-card parser (same rules as the web version's api.php).
 */
object SiteSearch {

    private const val SITE = "https://einthusan.tv"
    private const val UA = "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    private val pool = Executors.newFixedThreadPool(8)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var token = 0

    fun cancel() { token++ }

    /**
     * Calls [onLang] on the main thread as each language finishes (with its matches, maybe empty),
     * then [onDone] once all languages are back.
     */
    fun search(query: String, onLang: (String, List<Movie>) -> Unit, onDone: (failed: Int) -> Unit) {
        val t = ++token
        val langs = VersionFinder.LANGS
        var left = langs.size
        var failed = 0
        langs.forEach { lang ->
            pool.execute {
                val result: List<Movie>? = try { searchOne(lang, query) } catch (e: Exception) { null }
                main.post {
                    if (t != token) return@post
                    if (result == null) failed++ else onLang(lang, result)
                    left--
                    if (left == 0) onDone(failed)
                }
            }
        }
    }

    private fun searchOne(lang: String, query: String): List<Movie> {
        val url = Uri.parse("$SITE/movie/results/").buildUpon()
            .appendQueryParameter("lang", lang)
            .appendQueryParameter("query", query)
            .build().toString()
        val doc = Jsoup.connect(url).userAgent(UA).header("Accept-Language", "en").timeout(15_000).get()
        val movies = parse(doc)
        // If none of the titles relate to the search, the site didn't treat it as a search: show nothing
        val words = normalize(query).split(' ').filter { it.length >= 2 }
        val related = movies.any { m -> val t = normalize(m.title); words.any { t.contains(it) } }
        return if (words.isEmpty() || related) movies else emptyList()
    }

    private fun normalize(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    // ---------- Movie card parser (Jsoup) ----------

    fun parse(doc: Document): List<Movie> {
        val out = mutableListOf<Movie>()
        for ((watchUrl, card) in cards(doc)) {
            val img = card.selectFirst("img[src*=moviecovers], img[data-src*=moviecovers], img[src*=default-img]")
                ?: card.selectFirst("img[src], img[data-src]") ?: continue
            val poster = img.absUrl("data-src").ifBlank { img.absUrl("src") }
            val title = (card.selectFirst("h3, h2, h4")?.text()?.takeIf { it.isNotBlank() }
                ?: card.select("a[href*=/movie/watch/]").firstOrNull { it.text().isNotBlank() }?.text()
                ?: img.attr("alt")).trim()
            if (title.isBlank()) continue

            val synopsis = card.select("p").map { it.text().trim() }.filter { it.length > 40 }.maxByOrNull { it.length } ?: ""
            val imdb = card.select("a[href*=imdb.com/title/]").firstNotNullOfOrNull {
                Regex("/title/(tt\\d+)").find(it.attr("href"))?.groupValues?.get(1)
            } ?: ""

            var director = ""; var lead = ""
            for (pi in card.select("img[src*=/prof/], img[data-src*=/prof/]")) {
                val src = pi.attr("src").ifBlank { pi.attr("data-src") }
                val id = Regex("/prof/([A-Za-z0-9]{4,20})-").find(src)?.groupValues?.get(1) ?: continue
                val box = pi.parent()?.parent() ?: pi
                val label = box.selectFirst("label")?.text()?.lowercase() ?: ""
                if (label.contains("music")) continue
                if (label.contains("director")) { if (director.isEmpty()) director = id }
                else if (lead.isEmpty()) lead = id
            }

            out.add(Movie(
                title = title,
                url = watchUrl,
                poster = poster,
                year = yearOf(card),
                synopsis = synopsis,
                subtitles = card.html().contains("subtitle", ignoreCase = true),
                imdb = imdb, director = director, lead = lead, leadRole = ""
            ))
        }
        return out
    }

    private fun cards(doc: Document): List<Pair<String, Element>> {
        fun key(e: Element) = e.absUrl("href").substringBefore('#').substringBefore('?')
        fun distinct(e: Element) = e.select("a[href*=/movie/watch/]").map { key(it) }.toSet().size
        val out = LinkedHashMap<String, Pair<String, Element>>()
        for (a in doc.select("a[href*=/movie/watch/]")) {
            val k = key(a)
            if (out.containsKey(k)) continue
            var card: Element = a
            while (true) {
                val p = card.parent() ?: break
                if (p.tagName() == "body" || distinct(p) != 1) break
                card = p
            }
            out[k] = a.absUrl("href") to card
        }
        return out.values.toList()
    }

    private fun yearOf(card: Element): String {
        for (t in card.textNodes() + card.select("*").flatMap { it.textNodes() }) {
            Regex("^\\s*((?:19|20)\\d{2})\\s*$").find(t.text())?.let { return it.groupValues[1] }
        }
        return Regex("(?:^|\\D)((?:19|20)\\d{2})(?!\\d)").find(card.text())?.groupValues?.get(1) ?: ""
    }
}
