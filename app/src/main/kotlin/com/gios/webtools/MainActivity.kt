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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.gios.webtools.data.Tool
import com.gios.webtools.data.ToolKind
import com.gios.webtools.data.ToolStore
import com.gios.webtools.data.Prefs
import com.gios.webtools.data.Shelf
import com.gios.webtools.ui.FolderScreen
import com.gios.webtools.ui.FolderPickScreen
import com.gios.webtools.data.Download
import com.gios.webtools.data.DownloadStore
import com.gios.webtools.web.Downloader
import com.gios.webtools.ui.DownloadsScreen
import org.mozilla.geckoview.WebResponse
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
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.gios.webtools.ui.theme.Metrics
import com.gios.webtools.ui.theme.WebToolsTheme
import com.gios.webtools.web.GeckoTool
import org.mozilla.geckoview.GeckoView
import com.gios.webtools.web.OriginRule
import com.gios.webtools.web.QrPayload
import com.gios.webtools.web.Search
import com.gios.webtools.web.SearchEngine
import com.gios.webtools.web.ToolPageListener
import com.gios.webtools.web.Warmth
import com.gios.webtools.web.Portal
import com.gios.webtools.web.Epub
import androidx.activity.result.contract.ActivityResultContracts
import android.app.role.RoleManager
import android.provider.Settings
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private sealed class Screen {
    data object Tutorial : Screen()
    data object Home : Screen()
    data object Settings : Screen()
    data object Downloads : Screen()
    data class Folder(val name: String) : Screen()
    data class PickFolder(val id: String) : Screen()
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
    private var pullFrame: PullDownFrame? = null
    private val pulleyPitchDp: Float get() = (pullFrame?.pitchPx ?: (64f * resources.displayMetrics.density)) / resources.displayMetrics.density
    private var pulleyDeadzoneDp = 36f

    private var page by mutableStateOf<GeckoTool?>(null)
    /** The view the page draws in, for a picture of the page itself. */
    private var toolView: GeckoView? = null
    private lateinit var prefs: Prefs
    private lateinit var downloads: DownloadStore
    private lateinit var portal: Portal
    private var isBrowser by mutableStateOf(false)
    private lateinit var downloader: Downloader
    private var downloadsList = LazyListState()
    private var folderList = LazyListState()
    /** How far the open page has come, 0..1. The line along the top edge. */
    private var pageProgress by mutableStateOf(0f)
    private var pageLoading by mutableStateOf(false)
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
        downloads = DownloadStore(this)
        downloads.load()
        downloader = Downloader(downloads)
        portal = Portal(this)
        store.installBuiltIns()

        prefs = Prefs(this)
        if (!prefs.tutorialSeen) screen = Screen.Tutorial
        engine = prefs.engine
        graceMs = prefs.graceMs

        val frame = PullDownFrame(this)
        frame.itemCount = { pulleyItems().size }
        frame.onProgress = { travel, picked -> pullTravel = travel; pullPicked = picked }
        frame.onSelect = { index -> pulleyPick(index) }
        val d = resources.displayMetrics.density
        pulleyDeadzoneDp = frame.deadzonePx / d
        pullFrame = frame

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
        if (intent?.action == ConnectivityManager.ACTION_CAPTIVE_PORTAL_SIGN_IN) { signInToWifi(intent); return }
        val data = intent?.data ?: return
        // A link from another app. A shelf tool whose wall covers the host takes it; otherwise
        // it is a page like any typed address, with no wall and no row.
        if (data.scheme == "http" || data.scheme == "https") {
            val url = data.toString()
            val host = Tool.hostOf(url)
            val owner = store.tools.value.firstOrNull { it.kind == ToolKind.SITE && it.origins.isNotEmpty() && OriginRule.allows(it.origins, host) }
            if (owner != null) show(owner, resumeAt = url, live = true) else lookUp(url)
            return
        }
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
                                tools = tools,
                                listState = listState,
                                onSearch = { typed -> lookUp(typed) },
                                onOpen = { show(it) },
                                onInfo = { go(Screen.Info(it.id)) },
                                onFolder = { go(Screen.Folder(it)) },
                                onAdd = { go(Screen.Add()) },
                                onDownloads = { go(Screen.Downloads) },
                                onSettings = { go(Screen.Settings) },
                            )
                            is Screen.Folder -> FolderScreen(
                                folder = s.name,
                                tools = Shelf.inFolder(tools, s.name),
                                listState = folderList,
                                onOpen = { show(it) },
                                onInfo = { go(Screen.Info(it.id)) },
                                onBack = { go(Screen.Home) },
                            )
                            is Screen.PickFolder -> {
                                val t = store.get(s.id)
                                if (t == null) LaunchedEffect(Unit) { go(Screen.Home) } else FolderPickScreen(
                                    tool = t,
                                    folders = Shelf.folders(tools),
                                    listState = folderList,
                                    onPick = { f ->
                                        store.update(t.id) { it.copy(folder = f) }
                                        say(if (f.isEmpty()) "Back on the shelf" else "Filed under $f")
                                        go(Screen.Info(t.id))
                                    },
                                    onBack = { go(Screen.Info(t.id)) },
                                )
                            }
                            Screen.Downloads -> {
                                val items by downloads.items.collectAsStateWithLifecycle()
                                DownloadsScreen(
                                    items = items,
                                    listState = downloadsList,
                                    onOpen = { openDownload(it) },
                                    onRemove = { downloads.remove(it); say("Removed ${it.name}") },
                                    onBack = { go(Screen.Home) },
                                )
                            }
                            Screen.Settings -> SettingsScreen(
                                scroll = pageScroll,
                                engine = engine,
                                onCycleEngine = { cycleEngine() },
                                graceMs = graceMs,
                                onCycleGrace = {
                                    graceMs = Warmth.nextGrace(graceMs)
                                    prefs.graceMs = graceMs
                                },
                                isBrowser = isBrowser,
                                onAskBrowser = { askBrowserRole() },
                                passkeys = passkeyState(),
                                onPasskeys = { say(passkeyAdvice()) },
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
                                        index = Shelf.entries(tools).indexOfFirst { e -> e is Shelf.Entry.Single && e.tool.id == tool.id }
                                            .takeIf { it >= 0 }?.plus(1),
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
                                        onFolder = { go(Screen.PickFolder(tool.id)) },
                                        onForgetLogin = { forgetLogin(tool) },
                                        onRemove = { store.remove(tool.id); go(Screen.Home) },
                                        onBack = { go(Screen.Home) },
                                    )
                                }
                            }
                            is Screen.Page -> {
                                val p = page
                                if (p == null) LaunchedEffect(Unit) { go(Screen.Home) } else ToolScreen(session = p.session, status = status, loading = pageLoading, progress = pageProgress, onView = { toolView = it })
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
                        // A word about what just happened, on any screen but a page (a page has
                        // its own strip along the bottom). Without this, every row in Settings
                        // that only reports something read as a row that does nothing.
                        val note = status
                        if (note != null && screen !is Screen.Page) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                                Box(
                                    Modifier
                                        .padding(bottom = Metrics.bar + 8.dp, start = Metrics.pad, end = Metrics.pad)
                                        .background(Color.White)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(note, style = MaterialTheme.typography.bodySmall, color = Color.Black)
                                }
                            }
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
        pulleyPage = false
        screen = next
        ReportContext.screen = when (next) {
            is Screen.Page -> "page"
            is Screen.Info -> "tool"
            is Screen.Add -> "add"
            Screen.Settings -> "settings"
            Screen.Downloads -> "downloads"
            is Screen.Folder -> "folder"
            is Screen.PickFolder -> "folder"
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
        const val LIBRARY_PACKAGE = "com.lightfastread"
        const val AUTH_PACKAGE = "com.gios.lightauth"
        const val ACTION_PICK_CODE = "com.gios.lightauth.PICK_CODE"
        const val BITWARDEN_PACKAGE = "com.x8bit.bitwarden"
    }

    private object Pull {
        const val TOOLS = "Tools"
        const val SET_HOME = "Set as home"
        const val KEEP = "Keep on shelf"
        const val READER_ON = "Reader view"
        const val READER_OFF = "Full page"
        const val SAVE = "Save a copy"
        const val TICKET = "Make a ticket"
        const val BACK = "Back"
        const val FORWARD = "Forward"
        const val REFRESH = "Refresh"
        const val CONVERT = "Convert…"
        const val LIBRARY = "Send to library"
    }

    /**
     * Which page of the pull-down the next pull will draw.
     *
     * Two ways to turn this page into something else — a ticket, a book — is two rows of a menu
     * whose whole point is that the deepest pull is always the exit. They are one row now,
     * CONVERT, and letting go on it means the next pull is the short list of what to convert
     * into. It resets whenever a page opens, so a menu is never a surprise.
     */
    private var pulleyPage by mutableStateOf(false)

    /** Rows top-to-bottom, unrolling with the pull; TOOLS is last so the longest pull always leaves. */
    private fun pulleyItems(): List<String> {
        val p = page
        return when (screen) {
            Screen.Home -> emptyList()
            is Screen.Page -> if (p == null) listOf(Pull.TOOLS) else if (pulleyPage) buildList {
                // The convert page: what this page could become, and the way out.
                if (ticketsInstalled) add(Pull.TICKET)
                if (libraryInstalled) add(Pull.LIBRARY)
                add(Pull.TOOLS)
            } else buildList {
                // The shortest pull is one page back, the next one forward; each row is there
                // only when the page has somewhere to go. Never home: TOOLS is for that.
                if (p.canGoBack) add(Pull.BACK)
                if (p.canGoForward) add(Pull.FORWARD)
                add(Pull.REFRESH)
                if (p.tool.kind == ToolKind.SITE) {
                    // A copy is a file kept beside a tool's row, so only a tool on the shelf can
                    // have one. A page you searched for gets KEEP ON SHELF instead, further down.
                    if (p.tool.id.isNotEmpty()) add(Pull.SAVE)
                    val onHttp = p.currentUrl?.startsWith("http") == true
                    if (onHttp && (ticketsInstalled || libraryInstalled)) add(Pull.CONVERT)
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
        // Every row but CONVERT itself leaves the convert page behind.
        if (item != Pull.CONVERT) pulleyPage = false
        when (item) {
            Pull.TOOLS -> home()
            Pull.CONVERT -> {
                pulleyPage = true
                say("Pull again: make a ticket, or send to the library")
            }
            Pull.REFRESH -> {
                p?.reload()
                say("Reloading")
            }
            Pull.BACK -> p?.goBack()
            Pull.FORWARD -> p?.goForward()
            Pull.TICKET -> makeTicket()
            Pull.LIBRARY -> sendToLibrary()
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

    // ---- tools ----

    private fun show(tool: Tool, forceSaved: Boolean = false, resumeAt: String? = null, live: Boolean = false) {
        closeTool()
        readyUntil = 0L
        pulleyPage = false
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

    // ---- downloads ----

    private val downloadListener = object : Downloader.Listener {
        override fun onProgress(name: String, bytes: Long, total: Long) {
            status = if (total > 0) "Saving $name · ${(bytes * 100 / total)}%" else "Saving $name · ${Download.sizeLabel(bytes)}"
        }

        override fun onDone(download: Download) {
            say("Saved ${download.name} · DOWNLOADS on the shelf")
        }

        override fun onFailed(name: String, why: String) {
            say("Could not save $name: $why")
        }
    }

    /**
     * A saved file opens in the engine when the engine can show it (its PDF viewer, pictures,
     * text); anything else is offered to the phone by content URI, and if nothing takes it, we
     * say so rather than pretend.
     */
    private fun openDownload(d: Download) {
        val file = downloads.fileOf(d)
        if (!file.exists()) { say("That file is gone"); downloads.load(); return }
        if (d.viewable) {
            show(Tool(id = "", name = d.name, kind = ToolKind.SITE, url = Uri.fromFile(file).toString(), origins = emptyList(), added = System.currentTimeMillis()))
            return
        }
        val uri = FileProvider.getUriForFile(this, "com.gios.webtools.share", file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, d.mime.ifBlank { "*/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (runCatching { startActivity(intent) }.isFailure) say("Nothing on the phone opens a ${d.detail().substringBefore(" ·")}")
    }

    // ---- the browser role, and Wi-Fi sign-in ----

    private fun checkBrowserRole() {
        val rm = getSystemService(RoleManager::class.java)
        isBrowser = rm != null && rm.isRoleAvailable(RoleManager.ROLE_BROWSER) && rm.isRoleHeld(RoleManager.ROLE_BROWSER)
    }

    /**
     * Make this the browser, by whichever door the phone leaves open.
     *
     * Three, in order: the role dialog, which is the proper one; the Default apps screen, which
     * LightOS may or may not have an activity for; and, when neither answers, the one shell line
     * that always works, said out loud so it can be run from BrightControl. The row used to stop
     * at the first door and report nothing, which read as a row that does nothing at all.
     */
    private fun askBrowserRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleHeld(RoleManager.ROLE_BROWSER)) { say("Web Tools is already the browser"); return }
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
            if (runCatching { startActivity(rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER)) }.isSuccess) return
        }
        val settings = listOf(
            Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        )
        for (i in settings) {
            if (runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) {
                say("Pick Web Tools under Browser app")
                return
            }
        }
        say("This phone has no screen for it. BrightControl › ADB › GRANT ALL sets it, or: cmd role add-role-holder android.app.role.BROWSER com.gios.webtools")
    }

    /**
     * Whether the phone has a passkey provider. `credential_service` is the colon-joined list of
     * enabled providers; readable, not writable, from here. Bitwarden is the one this family uses.
     */
    private fun passkeyState(): String {
        val cur = runCatching { Settings.Secure.getString(contentResolver, "credential_service") }.getOrNull().orEmpty()
        return when {
            cur.contains(BITWARDEN_PACKAGE) -> "bitwarden"
            cur.isNotBlank() -> cur.substringBefore(':').substringBefore('/').substringAfterLast('.')
            installed(BITWARDEN_PACKAGE) -> "not set"
            else -> "none"
        }
    }

    private fun passkeyAdvice(): String = when {
        passkeyState() == "bitwarden" -> "Bitwarden answers for passkeys. It asks once whether to trust Web Tools."
        installed(BITWARDEN_PACKAGE) -> "Bitwarden is here but not set. BrightControl › ADB & grants › GRANT ALL sets it."
        else -> "No passkey provider on the phone. Install Bitwarden, then GRANT ALL in BrightControl."
    }

    private fun signInToWifi(intent: Intent?) {
        portal.begin(intent, object : Portal.Listener {
            override fun onStatus(text: String) { status = text }
            override fun onOpen(url: String) {
                show(Tool(id = "", name = "Wi-Fi sign-in", kind = ToolKind.SITE, url = url, origins = emptyList(), added = System.currentTimeMillis()))
            }
            override fun onSignedIn() {
                say("Signed in. The Wi-Fi is open.")
                handler.postDelayed({ if (page?.tool?.name == "Wi-Fi sign-in") go(Screen.Home) }, 1500)
            }
            override fun onGaveUp(why: String) { say(why) }
        })
    }

    // ---- tickets ----

    /** Which neighbours are on the phone; checked each time the app comes to the front. */
    private var ticketsInstalled by mutableStateOf(false)
    private var libraryInstalled by mutableStateOf(false)
    private var authInstalled by mutableStateOf(false)
    private fun installed(pkg: String) = runCatching { packageManager.getPackageInfo(pkg, 0) }.isSuccess
    private fun checkNeighbours() {
        ticketsInstalled = installed(TICKETS_PACKAGE)
        libraryInstalled = installed(LIBRARY_PACKAGE)
        authInstalled = installed(AUTH_PACKAGE)
    }

    // ---- the library ----

    /**
     * SEND TO LIBRARY: the article, lifted out of the page by Readability inside the page (the
     * same code as Reader View), written as a one-chapter EPUB and handed to the Library. Long
     * reads belong in a reader, not a browser.
     */
    private fun sendToLibrary() {
        val p = page ?: return
        say("Lifting the article out")
        app.bridge.article { a, why ->
            if (a == null) { say("Nothing to send: ${why ?: "no article on this page"}"); return@article }
            val article = Epub.Article(
                title = a.optString("title"), byline = a.optString("byline"), site = a.optString("site"),
                url = a.optString("url").ifBlank { p.currentUrl ?: p.tool.url }, lang = a.optString("lang"), xhtml = a.optString("xhtml"),
            )
            val dir = File(cacheDir, "books").apply { mkdirs() }
            val file = File(dir, Tool.slug(article.title.ifBlank { article.site }).ifBlank { "article" }.take(60) + ".epub")
            val ok = runCatching {
                Epub.write(article, file)
                val uri = FileProvider.getUriForFile(this, "com.gios.webtools.share", file)
                startActivity(
                    Intent(Intent.ACTION_SEND).setPackage(LIBRARY_PACKAGE).setType("application/epub+zip")
                        .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, article.title)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            }
            say(if (ok.isSuccess) "Sent to the Library" else "The Library is not on this phone")
        }
    }

    // ---- 2FA ----

    private val pickCode = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val code = result.data?.getStringExtra("code")
        if (result.resultCode != RESULT_OK || code.isNullOrBlank()) { say("No code"); return@registerForActivityResult }
        val account = result.data?.getStringExtra("account").orEmpty()
        val left = result.data?.getIntExtra("secondsLeft", 0) ?: 0
        app.bridge.type(code) { typed, why ->
            say(
                if (typed) "Typed the code from $account · ${left}s left"
                else "Code $code from $account · ${why ?: "could not type it"}",
            )
        }
    }

    /** 2FA CODE: ask Authenticator for one code for this site, then type it into the page. */
    private fun askForCode() {
        val p = page ?: return
        val host = Tool.hostOf(p.currentUrl ?: p.tool.url)
        val intent = Intent(ACTION_PICK_CODE).setPackage(AUTH_PACKAGE).putExtra("site", host)
        if (runCatching { pickCode.launch(intent) }.isFailure) say("Authenticator is not on this phone, or too old for this")
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
        checkNeighbours()
        checkBrowserRole()
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
        portal.stop()
    }

    private val listener = object : ToolPageListener {
        override fun onBlocked(host: String) {
            val tool = page?.tool
            if (tool != null && tool.id.isNotEmpty() && host.contains('.')) lastBlocked[tool.id] = host.lowercase().removePrefix("www.")
            say("Stays inside " + (tool?.origins?.firstOrNull() ?: "this site") + " · blocked $host · hold the tool to allow it")
        }

        override fun onSignInHop(host: String) {
            val tool = page?.tool ?: return
            if (tool.id.isNotEmpty()) {
                store.update(tool.id) { t -> if (t.origins.contains(host)) t else t.copy(origins = t.origins + host) }
            }
            say("Signing in at $host")
        }

        override fun onRedirectedTo(host: String) {
            val tool = page?.tool ?: return
            if (tool.id.isEmpty()) return
            store.update(tool.id) { t -> if (t.origins.contains(host)) t else t.copy(origins = t.origins + host) }
            say("Following the site to $host")
        }

        override fun onProgressAt(fraction: Float) {
            pageProgress = fraction
        }

        override fun onDownload(response: WebResponse) {
            downloader.start(response, downloadListener)
        }

        override fun onRefused(title: String) {
            say("This site refuses the sign-in from here. Sign in on a computer and bring the login over by code.")
        }

        override fun onProgress(loading: Boolean) {
            // The line along the top edge says it now, so the bottom strip stays for the things
            // that need words. It is still set for the saved-copy and no-signal cases below.
            pageLoading = loading
            if (!loading) pageProgress = 1f
            if (status == "Loading") status = null
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
            keep = s.keep, reader = s.reader, added = System.currentTimeMillis(), folder = s.folder,
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
