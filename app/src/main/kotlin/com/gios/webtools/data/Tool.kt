package com.gios.webtools.data

import org.json.JSONArray
import org.json.JSONObject

/** Which engine shows a site. */
enum class Engine {
    /** The app's own WebView: the allowlist, the ad block, the saved copy. */
    BUILTIN,
    /**
     * The phone's Chromium, as a Custom Tab. Its cookie jar, its fingerprint. For sites whose
     * bot gate (Kasada on Ticketmaster's sign-in) refuses any embedded view.
     */
    BROWSER,
}

/** What a tool is made of. */
enum class ToolKind {
    /** HTML shipped in the app or installed later, served from its own https origin. */
    BUNDLE,
    /** A real web address, opened inside its own allowlist. */
    SITE,
}

/**
 * One entry in the list. Pure data, no Android types, so the QR parser and the origin rule can
 * be tested on the JVM.
 *
 * @property id       slug, unique, also the bundle's directory name and origin host label
 * @property origins  hosts the tool may navigate to; a host matches when it equals an entry or
 *                    ends with `.entry`
 * @property engine   built-in view, or the phone's browser
 * @property keep     save an offline copy after every successful live load
 * @property snapshotAt  epoch ms of the last saved copy, 0 when there is none
 */
data class Tool(
    val id: String,
    val name: String,
    val kind: ToolKind,
    val url: String,
    val origins: List<String>,
    val engine: Engine = Engine.BUILTIN,
    val keep: Boolean = false,
    val snapshotAt: Long = 0L,
    val lastUsed: Long = 0L,
    val added: Long = 0L,
    val builtIn: Boolean = false,
) {
    /** The address a bundle is served from. */
    val bundleHost: String get() = "$id.$BUNDLE_DOMAIN"

    val hasSnapshot: Boolean get() = snapshotAt > 0L

    /** Second line in the list: where it goes, or that it lives on the phone. */
    fun detail(): String = when (kind) {
        ToolKind.BUNDLE -> "on the phone"
        ToolKind.SITE -> (origins.firstOrNull() ?: hostOf(url)) + if (engine == Engine.BROWSER) " · in Chromium" else ""
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("kind", kind.name)
        .put("url", url)
        .put("origins", JSONArray(origins))
        .put("engine", engine.name)
        .put("keep", keep)
        .put("snapshotAt", snapshotAt)
        .put("lastUsed", lastUsed)
        .put("added", added)
        .put("builtIn", builtIn)

    companion object {
        const val BUNDLE_DOMAIN = "webtools.internal"

        fun fromJson(o: JSONObject): Tool {
            val origins = o.optJSONArray("origins")?.let { arr ->
                List(arr.length()) { arr.getString(it) }
            } ?: emptyList()
            return Tool(
                id = o.getString("id"),
                name = o.getString("name"),
                kind = runCatching { ToolKind.valueOf(o.optString("kind", "SITE")) }
                    .getOrDefault(ToolKind.SITE),
                url = o.optString("url", ""),
                origins = origins,
                engine = runCatching { Engine.valueOf(o.optString("engine", "BUILTIN")) }
                    .getOrDefault(Engine.BUILTIN),
                keep = o.optBoolean("keep", false),
                snapshotAt = o.optLong("snapshotAt", 0L),
                lastUsed = o.optLong("lastUsed", 0L),
                added = o.optLong("added", 0L),
                builtIn = o.optBoolean("builtIn", false),
            )
        }

        /** `https://www.mta.info/status?x=1` -> `mta.info`. Empty when there is no host. */
        fun hostOf(url: String): String {
            val afterScheme = url.substringAfter("://", "")
            if (afterScheme.isEmpty()) return ""
            val host = afterScheme.takeWhile { it != '/' && it != '?' && it != '#' }
                .substringAfter('@')
                .substringBefore(':')
                .lowercase()
            return host.removePrefix("www.")
        }

        /** A stable id from a name: lowercase, letters and digits, dashes between words. */
        fun slug(name: String): String {
            val s = name.lowercase()
                .map { if (it.isLetterOrDigit()) it else '-' }
                .joinToString("")
                .split('-').filter { it.isNotEmpty() }.joinToString("-")
            return if (s.isEmpty()) "tool" else s.take(24)
        }
    }
}
