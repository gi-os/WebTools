package com.gios.webtools.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
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
import androidx.compose.ui.unit.dp
import com.gios.webtools.data.Engine
import com.gios.webtools.data.Tool
import com.gios.webtools.data.ToolKind
import com.gios.webtools.hw.WheelScroll
import com.gios.webtools.ui.theme.Dim
import java.text.DateFormat
import java.util.Date

/**
 * One tool's page: what it is, where it may go, and the three things you can do to it.
 * Remove asks once, on the same bar, so a slip does not cost a tool.
 */
@Composable
fun InfoScreen(
    tool: Tool,
    scroll: ScrollState,
    online: Boolean,
    onOpen: () -> Unit,
    onOpenSaved: () -> Unit,
    onToggleKeep: () -> Unit,
    onToggleEngine: () -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit,
) {
    WheelScroll(scroll)
    var confirmRemove by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar(tool.name, right = when {
            tool.kind == ToolKind.BUNDLE -> "on the phone"
            tool.engine == Engine.BROWSER -> "in Chromium"
            else -> "site"
        })
        Rule()
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            Spacer(Modifier.height(8.dp))
            Field("Address", tool.url)
            if (tool.kind == ToolKind.SITE && tool.engine == Engine.BUILTIN) {
                Field("Stays inside", tool.origins.joinToString("\n"))
                Field(
                    "Saved copy",
                    if (tool.hasSnapshot) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(tool.snapshotAt))
                    else "none yet",
                )
            }
            if (tool.lastUsed > 0L) {
                Field("Last opened", DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(tool.lastUsed)))
            }
            Spacer(Modifier.height(8.dp))
            Rule()
            ListRow(title = if (online) "Open" else "Open (offline)", detail = null, onClick = onOpen)
            if (tool.kind == ToolKind.SITE) {
                ListRow(
                    title = if (tool.engine == Engine.BROWSER) "Opens in: Chromium" else "Opens in: built-in view",
                    detail = if (tool.engine == Engine.BROWSER) {
                        "The phone's browser, its cookies, its fingerprint. No allowlist, no saved copy."
                    } else {
                        "Switch to Chromium when a sign-in page says your browsing was paused."
                    },
                    onClick = onToggleEngine,
                )
                if (tool.engine == Engine.BUILTIN) {
                    if (tool.hasSnapshot) {
                        ListRow(title = "Open the saved copy", detail = "Works with no signal", onClick = onOpenSaved)
                    }
                    ListRow(
                        title = if (tool.keep) "Keeping a copy: on" else "Keeping a copy: off",
                        detail = "Saves the page after each visit, so it opens with no signal",
                        onClick = onToggleKeep,
                    )
                }
            }
            ListRow(
                title = if (confirmRemove) "Tap again to remove" else "Remove",
                detail = if (confirmRemove) "This also deletes its saved copy" else null,
                onClick = { if (confirmRemove) onRemove() else confirmRemove = true },
            )
        }
        ActionBar(listOf(BarAction("BACK", onBack), BarAction("OPEN", onOpen)))
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Dim)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}
