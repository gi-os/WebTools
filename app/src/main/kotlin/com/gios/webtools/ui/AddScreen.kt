package com.gios.webtools.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.gios.webtools.ui.theme.Faint
import com.gios.webtools.ui.theme.Metrics
import com.gios.webtools.ui.theme.Mono

/**
 * A tool you might want, offered on the ADD page so the first tools cost nothing to type.
 *
 * @property folder where it lands. The three ticket sites arrive filed together, which is the
 *   whole point of a folder and saves the one piece of typing a new phone would otherwise need.
 */
data class Starter(
    val name: String,
    val url: String,
    val origins: List<String>,
    val keep: Boolean,
    val reader: Boolean = false,
    val folder: String = "",
)

val STARTERS = listOf(
    Starter("Subway status", "https://new.mta.info/status", listOf("mta.info"), keep = false),
    Starter("Weather", "https://forecast.weather.gov/", listOf("weather.gov"), keep = true),
    // Ticketmaster's sign-in runs reCAPTCHA Enterprise + FingerprintJS. Firefox's engine is a real
    // browser to it; if it still refuses, the login comes over from a computer by code.
    Starter("Ticketmaster", "https://www.ticketmaster.com/", listOf("ticketmaster.com", "livenation.com"), keep = false, folder = "Tickets"),
    Starter("AXS", "https://www.axs.com/", listOf("axs.com"), keep = false, folder = "Tickets"),
    Starter("DICE", "https://dice.fm/", listOf("dice.fm"), keep = false, folder = "Tickets"),
    Starter("Wikipedia", "https://en.m.wikipedia.org/", listOf("wikipedia.org", "wikimedia.org"), keep = false, reader = true),
)

/**
 * Three ways in, numbered: a code from the companion page, a typed address, or one of the
 * starters. The numerals are the page — there is nothing to read here, only a way to pick.
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
        TopBar("Add a tool", right = "3 ways")
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            Step(1, "Scan a code", "gi-os.github.io/WebTools on a computer", right = "→", onClick = onScan)
            Step(2, "Type an address", null) {
                BasicTextField(
                    value = typed,
                    onValueChange = { typed = it.replace("\n", "") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (typed.isNotBlank()) onAddUrl(typed.trim()) }),
                    decorationBox = { inner ->
                        Box {
                            if (typed.isEmpty()) Text("mta.info", style = MaterialTheme.typography.bodyLarge, color = Faint)
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (typed.isBlank()) "The tool stays inside that site." else "Tap ADD below. The tool stays inside that site.",
                    style = Mono.detail,
                    color = Dim,
                )
            }
            Step(3, "Take a starter", null) {
                STARTERS.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().height(Metrics.fact).clickable { onAddStarter(s) },
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(s.name, style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.weight(1f))
                        if (s.folder.isNotEmpty()) Text(s.folder.uppercase(), style = Mono.label, color = Faint)
                    }
                }
            }
            if (message != null) {
                Spacer(Modifier.height(16.dp))
                Text(message, style = MaterialTheme.typography.bodySmall, color = Dim, modifier = Modifier.padding(horizontal = Metrics.pad))
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

/** One numbered way in: the numeral in the left column, the step beside it, its own stuff under. */
@Composable
private fun Step(
    n: Int,
    title: String,
    detail: String?,
    right: String? = null,
    onClick: (() -> Unit)? = null,
    body: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Metrics.pad, vertical = 16.dp),
    ) {
        Text(
            n.toString().padStart(2, '0'),
            style = Mono.numeral,
            color = Faint,
            modifier = Modifier.width(Metrics.index).padding(top = 4.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White, modifier = Modifier.weight(1f))
                if (right != null) Text(right, style = Mono.label, color = Faint)
            }
            if (detail != null) {
                Spacer(Modifier.height(4.dp))
                Text(detail, style = Mono.detail, color = Dim)
            }
            if (body != null) {
                Spacer(Modifier.height(10.dp))
                body()
            }
        }
    }
    Rule()
}
