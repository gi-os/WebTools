package com.gios.webtools

import android.app.Application
import android.util.Log
import com.gios.webtools.web.Bridge
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Holds the one GeckoRuntime a process may have, and installs the two bundled extensions:
 * uBlock Origin, and our own bridge (a carried-over login, the shake report's probe, banner
 * hiding). Both live in `assets/` and load from `resource://android/assets/…`.
 */
class WebToolsApp : Application() {

    lateinit var runtime: GeckoRuntime
        private set

    val bridge = Bridge()

    override fun onCreate() {
        super.onCreate()
        val blocking = ContentBlocking.Settings.Builder()
            .antiTracking(ContentBlocking.AntiTracking.DEFAULT or ContentBlocking.AntiTracking.STP)
            .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
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
                { Log.i(TAG, "uBlock Origin ${it?.metaData?.version} ready") },
                { Log.w(TAG, "uBlock Origin did not install: $it") },
            )
        wec.ensureBuiltIn("resource://android/assets/bridge/", "bridge@webtools.gios")
            .accept(
                { ext -> if (ext != null) bridge.attach(ext) else Log.w(TAG, "bridge: null extension") },
                { Log.w(TAG, "bridge did not install: $it") },
            )
    }

    companion object {
        const val TAG = "WebTools"
    }
}
