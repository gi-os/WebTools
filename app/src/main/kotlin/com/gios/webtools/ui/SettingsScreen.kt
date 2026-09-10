package com.gios.webtools.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gios.light.common.report.Feedback
import com.gios.light.common.report.ShakeMonitor
import com.gios.webtools.hw.WheelScroll
import com.gios.webtools.ui.theme.Dim
import com.gios.webtools.web.SearchEngine
import com.gios.webtools.web.Warmth

/**
 * Settings: a list of rows, each a fact with the tap that changes it. No switches, no sliders,
 * no sub-pages. A row that cycles says what it is now; the next tap makes it the next thing.
 */
@Composable
fun SettingsScreen(
    scroll: ScrollState,
    engine: SearchEngine,
    onCycleEngine: () -> Unit,
    graceMs: Long,
    onCycleGrace: () -> Unit,
    isBrowser: Boolean,
    onAskBrowser: () -> Unit,
    onWifiSignIn: () -> Unit,
    about: String,
    onTutorial: () -> Unit,
    onBack: () -> Unit,
) {
    WheelScroll(scroll)
    // The gesture readout answers "I shook it and nothing happened" with a number.
    val shake by ShakeMonitor.reading.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        ShakeMonitor.watch()
        onDispose { ShakeMonitor.unwatch() }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Settings")
        Rule()
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            ListRow(
                title = "Search engine",
                detail = engine.label + " · tap for the next",
                onClick = onCycleEngine,
            )
            ListRow(
                title = "Keep a page warm",
                detail = Warmth.graceLabel(graceMs) + " after leaving · a ticket page, an hour",
                onClick = onCycleGrace,
            )
            ListRow(
                title = "The phone's browser",
                detail = if (isBrowser) "Web Tools opens links from other apps" else "Not yet · tap to ask the system",
                onClick = onAskBrowser,
            )
            ListRow(
                title = "Wi-Fi sign-in",
                detail = "Hotel or café Wi-Fi that wants a page filled in before it works",
                onClick = onWifiSignIn,
            )
            ListRow(
                title = "Send feedback",
                detail = "A bug or an idea. Raises the chip in the corner; a shake does the same.",
                onClick = { Feedback.ask() },
            )
            ListRow(
                title = "Shake",
                detail = "%.2f g now · peak %.2f g · %d of %d turns".format(shake.magnitudeG, shake.peakG, shake.turns, shake.turnsNeeded),
                onClick = {},
            )
            ListRow(
                title = "Show the tutorial",
                detail = "Seven short pages",
                onClick = onTutorial,
            )
            Text(
                about,
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
        ActionBar(listOf(BarAction("BACK", onBack)))
    }
}
