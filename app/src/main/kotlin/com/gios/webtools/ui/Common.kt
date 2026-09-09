package com.gios.webtools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gios.webtools.ui.theme.Dim
import com.gios.webtools.ui.theme.RuleGrey

@Composable
fun Rule(modifier: Modifier = Modifier) =
    HorizontalDivider(modifier = modifier, color = RuleGrey, thickness = 1.dp)

/** The thin title line at the top of a screen, in the LightOS `fine` style. */
@Composable
fun TopBar(title: String, right: String? = null) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Dim)
        if (right != null) Text(right.uppercase(), style = MaterialTheme.typography.labelSmall, color = Dim)
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(28.dp), Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Dim, textAlign = TextAlign.Center)
    }
}

data class BarAction(val label: String, val onClick: () -> Unit)

/**
 * Bottom action bar in the LightOS idiom: full-width word buttons, no icons, at most three.
 */
@Composable
fun ActionBar(actions: List<BarAction>) {
    Column {
        Rule()
        Row(
            Modifier.fillMaxWidth().height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEachIndexed { index, action ->
                if (index > 0) {
                    Box(Modifier.padding(vertical = 14.dp).fillMaxHeight().width(1.dp).background(RuleGrey))
                }
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(onClick = action.onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(action.label, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
                }
            }
        }
    }
}

/** A list row: a title over a dimmer detail line, with a rule underneath. */
@Composable
fun ListRow(title: String, detail: String?, right: String? = null, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
                    Modifier.longClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White, maxLines = 1)
            if (right != null) Text(right, style = MaterialTheme.typography.labelSmall, color = Dim)
        }
        if (!detail.isNullOrEmpty()) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Dim, maxLines = 1)
        }
    }
    Rule()
}
