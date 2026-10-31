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
import android.widget.CheckBox
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
        // Splash plays once per app start
        private var splashPlayed = false
        private const val SPLASH_MIN_MS = 2400L
        private const val SPLASH_MAX_MS = 9000L
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
    private lateinit var continueBox: LinearLayout

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
    private lateinit var splash: View
    private var splashStart = 0L
    private var splashHiding = false
    private val splashAnims = mutableListOf<android.animation.Animator>()

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
        // "Continue Watching" sits above the other rows and is rebuilt whenever Home is shown
        continueBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        (rows.parent as ViewGroup).addView(continueBox, (rows.parent as ViewGroup).indexOfChild(rows))
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
        findViewById<Button>(R.id.tabSearch).setOnClickListener { Nav.openSearch(this) }
        findViewById<Button>(R.id.tabUpdate).setOnClickListener { checkForUpdates() }
        findViewById<Button>(R.id.tabAccount).setOnClickListener { Nav.openWatch(this, "https://einthusan.tv/account/") }

        Nav.stopUp(
            findViewById(R.id.tabHome), findViewById(R.id.tabMovies), findViewById(R.id.tabBrowse), findViewById(R.id.tabSearch),
            findViewById(R.id.tabUpdate), findViewById(R.id.tabAccount)
        )

        btnLang.setOnClickListener { pickLanguage() }
        btnYear.setOnClickListener { pickYear() }
        btnPlay.setOnClickListener { heroMovie?.let { Nav.openMovie(this, it, lang) } }
        btnSeeAll.setOnClickListener { Nav.openGrid(this, Nav.trendingUrl(lang), getString(R.string.row_trending, Nav.langLabel(lang))) }
        status.setOnClickListener { reload() }

        reload()
        btnPlay.requestFocus()

        splash = findViewById(R.id.splash)
        if (!splashPlayed) {
            splashPlayed = true
            playSplash()
        } else {
            splash.visibility = View.GONE
            if (!loginAskedThisRun) checkLogin()
        }
    }

    override fun onResume() {
        super.onResume()
        buildContinueRow()
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
        if (splash.visibility == View.VISIBLE) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) finish()
            return true
        }
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

        addRow(getString(R.string.row_recent), Nav.recentUrl(lang), pages = 2, isMain = false)
        addRow(getString(R.string.row_new, label, year.toString()), Nav.yearUrl(lang, year), pages = 3, isMain = true)
        addRow(getString(R.string.row_trending, label), Nav.trendingUrl(lang), pages = 2, isMain = false, feedsHero = true)
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
                if (fresh.isNotEmpty()) hideSplash()
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

    // ---------- Splash ----------

    private fun playSplash() {
        splash.visibility = View.VISIBLE
        splash.alpha = 1f
        splash.requestFocus()
        splashStart = SystemClock.uptimeMillis()
        val d = resources.displayMetrics.density

        val glow = findViewById<View>(R.id.splashGlow)
        val logo = findViewById<View>(R.id.splashLogo)
        val strip = findViewById<View>(R.id.splashStrip)
        val play = findViewById<View>(R.id.splashPlay)
        val title = findViewById<LinearLayout>(R.id.splashTitle)
        val tag = findViewById<View>(R.id.splashTag)
        val track = findViewById<View>(R.id.splashTrack)
        val bar = findViewById<View>(R.id.splashBar)
        val credit = findViewById<View>(R.id.splashCredit)

        // 1. Glow breathes in
        glow.scaleX = 0.6f; glow.scaleY = 0.6f
        glow.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(900).start()

        // 2. Film strip drops in with a bounce
        strip.translationY = -60 * d; strip.rotation = -14f; strip.scaleX = 0.6f; strip.scaleY = 0.6f
        strip.animate().alpha(1f).translationY(0f).rotation(0f).scaleX(1f).scaleY(1f)
            .setStartDelay(150).setDuration(750)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.6f)).start()

        // 3. Play button pops, then pulses
        play.scaleX = 0f; play.scaleY = 0f
        play.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(750).setDuration(450)
            .setInterpolator(android.view.animation.OvershootInterpolator(3f))
            .withEndAction {
                val px = android.animation.ObjectAnimator.ofFloat(play, View.SCALE_X, 1f, 1.14f, 1f)
                val py = android.animation.ObjectAnimator.ofFloat(play, View.SCALE_Y, 1f, 1.14f, 1f)
                listOf(px, py).forEach {
                    it.duration = 1100; it.repeatCount = android.animation.ValueAnimator.INFINITE
                    it.start(); splashAnims.add(it)
                }
            }.start()

        // Logo gently floats
        val bob = android.animation.ObjectAnimator.ofFloat(logo, View.TRANSLATION_Y, 0f, -6 * d, 0f)
        bob.duration = 2200; bob.startDelay = 900; bob.repeatCount = android.animation.ValueAnimator.INFINITE
        bob.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        bob.start(); splashAnims.add(bob)

        // 4. Title letters rise in one by one
        title.removeAllViews()
        val word = "EINTHUTV"
        word.forEachIndexed { i, ch ->
            val tv = TextView(this).apply {
                text = ch.toString()
                textSize = 54f
                setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD))
                setTextColor(if (i < 6) getColor(R.color.accent) else 0xFFFFFFFF.toInt())
                alpha = 0f
                translationY = 28 * d
                letterSpacing = 0.04f
            }
            title.addView(tv)
            tv.animate().alpha(1f).translationY(0f)
                .setStartDelay(1050L + i * 70L).setDuration(380)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }

        // 5. Tagline, loading bar and credit
        tag.translationY = 10 * d
        tag.animate().alpha(1f).translationY(0f).setStartDelay(1750).setDuration(500).start()
        track.animate().alpha(1f).setStartDelay(1900).setDuration(400).start()
        credit.animate().alpha(1f).setStartDelay(2000).setDuration(500).start()
        val sweep = android.animation.ObjectAnimator.ofFloat(bar, View.TRANSLATION_X, -80 * d, 240 * d)
        sweep.duration = 1100; sweep.repeatCount = android.animation.ValueAnimator.INFINITE
        sweep.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        sweep.start(); splashAnims.add(sweep)

        // Never longer than SPLASH_MAX_MS, even if the internet is slow
        splash.postDelayed({ hideSplash() }, SPLASH_MAX_MS)
    }

    private fun hideSplash() {
        if (!::splash.isInitialized || splash.visibility != View.VISIBLE || splashHiding) return
        val wait = SPLASH_MIN_MS - (SystemClock.uptimeMillis() - splashStart)
        if (wait > 0) { splash.postDelayed({ hideSplash() }, wait); return }
        splashHiding = true
        val logo = findViewById<View>(R.id.splashCenter)
        logo.animate().scaleX(1.12f).scaleY(1.12f).setDuration(450).start()
        splash.animate().alpha(0f).setDuration(450).withEndAction {
            splashAnims.forEach { it.cancel() }
            splashAnims.clear()
            splash.visibility = View.GONE
            btnPlay.requestFocus()
            if (!loginAskedThisRun) checkLogin()
        }.start()
    }

    // ---------- Continue Watching ----------

    private fun buildContinueRow() {
        continueBox.removeAllViews()
        val items = WatchHistory.all(this)
        if (items.isEmpty()) return
        val section = LayoutInflater.from(this).inflate(R.layout.row_section, continueBox, false)
        section.findViewById<TextView>(R.id.rowTitle).text = getString(R.string.row_continue)
        val strip = section.findViewById<LinearLayout>(R.id.rowItems)
        items.forEach { e -> strip.addView(continueCard(strip, e)) }
        continueBox.addView(section)
    }

    private fun continueCard(parent: ViewGroup, e: WatchHistory.Entry): View {
        val v = LayoutInflater.from(this).inflate(R.layout.item_continue, parent, false)
        v.findViewById<TextView>(R.id.cardTitle).text = e.title
        v.findViewById<TextView>(R.id.cardMeta).text = WatchHistory.fmtLeft(e.left)
        v.findViewById<ProgressBar>(R.id.cardProgress).progress = (e.fraction * 1000).toInt()
        ImageLoader.load(e.poster, v.findViewById<ImageView>(R.id.cardPoster))
        v.setOnClickListener {
            startActivity(
                android.content.Intent(this, BrowserActivity::class.java)
                    .putExtra(BrowserActivity.EXTRA_URL, e.url)
                    .putExtra(BrowserActivity.EXTRA_TITLE, e.title)
                    .putExtra(BrowserActivity.EXTRA_POSTER, e.poster)
                    .putExtra(BrowserActivity.EXTRA_DESC, e.desc)
                    .putExtra(BrowserActivity.EXTRA_META, e.meta)
                    .putExtra(BrowserActivity.EXTRA_RESUME_AT, e.position)
            )
        }
        v.setOnLongClickListener {
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.continue_remove_title)
                .setMessage(e.title)
                .setPositiveButton(R.string.continue_remove) { _, _ ->
                    WatchHistory.remove(this, e.url)
                    buildContinueRow()
                    btnPlay.requestFocus()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }
        Nav.focusScale(v)
        return v
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
        if (wasEmpty) { goSlide(0, animate = false); hideSplash() }
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
            if (loggedIn != false || loginAskedThisRun) return@check
            loginAskedThisRun = true
            val saved = CredentialStore.load(this)
            if (saved == null) { showLoginDialog(); return@check }
            // Signed out but we have the saved login: sign back in quietly
            Toast.makeText(this, R.string.login_auto, Toast.LENGTH_SHORT).show()
            auth.login(saved.email, saved.password) { r ->
                if (isFinishing || isDestroyed) return@login
                when (r) {
                    is AuthHelper.Result.Success -> Toast.makeText(this, R.string.login_done, Toast.LENGTH_SHORT).show()
                    is AuthHelper.Result.Failed -> showLoginDialog(r.message)
                    is AuthHelper.Result.Unknown -> showLoginDialog(getString(R.string.login_unknown))
                }
            }
        }
    }

    private fun showLoginDialog(initialMessage: String? = null) {
        loginAskedThisRun = true
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_login, null)
        val email = view.findViewById<EditText>(R.id.loginEmail)
        val pass = view.findViewById<EditText>(R.id.loginPassword)
        val remember = view.findViewById<CheckBox>(R.id.loginRemember)
        val msg = view.findViewById<TextView>(R.id.loginStatus)
        val btnLogin = view.findViewById<Button>(R.id.btnLogin)
        val btnSkip = view.findViewById<Button>(R.id.btnSkip)
        val btnSite = view.findViewById<Button>(R.id.btnSiteLogin)

        // Fill in whatever was saved last time
        val saved = CredentialStore.load(this)
        email.setText(saved?.email ?: CredentialStore.savedEmail(this))
        if (saved != null) pass.setText(saved.password)

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
        if (initialMessage != null) show(initialMessage)

        fun submit() {
            val e = email.text.toString().trim()
            val p = pass.text.toString()
            if (e.isEmpty() || p.isEmpty()) { show(getString(R.string.login_missing)); return }
            setBusy(true)
            show(getString(R.string.login_signing_in), isError = false)
            auth.login(e, p) { r ->
                if (!dialog.isShowing) return@login
                setBusy(false)
                when (r) {
                    is AuthHelper.Result.Success -> {
                        if (remember.isChecked) CredentialStore.save(this, e, p)
                        else { CredentialStore.save(this, e, ""); CredentialStore.forgetPassword(this) }
                        dialog.dismiss()
                        Toast.makeText(this, R.string.login_done, Toast.LENGTH_SHORT).show()
                        btnPlay.requestFocus()
                    }
                    is AuthHelper.Result.Failed -> { show(r.message); pass.requestFocus() }
                    is AuthHelper.Result.Unknown -> {
                        // The details may still be right; keep them if asked so the next try is automatic
                        if (remember.isChecked) CredentialStore.save(this, e, p)
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
            Nav.openWatch(this, "https://einthusan.tv/login/")
        }
        dialog.setOnCancelListener { auth.cancel() }
        dialog.show()
        when {
            email.text.isNullOrBlank() -> email.requestFocus()
            pass.text.isNullOrBlank() -> pass.requestFocus()
            else -> btnLogin.requestFocus()
        }
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
