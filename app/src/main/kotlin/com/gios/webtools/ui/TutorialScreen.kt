package com.gios.webtools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gios.webtools.hw.WheelSteps
import com.gios.webtools.ui.theme.Dim
import com.gios.webtools.ui.theme.Metrics
import com.gios.webtools.ui.theme.RuleGrey

private data class Page(val title: String, val body: String)

private val PAGES = listOf(
    Page(
        "The shelf",
        "A short list of pages you need now and then. A ticket. The train. A form you fill twice " +
            "a year.\n\nTap a name to open it. Hold a name to see its page.",
    ),
    Page(
        "The field",
        "Type an address and it opens. Type anything else and it goes to DuckDuckGo, Ecosia or " +
            "Kagi — whichever you picked in Settings.\n\nNothing you type there is kept. To keep a " +
            "page, pull down on it and choose KEEP ON SHELF.",
    ),
    Page(
        "Adding a page",
        "ADD has three ways in: scan a code made at gi-os.github.io/WebTools, type an address, or " +
            "take a starter.\n\nA page on the shelf stays inside its own site. Links anywhere else " +
            "stop, and the bottom line says which one did. Allow it from the tool's page if you " +
            "meant it.",
    ),
    Page(
        "Folders",
        "Hold a tool, tap PUT IN A FOLDER, name it. Anything with the same name sits in one row.\n\n" +
            "Ticketmaster, AXS and DICE come as one row called Tickets.\n\nA folder is only a name " +
            "on its tools. Take the name off the last one and the folder is gone.",
    ),
    Page(
        "Pull down",
        "Start at the very top edge and pull, the way you pull a notification shade. Rows unroll " +
            "one at a time. Let go on the lit one.\n\nLet go early and nothing happens. Pull all the " +
            "way and you are back at the shelf.\n\nBACK, FORWARD, REFRESH. SAVE A COPY keeps the page " +
            "as a PDF. READER VIEW drops everything but the words. CONVERT sends the page to Movie " +
            "Tickets or the Library.",
    ),
    Page(
        "The wheel",
        "Turn it to scroll. Press it for the flashlight, as always.\n\nThe camera button goes back " +
            "one page, then to the shelf.",
    ),
    Page(
        "No signal",
        "Turn on Keeping a copy and the page is printed to a PDF after every visit.\n\nOpen a " +
            "ticket at home. It is on the phone at the gate.\n\nSomething broken? Shake the phone. " +
            "A chip appears in the corner; tap it to send what went wrong.",
    ),
)

/**
 * Seven short pages. Wheel or tap moves through them; the last one ends with DONE.
 */
@Composable
fun TutorialScreen(scroll: ScrollState, onDone: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    WheelSteps { delta -> index = (index - delta).coerceIn(0, PAGES.lastIndex) }
    val page = PAGES[index]
    val last = index == PAGES.lastIndex

    Column(Modifier.fillMaxSize()) {
        TopBar("How it works", right = "${(index + 1).toString().padStart(2, '0')} of ${PAGES.size}")
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = Metrics.pad, vertical = 26.dp)) {
            Text(page.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(18.dp))
            Text(page.body, style = MaterialTheme.typography.bodySmall, color = Dim)
        }
        // Where you are, as a row of rules rather than dots: the page you are on is white and
        // long, the rest are short and faint. A dot says "one of seven" and nothing else.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Metrics.pad, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PAGES.indices.forEach { i ->
                Spacer(
                    Modifier
                        .weight(if (i == index) 2f else 1f)
                        .height(2.dp)
                        .background(if (i == index) Color.White else RuleGrey),
                )
            }
        }
        ActionBar(
            if (last) {
                listOf(BarAction("BACK") { index-- }, BarAction("DONE", onDone))
            } else if (index == 0) {
                listOf(BarAction("SKIP", onDone), BarAction("NEXT") { index++ })
            } else {
                listOf(BarAction("BACK") { index-- }, BarAction("NEXT") { index++ })
            },
        )
    }
}
