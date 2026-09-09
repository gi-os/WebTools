package com.gios.webtools.web

import com.gios.webtools.data.Engine
import com.gios.webtools.data.Tool
import com.gios.webtools.data.ToolKind
import org.json.JSONObject

/**
 * What a scanned code can say.
 *
 * Two shapes are accepted:
 *
 *  - A bare `http(s)://` address. The tool is named after the host and allowed only that host.
 *  - JSON from the companion page: `{"wt":1,"n":"Tickets","u":"https://…","o":["ticketmaster.com"],
 *    "keep":true,"e":"browser"}`. `o`, `keep` and `e` are optional; a missing `o` derives from the
 *    address; `e` is `builtin` unless it says `browser`.
 *
 * Anything else is not a tool, and the caller says so rather than guessing.
 */
object QrPayload {

    sealed class Result {
        data class Ok(val tool: Tool) : Result()
        data class Bad(val why: String) : Result()
    }

    fun parse(raw: String?, now: Long = System.currentTimeMillis()): Result {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return Result.Bad("Nothing in the code.")
        return if (text.startsWith("{")) fromJson(text, now) else fromUrl(text, now)
    }

    private fun fromUrl(text: String, now: Long): Result {
        if (!isHttp(text)) return Result.Bad("Not a web address.")
        val host = Tool.hostOf(text)
        if (host.isEmpty()) return Result.Bad("No site in that address.")
        val name = host.substringBefore('.').replaceFirstChar { it.uppercase() }
        return Result.Ok(build(name, text, listOf(host), keep = false, now = now))
    }

    private fun fromJson(text: String, now: Long): Result {
        val o = runCatching { JSONObject(text) }.getOrElse { return Result.Bad("Code is not readable.") }
        if (!o.has("u")) return Result.Bad("Code has no address.")
        val url = o.optString("u").trim()
        if (!isHttp(url)) return Result.Bad("Not a web address.")
        val host = Tool.hostOf(url)
        if (host.isEmpty()) return Result.Bad("No site in that address.")
        val name = o.optString("n").trim().ifEmpty { host.substringBefore('.').replaceFirstChar { it.uppercase() } }
        val origins = o.optJSONArray("o")?.let { arr ->
            List(arr.length()) { arr.optString(it).trim().lowercase().removePrefix("www.") }
                .filter { it.isNotEmpty() }
        }.orEmpty().ifEmpty { listOf(host) }
        val withHost = if (OriginRule.allows(origins, host)) origins else origins + host
        val engine = if (o.optString("e").equals("browser", ignoreCase = true)) Engine.BROWSER else Engine.BUILTIN
        return Result.Ok(build(name, url, withHost, o.optBoolean("keep", false), now, engine))
    }

    private fun build(
        name: String, url: String, origins: List<String>, keep: Boolean, now: Long,
        engine: Engine = Engine.BUILTIN,
    ) = Tool(
        id = Tool.slug(name) + "-" + (url.hashCode().toUInt().toString(36).take(4)),
        name = name.take(40),
        kind = ToolKind.SITE,
        url = url,
        origins = origins.distinct(),
        engine = engine,
        keep = keep,
        added = now,
    )

    private fun isHttp(s: String) = s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true)
}
