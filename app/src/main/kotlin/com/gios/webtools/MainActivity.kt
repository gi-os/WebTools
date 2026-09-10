package com.gios.webtools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
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
import androidx.lifecycle.lifecycleScope
import com.gios.webtools.data.Tool
import com.gios.webtools.data.ToolKind
import com.gios.webtools.data.ToolStore
import com.gios.webtools.data.Prefs
import com.gios.webtools.gesture.PullDownFrame
import com.gios.webtools.hw.LightKey
import com.gios.webtools.hw.LightKeys
import com.gios.webtools.hw.LocalWheelBus
import com.gios.webtools.hw.WheelBus
import com.gios.webtools.report.PageLog
import com.gios.webtools.ui.AddScreen
import com.gios.webtools.ui.PulleyMenu
import com.gios.webtools.ui.InfoScreen
import com.gios.webtools.ui.ListScreen
import com.gios.webtools.ui.SettingsScreen
import com.gios.light.common.report.ReportContext
import com.gios.light.common.report.Device
import com.gios.light.common.report.ReportOverlay
import com.gios.light.common.report.Screenshot
import androidx.compose.ui.unit.dp
import com.gios.webtools.ui.Starter
import com.gios.webtools.ui.ToolScreen
import com.gios.webtools.ui.TutorialScreen
import com.gios.webtools.ui.theme.WebToolsTheme
import com.gios.webtools.web.GeckoTool
import org.mozilla.geckoview.GeckoView
import com.gios.webtools.web.OriginRule
import com.gios.webtools.web.QrPayload
import com.gios.webtools.web.Search
import com.gios.webtools.web.SearchEngine
import com.gios.webtools.web.ToolPageListener
import com.gios.webtools.web.Warmth
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private sealed class Screen {
    data object Tutorial : Screen()
    data object Home : Screen()
    data object Settings : Screen()
    data class Add(val message: String? = null) : Screen()
    data class Info(val id: String) : Screen()
    data class Page(val id: String, val saved: Boolean) : Screen()
}

/**
 * One activity, a handful of screens, one page at a time.
 *
 * The root view is a [PullDownFrame], so the back-to-the-list gesture works the same over a page
 * as over the list. `dispatchKeyEvent` is where the wheel and the camera button can be seen.
 */
class MainActivity : ComponentActivity() {

    private lateinit var store: ToolStore
    private val app: WebToolsApp get() = application as WebToolsApp
    private val wheel = WheelBus()
    private val handler = Handler(Looper.getMainLooper())

    private var screen by mutableStateOf<Screen>(Screen.Home)
    private var status by mutableStateOf<String?>(null)
    private var pullTravel by mutableFloatStateOf(0f)
    private var pullPicked by mutableStateOf(-1)
    private var pulleyPitchDp = 64f
    private var pulleyDeadzoneDp = 36f

    private var page by mutableStateOf<GeckoTool?>(null)
    /** The view the page draws in, for a picture of the page itself. */
    private var toolView: GeckoView? = null
    private lateinit var prefs: Prefs
    private var engine by mutableStateOf(SearchEngine.DUCKDUCKGO)
    private var graceMs by mutableStateOf(Warmth.GRACE_MS)

    /** A page closed while the app was in the background, to be re-opened where it was. */
    private data class Parked(val tool: Tool, val url: String?, val saved: Boolean)
    private var parked: Parked? = null
    /** Until when the open page is worth keeping warm in the background (MAKE A TICKET sets it). */
    private var readyUntil = 0L
    private val parkJob = Runnable { park() }
    private val exitJob = Runnable { leaveProcess() }
    /** The last host the wall refused, per tool, so the tool's page can offer to allow it. */
    private val lastBlocked = HashMap<String, String>()

    /** A code split over several images: the parts scanned so far. */
    private var partsId: String? = null
    private var partsCount = 0
    private val parts = HashMap<Int, String>()

