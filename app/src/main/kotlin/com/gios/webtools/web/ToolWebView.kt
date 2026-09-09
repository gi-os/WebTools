package com.gios.webtools.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.gios.webtools.data.Tool
import com.gios.webtools.data.ToolKind
import com.gios.webtools.report.PageLog
import java.io.ByteArrayInputStream
import java.io.File

/** What the screen around the WebView needs to hear. */
interface ToolWebListener {
    fun onBlocked(host: String)
    fun onProgress(loading: Boolean)
    fun onLoaded(url: String)
    fun onFailed(description: String)
    /** The page is a bot gate that has refused the embedded view. */
    fun onRefused(title: String)
    /**
     * The site itself sent the first load to another host (tutanota.com -> tuta.com). Followed,
     * and worth remembering on the tool.
     */
    fun onRedirectedTo(host: String)
}

/**
 * Builds the WebView for one tool: the origin wall, the ad block, the per-bundle https origin,
 * the settings that make an ordinary site usable on a 1080x1240 panel, and a page log the shake
 * report reads from.
 */
object ToolWebView {

    /** Titles Ticketmaster's EPS gate sets when it has decided against the client. */
    private val GATE_TITLES = listOf(
        "Your Browsing Activity Has Been Paused",
        "Let's Get Your Identity Verified",
        "Pardon Our Interruption",
        "Access Denied",
        "Just a moment",
    )

    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: Context,
        tool: Tool,
        toolDir: File,
        listener: ToolWebListener,
        log: PageLog,
    ): WebView {
        val view = WebView(context)
        val s: WebSettings = view.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        // A saved copy is a file:// load, and only from our own directory (the client checks).
        s.allowFileAccess = true
        s.allowContentAccess = false
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.mediaPlaybackRequiresUserGesture = true
        s.setSupportMultipleWindows(false)
        s.javaScriptCanOpenWindowsAutomatically = false
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.setGeolocationEnabled(false)
        lookLikeChrome(context, s)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)

        view.setBackgroundColor(android.graphics.Color.BLACK)
        view.isVerticalScrollBarEnabled = false
        view.isHorizontalScrollBarEnabled = false

        // Bundles: https://<id>.webtools.internal/ -> files/tools/<id>/, plus the shared
        // stylesheet at /_light/ from the APK's assets.
        val assets: WebViewAssetLoader? = if (tool.kind == ToolKind.BUNDLE) {
            WebViewAssetLoader.Builder()
                .setDomain(tool.bundleHost)
                .addPathHandler("/_light/", WebViewAssetLoader.AssetsPathHandler(context))
                .addPathHandler("/", WebViewAssetLoader.InternalStoragePathHandler(context, toolDir))
                .build()
        } else {
            null
        }
        val blockList: BlockList? = if (tool.kind == ToolKind.SITE) BlockLists.get(context) else null
        val ownDir = toolDir.canonicalPath
        // Grows when the first load is redirected elsewhere; the activity persists the addition.
        val allowed = ArrayList(tool.origins)
        var settled = false

        if (tool.kind == ToolKind.SITE) installDocumentStartScript(view)

        view.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                assets?.let { return it.shouldInterceptRequest(request.url) }
                val host = request.url.host
                if (blockList != null && !request.isForMainFrame && blockList.blocks(host)) {
                    log.blocked(host ?: "?")
                    return empty()
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val ownFile = uri.scheme == "file" && (uri.path?.let { File(it).canonicalPath.startsWith(ownDir) } == true)
                return when (OriginRule.decide(allowed, uri.scheme, uri.host, ownFile)) {
                    OriginRule.Decision.ALLOW -> false
                    OriginRule.Decision.BLOCK -> {
                        val host = uri.host
                        val isHttp = uri.scheme == "http" || uri.scheme == "https"
                        if (isHttp && host != null && request.isRedirect && request.isForMainFrame && !settled) {
                            // The site moved (tutanota.com is tuta.com now). A server redirect during
                            // the first load is the site's own doing, not a wander. Follow, remember.
                            val h = host.lowercase().removePrefix("www.")
                            allowed.add(h)
                            log.nav("follow   $uri")
                            listener.onRedirectedTo(h)
                            return false
                        }
                        log.navBlocked(uri.toString())
                        listener.onBlocked(host ?: uri.scheme.orEmpty())
                        true
                    }
                    OriginRule.Decision.HAND_OFF -> {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        true
                    }
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                log.nav(url)
                listener.onProgress(true)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                settled = true
                listener.onProgress(false)
                if (url != null) listener.onLoaded(url)
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame) {
                    log.http(request.url.toString(), errorResponse.statusCode, errorResponse.responseHeaders)
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                // Only the main document matters; a broken tracker pixel is not a failed page.
                if (request.isForMainFrame) {
                    val why = error.description?.toString() ?: "could not load"
                    log.error(request.url.toString(), why)
                    listener.onFailed(why)
                }
            }
        }

        view.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String?) {
                log.title(title)
                if (title != null && GATE_TITLES.any { title.contains(it, ignoreCase = true) }) {
                    listener.onRefused(title)
                }
            }

            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                log.console(m.messageLevel().name, m.message(), m.sourceId(), m.lineNumber())
                return true
            }
        }
        return view
    }

    private fun empty() = WebResourceResponse("text/plain", "utf-8", 200, "OK", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    /**
     * A stock WebView announces itself three ways, and bot checks refuse on any of them:
     *
     *  1. the user agent carries `; wv` and `Version/4.0`, which no browser sends;
     *  2. the client hints name the brand `Android WebView`, so a cleaned user agent is caught as
     *     a mismatch;
     *  3. every request carries `X-Requested-With: <package>`.
     *
     * This is the page the same person would get in Chrome. It is not a disguise, it is the
     * WebView not volunteering that it is embedded. It is also not enough for Kasada (see
     * [Engine.BROWSER][com.gios.webtools.data.Engine.BROWSER]).
     */
    private fun lookLikeChrome(context: Context, s: WebSettings) {
        runCatching {
            val stock = WebSettings.getDefaultUserAgent(context)
            s.userAgentString = stock
                .replace("; wv", "")
                .replace(Regex("""Version/\d+(\.\d+)* """), "")
        }
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
                val meta = WebSettingsCompat.getUserAgentMetadata(s)
                val brands = meta.brandVersionList.map { b ->
                    if (b.brand.contains("WebView", ignoreCase = true)) {
                        UserAgentMetadata.BrandVersion.Builder()
                            .setBrand("Google Chrome")
                            .setMajorVersion(b.majorVersion)
                            .setFullVersion(b.fullVersion)
                            .build()
                    } else {
                        b
                    }
                }
                WebSettingsCompat.setUserAgentMetadata(
                    s,
                    UserAgentMetadata.Builder(meta).setBrandVersionList(brands).setMobile(true).build(),
                )
            }
        }
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(s, emptySet())
            }
        }
    }

    /**
     * Runs before any page script on every site: hides "open in the app" banners by the class
     * and id names the common vendors use, and gives the page a `window.chrome` object when the
     * WebView has none (Chrome always has one; its absence is a tell).
     */
    private fun installDocumentStartScript(view: WebView) {
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(view, START_SCRIPT, setOf("*"))
            }
        }
    }

    /** Fallback for a WebView without DOCUMENT_START_SCRIPT: same script, after load. */
    fun applyPageFixes(view: WebView) {
        runCatching {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                view.evaluateJavascript(START_SCRIPT, null)
            }
        }
    }

    /** Asks the page what a bot check would see. Answers as one JSON line via [done]. */
    fun probe(view: WebView, done: (String) -> Unit) {
        view.evaluateJavascript(PROBE_SCRIPT) { done(it ?: "null") }
    }

    /** Load the tool live, or its saved copy. */
    fun open(view: WebView, tool: Tool, snapshotFile: File, saved: Boolean) {
        if (saved && snapshotFile.exists()) {
            view.loadUrl(Uri.fromFile(snapshotFile).toString())
        } else {
            view.loadUrl(tool.url)
        }
    }

    /** Writes the current page as an MHTML archive. Calls back with success. */
    fun snapshot(view: WebView, target: File, done: (Boolean) -> Unit) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        view.saveWebArchive(tmp.absolutePath, false) { path ->
            val ok = path != null && tmp.exists() && tmp.length() > 0 && tmp.renameTo(target)
            if (!ok) tmp.delete()
            done(ok)
        }
    }

    fun flushCookies() {
        runCatching { CookieManager.getInstance().flush() }
    }

    private const val BANNER_CSS = """
        .smartbanner, #smartbanner, .smart-banner, .smartbanner-show,
        #branch-banner-iframe, .branch-banner, #branch-banner, .branch-journeys-top, .branch-animation,
        .af-banner, #af-smart-banner, .af-smart-banner,
        [class*="app-banner" i], [id*="app-banner" i], [class*="appbanner" i], [id*="appbanner" i],
        [class*="download-app" i], [class*="open-in-app" i], [class*="openinapp" i], [class*="get-the-app" i],
        [class*="install-app" i], [data-testid*="app-banner" i], [data-testid*="smart-banner" i],
        .js-app-banner, .mobile-app-banner, .app-download-banner, .app-install-banner,
        a[href^="intent://"], a[href*="play.google.com/store/apps"], a[href*="apps.apple.com"]
        { display: none !important; visibility: hidden !important; height: 0 !important; }
        body.smartbanner-show { margin-top: 0 !important; }
    """

    private val START_SCRIPT = """
        (function () {
          try {
            if (typeof window.chrome === 'undefined') {
              window.chrome = { app: { isInstalled: false }, runtime: {}, csi: function () { return {}; }, loadTimes: function () { return {}; } };
            }
          } catch (e) {}
          var css = ${'`'}$BANNER_CSS${'`'};
          function add() {
            try {
              if (document.getElementById('__wt_banner_css')) return;
              var st = document.createElement('style'); st.id = '__wt_banner_css'; st.textContent = css;
              (document.head || document.documentElement).appendChild(st);
            } catch (e) {}
          }
          add();
          document.addEventListener('DOMContentLoaded', add);
          try { new MutationObserver(add).observe(document.documentElement, { childList: true }); } catch (e) {}
        })();
    """.trimIndent()

    private val PROBE_SCRIPT = """
        (function () {
          function t(x) { try { return typeof x; } catch (e) { return 'err'; } }
          var o = {
            title: document.title, url: location.href,
            ua: navigator.userAgent,
            brands: (navigator.userAgentData && navigator.userAgentData.brands) ? navigator.userAgentData.brands.map(function (b) { return b.brand + ' ' + b.version; }) : null,
            mobile: navigator.userAgentData ? navigator.userAgentData.mobile : null,
            chrome: t(window.chrome), chromeRuntime: t(window.chrome && window.chrome.runtime),
            webdriver: navigator.webdriver, plugins: navigator.plugins ? navigator.plugins.length : null,
            share: t(navigator.share), print: t(window.print), languages: navigator.languages,
            cookies: navigator.cookieEnabled, dpr: window.devicePixelRatio,
            inner: [window.innerWidth, window.innerHeight], outer: [window.outerWidth, window.outerHeight],
            screen: [screen.width, screen.height], touch: navigator.maxTouchPoints,
            kasada: t(window.KPSDK), nudata: t(window.ndsapi), recaptcha: t(window.grecaptcha),
            abuse: !!document.querySelector('abuse-component'),
            text: (document.body && document.body.innerText || '').replace(/\s+/g, ' ').slice(0, 600)
          };
          return JSON.stringify(o);
        })();
    """.trimIndent()
}
