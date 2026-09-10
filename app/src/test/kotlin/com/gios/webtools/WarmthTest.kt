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
}
