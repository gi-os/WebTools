package com.gios.webtools.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.gios.webtools.data.Download
import com.gios.webtools.hw.WheelScroll

/**
 * What the engine saved instead of showing. Tap opens it: the engine shows PDFs, pictures and
 * text itself, anything else goes to whatever on the phone takes it. Hold a row and the bar
 * asks once before removing. The kind sits on the right, monospaced, so the column reads as a
 * list of kinds even before the names are read.
 */
@Composable
fun DownloadsScreen(
    items: List<Download>,
    listState: LazyListState,
    onOpen: (Download) -> Unit,
    onRemove: (Download) -> Unit,
    onBack: () -> Unit,
) {
    WheelScroll(listState)
    var removing by remember { mutableStateOf<Download?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Downloads", right = if (items.isEmpty()) null else "${items.size} files")
        SectionHead("Newest first", if (items.isEmpty()) null else "Hold to remove")
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (items.isEmpty()) {
                EmptyState("Nothing saved yet.\n\nA link to a PDF, a picture, or a file a page will not show lands here.")
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(items, key = { _, d -> d.name }) { i, d ->
                        ListRow(
                            title = d.name,
                            detail = d.detail().substringAfter("· "),
                            right = if (removing == d) "remove?" else d.detail().substringBefore(" ·"),
                            index = i + 1,
                            onClick = { removing = null; onOpen(d) },
                            onLongClick = { removing = d },
                        )
                    }
                }
            }
        }
        val r = removing
        if (r == null) {
            ActionBar(listOf(BarAction("BACK", onBack)))
        } else {
            ActionBar(listOf(BarAction("CANCEL") { removing = null }, BarAction("REMOVE") { removing = null; onRemove(r) }))
        }
    }
}
