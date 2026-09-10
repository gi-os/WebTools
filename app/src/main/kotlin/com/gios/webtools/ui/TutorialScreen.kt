package com.gios.webtools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.gios.webtools.ui.theme.Faint

private data class Page(val title: String, val body: String)

private val PAGES = listOf(
    Page(
        "A shelf, and one field",
        "Web Tools keeps a short list of web pages you need now and then: a ticket, a train " +
            "status, a form you fill twice a year. That is the shelf.\n\nThe field above it is the " +
            "other half. Type an address and it opens. Type words and they go to a search engine: " +
            "DuckDuckGo, Ecosia or Kagi. The engine's name sits at the right end of the field; tap " +
            "it to switch. Nothing typed there is kept.\n\nUnderneath is Firefox's engine, so sites see a real " +
            "browser. Ads and trackers are blocked by uBlock Origin, always.",
    ),
    Page(
        "Adding one",
        "ADD gives you three ways in.\n\nScan a code: open gi-os.github.io/WebTools on a " +
            "computer, fill in the name and address, and point the phone at the code it draws.\n\n" +
            "Type an address, or take one of the starters. A page on the shelf stays inside its own " +
            "site; links elsewhere are dead ends, unless you allow them.",
    ),
    Page(
        "The pull-down menu",
        "From the top of any page, pull down. A menu unrolls from the top edge, one row at a " +
            "time, each lit as it comes. Let go on a lit row to do it. Let go early and nothing " +
            "happens. Pull all the way and it is always TOOLS: back to the list.\n\nSAVE A COPY " +
            "keeps the page as a PDF for when there is no signal. READER VIEW shows the text only. " +
            "SET AS HOME makes the page you are on the tool's start page (KEEP ON SHELF, for a page " +
            "you searched for).",
    ),
    Page(
        "The wheel and the button",
        "Turn the wheel to scroll any page or list. Pressing it is still the flashlight.\n\nThe " +
            "camera button steps back one page, then to the list. The home key leaves the app, as " +
            "everywhere.",
    ),
    Page(
        "When a sign-in refuses",
        "A few sign-in pages run a bot check that refuses anything but a browser they know. " +
            "Firefox's engine passes most of them. When one still refuses, sign in on a " +
            "computer. On gi-os.github.io/WebTools, under Bring a login, paste the page's Copy as " +
            "cURL from DevTools. Scan the code it draws, in parts if there are several. The tool " +
            "opens signed in.",
    ),
    Page(
        "Something wrong? Shake",
        "Shake the phone three times and it asks to send a report: what the page did, what the " +
            "page sees, and a picture of the screen. Add a line about what happened if you like. NO " +
            "sends nothing.",
    ),
    Page(
        "No signal",
        "Hold a tool in the list to see its page. Turn on Keeping a copy and the page is saved " +
            "as a PDF after every visit. With no signal, that copy opens instead.\n\nOpen a ticket at " +
            "home and it is on the phone at the gate.",
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
        TopBar("How it works", right = "${index + 1} of ${PAGES.size}")
        Rule()
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 28.dp)) {
            Text(page.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(20.dp))
            Text(page.body, style = MaterialTheme.typography.bodyMedium, color = Dim)
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            PAGES.indices.forEach { i ->
                Spacer(
                    Modifier
                        .padding(horizontal = 5.dp)
                        .size(6.dp)
                        .background(if (i == index) Color.White else Faint, CircleShape),
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
