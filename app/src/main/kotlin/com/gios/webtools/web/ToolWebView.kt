package com.gios.webtools.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.gios.webtools.data.Tool
import com.gios.webtools.data.ToolKind
import java.io.File

/** What the screen around the WebView needs to hear. */
interface ToolWebListener {
    fun onBlocked(host: String)
    fun onProgress(loading: Boolean)
    fun onLoaded(url: String)
    fun onFailed(description: String)
}

/**
 * Builds the WebView for one tool: the origin wall, the per-bundle https origin, and the
 * settings that make an ordinary site usable on a 1080x1240 panel.
 */
object ToolWebView {

    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: Context,
        tool: Tool,
        toolDir: File,
        snapshotFile: File,
        listener: ToolWebListener,
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

        val ownDir = toolDir.canonicalPath

        view.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return assets?.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val ownFile = uri.scheme == "file" && (uri.path?.let { File(it).canonicalPath.startsWith(ownDir) } == true)
                return when (OriginRule.decide(tool.origins, uri.scheme, uri.host, ownFile)) {
                    OriginRule.Decision.ALLOW -> false
                    OriginRule.Decision.BLOCK -> {
                        listener.onBlocked(uri.host ?: uri.scheme.orEmpty())
                        true
                    }
                    OriginRule.Decision.HAND_OFF -> {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        true
                    }
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                listener.onProgress(true)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                listener.onProgress(false)
                if (url != null) listener.onLoaded(url)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                // Only the main document matters; a broken tracker pixel is not a failed page.
                if (request.isForMainFrame) {
                    listener.onFailed(error.description?.toString() ?: "could not load")
                }
            }
        }
        return view
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
}
