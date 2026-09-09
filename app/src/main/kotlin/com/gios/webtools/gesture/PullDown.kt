package com.gios.webtools.gesture

import kotlin.math.abs

/**
 * The exit gesture: pull down from the top of whatever is on screen and let go.
 *
 * Crossing the trigger arms, the lift commits (BrightControl's rule). Firing at the threshold
 * would make the gesture unabortable; arming lets the thumb come back. A stroke that drifts
 * further sideways than down is cancelled for the rest of the stroke, so a scrub across a page
 * never leaves the app.
 *
 * Pure: pixels in, a stage out. The view layer decides when a stroke may even start (content at
 * its top) and draws the indicator from [progress].
 */
class PullDown(private val triggerPx: Float, private val slopPx: Float) {

    enum class Stage { IDLE, TRACKING, ARMED, CANCELLED }

    var stage: Stage = Stage.IDLE
        private set

    private var startX = 0f
    private var startY = 0f
    private var travel = 0f

    /** 0..1 of the way to the trigger; clamps at 1 once armed. */
    val progress: Float get() = if (stage == Stage.IDLE || stage == Stage.CANCELLED) 0f else (travel / triggerPx).coerceIn(0f, 1f)

    fun down(x: Float, y: Float) {
        startX = x; startY = y; travel = 0f
        stage = Stage.TRACKING
    }

    /** True while the gesture wants the stroke for itself. */
    fun move(x: Float, y: Float): Boolean {
        if (stage == Stage.IDLE || stage == Stage.CANCELLED) return false
        val dx = x - startX
        val dy = y - startY
        // Sideways wins: a long flick that ends with a drift is the stroke this rule exists for.
        if (abs(dx) > slopPx && abs(dx) > dy) {
            stage = Stage.CANCELLED
            return false
        }
        travel = dy
        stage = if (dy >= triggerPx) Stage.ARMED else Stage.TRACKING
        return dy > slopPx
    }

    /** True when the lift should leave the app. Always resets. */
    fun up(): Boolean {
        val commit = stage == Stage.ARMED
        reset()
        return commit
    }

    fun reset() {
        stage = Stage.IDLE
        travel = 0f
    }
}
