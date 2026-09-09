package com.gios.webtools.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gios.webtools.data.Tool
import com.gios.webtools.hw.WheelScroll

/**
 * The whole app when nothing is open: a list of names. Tap opens, hold shows the tool's page.
 */
@Composable
fun ListScreen(
    tools: List<Tool>,
    listState: LazyListState,
    onOpen: (Tool) -> Unit,
    onInfo: (Tool) -> Unit,
    onAdd: () -> Unit,
    onGo: () -> Unit,
    onHelp: () -> Unit,
) {
    WheelScroll(listState)
    Column(Modifier.fillMaxSize()) {
        TopBar("Web Tools", right = if (tools.isEmpty()) null else "${tools.size}")
        Rule()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tools.isEmpty()) {
                EmptyState("No tools yet.\n\nADD keeps a page on this list. GO opens one address, once.")
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(tools, key = { it.id }) { tool ->
                        ListRow(
                            title = tool.name,
                            detail = tool.detail(),
                            right = if (tool.hasSnapshot) "saved" else null,
                            onClick = { onOpen(tool) },
                            onLongClick = { onInfo(tool) },
                        )
                    }
                }
            }
        }
        ActionBar(listOf(BarAction("ADD", onAdd), BarAction("GO", onGo), BarAction("HELP", onHelp)))
    }
}
