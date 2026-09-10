package com.gios.webtools.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gios.webtools.data.Tool
import com.gios.webtools.hw.WheelScroll
import com.gios.webtools.ui.theme.Dim
import com.gios.webtools.web.SearchEngine

/**
 * The whole app when nothing is open: one field, then the shelf. The field takes an address or
 * words to look up; the engine's name sits at its right end and a tap cycles it. Tap a row to
 * open, hold a row for its page.
 */
@Composable
fun ListScreen(
    tools: List<Tool>,
    listState: LazyListState,
    engine: SearchEngine,
    onSearch: (String) -> Unit,
    onCycleEngine: () -> Unit,
    onOpen: (Tool) -> Unit,
    onInfo: (Tool) -> Unit,
    onAdd: () -> Unit,
    onHelp: () -> Unit,
) {
    WheelScroll(listState)
    var typed by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = typed,
                onValueChange = { typed = it.replace("\n", "") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (typed.isNotBlank()) { onSearch(typed); typed = "" } }),
                decorationBox = { inner ->
                    Box {
                        if (typed.isEmpty()) {
                            Text("Search, or an address", style = MaterialTheme.typography.bodyLarge, color = Dim)
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            // The engine's name is the switch. No menu, no gear: tap it and it is the next one.
            Text(
                engine.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Dim,
                modifier = Modifier.clickable(onClick = onCycleEngine).padding(horizontal = 8.dp, vertical = 10.dp),
            )
        }
        Rule()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tools.isEmpty()) {
                EmptyState("Nothing on the shelf yet.\n\nADD keeps a page here. The field above looks things up.")
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
        ActionBar(listOf(BarAction("ADD", onAdd), BarAction("HELP", onHelp)))
    }
}
