package com.gios.webtools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.gios.webtools.ui.theme.Dim
import com.gios.webtools.ui.theme.RuleGrey

/**
 * The menu the pull draws out of the top edge, the Sailfish way: rows stacked above the content,
 * revealed by how far the thumb has come, the one under the thumb's reach lit. Letting go on a lit
 * row does it; letting go early does nothing.
 *
 * Rows are given top-to-bottom; the bottom row is the one reached first.
 */
@Composable
fun PulleyMenu(items: List<String>, travelPx: Float, picked: Int, pitchDp: Float, deadzoneDp: Float) {
    if (travelPx <= 0f || items.isEmpty()) return
    val density = LocalDensity.current
    val heightDp = with(density) { travelPx.toDp() }.coerceAtMost((deadzoneDp + items.size * pitchDp).dp)
    Box(
        Modifier.fillMaxWidth().height(heightDp).background(Color.Black).clipToBounds(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(Modifier.fillMaxWidth()) {
            items.forEachIndexed { i, label ->
                val lit = i == picked
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(pitchDp.dp)
                        .background(if (lit) Color.White else Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (lit) Color.Black else Dim,
                    )
                }
            }
            // The rule where menu meets content, and the thin band the thumb crosses first.
            Box(Modifier.fillMaxWidth().height(1.dp).background(RuleGrey))
            Spacer(Modifier.height((deadzoneDp - 1f).coerceAtLeast(0f).dp))
        }
    }
}
