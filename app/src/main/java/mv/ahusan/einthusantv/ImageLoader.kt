package mv.ahusan.einthusantv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Tiny poster loader with an in-memory cache. */
object ImageLoader {

    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val pool = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())

    fun load(url: String, target: ImageView, maxWidth: Int = 400) {
        target.tag = url
        if (url.isBlank()) { target.setImageDrawable(null); return }
        cache.get(url)?.let { target.setImageBitmap(it); return }
        target.setImageDrawable(null)
        pool.execute {
            val bmp = fetch(url, maxWidth)
            if (bmp != null) cache.put(url, bmp)
            main.post { if (target.tag == url && bmp != null) target.setImageBitmap(bmp) }
        }
    }

    private fun fetch(url: String, maxWidth: Int): Bitmap? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Referer", "https://einthusan.tv/")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 Chrome/120 Safari/537.36")
        }
        val bytes = conn.inputStream.use { it.readBytes() }
        conn.disconnect()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Exception) {
        null
    }
}
