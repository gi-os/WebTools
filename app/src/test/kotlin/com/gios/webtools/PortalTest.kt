package com.gios.webtools

import com.gios.webtools.web.Portal
import kotlin.test.Test
import kotlin.test.assertEquals

class PortalTest {
    @Test
    fun aGateOpensOnlyAfterItWasSeenClosed() {
        assertEquals(Portal.Verdict.OPEN_ALREADY, Portal.verdict(seenClosed = false, code = 204))
        assertEquals(Portal.Verdict.CLOSED, Portal.verdict(seenClosed = false, code = 302))
        assertEquals(Portal.Verdict.CLOSED, Portal.verdict(seenClosed = false, code = 200))
        assertEquals(Portal.Verdict.CLOSED, Portal.verdict(seenClosed = true, code = 511))
        assertEquals(Portal.Verdict.OPEN, Portal.verdict(seenClosed = true, code = 204))
        assertEquals(Portal.Verdict.SILENT, Portal.verdict(seenClosed = true, code = -1))
    }
}
