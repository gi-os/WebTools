package com.gios.webtools.web

/**
 * How long a page stays alive after the app leaves the screen.
 *
 * Firefox's engine is a whole second program. A page left open in the background keeps its
 * scripts running and its processes warm, and that is where the battery went. So a page that
 * goes to the background is parked (its session closed, its address remembered) after a short
 * grace, and re-opened where it was when you come back. MAKE A TICKET extends the grace to an
 * hour, because that page is the one you will hold up at the gate. Once parked, the whole
 * process leaves a little later, so the engine is not sitting in memory for nothing.
 */
object Warmth {
    const val GRACE_MS = 2 * 60 * 1000L
    const val READY_MS = 60 * 60 * 1000L
    const val EXIT_AFTER_PARK_MS = 5 * 60 * 1000L

    /** The graces on offer in Settings, in minutes. */
    val GRACE_CHOICES_MIN = listOf(1, 2, 5, 15, 30)

    /** Milliseconds to wait before parking a page that just left the screen. */
    fun parkDelay(now: Long, readyUntil: Long, graceMs: Long = GRACE_MS): Long = maxOf(graceMs, readyUntil - now)

    /** Milliseconds to wait before the process leaves, measured from the same moment. */
    fun exitDelay(now: Long, readyUntil: Long, graceMs: Long = GRACE_MS): Long =
        parkDelay(now, readyUntil, graceMs) + EXIT_AFTER_PARK_MS

    /** The next grace after this one, round the list. */
    fun nextGrace(graceMs: Long): Long {
        val minutes = (graceMs / 60_000L).toInt()
        val i = GRACE_CHOICES_MIN.indexOf(minutes)
        return GRACE_CHOICES_MIN[(i + 1) % GRACE_CHOICES_MIN.size] * 60_000L
    }

    fun graceLabel(graceMs: Long): String {
        val m = (graceMs / 60_000L).toInt()
        return if (m == 1) "1 minute" else "$m minutes"
    }
}
