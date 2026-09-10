package com.gios.webtools.web

import android.content.Context
import android.content.Intent
import android.net.CaptivePortal
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL

/**
 * Hotel and café Wi-Fi: the sign-in page LightOS has no browser for.
 *
 * Such a network answers every request with its own login page until you submit it, and never
 * validates until you do. Two things make the sign-in work, both learned in BrightControl's
 * Wi-Fi login screen:
 *
 *  - **The process binds to the captive network.** An unvalidated Wi-Fi is exactly what Android
 *    routes around; unbound, the engine's requests would ride cellular and the portal would
 *    never see them. Gecko does its networking in this process, so the bind reaches it.
 *  - **Success is probed, not inferred.** A request to a known 204 endpoint over that network
 *    every few seconds; the first 204 after a non-204 means the gate opened. Portals end their
 *    flows a dozen ways and none is a reliable signal.
 *
 * If the system handed over a [CaptivePortal] (the `CAPTIVE_PORTAL` action), success goes back
 * through it so Android marks the network usable. **A VPN defeats this**: netd refuses a UID
 * under a VPN any other network (EPERM), and only the platform's own sign-in app is exempt.
 * BrightControl knows how to reach that app; this one just says so.
 */
class Portal(private val context: Context) {
    interface Listener {
        fun onStatus(text: String)
        fun onOpen(url: String)
        fun onSignedIn()
        fun onGaveUp(why: String)
    }

    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var network: Network? = null
    private var captive: CaptivePortal? = null
    private var seenClosed = false
    private var probes = 0
    private var running = false
    private var listener: Listener? = null

    val active: Boolean get() = running

    /** The network the system named, else the one it has flagged as a portal. */
    private fun findNetwork(intent: Intent?): Network? {
        val named: Network? = intent?.let {
            if (Build.VERSION.SDK_INT >= 33) it.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK, Network::class.java)
            else @Suppress("DEPRECATION") it.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK)
        }
        if (named != null) return named
        val nets = cm.allNetworks
        return nets.firstOrNull { cm.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true }
            ?: nets.firstOrNull {
                val c = cm.getNetworkCapabilities(it) ?: return@firstOrNull false
                c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
    }

    fun begin(intent: Intent?, l: Listener) {
        stop()
        listener = l
        captive = intent?.let {
            if (Build.VERSION.SDK_INT >= 33) it.getParcelableExtra(ConnectivityManager.EXTRA_CAPTIVE_PORTAL, CaptivePortal::class.java)
            else @Suppress("DEPRECATION") it.getParcelableExtra(ConnectivityManager.EXTRA_CAPTIVE_PORTAL)
        }
        val net = findNetwork(intent)
        if (net == null) { l.onGaveUp("No Wi-Fi waiting for a sign-in"); return }
        network = net
        if (!cm.bindProcessToNetwork(net)) {
            val vpn = cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
            l.onGaveUp(if (vpn) "A VPN is up, so this app may not use the Wi-Fi. Turn it off, or sign in from BrightControl." else "Could not hold the Wi-Fi")
            return
        }
        // Open the page first: opening closes whatever page was up, and closing a page stops
        // any sign-in in progress, which must not be this one.
        l.onOpen(PROBE_URL)
        running = true
        seenClosed = false
        probes = 0
        l.onStatus("Wi-Fi sign-in · waiting for the page")
        main.postDelayed(probeJob, PROBE_EVERY_MS)
    }

    private val probeJob = object : Runnable {
        override fun run() {
            if (!running) return
            val net = network ?: return
            Thread({
                val code = runCatching {
                    val c = net.openConnection(URL(PROBE_URL)) as HttpURLConnection
                    c.instanceFollowRedirects = false
                    c.connectTimeout = 4000
                    c.readTimeout = 4000
                    c.useCaches = false
                    c.responseCode.also { c.disconnect() }
                }.getOrDefault(-1)
                main.post { onProbe(code) }
            }, "portal-probe").start()
        }
    }

    private fun onProbe(code: Int) {
        if (!running) return
        probes++
        val verdict = verdict(seenClosed, code)
        if (verdict == Verdict.CLOSED) seenClosed = true
        when (verdict) {
            Verdict.OPEN -> {
                running = false
                runCatching { captive?.reportCaptivePortalDismissed() }
                cm.bindProcessToNetwork(null)
                listener?.onSignedIn()
            }
            Verdict.CLOSED -> {
                listener?.onStatus("Wi-Fi sign-in · the gate is still closed")
                main.postDelayed(probeJob, PROBE_EVERY_MS)
            }
            Verdict.OPEN_ALREADY -> {
                // 204 before any closed answer: nothing to sign in to, or the system already did.
                if (probes >= 3) {
                    running = false
                    cm.bindProcessToNetwork(null)
                    listener?.onGaveUp("This Wi-Fi is already open")
                } else {
                    main.postDelayed(probeJob, PROBE_EVERY_MS)
                }
            }
            Verdict.SILENT -> {
                listener?.onStatus("Wi-Fi sign-in · nothing answers yet")
                main.postDelayed(probeJob, PROBE_EVERY_MS)
            }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        main.removeCallbacks(probeJob)
        cm.bindProcessToNetwork(null)
    }

    enum class Verdict { OPEN, OPEN_ALREADY, CLOSED, SILENT }

    companion object {
        const val PROBE_URL = "http://connectivitycheck.gstatic.com/generate_204"
        const val PROBE_EVERY_MS = 4000L

        /** What one probe answer means, given whether the gate has been seen closed before. */
        fun verdict(seenClosed: Boolean, code: Int): Verdict = when {
            code == 204 && seenClosed -> Verdict.OPEN
            code == 204 -> Verdict.OPEN_ALREADY
            code in 200..399 -> Verdict.CLOSED
            code >= 400 -> Verdict.CLOSED
            else -> Verdict.SILENT
        }
    }
}
