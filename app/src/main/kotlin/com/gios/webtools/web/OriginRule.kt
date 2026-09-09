package com.gios.webtools.web

/**
 * The wall. A tool may navigate only to hosts on its own list; everything else is a dead end,
 * not a new tab. No Android types so it can be tested plainly.
 */
object OriginRule {

    /**
     * True when [host] equals an allowed origin or is a subdomain of one. An empty list means
     * no wall at all (a GO page): any host goes.
     */
    fun allows(origins: List<String>, host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        if (origins.isEmpty()) return true
        val h = host.lowercase().removeSuffix(".")
        return origins.any { o ->
            val origin = o.lowercase().removePrefix("www.").removeSuffix(".")
            origin.isNotEmpty() && (h == origin || h.endsWith(".$origin"))
        }
    }

    /**
     * Whether a top-level navigation should go ahead. Only http and https count; a bundle's own
     * https origin is on its list like any other host. `file:` is allowed so a saved copy can be
     * opened, and only from the app's own directory, which the caller establishes.
     */
    fun decide(origins: List<String>, scheme: String?, host: String?, isOwnFile: Boolean): Decision {
        return when (scheme?.lowercase()) {
            "http", "https" -> if (allows(origins, host)) Decision.ALLOW else Decision.BLOCK
            "file" -> if (isOwnFile) Decision.ALLOW else Decision.BLOCK
            "tel" -> Decision.HAND_OFF
            // The engine's own: reader view, bundled pages, the bridge extension, PDF viewer.
            "about", "resource", "moz-extension", "data", "blob", "javascript" -> Decision.ALLOW
            else -> Decision.BLOCK
        }
    }

    enum class Decision { ALLOW, BLOCK, HAND_OFF }
}
