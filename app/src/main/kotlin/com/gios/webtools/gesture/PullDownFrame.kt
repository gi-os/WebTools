package com.gios.webtools.gesture

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout

/**
 * The root view. Watches every stroke; when the content underneath is at its top and the thumb
 * pulls down far enough, the lift leaves the app.
 *
 * Interception is what makes this work over a WebView and a Compose list alike: once the stroke
 * is claimed the child gets a CANCEL and the frame owns the rest. Strokes that head up, or
 * sideways, or start with content scrolled down, are never touched.
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // A quarter of the screen: far enough that a scroll never gets there by accident.
        gesture = PullDown(triggerPx = h * 0.26f, slopPx = slop)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val g = gesture ?: return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                claimed = false
                eligible = atTop()
                if (eligible) g.down(ev.x, ev.y) else g.reset()
                report(g)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!eligible || claimed) return claimed
                val wants = g.move(ev.x, ev.y)
                report(g)
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
