package com.gios.webtools

import com.gios.webtools.web.OriginRule
import com.gios.webtools.web.OriginRule.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginRuleTest {

    private val tm = listOf("ticketmaster.com", "livenation.com")

    @Test fun `exact host is allowed`() = assertTrue(OriginRule.allows(tm, "ticketmaster.com"))
    @Test fun `subdomain is allowed`() = assertTrue(OriginRule.allows(tm, "auth.ticketmaster.com"))
    @Test fun `www prefix on the origin is ignored`() = assertTrue(OriginRule.allows(listOf("www.mta.info"), "new.mta.info"))
    @Test fun `case does not matter`() = assertTrue(OriginRule.allows(tm, "WWW.TicketMaster.COM"))
    @Test fun `trailing dot is tolerated`() = assertTrue(OriginRule.allows(tm, "ticketmaster.com."))
    @Test fun `a lookalike is not a subdomain`() = assertFalse(OriginRule.allows(tm, "notticketmaster.com"))
    @Test fun `an unrelated host is blocked`() = assertFalse(OriginRule.allows(tm, "google.com"))
    @Test fun `empty host is blocked`() = assertFalse(OriginRule.allows(tm, ""))
    @Test fun `null host is blocked`() = assertFalse(OriginRule.allows(tm, null))
    @Test fun `empty origin never matches`() = assertFalse(OriginRule.allows(listOf(""), "anything.com"))

    @Test fun `https to an allowed host goes ahead`() =
        assertEquals(Decision.ALLOW, OriginRule.decide(tm, "https", "ticketmaster.com", false))

    @Test fun `https elsewhere is a dead end`() =
        assertEquals(Decision.BLOCK, OriginRule.decide(tm, "https", "facebook.com", false))

    @Test fun `own saved copy opens`() =
        assertEquals(Decision.ALLOW, OriginRule.decide(tm, "file", null, true))

    @Test fun `a file outside our directory does not`() =
        assertEquals(Decision.BLOCK, OriginRule.decide(tm, "file", null, false))

    @Test fun `tel is handed to the phone`() =
        assertEquals(Decision.HAND_OFF, OriginRule.decide(tm, "tel", null, false))

    @Test fun `intent and market schemes are blocked`() {
        assertEquals(Decision.BLOCK, OriginRule.decide(tm, "intent", "x", false))
        assertEquals(Decision.BLOCK, OriginRule.decide(tm, "market", "x", false))
        assertEquals(Decision.BLOCK, OriginRule.decide(tm, "mailto", null, false))
    }
}
