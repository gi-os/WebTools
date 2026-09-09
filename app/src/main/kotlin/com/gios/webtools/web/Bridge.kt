package com.gios.webtools.web

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.WebExtension

/**
 * The app's side of the bridge extension: one native-messaging port, request ids, callbacks.
 *
 * GeckoView has no `CookieManager` and no `evaluateJavascript`; the extension is how the app
 * sets a carried-over login and asks a page what it sees. Requests made before the extension has
 * connected are queued and sent when it does. A request nobody answers in [TIMEOUT_MS] fails
 * rather than hangs.
 */
class Bridge {

    private val main = Handler(Looper.getMainLooper())
    private var port: WebExtension.Port? = null
    private var nextId = 1
    private val pending = HashMap<Int, (JSONObject?) -> Unit>()
    private val queue = ArrayList<JSONObject>()

    val connected: Boolean get() = port != null

    fun attach(ext: WebExtension) {
        ext.setMessageDelegate(
            object : WebExtension.MessageDelegate {
                override fun onConnect(p: WebExtension.Port) {
                    port = p
                    p.setDelegate(object : WebExtension.PortDelegate {
                        override fun onPortMessage(message: Any, source: WebExtension.Port) {
                            val o = message as? JSONObject ?: return
                            val id = o.optInt("id", -1)
                            val cb = pending.remove(id) ?: return
                            cb(o)
                        }

                        override fun onDisconnect(source: WebExtension.Port) {
                            if (port === source) port = null
                        }
                    })
                    val q = ArrayList(queue)
                    queue.clear()
                    q.forEach { p.postMessage(it) }
                    Log.i("WebTools", "bridge connected")
                }
            },
            "webtools",
        )
    }

    private fun send(type: String, body: JSONObject, cb: (JSONObject?) -> Unit) {
        val id = nextId++
        body.put("id", id).put("type", type)
        pending[id] = cb
        main.postDelayed({ pending.remove(id)?.invoke(null) }, TIMEOUT_MS)
        val p = port
        if (p != null) p.postMessage(body) else queue.add(body)
    }

    /** Sets `name=value` cookies for `https://<domain>/`. Reports how many stuck. */
    fun setCookies(domain: String, cookies: List<String>, done: (set: Int, failed: List<String>) -> Unit) {
        val arr = JSONArray()
        for (c in cookies) {
            arr.put(JSONObject().put("name", c.substringBefore('=')).put("value", c.substringAfter('=')))
        }
        send("setCookies", JSONObject().put("domain", domain).put("cookies", arr)) { r ->
            if (r == null || !r.optBoolean("ok")) {
                done(0, cookies.map { it.substringBefore('=') })
            } else {
                val f = r.optJSONArray("failed")
                done(r.optInt("set"), List(f?.length() ?: 0) { f!!.optString(it) })
            }
        }
    }

    fun clearCookies(domain: String, done: (Boolean) -> Unit) {
        send("clear", JSONObject().put("domain", domain)) { done(it?.optBoolean("ok") == true) }
    }

    /** What the current page sees, as a JSON string, or a short reason when it could not be asked. */
    fun probe(done: (String) -> Unit) {
        send("probe", JSONObject()) { r ->
            when {
                r == null -> done("""{"why":"bridge did not answer"}""")
                r.has("probe") && !r.isNull("probe") -> done(r.optJSONObject("probe")?.toString() ?: r.toString())
                else -> done(r.toString())
            }
        }
    }

    companion object {
        private const val TIMEOUT_MS = 4000L
    }
}
