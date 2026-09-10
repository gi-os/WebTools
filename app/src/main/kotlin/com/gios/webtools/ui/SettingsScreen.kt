package com.gios.webtools.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gios.light.common.report.Feedback
import com.gios.webtools.hw.WheelScroll
import com.gios.webtools.ui.theme.Faint
import com.gios.webtools.ui.theme.Metrics
import com.gios.webtools.ui.theme.Mono
import com.gios.webtools.web.SearchEngine
import com.gios.webtools.web.Warmth

/**
 * Settings: every row is a fact with its state on the right, and a tap changes the state. No
 * switches, no sliders, no sub-pages. A row that cycles shows what it is now; the next tap makes
 * it the next thing. Read down the right-hand column and you have read the settings.
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
    passkeys: String,
    onPasskeys: () -> Unit,
    about: String,
    onTutorial: () -> Unit,
    onBack: () -> Unit,
) {
    WheelScroll(scroll)

    Column(Modifier.fillMaxSize()) {
        TopBar("Settings")
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            FactRow("Search engine", engine.label, lit = true, onClick = onCycleEngine)
            FactRow("Keep a page warm", Warmth.graceLabel(graceMs), lit = true, onClick = onCycleGrace)
            FactRow(
                if (isBrowser) "The phone's browser" else "Set as default browser",
                if (isBrowser) "yes" else "not yet",
                lit = isBrowser,
                onClick = onAskBrowser,
            )
            FactRow("Passkeys", passkeys, lit = passkeys == "bitwarden", onClick = onPasskeys)
            FactRow("Send feedback", "or shake", onClick = { Feedback.ask() })
            FactRow("Show the tutorial", "7 pages", onClick = onTutorial)
            androidx.compose.material3.Text(
                about,
                style = Mono.detail,
                color = Faint,
                modifier = Modifier.padding(horizontal = Metrics.pad, vertical = 16.dp),
            )
        }
        ActionBar(listOf(BarAction("BACK", onBack)))
    }
}
