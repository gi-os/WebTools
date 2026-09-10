package com.gios.webtools

import com.gios.webtools.web.Search
import com.gios.webtools.web.SearchEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchTest {
    @Test
    fun addresses() {
        for (t in listOf("mta.info", "https://x", "localhost", "en.wikipedia.org/wiki/Cat", "kagi.com/search?q=a", "10.0.0.1")) {
            assertTrue(Search.isAddress(t), t)
        }
    }

    @Test
    fun searches() {
        for (t in listOf("what is 3.5 inches in cm", "cat", "subway status", "mta. info", ".hidden", "trailing.", "v2.0", "")) {
            assertFalse(Search.isAddress(t), t)
        }
    }

    @Test
    fun resolveAddsSchemeOrEncodes() {
        assertEquals("https://mta.info", Search.resolve(" mta.info ", SearchEngine.KAGI))
        assertEquals("http://x.org/a", Search.resolve("http://x.org/a", SearchEngine.ECOSIA))
        assertEquals(
            "https://html.duckduckgo.com/html/?q=what+is+3.5+inches+in+cm%3F",
            Search.resolve("what is 3.5 inches in cm?", SearchEngine.DUCKDUCKGO),
        )
        assertEquals("https://www.ecosia.org/search?q=cat", Search.resolve("cat", SearchEngine.ECOSIA))
    }

    @Test
    fun enginesCycleAndPersistByName() {
        assertEquals(SearchEngine.ECOSIA, SearchEngine.DUCKDUCKGO.next())
        assertEquals(SearchEngine.DUCKDUCKGO, SearchEngine.KAGI.next())
        assertEquals(SearchEngine.KAGI, SearchEngine.byName("KAGI"))
        assertEquals(SearchEngine.DUCKDUCKGO, SearchEngine.byName(null))
        assertEquals(SearchEngine.DUCKDUCKGO, SearchEngine.byName("bing"))
    }
}
