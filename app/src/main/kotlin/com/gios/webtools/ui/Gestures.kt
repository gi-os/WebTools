package com.gios.webtools.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier

/**
 * Tap and hold on a row. LightOS has no ripple, so no indication; `combinedClickable` with a
 * null interaction source is the SDK's own shape for this.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.longClickable(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    this.combinedClickable(
        interactionSource = null,
        indication = null,
        onClick = onClick,
        onLongClick = onLongClick,
    )
