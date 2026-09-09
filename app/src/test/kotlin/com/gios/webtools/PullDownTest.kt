package com.gios.webtools

import com.gios.webtools.gesture.PullDown
import com.gios.webtools.gesture.PullDown.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullDownTest {

    private fun g() = PullDown(triggerPx = 300f, slopPx = 16f)

    @Test fun `a short pull does nothing on lift`() {
        val p = g()
        p.down(100f, 100f)
        p.move(100f, 200f)
        assertEquals(Stage.TRACKING, p.stage)
        assertFalse(p.up())
        assertEquals(Stage.IDLE, p.stage)
    }

    @Test fun `crossing the trigger arms and the lift commits`() {
        val p = g()
        p.down(100f, 100f)
        p.move(100f, 250f)
        p.move(100f, 420f)
        assertEquals(Stage.ARMED, p.stage)
        assertEquals(1f, p.progress, 0f)
        assertTrue(p.up())
    }

    @Test fun `pulling back under the trigger disarms`() {
        val p = g()
        p.down(100f, 100f)
        p.move(100f, 420f)
        assertEquals(Stage.ARMED, p.stage)
        p.move(100f, 300f)
        assertEquals(Stage.TRACKING, p.stage)
        assertFalse(p.up())
    }

    @Test fun `a sideways stroke is cancelled for good`() {
        val p = g()
        p.down(100f, 100f)
        p.move(160f, 120f)
        assertEquals(Stage.CANCELLED, p.stage)
        // Coming back to a straight pull does not revive it.
        p.move(160f, 500f)
        assertEquals(Stage.CANCELLED, p.stage)
        assertEquals(0f, p.progress, 0f)
        assertFalse(p.up())
    }

    @Test fun `a downward stroke with a little drift is still a pull`() {
        val p = g()
        p.down(100f, 100f)
        p.move(130f, 400f)
        assertEquals(Stage.ARMED, p.stage)
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
        assertEquals(0f, p.progress, 0f)
    }

    @Test fun `progress grows with travel`() {
        val p = g()
        p.down(0f, 0f)
        p.move(0f, 150f)
        assertEquals(0.5f, p.progress, 0.001f)
    }
}
