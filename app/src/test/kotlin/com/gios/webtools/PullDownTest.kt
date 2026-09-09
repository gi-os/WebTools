package com.gios.webtools

import com.gios.webtools.gesture.PullDown
import com.gios.webtools.gesture.PullDown.Stage
import com.gios.webtools.gesture.Pulley
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullDownTest {

    private fun g() = PullDown(slopPx = 16f)

    @Test fun `travel follows the thumb down and never goes negative`() {
        val p = g()
        p.down(100f, 100f)
        p.move(100f, 250f)
        assertEquals(150f, p.travel, 0f)
        p.move(100f, 50f)
        assertEquals(0f, p.travel, 0f)
        assertEquals(Stage.TRACKING, p.stage)
    }

    @Test fun `the lift reports the travel and resets`() {
        val p = g()
        p.down(0f, 0f)
        p.move(0f, 300f)
        assertEquals(300f, p.up(), 0f)
        assertEquals(Stage.IDLE, p.stage)
        assertEquals(0f, p.travel, 0f)
    }

    @Test fun `a sideways stroke is cancelled for good`() {
        val p = g()
        p.down(100f, 100f)
        p.move(160f, 120f)
        assertEquals(Stage.CANCELLED, p.stage)
        p.move(160f, 500f)
        assertEquals(Stage.CANCELLED, p.stage)
        assertEquals(0f, p.travel, 0f)
        assertEquals(0f, p.up(), 0f)
    }

    @Test fun `a downward stroke with a little drift is still a pull`() {
        val p = g()
        p.down(100f, 100f)
        assertTrue(p.move(130f, 400f))
        assertEquals(300f, p.travel, 0f)
    }

    @Test fun `move reports when the stroke should be claimed`() {
        val p = g()
        p.down(0f, 0f)
        assertFalse(p.move(0f, 10f))
        assertTrue(p.move(0f, 40f))
    }

    @Test fun `an upward stroke is never claimed`() {
        val p = g()
        p.down(0f, 500f)
        assertFalse(p.move(0f, 100f))
        assertEquals(0f, p.travel, 0f)
    }

    // ---- the pulley ----

    private val pitch = 100f
    private val dead = 40f

    @Test fun `nothing is picked inside the dead band or before half a row`() {
        assertEquals(-1, Pulley.select(0f, 3, pitch, dead))
        assertEquals(-1, Pulley.select(39f, 3, pitch, dead))
        assertEquals(-1, Pulley.select(89f, 3, pitch, dead))
    }

    @Test fun `the first row is reached first, the last row with the longest pull`() {
        assertEquals(0, Pulley.select(90f, 3, pitch, dead))
        assertEquals(0, Pulley.select(189f, 3, pitch, dead))
        assertEquals(1, Pulley.select(190f, 3, pitch, dead))
        assertEquals(2, Pulley.select(290f, 3, pitch, dead))
    }

    @Test fun `past the last row stays on the last row, so a long flick always exits`() {
        assertEquals(2, Pulley.select(2000f, 3, pitch, dead))
    }

    @Test fun `no rows means nothing to pick`() {
        assertEquals(-1, Pulley.select(500f, 0, pitch, dead))
    }

    @Test fun `the drawn height is capped at the menu`() {
        assertEquals(0f, Pulley.height(-5f, 3, pitch, dead), 0f)
        assertEquals(120f, Pulley.height(120f, 3, pitch, dead), 0f)
        assertEquals(340f, Pulley.height(900f, 3, pitch, dead), 0f)
    }
}
