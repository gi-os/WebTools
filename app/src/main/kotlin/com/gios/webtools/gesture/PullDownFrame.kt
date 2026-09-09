package com.gios.webtools.gesture

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout

/**
 * The root view. Watches every stroke; when the content underneath is at its top and the thumb
 * pulls down, the pulley menu draws out and the lift picks an item.
 *
 * Interception is what makes this work over a page and a Compose list alike: once the stroke is
 * claimed the child gets a CANCEL and the frame owns the rest. Strokes that head up, or sideways,
 * or start with content scrolled down, are never touched.
 *
 * A web engine asks its parent not to intercept as soon as it starts handling a drag
 * (`requestDisallowInterceptTouchEvent`), which is why the first build only worked from the very
 * top edge. The frame ignores that request while a pull is still possible and honors it once the
 * pull is off (sideways, or upward). A stroke that starts in the top band of the screen may pull
 * regardless of scroll position.
 */
class PullDownFrame(context: Context) : FrameLayout(context) {

    /** Whether the content is at its top right now. Set by whichever screen is showing. */
    var atTop: () -> Boolean = { true }

    /** How many items the menu has right now; 0 disables the pull. */
    var itemCount: () -> Int = { 1 }

    /** Called on every change: travel in px and the picked item (-1 for none). */
    var onProgress: (travelPx: Float, picked: Int) -> Unit = { _, _ -> }

    /** The lift committed on an item. */
    var onSelect: (index: Int) -> Unit = {}

    private val density = context.resources.displayMetrics.density
    /** One menu row, and the empty band before the first row becomes live. */
    val pitchPx: Float = 64f * density
    val deadzonePx: Float = 36f * density

    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val gesture = PullDown(slopPx = slop)
    private var claimed = false
    private var eligible = false
    private var lastPicked = -1

    /** Fraction of the height inside which a stroke may pull even when the content is scrolled. */
    private val topBand = 0.14f

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // The child does not get to veto a pull that is still possible.
        if (disallowIntercept && eligible && !claimed) return
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                claimed = false
                lastPicked = -1
                eligible = itemCount() > 0 && (ev.y < height * topBand || atTop())
                if (eligible) gesture.down(ev.x, ev.y) else gesture.reset()
                report()
                return false
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
                report()
                if (picked >= 0) onSelect(picked)
            }
            MotionEvent.ACTION_CANCEL -> {
                gesture.reset()
                claimed = false
                report()
            }
        }
        return true
    }

    private fun report() {
        val picked = if (gesture.stage == PullDown.Stage.TRACKING) Pulley.select(gesture.travel, itemCount(), pitchPx, deadzonePx) else -1
        if (picked != lastPicked) {
            lastPicked = picked
            if (picked >= 0) performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        }
        onProgress(gesture.travel, picked)
    }
}
