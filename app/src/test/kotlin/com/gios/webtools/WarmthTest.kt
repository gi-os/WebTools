package com.gios.webtools

import com.gios.webtools.web.Warmth
import kotlin.test.Test
import kotlin.test.assertEquals

class WarmthTest {
    @Test
    fun plainPageParksAfterTheGrace() {
        assertEquals(Warmth.GRACE_MS, Warmth.parkDelay(now = 1_000_000L, readyUntil = 0L))
        assertEquals(Warmth.GRACE_MS, Warmth.parkDelay(now = 1_000_000L, readyUntil = 1_000_000L + 30_000L))
    }

    @Test
    fun aTicketPageStaysForTheRestOfItsHour() {
        val now = 5_000_000L
        val readyUntil = now + Warmth.READY_MS - 10 * 60 * 1000L
        assertEquals(50 * 60 * 1000L, Warmth.parkDelay(now, readyUntil))
        assertEquals(50 * 60 * 1000L + Warmth.EXIT_AFTER_PARK_MS, Warmth.exitDelay(now, readyUntil))
    }

    @Test
    fun aLongerGraceWins() {
        assertEquals(15 * 60_000L, Warmth.parkDelay(now = 0L, readyUntil = 0L, graceMs = 15 * 60_000L))
        assertEquals(Warmth.READY_MS, Warmth.parkDelay(now = 0L, readyUntil = Warmth.READY_MS, graceMs = 15 * 60_000L))
    }

    @Test
    fun graceCyclesRoundTheList() {
        assertEquals(5 * 60_000L, Warmth.nextGrace(2 * 60_000L))
        assertEquals(1 * 60_000L, Warmth.nextGrace(30 * 60_000L))
        assertEquals(1 * 60_000L, Warmth.nextGrace(7 * 60_000L)) // not on the list: start over
        assertEquals("1 minute", Warmth.graceLabel(60_000L))
        assertEquals("15 minutes", Warmth.graceLabel(15 * 60_000L))
    }
}
