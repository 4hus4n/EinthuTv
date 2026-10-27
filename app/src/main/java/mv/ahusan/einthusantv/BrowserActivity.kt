package mv.ahusan.einthusantv

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import org.json.JSONTokener

class BrowserActivity : Activity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_POSTER = "poster"
        const val EXTRA_DESC = "desc"
        const val EXTRA_META = "meta"
        /** Seconds to resume at; 0 = start over; missing = use saved progress. */
        const val EXTRA_RESUME_AT = "resume_at"
        private const val HOME = "https://einthusan.tv/"
        private const val POINTER_STEP = 28f

        /**
         * Watch-page helper. Starts the site's own player (quality pick + play),
         * reports what is playing, and shows the full movie video-only.
         * Short videos (trailers/ads, 5 min or less) are left to play on the page.
         */
        private val THEATER_JS = """
            (function(){
              if (window.__tv) return;
              var st = document.createElement('style');
              st.textContent =
                '.__tvfull{position:fixed!important;left:0!important;top:0!important;width:100vw!important;height:100vh!important;' +
                'max-width:none!important;max-height:none!important;z-index:2147483647!important;background:#000!important;' +
                'object-fit:contain!important;transform:none!important;margin:0!important;border:0!important;}' +
                'html.__tvon,html.__tvon body{overflow:hidden!important;background:#000!important;}';
              document.head.appendChild(st);

              var cur = null, adSent = false, kicks = 0, qualityClicks = 0;
              function bridge(fn, arg){ try { EinthuTvBridge[fn](arg); } catch(e) {} }
              function docs(){
                var ds = [document];
                document.querySelectorAll('iframe').forEach(function(f){
                  try { if (f.contentDocument) ds.push(f.contentDocument); } catch(e) {}
                });
                return ds;
              }
              function videos(){
                var vs = [];
                docs().forEach(function(d){ d.querySelectorAll('video').forEach(function(v){ vs.push(v); }); });
                return vs;
              }
              function isShort(v){ return isFinite(v.duration) && v.duration > 0 && v.duration <= 300; }
              function main(){
                var best = null;
                videos().forEach(function(v){
                  if (!isShort(v) && (!best || (v.duration || 0) > (best.duration || 0))) best = v;
                });
                return best;
              }
              function target(v){
                return v.ownerDocument === document ? v : (v.ownerDocument.defaultView.frameElement || v);
              }
              function enter(v){
                if (cur === v) return;
                cur = v;
                var t = target(v);
                var p = t.parentElement;
                while (p && p !== document.body) {
                  var cs = getComputedStyle(p);
                  if (cs.transform !== 'none' || cs.filter !== 'none' || cs.perspective !== 'none') {
                    p.style.setProperty('transform','none','important');
                    p.style.setProperty('filter','none','important');
                    p.style.setProperty('perspective','none','important');
                  }
                  p = p.parentElement;
                }
                t.classList.add('__tvfull');
                if (t !== v) {
                  v.style.setProperty('width','100%','important');
                  v.style.setProperty('height','100%','important');
                  v.style.setProperty('object-fit','contain','important');
                }
                v.controls = false;
                document.documentElement.classList.add('__tvon');
                bridge('theater', true);
              }
              function exit(){
                if (!cur) return;
                target(cur).classList.remove('__tvfull');
                cur.controls = true;
                document.documentElement.classList.remove('__tvon');
                cur = null;
                bridge('theater', false);
              }
              function textOf(el){ return (el.textContent || '').replace(/\s+/g,' ').trim(); }
              function clickQuality(){
                if (qualityClicks >= 2) return;
                var els = [].slice.call(document.querySelectorAll('a,button,li,label,span,div'))
                  .filter(function(e){ var t = textOf(e); return t.length < 40 && /(ultra high def|high definition)/i.test(t) && e.offsetParent !== null; });
                if (!els.length) return;
                var uhd = els.filter(function(e){ return /ultra/i.test(textOf(e)); });
                var pick = (qualityClicks === 0 && uhd.length) ? uhd[uhd.length - 1]
                         : els.filter(function(e){ return !/ultra/i.test(textOf(e)); }).pop() || els[els.length - 1];
                qualityClicks++;
                pick.click();
              }
              function kick(){
                if (kicks >= 20) return;
                kicks++;
                clickQuality();
                docs().forEach(function(d){
                  d.querySelectorAll('.vjs-big-play-button,.jw-display-icon-container,.plyr__control--overlaid,button[aria-label="Play"],.play-button,#play-button')
                    .forEach(function(b){ try { b.click(); } catch(e) {} });
                });
                videos().forEach(function(v){
                  if (v.paused) { try { var pr = v.play(); if (pr && pr.catch) pr.catch(function(){}); } catch(e) {} }
                });
              }
              var ticks = 0;
              function tick(){
                ticks++;
                var vs = videos();
                var playing = vs.filter(function(v){ return !v.paused && v.currentTime > 0.2; });
                var movie = playing.filter(function(v){ return !isShort(v); })[0];
                if (movie) { enter(movie); return; }
                if (cur) return;
                if (playing.length) { if (!adSent) { adSent = true; bridge('shortVideo', true); } return; }
                if (ticks % 3 === 1) kick();
              }
              setInterval(tick, 500);

              window.__tv = {
                state: function(){
                  var v = cur || main();
                  if (!v) return null;
                  var b = v.buffered.length ? v.buffered.end(v.buffered.length - 1) : 0;
                  return JSON.stringify({t: v.currentTime, d: v.duration, p: v.paused, b: b, on: !!cur});
                },
                toggle: function(){ var v = cur || main(); if (v) { if (v.paused) v.play(); else v.pause(); } },
                seek: function(s){
                  var v = cur || main();
                  if (v && isFinite(v.duration)) v.currentTime = Math.max(0, Math.min(v.duration - 1, v.currentTime + s));
                },
                exit: exit,
                seekTo: function(s){ var v = cur || main(); if (v) { try { v.currentTime = s; } catch(e) {} } }
              };
            })();
        """.trimIndent()
    }

    private lateinit var webView: WebView
    private lateinit var root: FrameLayout
    private lateinit var fullscreenHolder: FrameLayout
    private lateinit var pointer: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var hint: TextView
    private lateinit var homeBtn: Button

    private lateinit var playerBar: LinearLayout
    private lateinit var playerTitle: TextView
    private lateinit var playerProgress: ProgressBar
    private lateinit var playerState: TextView
    private lateinit var playerTime: TextView
    private lateinit var seekFlash: TextView

    private lateinit var loadingView: View
    private lateinit var loadTitle: TextView
    private lateinit var loadMeta: TextView
    private lateinit var loadDesc: TextView
    private lateinit var loadStatus: TextView
    private lateinit var loadHelp: TextView

    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    private var pointerMode = false
    private var theater = false
    private var loading = false
    private var videoPaused = false
    private var px = 0f
    private var py = 0f
    private var lastBackPress = 0L
    private var movieTitle: String? = null

    // Continue Watching
    private var startUrl = ""
    private var resumeAt = -1L
    private var resumeApplied = false
    private var lastPos = 0.0
    private var lastDur = 0.0
    private var lastSavedAt = 0L

    private val hintHandler = Handler(Looper.getMainLooper())
    private val overlayHandler = Handler(Looper.getMainLooper())
    private val pollHandler = Handler(Looper.getMainLooper())
    private val loadHandler = Handler(Looper.getMainLooper())

    private val pollTask = object : Runnable {
        override fun run() {
            if (!theater) return
            pollState()
            pollHandler.postDelayed(this, 500)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_browser)

        root = findViewById(R.id.root)
        webView = findViewById(R.id.webView)
        fullscreenHolder = findViewById(R.id.fullscreenHolder)
        pointer = findViewById(R.id.pointer)
        progress = findViewById(R.id.progress)
        hint = findViewById(R.id.hint)
        playerBar = findViewById(R.id.playerBar)
        playerTitle = findViewById(R.id.playerTitle)
        playerProgress = findViewById(R.id.playerProgress)
        playerState = findViewById(R.id.playerState)
        playerTime = findViewById(R.id.playerTime)
        seekFlash = findViewById(R.id.seekFlash)
        homeBtn = findViewById(R.id.btnHome)
        homeBtn.setOnClickListener { Nav.goHome(this) }

        loadingView = findViewById(R.id.loadingView)
        loadTitle = findViewById(R.id.loadTitle)
        loadMeta = findViewById(R.id.loadMeta)
        loadDesc = findViewById(R.id.loadDesc)
        loadStatus = findViewById(R.id.loadStatus)
        loadHelp = findViewById(R.id.loadHelp)

        // Movie info passed from the app's cards
        movieTitle = intent.getStringExtra(EXTRA_TITLE)
        startUrl = intent.getStringExtra(EXTRA_URL) ?: ""
        resumeAt = intent.getLongExtra(EXTRA_RESUME_AT, -1L)
        if (resumeAt < 0) resumeAt = WatchHistory.get(this, startUrl)?.position ?: 0L
        loadTitle.text = movieTitle ?: ""
        loadMeta.text = intent.getStringExtra(EXTRA_META) ?: ""
        loadDesc.text = intent.getStringExtra(EXTRA_DESC) ?: ""
        intent.getStringExtra(EXTRA_POSTER)?.let {
            ImageLoader.load(it, findViewById(R.id.loadPoster))
            ImageLoader.load(it, findViewById(R.id.loadBg))
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            // Pop-up protection: pages cannot open new windows
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun theater(on: Boolean) {
                runOnUiThread {
                    if (on) hideLoading()
                    setTheater(on)
                }
            }

            @JavascriptInterface
            fun shortVideo(on: Boolean) {
                // A trailer or ad is playing on the page: show the page so it can be seen
                runOnUiThread { if (on) hideLoading() }
            }
        }, "EinthuTvBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Redirect protection: only main-frame navigation to einthusan.tv is allowed
                if (!request.isForMainFrame) return false
                // Movie lists and the browse page open in the app's own screens
                if (isAllowed(request.url) && Nav.isResults(request.url)) {
                    Nav.openGrid(this@BrowserActivity, request.url.toString()); return true
                }
                if (isAllowed(request.url) && Nav.isBrowse(request.url)) {
                    Nav.openBrowse(this@BrowserActivity); return true
                }
                return if (isAllowed(request.url)) false else {
                    toast(getString(R.string.blocked_redirect))
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
                if (theater) setTheater(false)
                if (isWatchPage(url)) showLoading() else hideLoading()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progress.visibility = View.GONE
                injectTvHelpers()
                if (isWatchPage(url)) {
                    webView.evaluateJavascript(THEATER_JS, null)
                    if (loading) {
                        loadStatus.text = getString(R.string.load_starting)
                        if (loadTitle.text.isNullOrBlank()) loadTitle.text = cleanTitle(view.title)
                    }
                }
                CookieManager.getInstance().flush()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden(); return
                }
                hideLoading()
                customView = view
                customCallback = callback
                fullscreenHolder.addView(view, FrameLayout.LayoutParams(-1, -1))
                fullscreenHolder.visibility = View.VISIBLE
                webView.visibility = View.GONE
                homeBtn.visibility = View.GONE
                hideSystemUi()
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }
        }

        val start = intent.getStringExtra(EXTRA_URL) ?: HOME
        if (isWatchPage(start)) showLoading()
        if (savedInstanceState != null) webView.restoreState(savedInstanceState) else webView.loadUrl(start)

        root.post {
            px = root.width / 2f
            py = root.height / 2f
            movePointer(0f, 0f)
        }
        hideSystemUi()
    }

    private fun isAllowed(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return false
        return host == "einthusan.tv" || host.endsWith(".einthusan.tv") ||
            host == "einthusan.com" || host.endsWith(".einthusan.com")
    }

    private fun isWatchPage(url: String?) = url?.contains("/movie/watch/") == true

    /** Adds a visible focus ring and makes links/buttons reachable with the D-pad. */
    private fun injectTvHelpers() {
        val js = """
            (function(){
              if (window.__tvHelpers) return; window.__tvHelpers = true;
              var s = document.createElement('style');
              s.textContent = 'a:focus,button:focus,input:focus,select:focus,[tabindex]:focus,[role=button]:focus{outline:4px solid #ffb300 !important;outline-offset:2px !important;box-shadow:0 0 0 6px rgba(255,179,0,.35) !important;}';
              document.head.appendChild(s);
              window.open = function(){ return null; };
              function mark(){
                document.querySelectorAll('[onclick],[role=button],.button,li[data-url]').forEach(function(el){
                  if (!el.hasAttribute('tabindex')) el.setAttribute('tabindex','0');
                });
              }
              mark();
              new MutationObserver(mark).observe(document.body,{childList:true,subtree:true});
              document.addEventListener('keydown',function(e){
                if ((e.key==='Enter') && document.activeElement && document.activeElement.tagName!=='A'
                    && document.activeElement.tagName!=='INPUT' && document.activeElement.tagName!=='BUTTON') {
                  document.activeElement.click();
                }
              },true);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    // ---------- Loading screen ----------

    private fun showLoading() {
        if (loading) return
        loading = true
        loadingView.visibility = View.VISIBLE
        homeBtn.visibility = View.GONE
        loadStatus.text = getString(R.string.load_opening)
        loadHelp.visibility = View.GONE
        loadHandler.removeCallbacksAndMessages(null)
        loadHandler.postDelayed({ if (loading) loadStatus.text = getString(R.string.load_buffering) }, 9000)
        loadHandler.postDelayed({ if (loading) loadHelp.visibility = View.VISIBLE }, 15000)
    }

    private fun hideLoading() {
        if (!loading) return
        loading = false
        loadHandler.removeCallbacksAndMessages(null)
        loadingView.animate().alpha(0f).setDuration(300).withEndAction {
            loadingView.visibility = View.GONE
            loadingView.alpha = 1f
        }.start()
        if (!theater && customView == null) homeBtn.visibility = View.VISIBLE
    }

    // ---------- Video-only player ----------

    private fun setTheater(on: Boolean) {
        if (theater == on) return
        theater = on
        homeBtn.visibility = if (on || loading) View.GONE else View.VISIBLE
        pollHandler.removeCallbacks(pollTask)
        if (on) {
            if (pointerMode) togglePointer()
            playerTitle.text = movieTitle ?: cleanTitle(webView.title)
            showOverlay()
            pollHandler.post(pollTask)
            applyResume()
        } else {
            playerBar.visibility = View.GONE
            seekFlash.visibility = View.GONE
        }
    }

    private fun cleanTitle(t: String?): String =
        (t ?: "").replace(Regex("\\s*[-|]\\s*Einthusan.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^Watch\\s+", RegexOption.IGNORE_CASE), "").trim()

    private fun showOverlay() {
        if (!theater) return
        playerBar.visibility = View.VISIBLE
        overlayHandler.removeCallbacksAndMessages(null)
        overlayHandler.postDelayed({ if (!videoPaused) playerBar.visibility = View.GONE }, 4000)
    }

    private fun pollState() {
        webView.evaluateJavascript("window.__tv ? window.__tv.state() : null") { raw ->
            val json = try { JSONTokener(raw ?: "null").nextValue() as? String } catch (e: Exception) { null }
                ?: return@evaluateJavascript
            val o = try { JSONObject(json) } catch (e: Exception) { return@evaluateJavascript }
            val t = o.optDouble("t", 0.0)
            val d = o.optDouble("d", 0.0)
            val b = o.optDouble("b", 0.0)
            val paused = o.optBoolean("p", false)
            if (paused != videoPaused) {
                videoPaused = paused
                if (paused) playerBar.visibility = View.VISIBLE else showOverlay()
            }
            playerState.text = if (paused) "▶" else "❚❚"
            if (d > 0 && d.isFinite()) {
                lastPos = t; lastDur = d
                if (SystemClock.uptimeMillis() - lastSavedAt > 10_000) saveProgress()
                playerProgress.progress = ((t / d) * 1000).toInt()
                playerProgress.secondaryProgress = ((b / d) * 1000).toInt()
                playerTime.text = getString(R.string.player_time, fmt(t), fmt(d))
            }
        }
    }

    // ---------- Continue Watching ----------

    private fun applyResume() {
        if (resumeApplied || resumeAt < 30) return
        resumeApplied = true
        val target = resumeAt
        webView.evaluateJavascript("window.__tv && window.__tv.seekTo($target)", null)
        seekFlash.text = getString(R.string.resuming_at, WatchHistory.fmtClock(target))
        seekFlash.visibility = View.VISIBLE
        hintHandler.removeCallbacksAndMessages(null)
        hintHandler.postDelayed({ seekFlash.visibility = View.GONE }, 2500)
        // Some players ignore the first seek while still starting: check and retry
        pollHandler.postDelayed({
            if (theater && lastPos < target - 30) webView.evaluateJavascript("window.__tv && window.__tv.seekTo($target)", null)
        }, 2500)
    }

    private fun saveProgress() {
        lastSavedAt = SystemClock.uptimeMillis()
        if (startUrl.isBlank() || lastDur < 300 || !resumeApplied && resumeAt >= 30) return
        // Only save for the movie this screen was opened for
        val cur = webView.url ?: return
        if (WatchHistory.key(cur) != WatchHistory.key(startUrl)) return
        WatchHistory.save(this, WatchHistory.Entry(
            url = startUrl,
            title = movieTitle ?: cleanTitle(webView.title),
            poster = intent.getStringExtra(EXTRA_POSTER) ?: "",
            meta = intent.getStringExtra(EXTRA_META) ?: "",
            desc = intent.getStringExtra(EXTRA_DESC) ?: "",
            position = lastPos.toLong(),
            duration = lastDur.toLong(),
            updated = System.currentTimeMillis()
        ))
    }

    private fun fmt(sec: Double): String {
        val s = sec.toInt().coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val r = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, r) else String.format("%d:%02d", m, r)
    }

    private fun seekBy(sec: Int) {
        webView.evaluateJavascript("window.__tv && window.__tv.seek($sec)", null)
        seekFlash.text = if (sec > 0) "+${sec}s  ⏩" else "⏪  ${sec}s"
        seekFlash.visibility = View.VISIBLE
        hintHandler.removeCallbacksAndMessages(null)
        hintHandler.postDelayed({ seekFlash.visibility = View.GONE; hint.visibility = View.GONE }, 900)
        showOverlay()
        pollState()
    }

    private fun togglePlay() {
        webView.evaluateJavascript("window.__tv && window.__tv.toggle()", null)
        showOverlay()
        pollHandler.postDelayed({ pollState() }, 150)
    }

    // ---------- Pointer mode ----------

    private fun togglePointer() {
        pointerMode = !pointerMode
        pointer.visibility = if (pointerMode) View.VISIBLE else View.GONE
        showHint(getString(if (pointerMode) R.string.pointer_on else R.string.pointer_off))
    }

    private fun movePointer(dx: Float, dy: Float) {
        val w = root.width.toFloat()
        val h = root.height.toFloat()
        px = (px + dx).coerceIn(0f, w - 1)
        py = (py + dy).coerceIn(0f, h - 1)
        pointer.x = px
        pointer.y = py
        // Scroll the page when the pointer pushes against the top/bottom edge
        val edge = 40f
        if (dy > 0 && py >= h - edge) webView.scrollBy(0, 120)
        if (dy < 0 && py <= edge) webView.scrollBy(0, -120)
    }

    private fun clickAtPointer() {
        val loc = IntArray(2)
        webView.getLocationOnScreen(loc)
        val rootLoc = IntArray(2)
        root.getLocationOnScreen(rootLoc)
        val x = px + rootLoc[0] - loc[0]
        val y = py + rootLoc[1] - loc[1]
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0)
        webView.dispatchTouchEvent(down)
        webView.dispatchTouchEvent(up)
        down.recycle(); up.recycle()
    }

    // ---------- Keys ----------

    private var okDown: KeyEvent? = null
    private var okLongHandled = false

    private fun isOk(code: Int) = code == KeyEvent.KEYCODE_DPAD_CENTER ||
        code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode

        // Loading screen: OK shows the page once the "taking a while" help is visible
        if (loading && code != KeyEvent.KEYCODE_BACK) {
            if (isOk(code) && event.action == KeyEvent.ACTION_UP && loadHelp.visibility == View.VISIBLE) {
                hideLoading()
            }
            return true
        }

        // Video-only player controls
        if (theater && customView == null && code != KeyEvent.KEYCODE_BACK) {
            val down = event.action == KeyEvent.ACTION_DOWN
            val up = event.action == KeyEvent.ACTION_UP
            when {
                isOk(code) || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                    code == KeyEvent.KEYCODE_MEDIA_PLAY || code == KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    if (up) togglePlay()
                    return true
                }
                code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    if (down) seekBy(if (event.repeatCount >= 3) -30 else -10)
                    return true
                }
                code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    if (down) seekBy(if (event.repeatCount >= 3) 30 else 10)
                    return true
                }
                code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN ||
                    code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_INFO -> {
                    if (up) { showOverlay(); pollState() }
                    return true
                }
            }
        }

        // Toggle pointer: Menu / Info / Guide / Settings button, or Y on a gamepad
        if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_BUTTON_Y ||
            code == KeyEvent.KEYCODE_PROG_RED || code == KeyEvent.KEYCODE_INFO ||
            code == KeyEvent.KEYCODE_GUIDE || code == KeyEvent.KEYCODE_SETTINGS) {
            if (event.action == KeyEvent.ACTION_UP) togglePointer()
            return true
        }

        // OK button: short press = normal click, hold ~1s = toggle pointer mode
        if (isOk(code) && customView == null) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        okDown = KeyEvent(event)
                        okLongHandled = false
                    } else if (!okLongHandled && event.eventTime - event.downTime >= 800) {
                        okLongHandled = true
                        togglePointer()
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    val down = okDown
                    okDown = null
                    if (okLongHandled) return true
                    if (pointerMode) {
                        clickAtPointer()
                    } else if (down != null) {
                        super.dispatchKeyEvent(down)
                        super.dispatchKeyEvent(event)
                    }
                    return true
                }
            }
        }

        if (pointerMode && customView == null) {
            val step = if (event.repeatCount > 3) POINTER_STEP * 2.5f else POINTER_STEP
            when (code) {
                KeyEvent.KEYCODE_DPAD_UP -> { if (event.action == KeyEvent.ACTION_DOWN) movePointer(0f, -step); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { if (event.action == KeyEvent.ACTION_DOWN) movePointer(0f, step); return true }
                KeyEvent.KEYCODE_DPAD_LEFT -> { if (event.action == KeyEvent.ACTION_DOWN) movePointer(-step, 0f); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { if (event.action == KeyEvent.ACTION_DOWN) movePointer(step, 0f); return true }
            }
        }

        // Media keys -> HTML5 video on the page
        if (event.action == KeyEvent.ACTION_UP) {
            val js = when (code) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "var v=document.querySelector('video');if(v){v.paused?v.play():v.pause();}"
                KeyEvent.KEYCODE_MEDIA_PLAY -> "var v=document.querySelector('video');if(v)v.play();"
                KeyEvent.KEYCODE_MEDIA_PAUSE -> "var v=document.querySelector('video');if(v)v.pause();"
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "var v=document.querySelector('video');if(v)v.currentTime+=30;"
                KeyEvent.KEYCODE_MEDIA_REWIND -> "var v=document.querySelector('video');if(v)v.currentTime-=10;"
                else -> null
            }
            if (js != null) { webView.evaluateJavascript(js, null); return true }
        }

        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            loading -> finish()
            customView != null -> exitFullscreen()
            theater -> {
                webView.evaluateJavascript("window.__tv && window.__tv.exit()", null)
                setTheater(false)
            }
            pointerMode -> togglePointer()
            webView.canGoBack() -> webView.goBack()
            SystemClock.uptimeMillis() - lastBackPress < 2000 -> super.onBackPressed()
            else -> {
                lastBackPress = SystemClock.uptimeMillis()
                toast(getString(R.string.press_back_again))
            }
        }
    }

    // ---------- Helpers ----------

    private fun exitFullscreen() {
        val v = customView ?: return
        fullscreenHolder.removeView(v)
        fullscreenHolder.visibility = View.GONE
        webView.visibility = View.VISIBLE
        homeBtn.visibility = View.VISIBLE
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
        webView.requestFocus()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    private fun showHint(text: String) {
        hint.text = text
        hint.visibility = View.VISIBLE
        hintHandler.removeCallbacksAndMessages(null)
        hintHandler.postDelayed({ hint.visibility = View.GONE }, 2500)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        if (lastDur > 0) saveProgress()
        webView.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        hideSystemUi()
        if (theater) pollHandler.post(pollTask)
    }

    override fun onDestroy() {
        hintHandler.removeCallbacksAndMessages(null)
        overlayHandler.removeCallbacksAndMessages(null)
        pollHandler.removeCallbacksAndMessages(null)
        loadHandler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }
}
