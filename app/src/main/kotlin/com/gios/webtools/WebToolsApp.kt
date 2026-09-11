package com.gios.webtools

import android.app.Application
import android.util.Log
import com.gios.light.common.report.LightReport
import com.gios.webtools.web.GeckoTool
import com.gios.webtools.web.Bridge
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Holds the one GeckoRuntime a process may have, and installs the two bundled extensions:
 * uBlock Origin, and our own bridge (a carried-over login, the shake report's probe, banner
 * hiding). Both live in `assets/` and load from `resource://android/assets/…`.
 *
 * Gecko runs pages in child processes (`:tab0`, `:gpu`, …) that share this Application class,
 * so `onCreate` runs in each of them. The runtime belongs to the main process only; creating one
 * inside a child kills the child, and the page with it. That was 2.0.5's instant crash.
 */
class WebToolsApp : Application() {

    lateinit var runtime: GeckoRuntime
        private set

    val bridge = Bridge()

    /**
     * The page's own account of its last failures, refreshed after every load.
     *
     * The bridge answers over a port, which no report can wait for, so the answer is kept here
     * and printed as it stood. A stale trail is still the trail of the thing that went wrong.
     */
    @Volatile var lastTrail: String = ""

    /** Whether each bundled extension actually installed. A blank page's first question. */
    @Volatile var ublockReady = false
    @Volatile var bridgeReady = false

    /** Why an extension did not install, in its own words. */
    @Volatile var ublockWhy = ""
    @Volatile var bridgeWhy = ""

    /** uBlock, once installed, so a tool can be opened without it. */
    @Volatile var ublock: org.mozilla.geckoview.WebExtension? = null

    /**
     * Everything blocking touches, for one tool that wants none of it.
     *
     * Turning off a session's tracking protection is only a third of the job. uBlock is an
     * extension and runs whatever the session thinks, and the cookie rule lives on the runtime,
     * not the session — and the cookie rule is the one that breaks a sign-in. A form that posts
     * to `login.example` and comes back "incorrect password" with the right password is almost
     * always a cookie the browser refused to keep, not a password the site refused to accept.
     */
    fun blocking(on: Boolean) {
        val want = if (on) ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS else ContentBlocking.CookieBehavior.ACCEPT_ALL
        runCatching { runtime.settings.contentBlocking.setCookieBehavior(want) }
        val ext = ublock ?: return
        val wec = runtime.webExtensionController
        runCatching {
            if (on) wec.enable(ext, org.mozilla.geckoview.WebExtensionController.EnableSource.APP)
            else wec.disable(ext, org.mozilla.geckoview.WebExtensionController.EnableSource.APP)
        }
    }

    /** The page on screen, if any, so a bug report can carry its log. Set by MainActivity. */
    @Volatile var currentPage: GeckoTool? = null

    /** True in the UI process, false in Gecko's child processes. */
    val isMainProcess: Boolean get() = Application.getProcessName() == packageName

    override fun onCreate() {
        super.onCreate()
        if (!isMainProcess) return
        // Shake, crash and failure reports go to gi-os/light-reports through the family's shared
        // reporter. The label is what the triage skill filters on.
        LightReport.install(context = this, appName = "Web Tools", label = "webtools", token = BuildConfig.REPORT_TOKEN)
        LightReport.details = {
            val p = currentPage
            buildString {
                appendLine("engine: GeckoView " + org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION)
                appendLine("bridge: " + if (bridge.connected) "connected" else "not connected")
                appendLine(
                    "extensions: ublock " + (if (ublockReady) "installed" else "MISSING " + ublockWhy.take(160)) +
                        ", bridge " + (if (bridgeReady) "installed" else "MISSING " + bridgeWhy.take(160)),
                )
                appendLine("blocking: " + (if (p?.tool?.blocking == false) "off for this tool" else "on") + ", ETP standard")
                if (lastTrail.isNotBlank()) {
                    appendLine()
                    appendLine("what the page asked for (4xx, POSTs, failures):")
                    appendLine(lastTrail.take(1_200))
                    appendLine()
                }
                if (p != null) {
                    appendLine("tool: ${p.tool.name} (${p.tool.kind}) home ${p.tool.url}")
                    appendLine("wall: " + (p.tool.origins.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "none"))
                    appendLine("now: ${p.currentUrl}")
                    appendLine()
                    append(p.log.dump())
                }
            }
        }

        val blocking = ContentBlocking.Settings.Builder()
            .antiTracking(ContentBlocking.AntiTracking.DEFAULT or ContentBlocking.AntiTracking.STP)
            // STANDARD, not STRICT. Strict adds Total Cookie Protection, which partitions the
            // cookies a cross-site sign-in depends on: AXS hands you to login.axs.com and back to
            // www.axs.com/login-redirect, and under strict that round trip ends on a blank page.
            // uBlock Origin still blocks the ads; this is the setting that decides whether a
            // login works, and a browser that cannot sign in is not one.
            .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.DEFAULT)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS)
            // No Google Safe Browsing lookups: no Google, and nothing phoned home per page.
            .safeBrowsing(ContentBlocking.SafeBrowsing.NONE)
            .build()
        val settings = GeckoRuntimeSettings.Builder()
            .javaScriptEnabled(true)
            .remoteDebuggingEnabled(false)
            .consoleOutput(false)
            .aboutConfigEnabled(false)
            .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_DARK)
            .contentBlocking(blocking)
            .build()
        runtime = GeckoRuntime.create(this, settings)

        val wec = runtime.webExtensionController
        wec.ensureBuiltIn("resource://android/assets/ublock/", "uBlock0@raymondhill.net")
            .accept(
                { ublock = it; ublockReady = it != null; Log.i(TAG, "uBlock Origin ${it?.metaData?.version} ready") },
                { ublockWhy = it?.message ?: it.toString(); Log.w(TAG, "uBlock Origin did not install: $it") },
            )
        wec.ensureBuiltIn("resource://android/assets/bridge/", "bridge@webtools.gios")
            .accept(
                { ext -> bridgeReady = ext != null; if (ext != null) bridge.attach(ext) else Log.w(TAG, "bridge: null extension") },
                { bridgeWhy = it?.message ?: it.toString(); Log.w(TAG, "bridge did not install: $it") },
            )
    }

    companion object {
        const val TAG = "WebTools"
    }
}
