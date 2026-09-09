package com.gios.webtools.web

import com.gios.webtools.data.Engine
import com.gios.webtools.data.Tool
import com.gios.webtools.data.ToolKind
import org.json.JSONObject
import java.util.Base64
import java.util.zip.Inflater

/**
 * What a scanned code can say.
 *
 * Shapes accepted:
 *
 *  - A bare `http(s)://` address. The tool is named after the host and allowed only that host.
 *  - JSON from the companion page: `{"wt":1,"n":"Tickets","u":"https://…","o":["ticketmaster.com"],
 *    "keep":true,"e":"browser"}`. `o`, `keep` and `e` are optional; a missing `o` derives from the
 *    address; `e` is `builtin` unless it says `browser`.
 *  - The same, plus a login: `"k":"login","d":"ticketmaster.com","c":"<deflate-raw, base64url>"`
 *    where the compressed text is a `Cookie:` header (`a=1; b=2`). The cookies are set on `d`
 *    before the tool opens. This is how a sign-in done on a computer reaches the phone.
 *  - One part of a code too big for one image: `{"wt":1,"k":"part","id":"x7","i":1,"n":3,"p":"…"}`.
 *    The parts join in order into one of the shapes above.
 *
 * Anything else is not a tool, and the caller says so rather than guessing.
 */
object QrPayload {

    /** Cookies to set before opening: `name=value` pairs for one domain. */
    data class Login(val domain: String, val cookies: List<String>)

    sealed class Result {
        data class Ok(val tool: Tool, val login: Login? = null) : Result()
        data class Part(val id: String, val index: Int, val count: Int, val text: String) : Result()
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
        if (o.optString("k") == "part") {
            val id = o.optString("id")
            val i = o.optInt("i", 0)
            val n = o.optInt("n", 0)
            val p = o.optString("p")
            if (id.isEmpty() || i < 1 || n < 1 || i > n || p.isEmpty()) return Result.Bad("Broken part of a code.")
            return Result.Part(id, i, n, p)
        }
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
        val tool = build(name, url, withHost, o.optBoolean("keep", false), now, engine)

        if (o.optString("k") == "login") {
            val domain = o.optString("d").trim().lowercase().removePrefix(".").removePrefix("www.")
            if (domain.isEmpty()) return Result.Bad("Login code has no domain.")
            val header = runCatching { inflate(o.optString("c")) }.getOrElse { return Result.Bad("Login code did not unpack.") }
            val cookies = parseCookieHeader(header)
            if (cookies.isEmpty()) return Result.Bad("Login code has no cookies.")
            val origins2 = if (OriginRule.allows(tool.origins, domain)) tool.origins else tool.origins + domain
            return Result.Ok(tool.copy(origins = origins2), Login(domain, cookies))
        }
        return Result.Ok(tool)
    }

    /** `a=1; b=2; c=` -> `["a=1", "b=2", "c="]`. Names must be plain tokens. */
    fun parseCookieHeader(header: String): List<String> =
        header.split(';')
            .map { it.trim() }
            .filter { it.contains('=') && it.substringBefore('=').isNotBlank() && !it.substringBefore('=').any { c -> c.isWhitespace() || c == ',' } }
            .map { it.substringBefore('=').trim() + "=" + it.substringAfter('=').trim() }

    /** base64url of a raw DEFLATE stream (what the browser's CompressionStream("deflate-raw") emits). */
    fun inflate(b64: String): String {
        val data = Base64.getUrlDecoder().decode(b64.trim().trimEnd('='))
        val inf = Inflater(true)
        inf.setInput(data)
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!inf.finished()) {
            val n = inf.inflate(buf)
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
            out.write(buf, 0, n)
            if (out.size() > 512 * 1024) throw IllegalArgumentException("too large")
        }
        inf.end()
        return out.toString("UTF-8")
    }

    /** Joins scanned parts once all are present; null while some are missing. */
    fun assemble(parts: Map<Int, String>, count: Int): String? {
        if (count < 1 || parts.size < count) return null
        val sb = StringBuilder()
        for (i in 1..count) sb.append(parts[i] ?: return null)
        return sb.toString()
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
