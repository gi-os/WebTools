package com.gios.webtools.web

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.gios.webtools.data.Tool
import com.gios.webtools.data.ToolKind
import com.gios.webtools.report.PageLog
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.PanZoomController
import org.mozilla.geckoview.ScreenLength
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.io.InputStream

/** What the screen around the page needs to hear. */
interface ToolPageListener {
    fun onBlocked(host: String)
    fun onProgress(loading: Boolean)
    /** How far the page has come, 0..1. Drawn as a line along the top edge. */
    fun onProgressAt(fraction: Float)
    fun onLoaded(url: String)
    fun onFailed(description: String)
    /** The page is a bot gate that has refused this client. */
    fun onRefused(title: String)
    /** The site itself sent the first load to another host (tutanota.com -> tuta.com). */
    fun onRedirectedTo(host: String)
    /** A sign-in sent the page to an identity host, which is now allowed for this tool. */
    fun onSignInHop(host: String)
    /** A response the engine will not show (a PDF link, an attachment): save it. */
    fun onDownload(response: WebResponse)
}

/**
 * One open tool: a GeckoSession with the wall, the page log, and the few things the app does
 * to a page. Firefox's engine, so nothing here pretends to be anything.
 *
 * Bundles load from `resource://android/assets/builtin/<id>/`; a saved copy is a PDF the engine
 * wrote, opened back in its own viewer.
 */
