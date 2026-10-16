package mv.ahusan.einthusantv

import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Finds the same film in other languages (dubbed versions).
 * Looks at the director's (or lead actor's) movie list in every other language
 * and matches by IMDb id, or by release year when there is no IMDb id.
 */
object VersionFinder {

    val LANGS = listOf("malayalam", "tamil", "hindi", "telugu", "kannada", "bengali", "marathi", "punjabi")

    private const val SITE = "https://einthusan.tv"
    private const val UA = "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    private val pool = Executors.newFixedThreadPool(7)
    private val main = Handler(Looper.getMainLooper())
    private val cache = ConcurrentHashMap<String, List<Version>>()

    fun canSearch(m: Movie) = (m.director.isNotBlank() || m.lead.isNotBlank()) &&
        (m.imdb.isNotBlank() || m.year.isNotBlank())

    fun find(m: Movie, lang: String, callback: (List<Version>?) -> Unit) {
        val key = "$lang|${m.url}"
        cache[key]?.let { callback(it); return }
        if (!canSearch(m)) { callback(emptyList()); return }

        val person = m.director.ifBlank { m.lead }
        val role = if (m.director.isNotBlank()) "DIRECTOR" else m.leadRole

        Thread {
            val result = try {
                val tasks = LANGS.filter { it != lang }.map { other ->
                    Callable { search(other, person, role, m) }
                }
                pool.invokeAll(tasks, 30, TimeUnit.SECONDS)
                    .mapNotNull { f -> try { f.get() } catch (e: Exception) { null } }
                    .sortedBy { LANGS.indexOf(it.lang) }
            } catch (e: Exception) {
                null
            }
            if (result != null) cache[key] = result
            main.post { callback(result) }
        }.start()
    }

    private fun search(lang: String, person: String, role: String, m: Movie): Version? {
        val url = Uri.parse("$SITE/movie/results/").buildUpon()
            .appendQueryParameter("find", "Cast")
            .appendQueryParameter("id", person)
            .appendQueryParameter("lang", lang)
            .appendQueryParameter("role", role)
            .build().toString()
        val doc = Jsoup.connect(url)
            .userAgent(UA)
            .header("Accept-Language", "en")
            .timeout(15_000)
            .get()

        for ((watchUrl, card) in cards(doc)) {
            if (watchUrl.substringBefore('?') == m.url.substringBefore('?')) continue
            val imdb = card.select("a[href*=imdb.com/title/]").firstNotNullOfOrNull {
                Regex("/title/(tt\\d+)").find(it.attr("href"))?.groupValues?.get(1)
            } ?: ""
            val year = yearOf(card)
            val match = if (m.imdb.isNotBlank() && imdb.isNotBlank()) imdb == m.imdb
            else m.year.isNotBlank() && year == m.year
            if (!match) continue

            val img = card.selectFirst("img[src*=moviecovers], img[data-src*=moviecovers], img[src*=default-img]")
                ?: card.selectFirst("img[src], img[data-src]")
            val poster = img?.let { it.absUrl("data-src").ifBlank { it.absUrl("src") } } ?: ""
            val title = (card.selectFirst("h3, h2, h4")?.text()
                ?: card.select("a[href*=/movie/watch/]").firstOrNull { it.text().isNotBlank() }?.text()
                ?: img?.attr("alt") ?: "").trim()
            return Version(lang, title, watchUrl, poster)
        }
        return null
    }

    /** One element per movie: the largest element that links to only that movie. */
    private fun cards(doc: org.jsoup.nodes.Document): List<Pair<String, Element>> {
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
