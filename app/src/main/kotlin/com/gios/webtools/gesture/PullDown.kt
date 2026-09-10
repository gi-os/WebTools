package com.gios.webtools.gesture

import kotlin.math.abs

/**
 * The pull. From the top of whatever is on screen, the thumb draws down a menu; how far it has
 * come picks an item; the lift commits (Sailfish's pulley menu, BrightControl's arm-on-cross,
 * commit-on-lift rule). Coming back up un-picks. A stroke that drifts further sideways than
 * down is cancelled for the rest of the stroke, so a scrub across a page never opens anything.
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

    fun down(x: Float, y: Float) {
        startX = x; startY = y; travel = 0f
        stage = Stage.TRACKING
    }

    /** True while the gesture wants the stroke for itself. */
    fun move(x: Float, y: Float): Boolean {
        if (stage != Stage.TRACKING) return false
        val dx = x - startX
        val dy = y - startY
        // Sideways wins: a long flick that ends with a drift is the stroke this rule exists for.
        if (abs(dx) > slopPx && abs(dx) > dy) {
            stage = Stage.CANCELLED
            travel = 0f
            return false
        }
        travel = if (dy > 0f) dy else 0f
        return dy > slopPx
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
