package mv.ahusan.einthusantv

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

class HomeActivity : Activity() {

    companion object {
        // Asked once per app start; "Skip for now" stops it until the app is reopened
        private var loginAskedThisRun = false
        private const val SLIDE_MS = 6000L
        private const val PAUSE_AFTER_KEY_MS = 8000L
        private const val MAX_SLIDES = 8
    }

    private val languages = listOf(
        "Malayalam", "Tamil", "Hindi", "Telugu",
        "Kannada", "Bengali", "Marathi", "Punjabi"
    )
    private val thisYear = Calendar.getInstance().get(Calendar.YEAR)

    private lateinit var loader: CatalogLoader
    private lateinit var auth: AuthHelper
    private lateinit var btnLang: Button
    private lateinit var btnYear: Button
    private lateinit var rows: LinearLayout
    private lateinit var status: TextView

    private lateinit var heroBg: ImageView
    private lateinit var heroPoster: ImageView
    private lateinit var heroText: View
    private lateinit var heroEyebrow: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroMeta: TextView
    private lateinit var heroBadges: TextView
    private lateinit var heroDesc: TextView
    private lateinit var heroDots: LinearLayout
    private lateinit var btnPlay: Button
    private lateinit var btnSeeAll: Button

    private var lang = "malayalam"
    private var year = thisYear
    private var heroMovie: Movie? = null

