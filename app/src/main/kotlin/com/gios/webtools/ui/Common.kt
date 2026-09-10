package com.gios.webtools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gios.webtools.ui.theme.Dim
import com.gios.webtools.ui.theme.Faint
import com.gios.webtools.ui.theme.Metrics
import com.gios.webtools.ui.theme.Mono
import com.gios.webtools.ui.theme.RuleGrey

@Composable
fun Rule(modifier: Modifier = Modifier) =
    HorizontalDivider(modifier = modifier, color = RuleGrey, thickness = 1.dp)

/**
 * A screen's own name, once, at the top, in the content voice — with one monospaced fact beside
 * it. The name is the thing you are looking at; the fact is what it is.
 */
@Composable
fun TopBar(title: String, right: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = Metrics.pad, end = Metrics.pad, top = 22.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (right != null) {
            Spacer(Modifier.width(12.dp))
            Text(right.uppercase(), style = Mono.label, color = Dim)
        }
    }
    Rule()
}

/**
 * The line between a screen's name and its list: what the list is on the left, how to work it on
 * the right. Both monospaced, both dim, no rule under them — they belong to the list below.
 */
@Composable
fun SectionHead(left: String, right: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = Metrics.pad, end = Metrics.pad, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(left.uppercase(), style = Mono.label, color = Faint)
        if (right != null) Text(right.uppercase(), style = Mono.label, color = Faint)
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(28.dp), Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = Dim, textAlign = TextAlign.Center)
    }
}

data class BarAction(val label: String, val onClick: () -> Unit)

/**
 * Bottom action bar in the LightOS idiom: full-width word buttons, no icons, at most three.
 * Monospaced and tracked wide, so the bar reads as chrome and never as content.
 */
@Composable
fun ActionBar(actions: List<BarAction>) {
    Column {
        Rule()
        Row(
            Modifier.fillMaxWidth().height(Metrics.bar),
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
                    Text(action.label, style = Mono.bar, color = Color.White, maxLines = 1)
                }
            }
        }
    }
}

/**
 * A row of the shelf: an index, a name, what it is, and its state.
 *
 * The index is monospaced and faint at a fixed width, so names start on the same line down the
 * whole list and a row can be named out loud ("open four") without a tap. Fixed height rather
 * than padding, because a row with no detail line must still be the height of one that has it.
 */
@Composable
fun ListRow(
    title: String,
    detail: String?,
    right: String? = null,
    index: Int? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(Metrics.row)
            .then(
                if (onLongClick != null) {
                    Modifier.longClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .padding(horizontal = Metrics.pad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text(
                index.toString().padStart(2, '0'),
                style = Mono.numeral,
                color = Faint,
                modifier = Modifier.width(Metrics.index),
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!detail.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(detail, style = Mono.detail, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (right != null) {
            Spacer(Modifier.width(12.dp))
            Text(right.uppercase(), style = Mono.label, color = Dim)
        }
    }
    Rule()
}

/**
 * One fact and its state, on one line: *Keeping a copy · ON*. The state is monospaced, white when
 * it is on and dim when it is anything else, so a screen of these reads down the right edge.
 */
@Composable
fun FactRow(title: String, state: String?, lit: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(Metrics.fact)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Metrics.pad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // The title yields, not the state: a state clipped to "P" says nothing at all, while a
        // long title with its end trimmed still says which row this is.
        Text(title, style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (state != null) {
            Spacer(Modifier.width(12.dp))
            Text(state.uppercase(), style = Mono.label, color = if (lit) Color.White else Dim, maxLines = 1, softWrap = false)
        }
    }
    Rule()
}

/**
 * A block of label–value pairs: the label monospaced and faint in a fixed column, the value in
 * the content voice beside it. A grid rather than stacked fields, because six stacked fields is
 * a screen of headings and this is a screen of answers.
 */
@Composable
fun FactGrid(rows: List<Pair<String, String>>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Metrics.pad, vertical = 14.dp)) {
        rows.forEachIndexed { i, (label, value) ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    label.uppercase(),
                    style = Mono.label,
                    color = Faint,
                    modifier = Modifier.width(112.dp).padding(top = 3.dp),
                )
                Text(value, style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.weight(1f))
            }
        }
    }
    Rule()
}
