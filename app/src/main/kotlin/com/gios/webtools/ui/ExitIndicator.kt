package com.gios.webtools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gios.webtools.ui.theme.Dim

/**
 * The line that grows while you pull. Dim until the lift would commit, then white with the word
 * TOOLS, so the gesture always says where letting go will land: back on the list.
 */
@Composable
fun ExitIndicator(progress: Float, armed: Boolean) {
    if (progress <= 0f) return
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0xFF1A1A1A))) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(if (armed) Color.White else Dim),
            )
        }
        if (armed) {
            Box(
                Modifier.fillMaxWidth().background(Color.White).padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("TOOLS", style = MaterialTheme.typography.labelLarge, color = Color.Black)
            }
        }
    }
}