    // Hero slides (trending)
    private val slides = mutableListOf<Movie>()
    private var slideIndex = 0
    private var lastKeyAt = 0L
    private val slideHandler = Handler(Looper.getMainLooper())
    private val slideTask = object : Runnable {
        override fun run() {
            val idle = SystemClock.uptimeMillis() - lastKeyAt > PAUSE_AFTER_KEY_MS
            if (slides.size > 1 && idle && !isFinishing && hasWindowFocus()) goSlide(slideIndex + 1, animate = true)
            slideHandler.postDelayed(this, SLIDE_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val prefs = getSharedPreferences("einthutv", MODE_PRIVATE)
        lang = prefs.getString("lang", "malayalam") ?: "malayalam"
        year = prefs.getInt("year", 2026)

        loader = CatalogLoader(findViewById<WebView>(R.id.loaderWebView))
        auth = AuthHelper(findViewById(R.id.authWebView))
        btnLang = findViewById(R.id.btnLang)
        btnYear = findViewById(R.id.btnYear)
        rows = findViewById(R.id.rows)
        status = findViewById(R.id.status)
        heroBg = findViewById(R.id.heroBg)
        heroPoster = findViewById(R.id.heroPoster)
        heroText = findViewById(R.id.heroText)
        heroEyebrow = findViewById(R.id.heroEyebrow)
        heroTitle = findViewById(R.id.heroTitle)
        heroMeta = findViewById(R.id.heroMeta)
        heroBadges = findViewById(R.id.heroBadges)
        heroDesc = findViewById(R.id.heroDesc)
        heroDots = findViewById(R.id.heroDots)
        btnPlay = findViewById(R.id.btnPlay)
        btnSeeAll = findViewById(R.id.btnSeeAll)

        findViewById<Button>(R.id.tabHome).setOnClickListener { reload() }
        findViewById<Button>(R.id.tabMovies).setOnClickListener { Nav.openGrid(this, Nav.yearUrl(lang, year)) }
        findViewById<Button>(R.id.tabBrowse).setOnClickListener { Nav.openBrowse(this) }
        findViewById<Button>(R.id.tabUpdate).setOnClickListener { checkForUpdates() }
        findViewById<Button>(R.id.tabAccount).setOnClickListener { Nav.openWatch(this, "https://einthusan.tv/account/") }

        btnLang.setOnClickListener { pickLanguage() }
        btnYear.setOnClickListener { pickYear() }
        btnPlay.setOnClickListener { heroMovie?.let { Nav.openMovie(this, it, lang) } }
        btnSeeAll.setOnClickListener { Nav.openGrid(this, Nav.trendingUrl(lang), getString(R.string.row_trending, Nav.langLabel(lang))) }
        status.setOnClickListener { reload() }

        reload()
        btnPlay.requestFocus()

        if (!loginAskedThisRun) checkLogin()
    }

    override fun onResume() {
        super.onResume()
        // Language may have been changed on the Browse screen
        val saved = getSharedPreferences("einthutv", MODE_PRIVATE).getString("lang", lang) ?: lang
        if (saved != lang) { lang = saved; reload() }
        slideHandler.removeCallbacks(slideTask)
        slideHandler.postDelayed(slideTask, SLIDE_MS)
    }

    override fun onPause() {
        super.onPause()
        slideHandler.removeCallbacks(slideTask)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        lastKeyAt = SystemClock.uptimeMillis()
        return super.dispatchKeyEvent(event)
    }

    // ---------- Data ----------

    private fun reload() {
        loader.cancelAll()
        rows.removeAllViews()
        heroMovie = null
        slides.clear()
        slideIndex = 0
        heroDots.removeAllViews()
        val label = Nav.langLabel(lang)
        btnLang.text = getString(R.string.filter_language, label)
        btnYear.text = getString(R.string.filter_year, year.toString())
        heroEyebrow.text = getString(R.string.row_trending, label)
        heroTitle.text = getString(R.string.loading)
        heroMeta.text = ""
        heroBadges.visibility = View.GONE
        heroDesc.text = ""
        heroPoster.setImageDrawable(null)
        heroBg.setImageDrawable(null)
        status.visibility = View.GONE

        // Hero slides come from the first page of trending, loaded first
        loader.load(Nav.withPage(Nav.trendingUrl(lang), 1)) { movies -> addSlides(movies) }

        addRow(getString(R.string.row_new, label, year.toString()), Nav.yearUrl(lang, year), pages = 3, isMain = true)
        addRow(getString(R.string.row_trending, label), Nav.trendingUrl(lang), pages = 2, isMain = false, feedsHero = true)
        addRow(getString(R.string.row_recent), Nav.recentUrl(lang), pages = 2, isMain = false)
        addRow(getString(R.string.row_staff), Nav.staffPicksUrl(lang), pages = 1, isMain = false)
    }

    private fun addRow(title: String, url: String, pages: Int, isMain: Boolean, feedsHero: Boolean = false) {
        val section = LayoutInflater.from(this).inflate(R.layout.row_section, rows, false)
        section.findViewById<TextView>(R.id.rowTitle).text = title
        val strip = section.findViewById<LinearLayout>(R.id.rowItems)
        rows.addView(section)

        val seen = HashSet<String>()
        for (p in 1..pages) {
            loader.load(Nav.withPage(url, p)) { movies ->
                val fresh = movies.filter { seen.add(it.url) }
                fresh.forEach { m -> strip.addView(Nav.movieCard(this, strip, m, lang)) }
                if (feedsHero) addSlides(fresh)
                if (p == pages) {
                    if (seen.isEmpty()) {
                        if (isMain) {
                            status.visibility = View.VISIBLE
                            status.text = getString(R.string.load_failed)
                        } else {
                            section.visibility = View.GONE
                        }
                    }
                    if (seen.isNotEmpty()) {
                        strip.addView(Nav.seeAllCard(this, strip, getString(R.string.see_all_arrow)) {
                            Nav.openGrid(this, url, title)
                        })
                    }
                }
            }
        }
    }

    // ---------- Hero slides ----------

    private fun addSlides(movies: List<Movie>) {
        val wasEmpty = slides.isEmpty()
        for (m in movies) {
            if (slides.size >= MAX_SLIDES) break
            if (m.poster.contains("default-img")) continue
            if (slides.none { it.url == m.url }) slides.add(m)
        }
        if (slides.isEmpty()) {
            if (movies.isEmpty() && heroMovie == null) heroTitle.text = getString(R.string.no_movies)
            return
        }
        buildDots()
        if (wasEmpty) goSlide(0, animate = false)
    }

    private fun buildDots() {
        heroDots.removeAllViews()
        val d = resources.displayMetrics.density
        slides.forEachIndexed { i, _ ->
            val v = View(this)
            val on = i == slideIndex
            val lp = LinearLayout.LayoutParams(((if (on) 26 else 9) * d).toInt(), (9 * d).toInt())
            lp.marginEnd = (8 * d).toInt()
            v.layoutParams = lp
            v.setBackgroundResource(if (on) R.drawable.dot_on else R.drawable.dot_off)
            heroDots.addView(v)
        }
    }

    private fun goSlide(i: Int, animate: Boolean) {
        if (slides.isEmpty()) return
        slideIndex = ((i % slides.size) + slides.size) % slides.size
        val m = slides[slideIndex]
        if (!animate) { paintHero(m); buildDots(); return }
        val views = listOf(heroText, heroPoster, heroBg)
        views.forEach { it.animate().alpha(0f).setDuration(250).start() }
        heroText.postDelayed({
            paintHero(m)
            buildDots()
            heroText.animate().alpha(1f).setDuration(300).start()
            heroPoster.animate().alpha(1f).setDuration(300).start()
            heroBg.animate().alpha(0.35f).setDuration(300).start()
        }, 260)
        // Warm the next poster
        slides.getOrNull((slideIndex + 1) % slides.size)?.let {
            ImageLoader.load(it.poster, ImageView(this))
        }
    }

    private fun paintHero(m: Movie) {
        heroMovie = m
        heroTitle.text = m.title
        heroMeta.text = listOf(m.year, Nav.langLabel(lang)).filter { it.isNotBlank() }.joinToString("  •  ")
        heroBadges.visibility = View.VISIBLE
        heroBadges.text = if (m.subtitles) " HD   CC " else " HD "
        heroDesc.text = m.synopsis
        ImageLoader.load(m.poster, heroPoster)
        ImageLoader.load(m.poster, heroBg)
    }

    // ---------- Sign in ----------

    private fun checkLogin() {
        auth.check { loggedIn ->
            if (isFinishing || isDestroyed) return@check
            if (loggedIn == false && !loginAskedThisRun) showLoginDialog()
        }
    }

    private fun showLoginDialog() {
        loginAskedThisRun = true
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_login, null)
        val email = view.findViewById<EditText>(R.id.loginEmail)
        val pass = view.findViewById<EditText>(R.id.loginPassword)
        val msg = view.findViewById<TextView>(R.id.loginStatus)
        val btnLogin = view.findViewById<Button>(R.id.btnLogin)
        val btnSkip = view.findViewById<Button>(R.id.btnSkip)
        val btnSite = view.findViewById<Button>(R.id.btnSiteLogin)

        val prefs = getSharedPreferences("einthutv", MODE_PRIVATE)
        email.setText(prefs.getString("email", ""))

        val dialog = Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        fun setBusy(busy: Boolean) {
            btnLogin.isEnabled = !busy
            email.isEnabled = !busy
            pass.isEnabled = !busy
            btnLogin.text = getString(if (busy) R.string.login_busy else R.string.login_button)
        }
        fun show(text: String, isError: Boolean = true) {
            msg.visibility = View.VISIBLE
            msg.setTextColor(if (isError) 0xFFFFB3B3.toInt() else 0xFFB3FFC8.toInt())
            msg.text = text
        }

        fun submit() {
            val e = email.text.toString().trim()
            val p = pass.text.toString()
            if (e.isEmpty() || p.isEmpty()) { show(getString(R.string.login_missing)); return }
            prefs.edit().putString("email", e).apply()
            setBusy(true)
            show(getString(R.string.login_signing_in), isError = false)
            auth.login(e, p) { r ->
                if (!dialog.isShowing) return@login
                setBusy(false)
                when (r) {
                    is AuthHelper.Result.Success -> {
                        dialog.dismiss()
                        Toast.makeText(this, R.string.login_done, Toast.LENGTH_SHORT).show()
                        btnPlay.requestFocus()
                    }
                    is AuthHelper.Result.Failed -> { show(r.message); pass.requestFocus() }
                    is AuthHelper.Result.Unknown -> {
                        show(getString(R.string.login_unknown))
                        btnSite.visibility = View.VISIBLE
                    }
                }
            }
        }

        btnLogin.setOnClickListener { submit() }
        pass.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
        }
        btnSkip.setOnClickListener { auth.cancel(); dialog.dismiss(); btnPlay.requestFocus() }
        btnSite.setOnClickListener {
            auth.cancel(); dialog.dismiss()
            Nav.openWatch(this, "https://einthusan.tv/account/")
        }
        dialog.setOnCancelListener { auth.cancel() }
        dialog.show()
        (if (email.text.isNullOrBlank()) email else pass).requestFocus()
    }

