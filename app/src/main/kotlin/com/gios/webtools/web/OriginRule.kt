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

    /**
     * Whether a blocked address is a sign-in hop rather than a wander.
     *
     * A site's own wall is a list of the hosts it normally uses, and signing in is exactly the
     * moment a site stops using them: AXS, Ticketmaster and half the web hand the browser to an
     * identity host for one round trip. Dead-ending there is indistinguishable from the app being
     * broken — the page goes white and nothing says why. So a hop that names itself (auth, login,
     * sso, oauth, openid, identity, session, account, a `redirect_uri`) is followed, and its host
     * is learned for this tool, which is the same rule already used for a first-load redirect.
     *
     * It is a real widening of the wall, and a deliberate one: the alternative is a browser that
     * cannot sign in to anything, which is not a browser.
     */
    fun looksLikeSignIn(url: String): Boolean {
        val u = url.lowercase()
        if (!u.startsWith("http")) return false
        val host = u.substringAfter("://").substringBefore('/')
        val rest = u.substringAfter("://").substringAfter('/', "")
        val words = listOf("auth", "login", "signin", "sign-in", "sso", "oauth", "openid", "identity", "session", "account", "connect", "callback")
        if (words.any { host.contains(it) }) return true
        // A host whose first label is one of these is an identity host by convention, even
        // though the label spells none of the words above: id.ticketmaster.com, idp.example.
        if (host.substringBefore('.') in setOf("id", "idp", "secure", "my")) return true
        if (words.any { rest.substringBefore('?').contains(it) }) return true
        return rest.contains("redirect_uri=") || rest.contains("client_id=") || rest.contains("response_type=")
    }

    enum class Decision { ALLOW, BLOCK, HAND_OFF }
}
