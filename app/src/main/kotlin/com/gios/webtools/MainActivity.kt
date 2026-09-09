package com.gios.webtools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.gios.webtools.data.Engine
import com.gios.webtools.data.Tool
import com.gios.webtools.data.ToolKind
import com.gios.webtools.data.ToolStore
import com.gios.webtools.gesture.PullDownFrame
import com.gios.webtools.hw.LightKey
import com.gios.webtools.hw.LightKeys
import com.gios.webtools.hw.LocalWheelBus
import com.gios.webtools.hw.WheelBus
import com.gios.webtools.report.PageLog
import com.gios.webtools.report.Reports
import com.gios.webtools.report.ShakeGesture
import com.gios.webtools.ui.AddScreen
import com.gios.webtools.ui.ExitIndicator
import com.gios.webtools.ui.InfoScreen
import com.gios.webtools.ui.ListScreen
import com.gios.webtools.ui.ReportSheet
import com.gios.webtools.ui.Starter
import com.gios.webtools.ui.ToolScreen
import com.gios.webtools.ui.TutorialScreen
import com.gios.webtools.ui.theme.WebToolsTheme
import com.gios.webtools.web.BlockLists
import com.gios.webtools.web.QrPayload
import com.gios.webtools.web.ToolWebListener
import com.gios.webtools.web.ToolWebView
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private sealed class Screen {
    data object Tutorial : Screen()
    data object Home : Screen()
    data class Add(val message: String? = null) : Screen()
    data class Info(val id: String) : Screen()
    data class Page(val id: String, val saved: Boolean) : Screen()
}

/**
 * One activity, a handful of screens, one WebView at a time.
 *
 * The root view is a [PullDownFrame], so the exit gesture works the same over a page as over
 * the list. `dispatchKeyEvent` is where the wheel and the camera button can be seen.
 */
class MainActivity : ComponentActivity() {

    private lateinit var store: ToolStore
    private val wheel = WheelBus()
    private val handler = Handler(Looper.getMainLooper())

    private var screen by mutableStateOf<Screen>(Screen.Home)
    private var status by mutableStateOf<String?>(null)
    private var pullProgress by mutableFloatStateOf(0f)
    private var pullArmed by mutableStateOf(false)

    private var webView: WebView? = null
    private var openTool: Tool? = null
    private var viewingSaved = false
    private var pageLog: PageLog? = null
    /** The last host the wall refused, per tool, so the tool's page can offer to allow it. */
    private val lastBlocked = HashMap<String, String>()

    // Shake to report.
    private val shake = ShakeGesture()
    private var sensors: SensorManager? = null
    private var reportOffer by mutableStateOf(false)
    private var shot: Bitmap? = null
    private var probeJson: String? = null
    private var listState = LazyListState()
    private var pageScroll = ScrollState(0)

    private var lastCameraKeyAt = 0L
    private var statusClear: Runnable? = null
    private var snapshotJob: Runnable? = null

