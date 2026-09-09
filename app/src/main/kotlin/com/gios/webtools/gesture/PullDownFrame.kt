package com.gios.webtools.gesture

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout

/**
 * The root view. Watches every stroke; when the content underneath is at its top and the thumb
 * pulls down far enough, the lift goes back to the list.
 *
 * Interception is what makes this work over a WebView and a Compose list alike: once the stroke
 * is claimed the child gets a CANCEL and the frame owns the rest. Strokes that head up, or
 * sideways, or start with content scrolled down, are never touched.
 *
 * A WebView asks its parent not to intercept as soon as it starts handling a drag
 * (`requestDisallowInterceptTouchEvent`), which is why the first build only worked from the very
 * top edge: everywhere else the page had already claimed the stroke. The frame ignores that
 * request while a pull is still possible and honors it once the pull is off (sideways, or upward).
 * A stroke that starts in the top band of the screen may pull regardless of scroll position.
 */
class PullDownFrame(context: Context) : FrameLayout(context) {

    /** Whether the content is at its top right now. Set by whichever screen is showing. */
    var atTop: () -> Boolean = { true }

    /** Called on every change so an indicator can be drawn. */
    var onProgress: (progress: Float, armed: Boolean) -> Unit = { _, _ -> }

    /** The lift committed. */
    var onExit: () -> Unit = {}

    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var gesture: PullDown? = null
    private var claimed = false
    private var eligible = false

    /** Fraction of the height inside which a stroke may pull even when the content is scrolled. */
    private val topBand = 0.14f

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // The child does not get to veto a pull that is still possible.
        if (disallowIntercept && eligible && !claimed) return
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // A quarter of the screen: far enough that a scroll never gets there by accident.
        gesture = PullDown(triggerPx = h * 0.22f, slopPx = slop)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val g = gesture ?: return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                claimed = false
                eligible = ev.y < height * topBand || atTop()
                if (eligible) g.down(ev.x, ev.y) else g.reset()
                report(g)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!eligible || claimed) return claimed
                val wants = g.move(ev.x, ev.y)
                report(g)
                if (g.stage == PullDown.Stage.CANCELLED || (ev.y - g.startY) < -slop) {
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
                    g.reset()
                    report(g)
                }
                return false
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val g = gesture ?: return false
        if (!claimed) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                g.move(ev.x, ev.y)
                report(g)
            }
            MotionEvent.ACTION_UP -> {
                val commit = g.up()
                claimed = false
                report(g)
                if (commit) onExit()
            }
            MotionEvent.ACTION_CANCEL -> {
                g.reset()
                claimed = false
                report(g)
            }
        }
        return true
    }

    private fun report(g: PullDown) {
        onProgress(g.progress, g.stage == PullDown.Stage.ARMED)
    }
}
