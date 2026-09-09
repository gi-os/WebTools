package com.gios.webtools

import com.gios.webtools.report.ShakeGesture
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeGestureTest {

    private val g = 9.81f

    /** Alternating hard pushes and pulls along one axis, [n] of them, [gap] ms apart. */
    private fun shake(s: ShakeGesture, n: Int, gap: Long, start: Long = 1000L, strength: Float = 0.6f): Boolean {
        var fired = false
        for (i in 0 until n) {
            val sign = if (i % 2 == 0) 1 else -1
            fired = s.sample(0f, 0f, g * (1f + sign * strength), start + i * gap) || fired
        }
        return fired
    }

    @Test fun `three reversals fire`() = assertTrue(shake(ShakeGesture(), 3, 120))

    @Test fun `two reversals do not`() = assertFalse(shake(ShakeGesture(), 2, 120))

    @Test fun `a walk stays under the threshold`() {
        val s = ShakeGesture()
        var fired = false
        for (i in 0 until 40) fired = s.sample(0f, 0f, g * (1f + (if (i % 2 == 0) 0.3f else -0.3f)), 1000L + i * 100) || fired
        assertFalse(fired)
    }

    @Test fun `a single jolt is not a shake`() {
        val s = ShakeGesture()
        assertFalse(s.sample(0f, 0f, g * 3f, 1000L))
        assertFalse(s.sample(0f, 0f, g, 1100L))
        assertFalse(s.sample(0f, 0f, g, 1200L))
    }

    @Test fun `reversals spread over too long a window do not count`() =
        assertFalse(shake(ShakeGesture(windowMs = 900L), 3, 600))

    @Test fun `cooldown swallows the second shake`() {
        val s = ShakeGesture(cooldownMs = 2500L)
        assertTrue(shake(s, 3, 120, start = 1000L))
        assertFalse(shake(s, 3, 120, start = 2000L))
        assertTrue(shake(s, 3, 120, start = 5000L))
    }
}