    // ---------- Updates ----------

    private fun checkForUpdates() {
        Toast.makeText(this, R.string.update_checking, Toast.LENGTH_SHORT).show()
        Updater.check { release, error ->
            if (isFinishing || isDestroyed) return@check
            val mine = Updater.installedCode(this)
            when {
                release == null -> dialog(getString(R.string.update_title), getString(R.string.update_failed, error ?: ""))
                release.code <= mine -> dialog(getString(R.string.update_title),
                    getString(R.string.update_latest, Updater.installedName(this)))
                else -> AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(R.string.update_available)
                    .setMessage(getString(R.string.update_message, release.tag, Updater.installedName(this)))
                    .setPositiveButton(R.string.update_now) { _, _ -> downloadUpdate(release) }
                    .setNegativeButton(R.string.update_later, null)
                    .show()
            }
        }
    }

    private fun downloadUpdate(release: Updater.Release) {
        val d = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * d).toInt(), (16 * d).toInt(), (24 * d).toInt(), (8 * d).toInt())
        }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; isIndeterminate = true }
        val label = TextView(this).apply { text = getString(R.string.update_downloading, 0); setTextColor(0xFFFFFFFF.toInt()) }
        box.addView(label); box.addView(bar)
        val dlg = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(getString(R.string.update_available))
            .setView(box)
            .setCancelable(false)
            .show()
        Updater.download(this, release.apkUrl, progress = { pct ->
            bar.isIndeterminate = false
            bar.progress = pct
            label.text = getString(R.string.update_downloading, pct)
        }) { file, error ->
            dlg.dismiss()
            if (file == null) { dialog(getString(R.string.update_title), getString(R.string.update_failed, error ?: "")); return@download }
            try {
                Updater.install(this, file)
            } catch (e: Exception) {
                dialog(getString(R.string.update_title), getString(R.string.update_failed, e.message ?: ""))
            }
        }
    }

    private fun dialog(title: String, message: String) {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(title).setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---------- Pickers ----------

    private fun pickLanguage() {
        val idx = languages.indexOfFirst { it.equals(lang, true) }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.choose_language)
            .setSingleChoiceItems(languages.toTypedArray(), idx) { d, which ->
                lang = languages[which].lowercase()
                save(); d.dismiss(); reload()
            }
            .show()
    }

    private fun pickYear() {
        val years = (thisYear downTo 1961).map { it.toString() }
        val idx = years.indexOf(year.toString())
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.choose_year)
            .setSingleChoiceItems(years.toTypedArray(), idx) { d, which ->
                year = years[which].toInt()
                save(); d.dismiss(); reload()
            }
            .show()
    }

    private fun save() {
        getSharedPreferences("einthutv", MODE_PRIVATE).edit()
            .putString("lang", lang).putInt("year", year).apply()
    }

    override fun onDestroy() {
        slideHandler.removeCallbacksAndMessages(null)
        loader.cancelAll()
        auth.cancel()
        super.onDestroy()
    }
}