class GeckoTool(
    private val context: Context,
    val tool: Tool,
    private val runtime: GeckoRuntime,
    private val listener: ToolPageListener,
    val log: PageLog,
) {
    val session: GeckoSession
    var scrollY: Int = 0
        private set
    var canGoBack: Boolean = false
    var canGoForward: Boolean = false
        private set
    var currentUrl: String? = null
        private set
    var viewingSaved: Boolean = false
        private set

    /** Grows when the first load is redirected elsewhere; the activity persists the addition. */
    private val allowed = ArrayList(tool.origins)
    private var settled = false

    init {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .useTrackingProtection(true)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .suspendMediaWhenInactive(true)
            .build()
        session = GeckoSession(settings)

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(s: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                val uri = Uri.parse(request.uri)
                val decision = OriginRule.decide(allowed, uri.scheme, uri.host, isOwnFile(uri))
                return when (decision) {
                    OriginRule.Decision.ALLOW -> GeckoResult.fromValue(AllowOrDeny.ALLOW)
                    OriginRule.Decision.HAND_OFF -> {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                    OriginRule.Decision.BLOCK -> {
                        val host = uri.host
                        val isHttp = uri.scheme == "http" || uri.scheme == "https"
                        if (isHttp && host != null && OriginRule.looksLikeSignIn(request.uri)) {
                            // A sign-in hop. Follow it and remember the host for this tool.
                            val h = host.lowercase().removePrefix("www.")
                            allowed.add(h)
                            log.nav("sign-in  ${request.uri}")
                            listener.onSignInHop(h)
                            GeckoResult.fromValue(AllowOrDeny.ALLOW)
                        } else if (isHttp && host != null && request.isRedirect && !settled) {
                            // The site moved. A server redirect during the first load is the site's
                            // own doing, not a wander. Follow, remember.
                            val h = host.lowercase().removePrefix("www.")
                            allowed.add(h)
                            log.nav("follow   ${request.uri}")
                            listener.onRedirectedTo(h)
                            GeckoResult.fromValue(AllowOrDeny.ALLOW)
                        } else {
                            log.navBlocked(request.uri)
                            listener.onBlocked(host ?: uri.scheme.orEmpty())
                            GeckoResult.fromValue(AllowOrDeny.DENY)
                        }
                    }
                }
            }

            override fun onLocationChange(s: GeckoSession, url: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
                currentUrl = url
                log.nav(url)
            }

            override fun onCanGoBack(s: GeckoSession, value: Boolean) {
                canGoBack = value
            }

            override fun onCanGoForward(s: GeckoSession, value: Boolean) {
                canGoForward = value
            }

            /**
             * No second window, but the page it wanted is not lost.
             *
             * Returning null denies the popup, which used to be the whole of it — and a sign-in
             * button that opens a window then leaves a white screen behind is the commonest way
             * for that to go wrong (AXS does this). The address is loaded here instead, in the
             * one session there is, if the wall or the sign-in rule allows it.
             */
            override fun onNewSession(s: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                val u = Uri.parse(uri)
                val host = u.host
                val ok = OriginRule.decide(allowed, u.scheme, host, false) == OriginRule.Decision.ALLOW ||
                    OriginRule.looksLikeSignIn(uri)
                if (ok) {
                    if (host != null) allowed.add(host.lowercase().removePrefix("www."))
                    log.nav("popup→here $uri")
                    session.loadUri(uri)
                } else {
                    log.navBlocked("popup $uri")
                    listener.onBlocked(host ?: "a new window")
                }
                return null
            }

            override fun onLoadError(s: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
                val why = "error ${error.category}/${error.code}"
                log.error(uri ?: "?", why)
                if (uri != null && uri.startsWith("about:reader")) {
                    // Reader view could not take this page; show the page itself.
                    log.nav("reader fell back to ${tool.url}")
                    session.loadUri(tool.url)
                    return null
                }
                listener.onFailed(why)
                return null
            }
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(s: GeckoSession, url: String) {
                listener.onProgress(true)
                listener.onProgressAt(0f)
            }

            override fun onProgressChange(s: GeckoSession, progress: Int) {
                listener.onProgressAt(progress / 100f)
            }

            override fun onPageStop(s: GeckoSession, success: Boolean) {
                settled = true
                listener.onProgress(false)
                if (success) listener.onLoaded(currentUrl ?: tool.url)
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(s: GeckoSession, title: String?) {
                log.title(title)
                if (title != null && GATE_TITLES.any { title.contains(it, ignoreCase = true) }) listener.onRefused(title)
            }

            override fun onCrash(s: GeckoSession) {
                log.error(currentUrl ?: "?", "content process crashed")
                listener.onFailed("the page crashed")
            }

            override fun onKill(s: GeckoSession) {
                log.error(currentUrl ?: "?", "content process killed")
            }

            override fun onExternalResponse(s: GeckoSession, response: WebResponse) {
                log.nav("download ${response.uri}")
                listener.onDownload(response)
            }
        }

        session.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(s: GeckoSession, x: Int, y: Int) {
                scrollY = y
            }
        }

        // Pages get no permissions at all: no location, no camera, no notifications.
        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(s: GeckoSession, perm: GeckoSession.PermissionDelegate.ContentPermission): GeckoResult<Int>? =
                GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)

            override fun onAndroidPermissionsRequest(s: GeckoSession, permissions: Array<out String>?, callback: GeckoSession.PermissionDelegate.Callback) {
                callback.reject()
            }

            override fun onMediaPermissionRequest(s: GeckoSession, uri: String, video: Array<out GeckoSession.PermissionDelegate.MediaSource>?, audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?, callback: GeckoSession.PermissionDelegate.MediaCallback) {
                callback.reject()
            }
        }

        session.open(runtime)
    }

    private fun isOwnFile(uri: Uri): Boolean {
        if (uri.scheme != "file") return false
        val p = uri.path ?: return false
        // Saved copies live under files/tools, downloads under files/downloads; nothing else.
        val own = listOf("tools", "downloads").map { File(context.filesDir, it).canonicalPath }
        return runCatching { val c = File(p).canonicalPath; own.any { c.startsWith(it) } }.getOrDefault(false)
    }

    /** The address to load: bundle, saved copy, reader view, or the site. */
    fun open(snapshot: File, saved: Boolean, resumeAt: String? = null) {
        val url = when {
            tool.kind == ToolKind.BUNDLE -> "resource://android/assets/builtin/${tool.id}/index.html"
            saved && snapshot.exists() -> { viewingSaved = true; Uri.fromFile(snapshot).toString() }
            resumeAt != null -> resumeAt
            tool.reader -> "about:reader?url=" + Uri.encode(tool.url)
            else -> tool.url
        }
        log.nav("open     $url")
        session.loadUri(url)
    }

    fun goBack() = session.goBack()
    fun reload() = session.reload()
    fun goForward() = session.goForward()

    fun scrollBy(dyPx: Int) {
        session.panZoomController.scrollBy(
            ScreenLength.zero(),
            ScreenLength.fromPixels(dyPx.toDouble()),
            PanZoomController.SCROLL_BEHAVIOR_SMOOTH,
        )
    }

    /** The engine prints the page to a PDF; the app keeps the bytes. */
    fun snapshot(target: File, done: (Boolean) -> Unit) {
        session.saveAsPdf().accept(
            { stream: InputStream? ->
                val ok = runCatching {
                    if (stream == null) return@runCatching false
                    val tmp = File(target.parentFile, target.name + ".tmp")
                    stream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    tmp.length() > 0 && tmp.renameTo(target)
                }.getOrDefault(false)
                done(ok)
            },
            { e -> Log.w("WebTools", "saveAsPdf failed: $e"); done(false) },
        )
    }

    fun setActive(active: Boolean) = session.setActive(active)

    fun close() {
        runCatching { session.close() }
    }

    companion object {
        /** Titles bot gates set when they have decided against the client. */
        val GATE_TITLES = listOf(
            "Your Browsing Activity Has Been Paused",
            "Let's Get Your Identity Verified",
            "Pardon Our Interruption",
            "Access Denied",
            "Just a moment",
        )
    }
}
