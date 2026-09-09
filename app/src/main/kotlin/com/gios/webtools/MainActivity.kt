package com.gios.webtools

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
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
import com.gios.webtools.data.Tool
import com.gios.webtools.data.ToolKind
import com.gios.webtools.data.ToolStore
import com.gios.webtools.gesture.PullDownFrame
import com.gios.webtools.hw.LightKey
import com.gios.webtools.hw.LightKeys
import com.gios.webtools.hw.LocalWheelBus
import com.gios.webtools.hw.WheelBus
import com.gios.webtools.ui.AddScreen
import com.gios.webtools.ui.ExitIndicator
import com.gios.webtools.ui.InfoScreen
import com.gios.webtools.ui.ListScreen
import com.gios.webtools.ui.Starter
import com.gios.webtools.ui.ToolScreen
import com.gios.webtools.ui.TutorialScreen
import com.gios.webtools.ui.theme.WebToolsTheme
import com.gios.webtools.web.QrPayload
import com.gios.webtools.web.ToolWebListener
import com.gios.webtools.web.ToolWebView
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
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
                                        onToggleKeep = { store.update(tool.id) { t -> t.copy(keep = !t.keep) } },
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
        val snapshot = store.snapshotFile(tool.id)
        val saved = tool.kind == ToolKind.SITE && snapshot.exists() && (forceSaved || !online())
        viewingSaved = saved
        openTool = tool
        val wv = ToolWebView.create(this, tool, store.dirFor(tool.id), snapshot, listener)
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
    }

    private val listener = object : ToolWebListener {
        override fun onBlocked(host: String) {
            say("Stays inside " + (openTool?.origins?.firstOrNull() ?: "this site") + " · blocked $host")
        }

        override fun onProgress(loading: Boolean) {
            if (loading && status == null) status = "Loading"
            if (!loading && status == "Loading") status = null
        }

        override fun onLoaded(url: String) {
            val tool = openTool ?: return
            if (status == "Loading") status = null
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
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onDestroy() {
        closeTool()
        super.onDestroy()
    }
}
