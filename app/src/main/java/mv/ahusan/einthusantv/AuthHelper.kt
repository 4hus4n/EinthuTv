package mv.ahusan.einthusantv

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Checks whether the user is signed in to einthusan.tv, and signs in by filling
 * the site's own login form in a hidden WebView. Cookies are shared with the
 * watch screen, so a sign-in here also applies to playback.
 */
class AuthHelper(private val web: WebView) {

    sealed class Result {
        object Success : Result()
        data class Failed(val message: String) : Result()
        object Unknown : Result()
    }

    private val handler = Handler(Looper.getMainLooper())
    private var token = 0
    private var onLoaded: (() -> Unit)? = null

    init { setup() }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setup() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            blockNetworkImage = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                CookieManager.getInstance().flush()
                val cb = onLoaded ?: return
                onLoaded = null
                handler.postDelayed({ cb() }, 1200)
            }
        }
    }

    fun cancel() {
        token++
        onLoaded = null
        handler.removeCallbacksAndMessages(null)
        web.stopLoading()
    }

    /** true = signed in, false = signed out, null = couldn't tell (e.g. offline). */
    fun check(callback: (Boolean?) -> Unit) {
        val t = ++token
        val timeout = Runnable { if (t == token) { onLoaded = null; callback(null) } }
        handler.postDelayed(timeout, 20_000)
        load {
            if (t != token) return@load
            readState { st ->
                if (t != token) return@readState
                handler.removeCallbacks(timeout)
                callback(st?.loggedIn)
            }
        }
    }

    fun login(email: String, password: String, callback: (Result) -> Unit) {
        val t = ++token
        load {
            if (t != token) return@load
            val js = "($LOGIN_JS)(${JSONObject.quote(email)}, ${JSONObject.quote(password)})"
            web.evaluateJavascript(js) { raw ->
                if (t != token) return@evaluateJavascript
                val r = unquote(raw)
                if (r == "nopw") {
                    // No login form on the page: maybe already signed in
                    readState { st -> if (t == token) callback(if (st?.loggedIn == true) Result.Success else Result.Unknown) }
                } else {
                    poll(t, 0, callback)
                }
            }
        }
    }

    // Watch the page for an error message or a signed-in state; then reload once to confirm.
    private fun poll(t: Int, n: Int, callback: (Result) -> Unit) {
        handler.postDelayed({
            if (t != token) return@postDelayed
            readState { st ->
                if (t != token) return@readState
                when {
                    st?.error?.isNotBlank() == true -> callback(Result.Failed(st.error))
                    st?.logout == true -> { CookieManager.getInstance().flush(); callback(Result.Success) }
                    n < 5 -> poll(t, n + 1, callback)
                    else -> load {
                        if (t != token) return@load
                        readState { s2 ->
                            if (t != token) return@readState
                            CookieManager.getInstance().flush()
                            callback(if (s2?.loggedIn == true) Result.Success else Result.Unknown)
                        }
                    }
                }
            }
        }, 1500)
    }

    private fun load(then: () -> Unit) {
        onLoaded = then
        web.loadUrl(HOME)
    }

    private data class State(val logout: Boolean, val password: Boolean, val error: String) {
        val loggedIn get() = logout || !password
    }

    private fun readState(cb: (State?) -> Unit) {
        web.evaluateJavascript(STATE_JS) { raw ->
            val s = unquote(raw)
            cb(try {
                val o = JSONObject(s)
                State(o.optBoolean("logout"), o.optBoolean("pw"), o.optString("error"))
            } catch (e: Exception) { null })
        }
    }

    private fun unquote(raw: String?): String =
        try { JSONTokener(raw ?: "null").nextValue() as? String ?: "" } catch (e: Exception) { "" }

    companion object {
        private const val HOME = "https://einthusan.tv/"

        private val STATE_JS = """
            (function(){
              var logout = !!document.querySelector('a[href*="logout" i],a[href*="signout" i],a[href*="log-out" i],[onclick*="logout" i]');
              var pw = !!document.querySelector('input[type=password]');
              var err = '';
              document.querySelectorAll('.error,.errors,.alert,.message,[class*=error],[class*=alert],[class*=notif]').forEach(function(e){
                if (e.offsetParent === null) return;
                var t = (e.textContent || '').replace(/\s+/g,' ').trim();
                if (t && t.length < 200 && /(incorrect|invalid|wrong|not match|failed|not found|does not exist|try again)/i.test(t)) err = t;
              });
              return JSON.stringify({logout: logout, pw: pw, error: err});
            })()
        """.trimIndent()

        private val LOGIN_JS = """
            function(email, pass){
              var pw = document.querySelector('input[type=password]');
              if (!pw) return 'nopw';
              var form = pw.form || pw.closest('form') || document;
              var em = form.querySelector('input[type=email]')
                    || form.querySelector('input[name*=mail i],input[id*=mail i],input[name*=user i],input[id*=user i],input[name*=login i]')
                    || form.querySelector('input[type=text]');
              if (!em) return 'noemail';
              function setv(el, v){
                var d = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
                d.set.call(el, v);
                el.dispatchEvent(new Event('input', {bubbles: true}));
                el.dispatchEvent(new Event('change', {bubbles: true}));
              }
              setv(em, email); setv(pw, pass);
              var btn = form.querySelector('button[type=submit],input[type=submit]') || form.querySelector('button');
              if (btn) btn.click();
              else if (form.requestSubmit) form.requestSubmit();
              else if (form.submit) form.submit();
              return 'submitted';
            }
        """.trimIndent()
    }
}
