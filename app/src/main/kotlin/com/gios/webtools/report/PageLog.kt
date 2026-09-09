package com.gios.webtools.report

import java.util.ArrayDeque

/**
 * What happened inside the WebView since the tool opened, kept in memory so a shake can send it.
 * Bounded, so a page that logs in a loop cannot grow it without limit. No Android types.
 */
class PageLog(private val max: Int = 120) {

    private val lines = ArrayDeque<String>()
    private var blockedCount = 0
    private val blockedHosts = LinkedHashSet<String>()
    private var startedAt = System.currentTimeMillis()

    @Synchronized private fun add(line: String) {
        val t = ((System.currentTimeMillis() - startedAt) / 1000.0)
        lines.addLast(String.format("%6.1fs  %s", t, line.take(400)))
        while (lines.size > max) lines.removeFirst()
    }

    fun nav(url: String?) = add("nav      $url")
    fun navBlocked(url: String) = add("wall     $url")
    fun title(title: String?) = add("title    $title")
    fun error(url: String, why: String) = add("error    $why  <- $url")

    fun http(url: String, status: Int, headers: Map<String, String>?) {
        val interesting = headers.orEmpty().filterKeys { k ->
            k.lowercase() in setOf("server", "content-type", "location", "x-iinfo", "cf-ray", "x-cache", "via", "set-cookie")
        }.map { (k, v) -> "$k: ${v.take(80)}" }
        add("http $status  $url  ${interesting.joinToString(" | ")}")
    }

    fun console(level: String, message: String?, source: String?, line: Int) =
        add("js.${level.lowercase().take(5)}  ${message?.take(200)}  (${source?.substringAfterLast('/')?.take(40)}:$line)")

    @Synchronized fun blocked(host: String) {
        blockedCount++
        if (blockedHosts.size < 40) blockedHosts.add(host)
    }

    @Synchronized fun dump(): String {
        val sb = StringBuilder()
        sb.append("blocked requests: ").append(blockedCount)
        if (blockedHosts.isNotEmpty()) sb.append("  (").append(blockedHosts.joinToString(", ")).append(')')
        sb.append('\n')
        lines.forEach { sb.append(it).append('\n') }
        return sb.toString()
    }
}
