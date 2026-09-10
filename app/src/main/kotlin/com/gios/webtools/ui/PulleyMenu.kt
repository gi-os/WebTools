package com.gios.webtools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gios.webtools.ui.theme.Dim
import com.gios.webtools.ui.theme.Faint
import com.gios.webtools.ui.theme.Ghost
import com.gios.webtools.ui.theme.Metrics
import com.gios.webtools.ui.theme.Mono
import com.gios.webtools.ui.theme.RuleGrey

/**
 * The menu the pull draws out of the top edge. It unrolls downward, the way the thumb moves: a
 * band with a handle first, then row one, row two, and so on. Letting go on a lit row does it;
 * letting go early does nothing. Past the last row the last row stays lit, so a long pull always
 * lands there.
 *
 * **A lit row is marked, not filled.** A white block behind black text is the loudest thing this
 * app can draw, and it appeared and vanished under the thumb at every row — the menu flashed
 * rather than moved. Now every row keeps the page's black, the labels sit dim, and the row the
 * thumb has reached turns white and grows a short bar at the left edge. The eye follows the bar
 * down the list. The lit row says LET GO, which is the only instruction the gesture ever needed;
 * the others say nothing, because a row you cannot tap by number does not need one.
 */
@Composable
fun PulleyMenu(items: List<String>, travelPx: Float, picked: Int, pitchDp: Float, deadzoneDp: Float) {
    if (travelPx <= 0f || items.isEmpty()) return
    val density = LocalDensity.current
    val heightDp = with(density) { travelPx.toDp() }.coerceAtMost((deadzoneDp + items.size * pitchDp).dp)
    Box(
        Modifier.fillMaxWidth().height(heightDp).background(Color.Black).clipToBounds(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.fillMaxWidth()) {
            // The band the thumb crosses before anything is live, with a handle in it so the
            // band reads as part of the menu rather than as the menu failing to start.
            Box(
                Modifier.fillMaxWidth().height((deadzoneDp - 1f).coerceAtLeast(0f).dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(28.dp).height(2.dp).background(Ghost))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(RuleGrey))
            items.forEachIndexed { i, label ->
                val lit = i == picked
                Box(Modifier.fillMaxWidth().height(pitchDp.dp)) {
                    if (lit) {
                        Box(
                            Modifier.align(Alignment.CenterStart).width(20.dp).height(2.dp).background(Color.White),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().height(pitchDp.dp).padding(start = 36.dp, end = Metrics.pad),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (lit) Color.White else Faint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (lit) {
                            Spacer(Modifier.width(12.dp))
                            Text("LET GO", style = Mono.label, color = Dim)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(RuleGrey))
            }
        }
    }
}
