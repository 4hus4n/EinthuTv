package mv.ahusan.einthusantv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Remembers where each movie was stopped, for "Continue Watching". Kept on the TV only. */
object WatchHistory {

    private const val PREFS = "einthutv"
    private const val KEY = "history"
    private const val MAX = 20

    data class Entry(
        val url: String,
        val title: String,
        val poster: String,
        val meta: String,
        val desc: String,
        val position: Long,   // seconds
        val duration: Long,   // seconds
        val updated: Long     // epoch ms
    ) {
        val left get() = (duration - position).coerceAtLeast(0)
        val fraction get() = if (duration > 0) position.toFloat() / duration else 0f
    }

    fun key(url: String) = url.substringBefore('#').substringBefore('?').trimEnd('/')

    fun all(ctx: Context): List<Entry> = read(ctx).sortedByDescending { it.updated }

    fun get(ctx: Context, url: String): Entry? = read(ctx).firstOrNull { key(it.url) == key(url) }

    /** Saves progress. Finished movies (last 3 min / 95%) are dropped from the list. */
    fun save(ctx: Context, e: Entry) {
        val list = read(ctx).filterNot { key(it.url) == key(e.url) }.toMutableList()
        val finished = e.duration > 0 && (e.left < 180 || e.fraction > 0.95f)
        if (!finished && e.position >= 30) list.add(0, e)
        write(ctx, list.sortedByDescending { it.updated }.take(MAX))
    }

    fun remove(ctx: Context, url: String) {
        write(ctx, read(ctx).filterNot { key(it.url) == key(url) })
    }

    fun fmtClock(sec: Long): String {
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
    }

    fun fmtLeft(sec: Long): String {
        val h = sec / 3600; val m = (sec % 3600) / 60
        return if (h > 0) "${h}h ${m}m left" else "${m.coerceAtLeast(1)}m left"
    }

    private fun read(ctx: Context): List<Entry> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Entry(
                    o.optString("url"), o.optString("title"), o.optString("poster"),
                    o.optString("meta"), o.optString("desc"),
                    o.optLong("pos"), o.optLong("dur"), o.optLong("upd")
                )
            }.filter { it.url.isNotBlank() }
        } catch (e: Exception) { emptyList() }
    }

    private fun write(ctx: Context, list: List<Entry>) {
        val a = JSONArray()
        list.forEach { e ->
            a.put(JSONObject()
                .put("url", e.url).put("title", e.title).put("poster", e.poster)
                .put("meta", e.meta).put("desc", e.desc)
                .put("pos", e.position).put("dur", e.duration).put("upd", e.updated))
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply()
    }
}
