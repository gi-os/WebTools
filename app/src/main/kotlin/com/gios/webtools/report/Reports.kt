package com.gios.webtools.report

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.PixelCopy
import android.view.View
import com.gios.webtools.BuildConfig
import com.gios.webtools.data.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Everything a report is made of, gathered at the shake, sent after the sheet says yes. */
data class Draft(
    val title: String,
    val body: String,
)

/**
 * Files an issue against `gi-os/light-reports`, the private tracker the whole family reports
 * into. Queued on disk first and posted after: a phone reporting a stuck page was misbehaving,
 * and anything held only in flight is what gets lost. A build with no token still collects.
 */
object Reports {

    private const val REPO = "gi-os/light-reports"
    private const val BODY_CAP = 60_000

    /** Eight hex characters that separate Gio's phone from a stranger's without naming either. */
    fun installId(context: Context): String {
        val androidId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "?"
        val digest = MessageDigest.getInstance("SHA-256").digest(("webtools:" + androidId).toByteArray())
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }

    fun compose(
        context: Context,
        tool: Tool?,
        note: String,
        pageLog: String?,
        probe: String?,
        screenshot: Bitmap?,
        online: Boolean,
    ): Draft {
        val v = BuildConfig.VERSION_NAME
        val what = note.ifBlank { tool?.name?.let { "$it: page report" } ?: "report" }
        val title = "WebTools v$v — $what".take(120)
        val sb = StringBuilder()
        sb.append("| | |\n|---|---|\n")
        sb.append("| app | WebTools v").append(v).append(" (").append(BuildConfig.VERSION_CODE).append(") |\n")
        sb.append("| install | `").append(installId(context)).append("` |\n")
        sb.append("| device | ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(", Android ").append(Build.VERSION.RELEASE).append(" (").append(Build.DISPLAY).append(") |\n")
        sb.append("| webview | ").append(webViewVersion(context)).append(" |\n")
        sb.append("| online | ").append(online).append(" |\n")
        if (tool != null) {
            sb.append("| tool | ").append(tool.name).append(" · ").append(tool.kind).append(" · ").append(tool.engine).append(" |\n")
            sb.append("| url | ").append(tool.url).append(" |\n")
            sb.append("| origins | ").append(tool.origins.joinToString(", ")).append(" |\n")
        }
        sb.append('\n')
        if (note.isNotBlank()) sb.append("**Note:** ").append(note.trim()).append("\n\n")
        if (probe != null) {
            sb.append("<details><summary>What the page sees</summary>\n\n```json\n").append(pretty(probe).take(6000)).append("\n```\n</details>\n\n")
        }
        if (pageLog != null) {
            sb.append("<details><summary>Page log</summary>\n\n```\n").append(pageLog.take(20_000)).append("\n```\n</details>\n\n")
        }
        if (screenshot != null) {
            val b64 = encode(screenshot)
            if (b64 != null && sb.length + b64.length < BODY_CAP) {
                sb.append("<details><summary>Screen</summary>\n\n<img alt=\"screen\" width=\"270\" src=\"data:image/jpeg;base64,").append(b64).append("\">\n</details>\n")
            } else {
                sb.append("_Screenshot left out: too large._\n")
            }
        }
        return Draft(title, sb.toString().take(BODY_CAP))
    }

    private fun pretty(json: String): String = runCatching {
        // evaluateJavascript hands back a JSON string literal; unwrap it once.
        val inner = if (json.startsWith("\"")) JSONArray("[$json]").getString(0) else json
        JSONObject(inner).toString(2)
    }.getOrDefault(json)

    private fun webViewVersion(context: Context): String = runCatching {
        val pkg = android.webkit.WebView.getCurrentWebViewPackage()
        if (pkg == null) "unknown" else pkg.packageName + " " + pkg.versionName
    }.getOrDefault("unknown")

    /** 360px wide, greyscale, JPEG. Lands around 20KB. */
    private fun encode(src: Bitmap): String? = runCatching {
        val w = 360
        val h = (src.height.toLong() * w / src.width).toInt().coerceAtLeast(1)
        val small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(small)
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        p.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        c.drawColor(Color.BLACK)
        c.drawBitmap(src, null, android.graphics.Rect(0, 0, w, h), p)
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 55, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    /** Grabs the window as the user sees it. Calls back on the main thread, null on failure. */
    fun screenshot(activity: Activity, done: (Bitmap?) -> Unit) {
        val root: View = activity.window.decorView
        if (root.width <= 0 || root.height <= 0) { done(null); return }
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        runCatching {
            PixelCopy.request(activity.window, bmp, { result ->
                if (result == PixelCopy.SUCCESS) done(bmp) else done(fallback(root))
            }, Handler(Looper.getMainLooper()))
        }.onFailure { done(fallback(root)) }
    }

    private fun fallback(root: View): Bitmap? = runCatching {
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        bmp
    }.getOrNull()

    // ---- queue + send ----

    private fun queueDir(context: Context) = File(context.filesDir, "reports").also { it.mkdirs() }

    fun enqueue(context: Context, draft: Draft) {
        val f = File(queueDir(context), "r-${System.currentTimeMillis()}.json")
        f.writeText(JSONObject().put("title", draft.title).put("body", draft.body).toString())
    }

    /** Posts everything queued. Returns how many went. Call off the main thread. */
    suspend fun drain(context: Context): Int = withContext(Dispatchers.IO) {
        val token = BuildConfig.REPORT_TOKEN
        if (token.isBlank()) return@withContext 0
        var sent = 0
        for (f in queueDir(context).listFiles().orEmpty().sortedBy { it.name }) {
            val o = runCatching { JSONObject(f.readText()) }.getOrNull()
            if (o == null) { f.delete(); continue }
            when (post(token, o)) {
                Outcome.SENT -> { f.delete(); sent++ }
                Outcome.REJECTED -> f.delete()      // the payload's fault; retrying cannot help
                Outcome.RETRY -> break               // token, network, or the tracker: try next launch
            }
        }
        sent
    }

    private enum class Outcome { SENT, REJECTED, RETRY }

    private fun post(token: String, issue: JSONObject): Outcome = runCatching {
        val conn = URL("https://api.github.com/repos/$REPO/issues").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", "WebTools/" + BuildConfig.VERSION_NAME)
        val payload = JSONObject()
            .put("title", issue.getString("title"))
            .put("body", issue.getString("body"))
            .put("labels", JSONArray(listOf("webtools", "self-reported")))
        conn.outputStream.use { it.write(payload.toString().toByteArray()) }
        val code = conn.responseCode
        when {
            code in 200..299 -> Outcome.SENT
            code == 400 || code == 413 || code == 422 -> Outcome.REJECTED
            else -> Outcome.RETRY
        }
    }.getOrDefault(Outcome.RETRY)

    fun pending(context: Context): Int = queueDir(context).listFiles().orEmpty().size
}
