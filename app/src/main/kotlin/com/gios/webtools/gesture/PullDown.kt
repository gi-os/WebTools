package com.gios.webtools.gesture

import kotlin.math.abs

/**
 * The pull. From the top of whatever is on screen, the thumb draws down a menu; how far it has
 * come picks an item; the lift commits (Sailfish's pulley menu, BrightControl's arm-on-cross,
 * commit-on-lift rule). Coming back up un-picks. A stroke that is mostly sideways before it has
 * pulled is cancelled, so a scrub across a page never opens anything; once the pull has started,
 * the thumb's sideways arc is ignored, because dropping the menu halfway through is worse than
 * letting a diagonal pull through.
 *
 * Pure: pixels in, travel out. [Pulley] turns travel into an item; the view layer decides when a
 * stroke may even start (content at its top) and draws the menu from [travel].
 */
class PullDown(private val slopPx: Float) {

    enum class Stage { IDLE, TRACKING, CANCELLED }

    var stage: Stage = Stage.IDLE
        private set

    private var startX = 0f
    var startY = 0f
        private set

    /** How far down the thumb has come from where it landed, never negative. */
    var travel = 0f
        private set

    /** Whether this stroke has already crossed the slop and become a pull. */
    private var started = false

    fun down(x: Float, y: Float) {
        startX = x; startY = y; travel = 0f; started = false
        stage = Stage.TRACKING
    }

    /** True while the gesture wants the stroke for itself. */
    fun move(x: Float, y: Float): Boolean {
        if (stage != Stage.TRACKING) return false
        val dx = x - startX
        val down = (y - startY).coerceAtLeast(0f)
        if (!started) {
            // A flick that is still mostly across the page is a scrub, not a pull. Give a
            // diagonal thumb the benefit of the doubt when it is at least half downward.
            if (abs(dx) > slopPx && abs(dx) > down * 2f) {
                stage = Stage.CANCELLED
                travel = 0f
                return false
            }
            travel = down
            if (down > slopPx) started = true
            return started
        }
        // Once it is a pull, sideways drift is just the thumb's arc. Keep the travel.
        travel = down
        return true
    }

    /** The travel at the lift, or 0 when the stroke was cancelled. Always resets. */
    fun up(): Float {
        val t = if (stage == Stage.TRACKING) travel else 0f
        reset()
        return t
    }

    fun reset() {
        stage = Stage.IDLE
        travel = 0f
        started = false
    }
}

/**
 * Travel to item. The menu unrolls down from the top edge, so the first row is reached first and
 * the last row with the longest pull; past the last row the pick stays on it, so a long flick
 * always lands on the row that matters most. Below [deadzonePx] nothing is picked and a lift does
 * nothing.
 */
object Pulley {
    /**
     * Index into a list of [count] items ordered top-to-bottom; -1 for none. A row is picked once
     * the thumb has drawn out half of it, so a row just peeking in is not yet live.
     */
    fun select(travelPx: Float, count: Int, pitchPx: Float, deadzonePx: Float): Int {
        if (count <= 0 || pitchPx <= 0f) return -1
        val units = (travelPx - deadzonePx) / pitchPx - 0.5f
        if (units < 0f) return -1
        return units.toInt().coerceAtMost(count - 1)
    }

    /**
     * The row height for [count] rows on a screen [screenPx] tall: 64dp when they fit, shrunk
     * so the last row (TOOLS, the exit) is always within a thumb's reach, never under 40dp.
     * The deadzone and a hand's width at the bottom are kept out of the arithmetic.
     */
    fun pitchFor(count: Int, screenPx: Float, deadzonePx: Float, density: Float): Float {
        val full = 44f * density
        if (count <= 0) return full
        val room = screenPx - deadzonePx - 72f * density
        return (room / count).coerceIn(34f * density, full)
    }

    /** How tall the drawn menu is for this travel: never past the items, never negative. */
    fun height(travelPx: Float, count: Int, pitchPx: Float, deadzonePx: Float): Float =
        travelPx.coerceIn(0f, deadzonePx + count * pitchPx)
}