    private val scanner = registerForActivityResult(ScanContract()) { result ->
        val text = result.contents
        if (text == null) {
            screen = Screen.Add("Nothing scanned.")
        } else {
            addFromText(text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ToolStore(this)
        store.load()
        store.installBuiltIns()

        val prefs = getSharedPreferences("webtools", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("tutorialSeen", false)) screen = Screen.Tutorial

        val frame = PullDownFrame(this)
        frame.atTop = { contentAtTop() }
        frame.onProgress = { p, armed -> pullProgress = p; pullArmed = armed }
        frame.onExit = { home() }

        val compose = ComposeView(this)
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent { Root() }
        frame.addView(compose)
        setContentView(frame)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { back() }
        })

        handleIntent(intent)

        sensors = getSystemService(Context.SENSOR_SERVICE) as? SensorManager

        // Housekeeping off the main thread: post anything queued from an earlier shake, and
        // refresh the ad list once a week.
        lifecycleScope.launch {
            val sent = Reports.drain(this@MainActivity)
            if (sent > 0) say(if (sent == 1) "Sent a saved report" else "Sent $sent saved reports")
            withContext(Dispatchers.IO) { BlockLists.refreshIfStale(this@MainActivity) }
        }
    }

    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            if (reportOffer) return
            if (shake.sample(e.values[0], e.values[1], e.values[2], System.currentTimeMillis())) offerReport()
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "webtools" && data.host == "open") {
            val id = data.pathSegments.firstOrNull() ?: return
            store.get(id)?.let { show(it) }
        }
    }

    @Composable
    private fun Root() {
        val tools by store.tools.collectAsStateWithLifecycle()
        WebToolsTheme {
            Surface(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalWheelBus provides wheel) {
                    Box(Modifier.fillMaxSize()) {
                        when (val s = screen) {
                            Screen.Tutorial -> TutorialScreen(scroll = pageScroll) {
                                getSharedPreferences("webtools", Context.MODE_PRIVATE).edit().putBoolean("tutorialSeen", true).apply()
                                go(Screen.Home)
                            }
                            Screen.Home -> ListScreen(
                                tools = tools.sortedWith(compareByDescending<Tool> { it.lastUsed }.thenBy { it.added }),
                                listState = listState,
                                onOpen = { show(it) },
                                onInfo = { go(Screen.Info(it.id)) },
                                onAdd = { go(Screen.Add()) },
                                onHelp = { go(Screen.Tutorial) },
                            )
                            is Screen.Add -> AddScreen(
                                message = s.message,
                                scroll = pageScroll,
                                onScan = { scan() },
                                onAddUrl = { addFromText(it) },
                                onAddStarter = { addStarter(it) },
                                onBack = { go(Screen.Home) },
                            )
                            is Screen.Info -> {
                                val tool = tools.firstOrNull { it.id == s.id }
                                if (tool == null) {
                                    LaunchedEffect(Unit) { go(Screen.Home) }
                                } else {
                                    InfoScreen(
                                        tool = tool,
                                        scroll = pageScroll,
                                        online = online(),
                                        onOpen = { show(tool) },
                                        onOpenSaved = { show(tool, forceSaved = true) },
                                        blockedHost = lastBlocked[tool.id],
                                        onAllowBlocked = {
                                            lastBlocked[tool.id]?.let { h ->
                                                store.update(tool.id) { t -> if (t.origins.contains(h)) t else t.copy(origins = t.origins + h) }
                                                lastBlocked.remove(tool.id)
                                            }
                                        },
                                        onToggleKeep = { store.update(tool.id) { t -> t.copy(keep = !t.keep) } },
                                        onToggleEngine = {
                                            store.update(tool.id) { t ->
                                                t.copy(engine = if (t.engine == Engine.BROWSER) Engine.BUILTIN else Engine.BROWSER)
                                            }
                                        },
                                        onRemove = { store.remove(tool.id); go(Screen.Home) },
                                        onBack = { go(Screen.Home) },
                                    )
                                }
                            }
                            is Screen.Page -> {
                                val wv = webView
                                if (wv == null) LaunchedEffect(Unit) { go(Screen.Home) } else ToolScreen(webView = wv, status = status)
                            }
                        }
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            ExitIndicator(progress = pullProgress, armed = pullArmed)
                        }
                        if (reportOffer) {
                            ReportSheet(
                                toolName = openTool?.name,
                                hasToken = BuildConfig.REPORT_TOKEN.isNotBlank(),
                                onSend = { note -> sendReport(note) },
                                onDismiss = { reportOffer = false; shot = null; probeJson = null },
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- navigation ----

    private fun go(next: Screen) {
        if (screen is Screen.Page && next !is Screen.Page) closeTool()
        if (next !is Screen.Page) pageScroll = ScrollState(0)
        status = null
        screen = next
    }

    private fun back() {
        when (screen) {
            is Screen.Page -> {
                val wv = webView
                if (wv != null && wv.canGoBack()) wv.goBack() else go(Screen.Home)
            }
            Screen.Home -> leave()
            Screen.Tutorial -> {
                getSharedPreferences("webtools", Context.MODE_PRIVATE).edit().putBoolean("tutorialSeen", true).apply()
                go(Screen.Home)
            }
            else -> go(Screen.Home)
        }
    }

    /** The pull-down landed: back to the list. The list itself has nowhere to pull to. */
    private fun home() {
        if (screen is Screen.Tutorial) {
            getSharedPreferences("webtools", Context.MODE_PRIVATE).edit().putBoolean("tutorialSeen", true).apply()
        }
        go(Screen.Home)
    }

    /** Leaving the app is the system's business (home key); the list just closes. */
    private fun leave() {
        closeTool()
        finish()
    }

    /** The pull-down may start only when the content is at its top, and never on the list. */
    private fun contentAtTop(): Boolean = when (screen) {
        is Screen.Page -> (webView?.scrollY ?: 0) <= 0
        Screen.Home -> false
        else -> pageScroll.value == 0
    }

    // ---- tools ----

    private fun show(tool: Tool, forceSaved: Boolean = false) {
        closeTool()
        if (tool.kind == ToolKind.SITE && tool.engine == Engine.BROWSER) {
            store.touch(tool.id)
            openInBrowser(tool)
            return
        }
        val snapshot = store.snapshotFile(tool.id)
        val saved = tool.kind == ToolKind.SITE && snapshot.exists() && (forceSaved || !online())
        viewingSaved = saved
        openTool = tool
        val log = PageLog()
        pageLog = log
        val wv = ToolWebView.create(this, tool, store.dirFor(tool.id), listener, log)
        webView = wv
        store.touch(tool.id)
        screen = Screen.Page(tool.id, saved)
        status = if (saved) "Saved copy · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(tool.snapshotAt)) else "Loading"
        if (!saved && !online() && tool.kind == ToolKind.SITE) status = "No signal, and no saved copy yet"
        ToolWebView.open(wv, tool, snapshot, saved)
    }

    private fun closeTool() {
        snapshotJob?.let { handler.removeCallbacks(it) }
        snapshotJob = null
        ToolWebView.flushCookies()
        webView?.let { wv ->
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
            wv.stopLoading()
            wv.destroy()
        }
        webView = null
        openTool = null
        viewingSaved = false
        pageLog = null
    }

    /**
     * The phone's own browser as a Custom Tab: Chromium's cookie jar and Chromium's fingerprint,
     * which is what a Kasada-gated sign-in (Ticketmaster) insists on. The allowlist and the saved
     * copy do not apply there; the tool row says so.
     */
    private fun openInBrowser(tool: Tool) {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/"))
        val browsers = packageManager.queryIntentActivities(probe, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .filter { it != packageName }
            .distinct()
        val pkg = CustomTabsClient.getPackageName(this, browsers, true)
            ?: browsers.firstOrNull { it.contains("chrom", ignoreCase = true) }
            ?: browsers.firstOrNull()
        if (pkg == null) {
            say("No browser on this phone")
            return
        }
        val tab = CustomTabsIntent.Builder()
            .setShowTitle(false)
            .setUrlBarHidingEnabled(true)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(android.graphics.Color.BLACK)
                    .setNavigationBarColor(android.graphics.Color.BLACK)
                    .build(),
            )
            .build()
        tab.intent.setPackage(pkg)
        runCatching { tab.launchUrl(this, Uri.parse(tool.url)) }
            .onFailure {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tool.url)).setPackage(pkg)) }
                    .onFailure { say("Could not open $pkg") }
            }
    }

    private val listener = object : ToolWebListener {
        override fun onBlocked(host: String) {
            val tool = openTool
            if (tool != null && host.contains('.')) lastBlocked[tool.id] = host.lowercase().removePrefix("www.")
            say("Stays inside " + (tool?.origins?.firstOrNull() ?: "this site") + " · blocked $host · hold the tool to allow it")
        }

        override fun onRedirectedTo(host: String) {
            val tool = openTool ?: return
            store.update(tool.id) { t -> if (t.origins.contains(host)) t else t.copy(origins = t.origins + host) }
            openTool = store.get(tool.id)
            say("Following the site to $host")
        }

        override fun onProgress(loading: Boolean) {
            if (loading && status == null) status = "Loading"
            if (!loading && status == "Loading") status = null
        }

        override fun onRefused(title: String) {
            val tool = openTool ?: return
            say("This site refuses the built-in view. Hold ${tool.name} in the list and switch it to Chromium.")
        }

        override fun onLoaded(url: String) {
            val tool = openTool ?: return
            if (status == "Loading") status = null
            webView?.let { ToolWebView.applyPageFixes(it) }
            // Freshness: a kept site is re-saved after every live visit, once the page has had a
            // moment to finish its own scripts.
            if (tool.kind == ToolKind.SITE && tool.keep && !viewingSaved && url.startsWith("http")) {
                snapshotJob?.let { handler.removeCallbacks(it) }
                val job = Runnable { saveSnapshot() }
                snapshotJob = job
                handler.postDelayed(job, 1500L)
            }
        }

        override fun onFailed(description: String) {
            val tool = openTool ?: return
            if (!viewingSaved && tool.hasSnapshot && store.snapshotFile(tool.id).exists()) {
                // The live page did not come; the saved copy is the whole point of having one.
                // Posted: destroying a WebView from inside its own client callback is not safe.
                handler.post { if (openTool?.id == tool.id && !viewingSaved) show(tool, forceSaved = true) }
            } else {
                say("Could not load: $description")
            }
        }
    }

    private fun saveSnapshot() {
        val wv = webView ?: return
        val tool = openTool ?: return
        val target = store.snapshotFile(tool.id)
        ToolWebView.snapshot(wv, target) { ok ->
            if (ok) {
                store.update(tool.id) { it.copy(snapshotAt = System.currentTimeMillis()) }
                say("Saved a copy")
            }
        }
    }

    private fun say(text: String) {
        status = text
        statusClear?.let { handler.removeCallbacks(it) }
        val r = Runnable { if (status == text) status = null }
        statusClear = r
        handler.postDelayed(r, 3500L)
    }

    private fun online(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // ---- reporting ----

    /** The picture is taken at the shake, before the sheet is what is on screen. */
    private fun offerReport() {
        val tool = openTool
        val wv = webView
        Reports.screenshot(this) { bmp ->
            shot = bmp
            if (wv != null && tool != null) {
                ToolWebView.probe(wv) { json ->
                    probeJson = json
                    reportOffer = true
                }
            } else {
                probeJson = null
                reportOffer = true
            }
        }
    }

    private fun sendReport(note: String) {
        val draft = Reports.compose(
            context = this,
            tool = openTool,
            note = note,
            pageLog = pageLog?.dump(),
            probe = probeJson,
            screenshot = shot,
            online = online(),
        )
        reportOffer = false
        shot = null
        probeJson = null
        Reports.enqueue(this, draft)
        lifecycleScope.launch {
            val sent = Reports.drain(this@MainActivity)
            say(
                when {
                    sent > 0 -> "Report sent"
                    BuildConfig.REPORT_TOKEN.isBlank() -> "Report kept on the phone (no tracker key in this build)"
                    else -> "Report kept, will send when the tracker answers"
                },
            )
        }
    }

    // ---- adding ----

    private fun scan() {
        scanner.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setBeepEnabled(false)
                .setOrientationLocked(true)
                .setPrompt("Point at the code"),
        )
    }

    private fun addFromText(text: String) {
        val t = text.trim()
        // A bare host typed in: assume https.
        val fixed = if (t.startsWith("{") || t.contains("://")) t else "https://$t"
        when (val r = QrPayload.parse(fixed)) {
            is QrPayload.Result.Ok -> {
                store.put(r.tool)
                go(Screen.Info(r.tool.id))
            }
            is QrPayload.Result.Bad -> screen = Screen.Add(r.why)
        }
    }

    private fun addStarter(s: Starter) {
        val id = Tool.slug(s.name)
        val tool = Tool(
            id = id, name = s.name, kind = ToolKind.SITE, url = s.url, origins = s.origins,
            engine = if (s.browser) Engine.BROWSER else Engine.BUILTIN,
            keep = s.keep, added = System.currentTimeMillis(),
        )
        store.put(tool)
        go(Screen.Info(id))
    }

    // ---- hardware ----

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (LightKeys.of(event)) {
                LightKey.WheelUp -> { scrollWheel(1); return true }
                LightKey.WheelDown -> { scrollWheel(-1); return true }
                LightKey.Camera, LightKey.Focus -> {
                    // Both stages arrive per press, in either order; one press is one step back.
                    val now = System.currentTimeMillis()
                    if (now - lastCameraKeyAt > 500L) {
                        lastCameraKeyAt = now
                        back()
                    }
                    return true
                }
                else -> Unit
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            when (LightKeys.of(event)) {
                LightKey.WheelUp, LightKey.WheelDown, LightKey.Camera, LightKey.Focus -> return true
                else -> Unit
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun scrollWheel(delta: Int) {
        val wv = webView
        if (screen is Screen.Page && wv != null) {
            val step = (wv.height * 0.22f).toInt().coerceAtLeast(120)
            val dy = -delta * step
            if ((dy > 0 && wv.canScrollVertically(1)) || (dy < 0 && wv.canScrollVertically(-1))) {
                wv.scrollBy(0, dy)
            }
        } else {
            wheel.send(delta)
        }
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
        ToolWebView.flushCookies()
        sensors?.unregisterListener(shakeListener)
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
        sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensors?.registerListener(shakeListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onDestroy() {
        closeTool()
        super.onDestroy()
    }
}
