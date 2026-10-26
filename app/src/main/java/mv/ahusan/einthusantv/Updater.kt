package mv.ahusan.einthusantv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Checks GitHub Releases for a newer APK, downloads it and opens the installer. */
object Updater {

    private const val API = "https://api.github.com/repos/4hus4n/EinthuTv/releases/latest"
    private val main = Handler(Looper.getMainLooper())

    data class Release(val tag: String, val code: Int, val apkUrl: String)

    fun installedCode(ctx: Context): Int {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt()
        else @Suppress("DEPRECATION") info.versionCode
    }

    fun installedName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    fun check(callback: (Release?, String?) -> Unit) {
        Thread {
            try {
                val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "EinthuTv-Updater")
                }
                val code = conn.responseCode
                if (code != 200) throw Exception("GitHub replied $code")
                val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                conn.disconnect()
                val tag = o.optString("tag_name")
                val num = tag.substringAfterLast('.').toIntOrNull() ?: throw Exception("Unknown version $tag")
                val assets = o.optJSONArray("assets")
                var apk = ""
                if (assets != null) for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break }
                }
                if (apk.isBlank()) throw Exception("No APK in release $tag")
                val r = Release(tag, num, apk)
                main.post { callback(r, null) }
            } catch (e: Exception) {
                main.post { callback(null, e.message ?: "Network error") }
            }
        }.start()
    }

    fun download(ctx: Context, url: String, progress: (Int) -> Unit, done: (File?, String?) -> Unit) {
        Thread {
            try {
                val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, "EinthuTv-update.apk")
                var link = url
                var conn: HttpURLConnection
                var hops = 0
                // Follow GitHub's redirects to the file host
                while (true) {
                    conn = (URL(link).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        connectTimeout = 15_000
                        readTimeout = 30_000
                        setRequestProperty("User-Agent", "EinthuTv-Updater")
                    }
                    val c = conn.responseCode
                    if (c in 300..399 && hops < 5) {
                        link = conn.getHeaderField("Location") ?: break
                        conn.disconnect(); hops++
                        continue
                    }
                    if (c != 200) throw Exception("Download failed ($c)")
                    break
                }
                val total = conn.contentLength.toLong()
                var read = 0L
                var lastPct = -1
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val pct = (read * 100 / total).toInt()
                                if (pct != lastPct) { lastPct = pct; main.post { progress(pct) } }
                            }
                        }
                    }
                }
                conn.disconnect()
                main.post { done(out, null) }
            } catch (e: Exception) {
                main.post { done(null, e.message ?: "Download failed") }
            }
        }.start()
    }

    fun install(act: Activity, apk: File) {
        val uri = FileProvider.getUriForFile(act, act.packageName + ".files", apk)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        act.startActivity(i)
    }
}
