package com.gios.webtools.web

import java.net.URLEncoder

/**
 * The one field at the top of the shelf. An address opens; anything else goes to the chosen
 * engine. Three engines, none of them Google: DuckDuckGo's plain HTML results (fast on a small
 * panel, no scripts to speak of), Ecosia, and Kagi (a paid account; its login comes over by
 * code like any other).
 */
enum class SearchEngine(val label: String, private val template: String) {
    DUCKDUCKGO("DuckDuckGo", "https://html.duckduckgo.com/html/?q=%s"),
    ECOSIA("Ecosia", "https://www.ecosia.org/search?q=%s"),
    KAGI("Kagi", "https://kagi.com/search?q=%s");

    fun url(query: String): String = template.replace("%s", URLEncoder.encode(query.trim(), "UTF-8"))

    fun next(): SearchEngine = entries[(ordinal + 1) % entries.size]

    companion object {
        fun byName(name: String?): SearchEngine = entries.firstOrNull { it.name == name } ?: DUCKDUCKGO
    }
}

object Search {
    /**
     * Whether typed text is an address rather than words to look up. A scheme settles it; so does
     * a single token with a dot and no spaces (`mta.info`, `en.wikipedia.org/wiki/Cat`). "what is
     * 3.5 inches in cm" has a dot and spaces, so it searches.
     */
    fun isAddress(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (t.contains("://")) return true
        if (t.any { it.isWhitespace() }) return false
        val host = t.substringBefore('/').substringBefore('?').substringBefore('#')
        if (host == "localhost") return true
        if (!host.contains('.')) return false
        if (host.startsWith(".") || host.endsWith(".")) return false
        val tld = host.substringAfterLast('.')
        return (tld.length >= 2 && tld.all { it.isLetter() }) || host.all { it.isDigit() || it == '.' }
    }

    /** The address to open for what was typed. */
    fun resolve(text: String, engine: SearchEngine): String {
        val t = text.trim()
        return if (isAddress(t)) (if (t.contains("://")) t else "https://$t") else engine.url(t)
    }
}
