package com.gios.webtools.gesture

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout

/**
 * The root view. Watches every stroke; when the content underneath is at its top and the thumb
 * pulls down, the pulley menu draws out and the lift picks an item.
 *
 * Interception is what makes this work over a page and a Compose list alike: once the stroke is
 * claimed the child gets a CANCEL and the frame owns the rest. Strokes that head up, begin as a
 * sideways scrub, or start with content scrolled down, are never touched.
 *
 * A web engine asks its parent not to intercept as soon as it starts handling a drag
 * (`requestDisallowInterceptTouchEvent`), which is why the first build only worked from the very
 * top edge. The frame ignores that request while a pull is still possible and honors it once the
 * stroke is off (upward, or a scrub). A stroke that starts in the pull band just below Android's
 * top strip may pull regardless of scroll position.
 */
class PullDownFrame(context: Context) : FrameLayout(context) {

    companion object {
        /** The pull band: below Android's top strip, with 60dp of thumb room. */
        const val BAND_START_DP = 28f
        const val BAND_END_DP = 88f

        /** The black strip that carries the handle line, and its line's rise above the bottom. */
        const val HANDLE_BAR_DP = 38f
        const val HANDLE_LINE_BOTTOM_DP = 4f
    }

    /** How many items the menu has right now; 0 disables the pull. */
    var itemCount: () -> Int = { 1 }

    /** Called on every change: travel in px and the picked item (-1 for none). */
    var onProgress: (travelPx: Float, picked: Int) -> Unit = { _, _ -> }

    /** The lift committed on an item. */
    var onSelect: (index: Int) -> Unit = {}

    /** A tap on the handle bar, where the system steals drags. */
    var onTopTap: () -> Unit = {}

    private val density = context.resources.displayMetrics.density
    /** The empty band before the first row becomes live. */
    val deadzonePx: Float = 40f * density
    /** One menu row: 44dp, or less when the menu would otherwise run off the screen. */
    val pitchPx: Float get() = Pulley.pitchFor(itemCount(), height.toFloat().takeIf { it > 0f } ?: (472f * density), deadzonePx, density)

    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val gesture = PullDown(slopPx = slop)

    /**
     * The tick per row. `performHapticFeedback` obeys the system's haptic-feedback switch, which
     * LightOS leaves off, so nothing was felt. The vibrator itself does not ask; a 30 ms one-shot
     * is the Light SDK's own tap feel.
     */
    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    private fun tick() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }
    private var claimed = false
    private var eligible = false
    private var tapping = false
    private var tapCancelled = false
    private var lastPicked = -1

    /**
     * Where a pull may begin: a band just below Android's own top strip.
     *
     * The first ~25dp of the screen belongs to the system. A fast stroke there is cancelled before
     * the frame sees a move; a slow one sometimes gets through, which is why the pull felt
     * speed-sensitive. Starting 28dp down keeps thumb room, moved clear of the system.
     *
     * It used to be "the top strip *or* the content is at its top", and the second half was the
     * problem. A page scrolled to the top is the normal state of a page you have just opened, so
     * every downward stroke anywhere on it opened the menu instead of scrolling — the whole
     * screen was a handle. One band, always, is a rule the thumb learns once.
     */
    private val topBandStartPx: Float = BAND_START_DP * density
    private val topBandEndPx: Float = BAND_END_DP * density
    private val handleBarPx: Float = HANDLE_BAR_DP * density

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // The child does not get to veto a pull that is still possible.
        if (disallowIntercept && eligible && !claimed) return
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (tapping) return true
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                claimed = false
                lastPicked = -1
                tapping = itemCount() > 0 && ev.y < handleBarPx
                tapCancelled = false
                eligible = !tapping && itemCount() > 0 && ev.y >= topBandStartPx && ev.y < topBandEndPx
                if (tapping || eligible) gesture.down(ev.x, ev.y) else gesture.reset()
                report()
                return tapping
            }
            MotionEvent.ACTION_MOVE -> {
                if (!eligible || claimed) return claimed
                val wants = gesture.move(ev.x, ev.y)
                report()
                if (gesture.stage == PullDown.Stage.CANCELLED || (ev.y - gesture.startY) < -slop) {
                    // Sideways, or upward: the page owns this stroke after all.
                    eligible = false
                    return false
                }
                if (wants) {
                    claimed = true
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!claimed) {
                    gesture.reset()
                    report()
                }
                return false
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (tapping && !claimed) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val wants = gesture.move(ev.x, ev.y)
                    report()
                    if (gesture.stage == PullDown.Stage.CANCELLED || (ev.y - gesture.startY) < -slop) {
                        tapCancelled = true
                    } else if (wants) {
                        claimed = true
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val tap = !tapCancelled && gesture.travel <= slop
                    tapping = false
                    tapCancelled = false
                    gesture.reset()
                    claimed = false
                    report()
                    if (tap) onTopTap()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    tapping = false
                    tapCancelled = false
                    gesture.reset()
                    claimed = false
                    report()
                    return true
                }
                else -> return true
            }
        }
        if (!claimed) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                gesture.move(ev.x, ev.y)
                report()
            }
            MotionEvent.ACTION_UP -> {
                val travel = gesture.up()
                val picked = Pulley.select(travel, itemCount(), pitchPx, deadzonePx)
                claimed = false
                tapping = false
                tapCancelled = false
                report()
                if (picked >= 0) onSelect(picked)
            }
            MotionEvent.ACTION_CANCEL -> {
                gesture.reset()
                claimed = false
                tapping = false
                tapCancelled = false
                report()
            }
        }
        return true
    }

    private fun report() {
        val picked = if (gesture.stage == PullDown.Stage.TRACKING) Pulley.select(gesture.travel, itemCount(), pitchPx, deadzonePx) else -1
        if (picked != lastPicked) {
            lastPicked = picked
            if (picked >= 0) tick()
        }
        onProgress(gesture.travel, picked)
    }
}
