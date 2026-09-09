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
        "A shelf, not a browser",
        "Web Tools keeps a short list of web pages you need now and then: a ticket, a train " +
            "status, a form you fill twice a year.\n\nEach one opens by itself and stays inside its " +
            "own site. There is no address bar and no way to wander off. Links to other sites are " +
            "dead ends.",
    ),
    Page(
        "Adding one",
        "ADD gives you three ways in.\n\nScan a code: open gi-os.github.io/WebTools on a " +
            "computer, fill in the name and address, and point the phone at the code it draws.\n\n" +
            "Type an address, or take one of the starters.",
    ),
    Page(
        "Getting back",
        "Pull down from the top of a page and let go. A line grows as you pull. When it reads " +
            "TOOLS, lifting your thumb returns to the list.\n\nLet go early and nothing happens. " +
            "Pull sideways and it is a scroll.\n\nThe camera button steps back one page, then to " +
            "the list. The home key leaves the app, as everywhere.",
    ),
    Page(
        "The wheel",
        "Turn the wheel to scroll any page or list. Pressing it is still the flashlight.",
    ),
    Page(
        "When a site refuses",
        "Some sign-in pages run a bot check that refuses any embedded view. Ticketmaster's says " +
            "your browsing was paused.\n\nHold the tool in the list and set Opens in: Chromium. " +
            "The page then runs in the phone's own browser, with its cookies. The allowlist and " +
            "the saved copy do not apply there.\n\nAds and \"open in the app\" banners are " +
            "blocked in the built-in view.",
    ),
    Page(
        "Something wrong? Shake",
        "Shake the phone three times and it asks to send a report: what the page did, its log, " +
            "and a picture of the screen. Add a line about what happened if you like. NO sends " +
            "nothing.",
    ),
    Page(
        "No signal",
        "Hold a tool in the list to see its page. Turn on Keeping a copy and the page is saved " +
            "after every visit. With no signal, that copy opens instead.\n\nOpen a ticket at home " +
            "and it is on the phone at the gate.",
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
