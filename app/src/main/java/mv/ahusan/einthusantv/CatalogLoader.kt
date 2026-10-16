package mv.ahusan.einthusantv

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONTokener

/**
 * Loads einthusan.tv result pages in a hidden WebView (same cookies as the
 * main browser) and reads the movie list from the page. One page at a time.
 */
class CatalogLoader(private val webView: WebView) {

    private class Request(val url: String, val callback: (List<Movie>) -> Unit)

    private val queue = ArrayDeque<Request>()
    private val handler = Handler(Looper.getMainLooper())
    private var current: Request? = null
    private var token = 0
    private var extracted = false

    init {
        setup()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setup() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            blockNetworkImage = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {}

            override fun onPageFinished(view: WebView, url: String?) {
                val t = token
                handler.postDelayed({ extract(t, 0) }, 900)
            }
        }
    }

    fun load(url: String, callback: (List<Movie>) -> Unit) {
        queue.addLast(Request(url, callback))
        if (current == null) next()
    }

    fun cancelAll() {
        queue.clear()
        current = null
        token++
        handler.removeCallbacksAndMessages(null)
        webView.stopLoading()
    }

    private fun next() {
        val req = queue.removeFirstOrNull() ?: run { current = null; return }
        current = req
        extracted = false
        val t = ++token
        webView.loadUrl(req.url)
        // Safety timeout
        handler.postDelayed({ if (t == token && !extracted) finish(t, emptyList()) }, 25_000)
    }

    private fun extract(t: Int, attempt: Int) {
        if (t != token || extracted) return
        webView.evaluateJavascript(EXTRACT_JS) { raw ->
            if (t != token || extracted) return@evaluateJavascript
            val movies = parse(raw)
            if (movies.isEmpty() && attempt < 3) {
                handler.postDelayed({ extract(t, attempt + 1) }, 1500)
            } else {
                finish(t, movies)
            }
        }
    }

    private fun finish(t: Int, movies: List<Movie>) {
        if (t != token) return
        extracted = true
        val req = current
        current = null
        req?.callback?.invoke(movies)
        next()
    }

    private fun parse(raw: String?): List<Movie> {
        if (raw.isNullOrBlank() || raw == "null") return emptyList()
        return try {
            val json = JSONTokener(raw).nextValue() as? String ?: return emptyList()
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Movie(
                    title = o.optString("title"),
                    url = o.optString("url"),
                    poster = o.optString("poster"),
                    year = o.optString("year"),
                    synopsis = o.optString("synopsis"),
                    subtitles = o.optBoolean("subs"),
                    imdb = o.optString("imdb"),
                    director = o.optString("director"),
                    lead = o.optString("lead"),
                    leadRole = o.optString("leadRole")
                )
            }.filter { it.title.isNotBlank() && it.url.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private val EXTRACT_JS = """
            (function(){
              function key(h){ return (h||'').split('#')[0].split('?')[0]; }
              function watchKeys(el){
                var s={}, n=0;
                el.querySelectorAll('a[href*="/movie/watch/"]').forEach(function(a){
                  var k=key(a.href); if(!s[k]){s[k]=1;n++;}
                });
                return n;
              }
              var out=[], seen={};
              document.querySelectorAll('a[href*="/movie/watch/"]').forEach(function(a){
                var k=key(a.href);
                if(seen[k]) return;
                var c=a;
                while(c.parentElement && c.parentElement!==document.body && watchKeys(c.parentElement)===1){
                  c=c.parentElement;
                }
                var img=c.querySelector('img[src*="moviecovers"],img[data-src*="moviecovers"],img[src*="default-img"]');
                if(!img) return;
                seen[k]=1;
                var poster=img.getAttribute('data-src')||img.getAttribute('src')||'';
                try{ poster=new URL(poster, location.href).href; }catch(e){}
                var h=c.querySelector('h3,h2,h4');
                var title=h?h.textContent.trim():'';
                if(!title){
                  c.querySelectorAll('a[href*="/movie/watch/"]').forEach(function(x){
                    var t=x.textContent.trim(); if(t && !title) title=t;
                  });
                }
                if(!title) title=img.getAttribute('alt')||'';
                var year='';
                var tw=document.createTreeWalker(c, NodeFilter.SHOW_TEXT), tn;
                while((tn=tw.nextNode())){
                  var mt=tn.nodeValue.match(/^\s*((?:19|20)\d{2})\s*$/); if(mt){ year=mt[1]; break; }
                }
                if(!year){ var ym=(c.textContent||'').match(/(?:^|\D)((?:19|20)\d{2})(?!\d)/); if(ym) year=ym[1]; }
                var syn='';
                c.querySelectorAll('p').forEach(function(p){
                  var t=p.textContent.trim(); if(t.length>syn.length && t.length>40) syn=t;
                });
                var subs=/subtitle/i.test(c.innerHTML);
                var imdb='';
                c.querySelectorAll('a[href*="imdb.com/title/"]').forEach(function(x){
                  var mi=x.href.match(/\/title\/(tt\d+)/); if(mi && !imdb) imdb=mi[1];
                });
                var director='', lead='', leadRole='';
                c.querySelectorAll('a[href*="find=Cast"]').forEach(function(x){
                  try{
                    var u=new URL(x.href, location.href), id=u.searchParams.get('id')||'',
                        role=(u.searchParams.get('role')||'').toUpperCase();
                    if(!/^[A-Za-z0-9]{4,20}$/.test(id)) return;
                    if(role==='DIRECTOR'){ if(!director) director=id; }
                    else if(!lead && role!=='MUSIC_DIRECTOR'){ lead=id; leadRole=role; }
                  }catch(e){}
                });
                out.push({title:title.replace(/\s+/g,' '), url:k+'?'+(a.href.split('?')[1]||''),
                  poster:poster, year:year, synopsis:syn.replace(/\s+/g,' '), subs:subs,
                  imdb:imdb, director:director, lead:lead, leadRole:leadRole});
              });
              return JSON.stringify(out);
            })();
        """.trimIndent()
    }
}
