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
 * the site's own login page in a hidden WebView. Cookies are shared with the
 * watch screen, so a sign-in here also applies to playback.
 *
 * How einthusan.tv behaves (checked from the live site):
 *  - /account/ redirects to /login/ when signed out
 *  - /login/ has #login-email, #login-password and a #login-submit link
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
        // Hidden helper view: never let the remote focus land on it
        web.isFocusable = false
        web.isFocusableInTouchMode = false
        web.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
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
        load(ACCOUNT) {
            if (t != token) return@load
            handler.removeCallbacks(timeout)
            callback(signedInByUrl())
        }
    }

    fun login(email: String, password: String, callback: (Result) -> Unit) {
        val t = ++token
        load(LOGIN) {
            if (t != token) return@load
            if (signedInByUrl() == true) { callback(Result.Success); return@load }   // /login/ sent us away: already in
            val js = "($LOGIN_JS)(${JSONObject.quote(email)}, ${JSONObject.quote(password)})"
            web.evaluateJavascript(js) { raw ->
                if (t != token) return@evaluateJavascript
                when (unquote(raw)) {
                    "noform" -> callback(Result.Unknown)
                    else -> poll(t, 0, callback)
                }
            }
        }
    }

    /** After pressing Login: wait to leave the login page, or for an error message. */
    private fun poll(t: Int, n: Int, callback: (Result) -> Unit) {
        handler.postDelayed({
            if (t != token) return@postDelayed
            if (!onLoginPage()) { verify(t, callback); return@postDelayed }
            web.evaluateJavascript(ERROR_JS) { raw ->
                if (t != token) return@evaluateJavascript
                val err = unquote(raw)
                when {
                    err.isNotBlank() -> callback(Result.Failed(err))
                    n < 7 -> poll(t, n + 1, callback)
                    else -> verify(t, callback)
                }
            }
        }, 1500)
    }

    /** Final answer: open /account/ — if it doesn't bounce to /login/, we're signed in. */
    private fun verify(t: Int, callback: (Result) -> Unit) {
        load(ACCOUNT) {
            if (t != token) return@load
            CookieManager.getInstance().flush()
            callback(if (signedInByUrl() == true) Result.Success else Result.Unknown)
        }
    }

    private fun onLoginPage(): Boolean = web.url?.contains("/login") == true

    private fun signedInByUrl(): Boolean? {
        val u = web.url ?: return null
        if (!u.contains("einthusan")) return null
        return !u.contains("/login")
    }

    private fun load(url: String, then: () -> Unit) {
        onLoaded = then
        web.loadUrl(url)
    }

    private fun unquote(raw: String?): String =
        try { JSONTokener(raw ?: "null").nextValue() as? String ?: "" } catch (e: Exception) { "" }

    companion object {
        private const val ACCOUNT = "https://einthusan.tv/account/"
        private const val LOGIN = "https://einthusan.tv/login/"

        private val LOGIN_JS = """
            function(email, pass){
              var em = document.getElementById('login-email') || document.querySelector('input[type=email]');
              var pw = document.getElementById('login-password') || document.querySelector('input[type=password]');
              if (!em || !pw) return 'noform';
              function setv(el, v){
                el.focus();
                var d = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
                d.set.call(el, v);
                el.dispatchEvent(new Event('input', {bubbles: true}));
                el.dispatchEvent(new Event('change', {bubbles: true}));
                el.dispatchEvent(new KeyboardEvent('keyup', {bubbles: true}));
              }
              setv(em, email); setv(pw, pass);
              var btn = document.getElementById('login-submit')
                     || document.querySelector('#login-form button, #login-form a[id*=submit], button[type=submit], input[type=submit]');
              if (btn) btn.click();
              else pw.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter', keyCode: 13, bubbles: true}));
              return 'submitted';
            }
        """.trimIndent()

        // Any visible message on the login page that looks like a sign-in error
        private val ERROR_JS = """
            (function(){
              var root = document.getElementById('login-form') || document.body;
              var found = '';
              root.querySelectorAll('*').forEach(function(e){
                if (found || e.children.length > 2 || e.offsetParent === null) return;
                var t = (e.textContent || '').replace(/\s+/g, ' ').trim();
                if (t.length > 3 && t.length < 160 &&
                    /(incorrect|invalid|wrong|not match|failed|not found|does not exist|try again|not registered|unverified|verify your)/i.test(t)) found = t;
              });
              return found;
            })()
        """.trimIndent()
    }
}