    private var listState = LazyListState()
    private var pageScroll = ScrollState(0)

    private var lastCameraKeyAt = 0L
    private var statusClear: Runnable? = null
    private var snapshotJob: Runnable? = null

    private val scanner = registerForActivityResult(ScanContract()) { result ->
        val text = result.contents
        if (text == null) {
            if (partsId != null) {
                partsId = null; parts.clear(); partsCount = 0
                screen = Screen.Add("Stopped before all parts were scanned.")
            } else {
                screen = Screen.Add("Nothing scanned.")
            }
        } else {
            addFromText(text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ToolStore(this)
        store.load()
        store.installBuiltIns()

        prefs = Prefs(this)
        if (!prefs.tutorialSeen) screen = Screen.Tutorial
        engine = prefs.engine
        graceMs = prefs.graceMs

        val frame = PullDownFrame(this)
        frame.atTop = { contentAtTop() }
        frame.itemCount = { pulleyItems().size }
        frame.onProgress = { travel, picked -> pullTravel = travel; pullPicked = picked }
        frame.onSelect = { index -> pulleyPick(index) }
        val d = resources.displayMetrics.density
        pulleyPitchDp = frame.pitchPx / d
        pulleyDeadzoneDp = frame.deadzonePx / d

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
        if (data.scheme != "webtools") return
        when (data.host) {
            // webtools://open/<id>[?u=<address>]: a tool, at its home or at one page of it,
            // live rather than the saved copy. Movie Tickets sends you back here from a pass.
            "open" -> {
                val id = data.pathSegments.firstOrNull() ?: return
                val tool = store.get(id) ?: return
                val u = data.getQueryParameter("u")?.takeIf { it.startsWith("http") }
                when {
                    u == null -> show(tool)
                    OriginRule.allows(tool.origins, Tool.hostOf(u)) -> show(tool, resumeAt = u, live = true)
                    else -> lookUp(u)
                }
            }
            // webtools://go?u=<address>: a page that was never on the shelf, opened again by
            // whoever was handed its address (Movie Tickets, from a pass made off it).
            "go" -> {
                val u = data.getQueryParameter("u") ?: return
                if (u.startsWith("http")) lookUp(u)
            }
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
                                prefs.tutorialSeen = true
                                go(Screen.Home)
                            }
                            Screen.Home -> ListScreen(
                                tools = tools.sortedWith(compareByDescending<Tool> { it.lastUsed }.thenBy { it.added }),
                                listState = listState,
                                engine = engine,
                                onSearch = { typed -> lookUp(typed) },
                                onCycleEngine = { cycleEngine() },
                                onOpen = { show(it) },
                                onInfo = { go(Screen.Info(it.id)) },
                                onAdd = { go(Screen.Add()) },
                                onSettings = { go(Screen.Settings) },
                            )
                            Screen.Settings -> SettingsScreen(
                                scroll = pageScroll,
                                engine = engine,
                                onCycleEngine = { cycleEngine() },
                                graceMs = graceMs,
                                onCycleGrace = {
                                    graceMs = Warmth.nextGrace(graceMs)
                                    prefs.graceMs = graceMs
                                },
                                about = "Web Tools ${BuildConfig.VERSION_NAME} · GeckoView " +
                                    org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION +
                                    " · uBlock Origin, always on\n" + Device.summary(this@MainActivity),
                                onTutorial = { go(Screen.Tutorial) },
                                onBack = { go(Screen.Home) },
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
                                        onToggleReader = { store.update(tool.id) { t -> t.copy(reader = !t.reader) } },
                                        onForgetLogin = { forgetLogin(tool) },
                                        onRemove = { store.remove(tool.id); go(Screen.Home) },
                                        onBack = { go(Screen.Home) },
                                    )
                                }
                            }
                            is Screen.Page -> {
                                val p = page
                                if (p == null) LaunchedEffect(Unit) { go(Screen.Home) } else ToolScreen(session = p.session, status = status, onView = { toolView = it })
                            }
                        }
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            PulleyMenu(
                                items = pulleyItems(),
                                travelPx = pullTravel,
                                picked = pullPicked,
                                pitchDp = pulleyPitchDp,
                                deadzoneDp = pulleyDeadzoneDp,
                            )
                        }
                        // The shake / crash / failure offer, from light-common. Bottom right,
                        // above the action bar and the status strip.
                        ReportOverlay(corner = Alignment.BottomEnd, bottomInset = 64.dp)
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
        ReportContext.screen = when (next) {
            is Screen.Page -> "page"
            is Screen.Info -> "tool"
            is Screen.Add -> "add"
            Screen.Settings -> "settings"
            Screen.Tutorial -> "tutorial"
            else -> "shelf"
        }
    }

    private fun back() {
        when (screen) {
            is Screen.Page -> {
                val p = page
                if (p != null && p.canGoBack) p.goBack() else go(Screen.Home)
            }
            Screen.Home -> leave()
            Screen.Tutorial -> {
                prefs.tutorialSeen = true
                go(Screen.Home)
            }
            else -> go(Screen.Home)
        }
    }

    /** The pull-down landed on "Tools": back to the list. The list itself has nowhere to pull to. */
    private fun home() {
        if (screen is Screen.Tutorial) {
            prefs.tutorialSeen = true
        }
        go(Screen.Home)
    }

    // ---- the pulley menu ----

    private companion object {
        const val TICKETS_PACKAGE = "com.gios.lightpass"
    }

    private object Pull {
        const val TOOLS = "Tools"
        const val SET_HOME = "Set as home"
        const val KEEP = "Keep on shelf"
        const val READER_ON = "Reader view"
        const val READER_OFF = "Full page"
        const val SAVE = "Save a copy"
        const val TICKET = "Make a ticket"
    }

    /** Rows top-to-bottom, unrolling with the pull; TOOLS is last so the longest pull always leaves. */
    private fun pulleyItems(): List<String> {
        val p = page
        return when (screen) {
            Screen.Home -> emptyList()
            is Screen.Page -> if (p == null) listOf(Pull.TOOLS) else buildList {
                if (p.tool.kind == ToolKind.SITE) {
                    add(Pull.SAVE)
                    if (ticketsInstalled) add(Pull.TICKET)
                    add(if (p.tool.reader) Pull.READER_OFF else Pull.READER_ON)
                    add(if (p.tool.id.isEmpty()) Pull.KEEP else Pull.SET_HOME)
                }
                add(Pull.TOOLS)
            }
            else -> listOf(Pull.TOOLS)
        }
    }

    private fun pulleyPick(index: Int) {
        val item = pulleyItems().getOrNull(index) ?: return
        val p = page
        when (item) {
            Pull.TOOLS -> home()
            Pull.TICKET -> makeTicket()
            Pull.SET_HOME -> {
                val url = p?.currentUrl ?: return
                if (!url.startsWith("http")) { say("Not a page to set as home"); return }
                val host = Tool.hostOf(url)
                store.update(p.tool.id) { t ->
                    t.copy(url = url, origins = if (OriginRule.allows(t.origins, host)) t.origins else t.origins + host)
                }
                say("Home is now ${url.take(60)}")
            }
            Pull.KEEP -> {
                val url = p?.currentUrl ?: return
                if (!url.startsWith("http")) { say("Not a page to keep"); return }
                val host = Tool.hostOf(url)
                val id = Tool.slug(host) + "-" + url.hashCode().toUInt().toString(36).take(4)
                store.put(
                    Tool(
                        id = id, name = host.substringBefore('.').replaceFirstChar { it.uppercase() },
                        kind = ToolKind.SITE, url = url, origins = listOf(host), added = System.currentTimeMillis(),
                    ),
                )
                say("Kept on the shelf. Hold it there to rename or loosen the wall.")
            }
            Pull.READER_ON, Pull.READER_OFF -> {
                val tool = p?.tool ?: return
                val next = tool.copy(reader = !tool.reader)
                if (tool.id.isNotEmpty()) store.update(tool.id) { it.copy(reader = next.reader) }
                show(next)
            }
            Pull.SAVE -> {
                val tool = p?.tool ?: return
                if (tool.id.isEmpty()) { say("Keep it on the shelf first, then save") ; return }
                saveSnapshot()
            }
        }
    }

    /** Leaving the app is the system's business (home key); the list just closes. */
    private fun leave() {
        closeTool()
        finish()
    }

    /** The pull-down may start only when the content is at its top, and never on the list. */
    private fun contentAtTop(): Boolean = when (screen) {
        is Screen.Page -> (page?.scrollY ?: 0) <= 0
        Screen.Home -> false
        else -> pageScroll.value == 0
    }

    // ---- tools ----

    private fun show(tool: Tool, forceSaved: Boolean = false, resumeAt: String? = null, live: Boolean = false) {
        closeTool()
        readyUntil = 0L
        val snapshot = store.snapshotFile(tool.id.ifEmpty { "once" })
        val saved = !live && tool.kind == ToolKind.SITE && tool.id.isNotEmpty() && snapshot.exists() && (forceSaved || !online())
        val log = PageLog()
        val p = GeckoTool(this, tool, app.runtime, listener, log)
        page = p
        app.currentPage = p
        if (tool.id.isNotEmpty() && store.get(tool.id) != null) store.touch(tool.id)
        screen = Screen.Page(tool.id, saved)
        status = if (saved) "Saved copy · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(tool.snapshotAt)) else "Loading"
        if (!saved && !online() && tool.kind == ToolKind.SITE) status = "No signal, and no saved copy yet"
        p.open(snapshot, saved, resumeAt?.takeIf { it.startsWith("http") || it.startsWith("about:reader") })
    }

    /**
     * The field at the top: an address opens, words go to the engine. Either way it is one page
     * with no wall and no row in the list, kept only if the pull-down says so.
     */
    private fun lookUp(typed: String) {
        val t = typed.trim()
        if (t.isEmpty()) return
        val url = Search.resolve(t, engine)
        val host = Tool.hostOf(url)
        if (host.isEmpty()) { say("Not an address"); return }
        val tool = Tool(
            id = "", name = if (Search.isAddress(t)) host else t.take(40), kind = ToolKind.SITE, url = url,
            origins = emptyList(), added = System.currentTimeMillis(),
        )
        show(tool)
    }

    // ---- tickets ----

    private val ticketsInstalled: Boolean by lazy {
        runCatching { packageManager.getPackageInfo(TICKETS_PACKAGE, 0) }.isSuccess
    }

    /**
     * MAKE A TICKET: a screenshot of the page goes to Movie Tickets (BrightPasses), which reads
     * it the way it reads a photographed stub. The picture carries this page's own address, so
     * the pass can lead back here, and the page stays warm for an hour: a barcode that rotates
     * only works on the live page, and the live page is what you will hold up.
     */
    private fun makeTicket() {
        val p = page ?: return
        val url = p.currentUrl ?: p.tool.url
        if (!url.startsWith("http")) { say("Not a page to make a ticket from"); return }
        say("Taking the picture")
        // A beat, so the pulley has rolled back up before the picture is taken.
        handler.postDelayed({ capturePage { bmp -> ticketTaken(bmp, p.tool, url) } }, 250)
    }

    /**
     * The page's own pixels, from the engine, so the picture is the page and nothing else: no
     * status strip, no menu, and no black hole where a SurfaceView used to be. The window is the
     * fallback if the engine has nothing to give.
     */
    private fun capturePage(done: (Bitmap?) -> Unit) {
        val view = toolView
        if (view == null) { Screenshot.capture(window, done); return }
        runCatching {
            view.capturePixels().accept(
                { bmp -> if (bmp != null) done(bmp) else Screenshot.capture(window, done) },
                { Screenshot.capture(window, done) },
            )
        }.onFailure { Screenshot.capture(window, done) }
    }

    private fun ticketTaken(bmp: Bitmap?, tool: Tool, url: String) {
        if (bmp == null) { say("Could not take the picture"); return }
        val ok = runCatching { sendTicket(bmp, tool, url) }.isSuccess
        if (ok) {
            readyUntil = System.currentTimeMillis() + Warmth.READY_MS
            say("Sent to Movie Tickets. This page stays ready for an hour.")
        } else {
            say("Movie Tickets is not on this phone")
        }
    }

    private fun sendTicket(bmp: Bitmap, tool: Tool, url: String) {
        val dir = File(cacheDir, "tickets").apply { mkdirs() }
        val file = File(dir, "ticket.jpg")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val uri = FileProvider.getUriForFile(this, "com.gios.webtools.share", file)
        // The page you were on, not the tool's home. The tool's id keeps its wall and its row.
        val back = if (tool.id.isNotEmpty()) "webtools://open/${tool.id}?u=" + Uri.encode(url)
        else "webtools://go?u=" + Uri.encode(url)
        val intent = Intent(Intent.ACTION_SEND)
            .setPackage(TICKETS_PACKAGE)
            .setType("image/jpeg")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, back)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
    }

    // ---- warmth: what happens to a page when the app leaves the screen ----

    /** Close the page's session but remember where it was, so coming back re-opens it there. */
    private fun park() {
        val p = page ?: return
        val s = screen
        parked = Parked(p.tool, p.currentUrl, s is Screen.Page && s.saved)
        closeTool()
    }

    /**
     * Let the process go once the page is parked and nothing is being written. Firefox's engine
     * cannot be shut down and started again inside one process, and an idle engine is still a
     * few hundred megabytes and a handful of threads; the next launch is a cold one, a second
     * or two, which is cheaper than a warm one all night.
     */
    private fun leaveProcess() {
        if (page != null || snapshotJob != null) return
        finishAndRemoveTask()
        Process.killProcess(Process.myPid())
    }

    override fun onStop() {
        super.onStop()
        val now = System.currentTimeMillis()
        if (page != null) handler.postDelayed(parkJob, Warmth.parkDelay(now, readyUntil, graceMs))
        handler.postDelayed(exitJob, Warmth.exitDelay(now, readyUntil, graceMs))
    }

    override fun onStart() {
        super.onStart()
        handler.removeCallbacks(parkJob)
        handler.removeCallbacks(exitJob)
        parked?.let {
            parked = null
            // A ticket page keeps the rest of its hour across the round trip.
            val keep = readyUntil
            if (screen is Screen.Page) show(it.tool, it.saved, it.url)
            readyUntil = keep
        }
    }

    private fun cycleEngine() {
        engine = engine.next()
        prefs.engine = engine
    }

    private fun closeTool() {
        snapshotJob?.let { handler.removeCallbacks(it) }
        snapshotJob = null
        page?.close()
        page = null
        app.currentPage = null
    }

    private val listener = object : ToolPageListener {
        override fun onBlocked(host: String) {
            val tool = page?.tool
            if (tool != null && tool.id.isNotEmpty() && host.contains('.')) lastBlocked[tool.id] = host.lowercase().removePrefix("www.")
            say("Stays inside " + (tool?.origins?.firstOrNull() ?: "this site") + " · blocked $host · hold the tool to allow it")
        }

        override fun onRedirectedTo(host: String) {
            val tool = page?.tool ?: return
            if (tool.id.isEmpty()) return
            store.update(tool.id) { t -> if (t.origins.contains(host)) t else t.copy(origins = t.origins + host) }
            say("Following the site to $host")
        }

        override fun onRefused(title: String) {
            say("This site refuses the sign-in from here. Sign in on a computer and bring the login over by code.")
        }

        override fun onProgress(loading: Boolean) {
            if (loading && status == null) status = "Loading"
            if (!loading && status == "Loading") status = null
        }

        override fun onLoaded(url: String) {
            val p = page ?: return
            val tool = p.tool
            if (status == "Loading") status = null
            // Freshness: a kept site is re-saved after every live visit, once the page has had a
            // moment to finish its own scripts.
            if (tool.kind == ToolKind.SITE && tool.keep && tool.id.isNotEmpty() && !p.viewingSaved && url.startsWith("http")) {
                snapshotJob?.let { handler.removeCallbacks(it) }
                val job = Runnable { saveSnapshot() }
                snapshotJob = job
                handler.postDelayed(job, 2500L)
            }
        }

        override fun onFailed(description: String) {
            val p = page ?: return
            val tool = p.tool
            if (!p.viewingSaved && tool.id.isNotEmpty() && tool.hasSnapshot && store.snapshotFile(tool.id).exists()) {
                // The live page did not come; the saved copy is the whole point of having one.
                handler.post { if (page === p) show(tool, forceSaved = true) }
            } else {
                say("Could not load: $description")
            }
        }
    }

    private fun saveSnapshot() {
        val p = page ?: return
        val tool = p.tool
        val target = store.snapshotFile(tool.id)
        p.snapshot(target) { ok ->
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
                partsId = null; parts.clear(); partsCount = 0
                store.put(r.tool)
                val login = r.login
                if (login != null) {
                    say("Signing in as on the computer…")
                    app.bridge.setCookies(login.domain, login.cookies) { set, failed ->
                        if (set == 0) {
                            screen = Screen.Add("The login did not take (${failed.size} cookies refused). Try a fresh code.")
                        } else {
                            say("Signed in: $set cookies" + if (failed.isNotEmpty()) ", ${failed.size} refused" else "")
                            show(r.tool)
                        }
                    }
                } else {
                    go(Screen.Info(r.tool.id))
                }
            }
            is QrPayload.Result.Part -> {
                if (partsId != r.id) { partsId = r.id; parts.clear(); partsCount = r.count }
                parts[r.index] = r.text
                val whole = QrPayload.assemble(parts, partsCount)
                if (whole != null) {
                    addFromText(whole)
                } else {
                    val next = (1..partsCount).firstOrNull { it !in parts } ?: 1
                    screen = Screen.Add("Part ${parts.size} of $partsCount read. Scan part $next.")
                    scanner.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setBeepEnabled(false)
                            .setOrientationLocked(true)
                            .setPrompt("Part $next of $partsCount"),
                    )
                }
            }
            is QrPayload.Result.Bad -> screen = Screen.Add(r.why)
        }
    }

    private fun forgetLogin(tool: Tool) {
        val domain = tool.origins.firstOrNull() ?: Tool.hostOf(tool.url)
        if (domain.isEmpty()) return
        app.bridge.clearCookies(domain) { ok -> say(if (ok) "Signed out of $domain" else "Could not clear $domain") }
    }

    private fun addStarter(s: Starter) {
        val id = Tool.slug(s.name)
        val tool = Tool(
            id = id, name = s.name, kind = ToolKind.SITE, url = s.url, origins = s.origins,
            keep = s.keep, reader = s.reader, added = System.currentTimeMillis(),
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
        val p = page
        if (screen is Screen.Page && p != null) {
            val step = (resources.displayMetrics.heightPixels * 0.22f).toInt().coerceAtLeast(120)
            p.scrollBy(-delta * step)
        } else {
            wheel.send(delta)
        }
    }

    override fun onPause() {
        super.onPause()
        page?.setActive(false)
    }

    override fun onResume() {
        super.onResume()
        page?.setActive(true)
    }

    override fun onDestroy() {
        closeTool()
        super.onDestroy()
    }
}
