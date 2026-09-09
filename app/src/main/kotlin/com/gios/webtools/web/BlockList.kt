package com.gios.webtools.web

/**
 * Hosts a site tool never fetches: ad servers, trackers, and the deep-link routers behind
 * "open in the app" banners. A request whose host equals an entry, or sits under one, gets an
 * empty answer instead of a network round trip.
 *
 * Pure. The list arrives as lines; `#` comments and blanks are dropped.
 */
class BlockList(lines: Sequence<String>) {

    private val hosts: HashSet<String> = HashSet()

    init {
        for (raw in lines) {
            val line = raw.substringBefore('#').trim().lowercase()
            if (line.isEmpty()) continue
            // Tolerate hosts-file shape ("0.0.0.0 host") as well as one host per line.
            val host = line.split(' ', '\t').last().removePrefix("www.").removeSuffix(".")
            if (host.isNotEmpty() && host != "localhost" && host.contains('.')) hosts.add(host)
        }
    }

    val size: Int get() = hosts.size

    /** True when [host] or any parent domain of it is listed. */
    fun blocks(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        var h = host.lowercase().removeSuffix(".")
        while (true) {
            if (h in hosts) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
            if (!h.contains('.')) return false   // never match a bare TLD
        }
    }

    companion object {
        fun of(vararg text: String): BlockList = BlockList(text.asSequence().flatMap { it.lineSequence() })
    }
}
