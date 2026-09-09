package com.gios.webtools.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gios.webtools.hw.WheelScroll
import com.gios.webtools.ui.theme.Dim

/** A tool you might want, offered on the ADD page so the first tools cost nothing to type. */
data class Starter(val name: String, val url: String, val origins: List<String>, val keep: Boolean, val browser: Boolean = false)

val STARTERS = listOf(
    Starter("Subway status", "https://new.mta.info/status", listOf("mta.info"), keep = false),
    Starter("Weather", "https://forecast.weather.gov/", listOf("weather.gov"), keep = true),
    // Ticketmaster's sign-in runs reCAPTCHA Enterprise + FingerprintJS and refuses this WebView.
    // The site itself works; the login comes over from a computer by code (companion page).
    Starter("Ticketmaster", "https://www.ticketmaster.com/", listOf("ticketmaster.com", "livenation.com"), keep = false),
    Starter("Wikipedia", "https://en.m.wikipedia.org/", listOf("wikipedia.org", "wikimedia.org"), keep = false),
)

/**
 * Three ways in: a code from the companion page, a typed address, or one of the starters.
 */
@Composable
fun AddScreen(
    message: String?,
    scroll: ScrollState,
    onScan: () -> Unit,
    onAddUrl: (String) -> Unit,
    onAddStarter: (Starter) -> Unit,
    onBack: () -> Unit,
) {
    WheelScroll(scroll)
    var typed by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        TopBar("Add a tool")
        Rule()
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            ListRow(
                title = "Scan a code",
                detail = "Make one at gi-os.github.io/WebTools on a computer",
                onClick = onScan,
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text("OR TYPE AN ADDRESS", style = MaterialTheme.typography.labelSmall, color = Dim)
                Spacer(Modifier.height(10.dp))
                BasicTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (typed.isNotBlank()) onAddUrl(typed.trim()) }),
                    decorationBox = { inner ->
                        Column {
                            if (typed.isEmpty()) Text("mta.info", style = MaterialTheme.typography.bodyLarge, color = Dim)
                            inner()
                            Spacer(Modifier.height(6.dp))
                            Rule()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (typed.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Tap ADD below. The tool stays inside that site.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Dim,
                    )
                }
            }
            Rule()
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text("STARTERS", style = MaterialTheme.typography.labelSmall, color = Dim)
            }
            Spacer(Modifier.height(4.dp))
            Rule()
            STARTERS.forEach { s ->
                ListRow(title = s.name, detail = s.origins.first(), onClick = { onAddStarter(s) })
            }
            if (message != null) {
                Spacer(Modifier.height(16.dp))
                Text(message, style = MaterialTheme.typography.bodySmall, color = Dim, modifier = Modifier.padding(horizontal = 20.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
        ActionBar(
            if (typed.isNotBlank()) {
                listOf(BarAction("BACK", onBack), BarAction("ADD") { onAddUrl(typed.trim()) })
            } else {
                listOf(BarAction("BACK", onBack), BarAction("SCAN", onScan))
            },
        )
    }
}
