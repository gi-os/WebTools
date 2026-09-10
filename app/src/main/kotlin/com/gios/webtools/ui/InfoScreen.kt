package com.gios.webtools.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.gios.webtools.data.Tool
import com.gios.webtools.data.ToolKind
import com.gios.webtools.hw.WheelScroll
import java.text.DateFormat
import java.util.Date

/**
 * One tool's page: what it is on top as a grid of label and answer, then the things you can do
 * to it as one-line facts with their state on the right. Remove asks once, on the row itself, so
 * a slip does not cost a tool.
 */
@Composable
fun InfoScreen(
    tool: Tool,
    scroll: ScrollState,
    online: Boolean,
    index: Int?,
    onOpen: () -> Unit,
    onOpenSaved: () -> Unit,
    blockedHost: String?,
    onAllowBlocked: () -> Unit,
    onToggleKeep: () -> Unit,
    onToggleReader: () -> Unit,
    onToggleBlocking: () -> Unit,
    onFolder: () -> Unit,
    onForgetLogin: () -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit,
) {
    WheelScroll(scroll)
    var confirmRemove by remember { mutableStateOf(false) }
    val stamp = { at: Long -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(at)) }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            tool.name,
            right = (if (tool.kind == ToolKind.BUNDLE) "on the phone" else "site") +
                (index?.let { " · " + it.toString().padStart(2, '0') } ?: ""),
        )
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            FactGrid(
                buildList {
                    add("Address" to tool.url)
                    if (tool.kind == ToolKind.SITE) {
                        add("Stays inside" to tool.origins.joinToString("\n").ifEmpty { "anywhere" })
                        add("Saved copy" to if (tool.hasSnapshot) stamp(tool.snapshotAt) else "none yet")
                    }
                    add("Folder" to tool.folder.ifEmpty { "the shelf itself" })
                    if (tool.lastUsed > 0L) add("Last opened" to stamp(tool.lastUsed))
                },
            )
            FactRow(title = if (online) "Open" else "Open (offline)", state = null, onClick = onOpen)
            if (tool.kind == ToolKind.SITE) {
                if (blockedHost != null && !tool.origins.contains(blockedHost)) {
                    FactRow(title = "Allow $blockedHost", state = "blocked once", onClick = onAllowBlocked)
                }
                if (tool.hasSnapshot) {
                    FactRow(title = "Open the saved copy", state = "no signal ok", onClick = onOpenSaved)
                }
                FactRow(
                    title = "Keeping a copy",
                    state = if (tool.keep) "on" else "off",
                    lit = tool.keep,
                    onClick = onToggleKeep,
                )
                FactRow(
                    title = "Block ads and trackers",
                    state = if (tool.blocking) "on" else "off",
                    lit = tool.blocking,
                    onClick = onToggleBlocking,
                )
                FactRow(
                    title = "Reader view",
                    state = if (tool.reader) "on" else "off",
                    lit = tool.reader,
                    onClick = onToggleReader,
                )
            }
            FactRow(
                title = "Put in a folder",
                state = tool.folder.ifEmpty { "none" },
                lit = tool.folder.isNotEmpty(),
                onClick = onFolder,
            )
            if (tool.kind == ToolKind.SITE) {
                FactRow(title = "Sign out of this site", state = "clears cookies", onClick = onForgetLogin)
            }
            FactRow(
                title = if (confirmRemove) "Tap again to remove" else "Remove",
                state = if (confirmRemove) "and its copy" else null,
                onClick = { if (confirmRemove) onRemove() else confirmRemove = true },
            )
        }
        ActionBar(listOf(BarAction("BACK", onBack), BarAction("OPEN", onOpen)))
    }
}
