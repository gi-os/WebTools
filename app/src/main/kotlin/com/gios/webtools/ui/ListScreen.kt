package com.gios.webtools.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.gios.webtools.data.Shelf
import com.gios.webtools.data.Tool
import com.gios.webtools.hw.WheelScroll
import com.gios.webtools.ui.theme.Faint
import com.gios.webtools.ui.theme.Metrics
import com.gios.webtools.ui.theme.Mono

/**
 * The whole app when nothing is open: one field, then the shelf. The field takes an address or
 * words to look up (the engine is chosen in Settings). Tap a row to open, hold a row for its
 * page. Tools filed together show as one folder row, which opens to its own list.
 */
@Composable
fun ListScreen(
    tools: List<Tool>,
    listState: LazyListState,
    onSearch: (String) -> Unit,
    onOpen: (Tool) -> Unit,
    onInfo: (Tool) -> Unit,
    onFolder: (String) -> Unit,
    onAdd: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
) {
    WheelScroll(listState)
    var typed by remember { mutableStateOf("") }
    val entries = Shelf.entries(tools)
    Column(Modifier.fillMaxSize()) {
        SearchField(typed, { typed = it }) { if (typed.isNotBlank()) { onSearch(typed); typed = "" } }
        Rule()
        SectionHead("Shelf · ${tools.size}", if (tools.isEmpty()) null else "Hold for details")
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (entries.isEmpty()) {
                EmptyState("Nothing on the shelf yet.\n\nADD keeps a page here. The field above looks things up.")
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(entries, key = { _, e -> if (e is Shelf.Entry.Single) e.tool.id else "folder:" + e.name }) { i, entry ->
                        when (entry) {
                            is Shelf.Entry.Folder -> ListRow(
                                title = entry.name,
                                detail = entry.detail(),
                                right = "${entry.count} tools",
                                index = i + 1,
                                onClick = { onFolder(entry.name) },
                            )
                            is Shelf.Entry.Single -> ListRow(
                                title = entry.tool.name,
                                detail = entry.tool.detail(),
                                right = if (entry.tool.hasSnapshot) "saved" else null,
                                index = i + 1,
                                onClick = { onOpen(entry.tool) },
                                onLongClick = { onInfo(entry.tool) },
                            )
                        }
                    }
                }
            }
        }
        ActionBar(listOf(BarAction("ADD", onAdd), BarAction("DOWNLOADS", onDownloads), BarAction("SETTINGS", onSettings)))
    }
}

/** One folder, opened: the same rows, without the field. */
@Composable
fun FolderScreen(
    folder: String,
    tools: List<Tool>,
    listState: LazyListState,
    onOpen: (Tool) -> Unit,
    onInfo: (Tool) -> Unit,
    onBack: () -> Unit,
) {
    WheelScroll(listState)
    Column(Modifier.fillMaxSize()) {
        TopBar(folder, right = "folder · ${tools.size}")
        SectionHead("Inside", "Hold for details")
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tools.isEmpty()) {
                EmptyState("This folder is empty.\n\nA folder is only a name on its tools, so it goes when the last one leaves.")
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(tools, key = { _, t -> t.id }) { i, tool ->
                        ListRow(
                            title = tool.name,
                            detail = tool.detail(),
                            right = if (tool.hasSnapshot) "saved" else null,
                            index = i + 1,
                            onClick = { onOpen(tool) },
                            onLongClick = { onInfo(tool) },
                        )
                    }
                }
            }
        }
        ActionBar(listOf(BarAction("BACK", onBack)))
    }
}

/** Which folder a tool sits in: the ones that exist, the shelf itself, or a name you type. */
@Composable
fun FolderPickScreen(
    tool: Tool,
    folders: List<String>,
    listState: LazyListState,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
) {
    WheelScroll(listState)
    var typed by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        TopBar(tool.name, right = "put in a folder")
        SectionHead("New folder")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Metrics.pad, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = typed,
                onValueChange = { typed = it.replace("\n", "") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (typed.isNotBlank()) onPick(Tool.folderName(typed)) }),
                decorationBox = { inner ->
                    Box {
                        if (typed.isEmpty()) Text("Tickets", style = MaterialTheme.typography.titleMedium, color = Faint)
                        inner()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text("DONE ↵", style = Mono.label, color = Faint)
        }
        Rule()
        SectionHead(if (folders.isEmpty()) "Nowhere else yet" else "Or one that exists")
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item {
                    FactRow(
                        title = "The shelf itself",
                        state = if (tool.folder.isEmpty()) "here" else null,
                        lit = tool.folder.isEmpty(),
                        onClick = { onPick("") },
                    )
                }
                itemsIndexed(folders, key = { _, f -> f }) { _, f ->
                    FactRow(
                        title = f,
                        state = if (tool.folder == f) "here" else null,
                        lit = tool.folder == f,
                        onClick = { onPick(f) },
                    )
                }
            }
        }
        ActionBar(listOf(BarAction("BACK", onBack)))
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onGo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Metrics.pad, end = Metrics.pad, top = 22.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.replace("\n", "")) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = Color.White),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text("Search, or an address", style = MaterialTheme.typography.titleMedium, color = Faint)
                    }
                    inner()
                }
            },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text("GO ↵", style = Mono.label, color = Faint)
    }
}
