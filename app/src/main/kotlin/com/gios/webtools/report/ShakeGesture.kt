package com.gios.webtools.report

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Shake detection by counting reversals of (|a| - 1g), the shape the rest of the family settled
 * on: three reversals past 0.38g inside a short window is a flick of the wrist, not a walk (which
 * peaks around 0.3g) and not a drop (one jolt, no reversal). No Android types.
 */
class ShakeGesture(
    private val thresholdG: Float = 0.38f,
    private val reversals: Int = 3,
    private val windowMs: Long = 900L,
    private val cooldownMs: Long = 2500L,
) {
    private var lastSign = 0
    private var count = 0
    private var firstAt = 0L
    private var firedAt = -1L

    /** Feed raw accelerometer m/s^2; true exactly when a shake completes. */
    fun sample(x: Float, y: Float, z: Float, nowMs: Long): Boolean {
        val g = sqrt(x * x + y * y + z * z) / 9.81f - 1f
        if (firedAt >= 0 && nowMs - firedAt < cooldownMs) return false
        if (abs(g) < thresholdG) return false
        val sign = if (g > 0) 1 else -1
        if (sign == lastSign) return false
        if (count == 0 || nowMs - firstAt > windowMs) {
            count = 0
            firstAt = nowMs
        }
        lastSign = sign
        count++
        if (count >= reversals) {
            count = 0
            lastSign = 0
            firedAt = nowMs
            return true
        }
        return false
    }
}
