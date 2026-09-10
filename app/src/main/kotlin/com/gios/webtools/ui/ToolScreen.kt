package com.gios.webtools.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.ui.viewinterop.AndroidView
import com.gios.webtools.ui.theme.Dim
import com.gios.webtools.ui.theme.Metrics
import com.gios.webtools.ui.theme.Mono
import com.gios.webtools.ui.theme.RuleGrey
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * The page and nothing else.
 *
 * **Loading is a dimmed page and a line along the top edge, and the page does not move.** A
 * spinner over a blank screen says only that something is happening; the page you were reading,
 * dimmed, with a line filling across the top, says how far and leaves the words where they were,
 * so the moment it clears your eye is already in the right place. The line is two pixels and
 * black-and-white, which is all this panel can draw well anyway.
 *
 * One thin line at the bottom appears only when there is something to say: a blocked link, the
 * saved copy, a report sent.
 */
@Composable
fun ToolScreen(
    session: GeckoSession,
    status: String?,
    loading: Boolean,
    progress: Float,
    onView: (GeckoView) -> Unit = {},
) {
    val shown by animateFloatAsState(if (loading) progress.coerceIn(0.02f, 1f) else 1f, label = "progress")
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    GeckoView(ctx).apply {
                        // A TextureView, not a SurfaceView: a SurfaceView is its own layer under
                        // the window, and PixelCopy of the window (the report chip's screenshot,
                        // MAKE A TICKET's picture) would come back black where the page is.
                        setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW)
                        setBackgroundColor(android.graphics.Color.BLACK)
                        onView(this)
                        coverUntilFirstPaint(android.graphics.Color.BLACK)
                    }
                },
                update = { view ->
                    if (view.session !== session) {
                        if (view.session != null) view.releaseSession()
                        view.setSession(session)
                    }
                },
                onRelease = { view -> runCatching { view.releaseSession() } },
                modifier = Modifier.fillMaxSize(),
            )
            if (loading) {
                // The page stays put underneath, dimmed rather than replaced.
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                Box(Modifier.fillMaxWidth().height(2.dp).background(RuleGrey).align(Alignment.TopStart))
                Box(
                    Modifier
                        .fillMaxWidth(shown)
                        .height(2.dp)
                        .background(Color.White)
                        .align(Alignment.TopStart),
                )
            }
        }
        if (status != null) {
            Rule()
            Box(
                Modifier.fillMaxWidth().height(30.dp).padding(horizontal = Metrics.pad),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(status.uppercase(), style = Mono.label, color = Dim, maxLines = 1)
            }
        }
    }
}
