package com.gios.webtools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gios.webtools.ui.theme.Dim
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * The page and nothing else. One thin line at the bottom appears only when there is something
 * to say: loading, a blocked link, or that this is the saved copy.
 */
@Composable
fun ToolScreen(session: GeckoSession, status: String?) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    GeckoView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
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
        }
        if (status != null) {
            Rule()
            Box(
                Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(status.uppercase(), style = MaterialTheme.typography.labelSmall, color = Dim, maxLines = 1)
            }
        }
    }
}
