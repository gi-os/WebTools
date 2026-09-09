package com.gios.webtools

import com.gios.webtools.web.BlockList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockListTest {

    private val list = BlockList.of(
        """
        # comment
        doubleclick.net
        0.0.0.0 ads.example.com
        www.tracker.io
        branch.io
        localhost
        """.trimIndent(),
    )

    @Test fun `parses comments hosts-file lines and www`() = assertEquals(4, list.size)
    @Test fun `exact host`() = assertTrue(list.blocks("doubleclick.net"))
    @Test fun `subdomain of a listed host`() = assertTrue(list.blocks("stats.g.doubleclick.net"))
    @Test fun `hosts-file shape`() = assertTrue(list.blocks("ads.example.com"))
    @Test fun `www stripped from the list matches without www`() = assertTrue(list.blocks("tracker.io"))
    @Test fun `parent of a listed host is not blocked`() = assertFalse(list.blocks("example.com"))
    @Test fun `unrelated host`() = assertFalse(list.blocks("ticketmaster.com"))
    @Test fun `a bare tld never matches`() = assertFalse(list.blocks("net"))
    @Test fun `null and empty`() {
        assertFalse(list.blocks(null))
        assertFalse(list.blocks(""))
    }
    @Test fun `app banner router`() = assertTrue(list.blocks("x.branch.io"))
}
