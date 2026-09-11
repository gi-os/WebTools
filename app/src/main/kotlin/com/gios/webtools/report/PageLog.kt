package com.gios.webtools.report

import java.util.ArrayDeque

/**
 * What happened inside the WebView since the tool opened, kept in memory so a shake can send it.
 * Bounded, so a page that logs in a loop cannot grow it without limit. No Android types.
 */
class PageLog(private val max: Int = 60) {

    private val lines = ArrayDeque<String>()
    private var blockedCount = 0
    private val blockedHosts = LinkedHashSet<String>()
    private var startedAt = System.currentTimeMillis()

    @Synchronized private fun add(line: String) {
        val t = ((System.currentTimeMillis() - startedAt) / 1000.0)
        lines.addLast(String.format("%6.1fs  %s", t, line.take(160)))
        while (lines.size > max) lines.removeFirst()
    }

    /**
     * An address, short enough to read and short enough to send.
     *
     * A sign-in URL carries the whole OAuth request in its query — AXS's is 700 characters, and
     * eight of them in a log is most of a report. The query is never what a reader needs; that
     * it *had* one, and how long, sometimes is.
     */
    private fun short(url: String?): String {
        val u = url ?: return "null"
        val q = u.indexOf('?')
        if (q < 0) return u.take(160)
        return u.take(q).take(120) + "?[" + (u.length - q - 1) + " chars]"
    }

    fun nav(url: String?) = add("nav      " + short(url))
    fun navBlocked(url: String) = add("wall     " + short(url))
    fun title(title: String?) = add("title    ${title?.take(100)}")
    fun error(url: String, why: String) = add("error    $why  <- " + short(url))

    fun http(url: String, status: Int, headers: Map<String, String>?) {
        val interesting = headers.orEmpty().filterKeys { k ->
            k.lowercase() in setOf("server", "content-type", "location", "x-iinfo", "cf-ray", "x-cache", "via", "set-cookie")
        }.map { (k, v) -> "$k: ${v.take(80)}" }
        add("http $status  " + short(url) + "  ${interesting.joinToString(" | ")}")
    }

    fun console(level: String, message: String?, source: String?, line: Int) =
        add("js.${level.lowercase().take(5)}  ${message?.take(200)}  (${source?.substringAfterLast('/')?.take(40)}:$line)")

    @Synchronized fun blocked(host: String) {
        blockedCount++
        if (blockedHosts.size < 40) blockedHosts.add(host)
    }

    /** Everything the log holds, capped: a report has to fit in one issue. */
    @Synchronized fun dump(): String {
        val sb = StringBuilder()
        sb.append("blocked requests: ").append(blockedCount)
        if (blockedHosts.isNotEmpty()) sb.append("  (").append(blockedHosts.joinToString(", ")).append(')')
        sb.append('\n')
        lines.forEach { sb.append(it).append('\n') }
        return sb.toString().take(8_000)
    }
}
