package com.gios.webtools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.gios.webtools.ui.theme.Dim
import com.gios.webtools.ui.theme.RuleGrey

/**
 * The sheet a shake raises. One question, an optional line of words, two answers. The
 * screenshot was taken before this went up, so it shows the page and not the sheet.
 */
@Composable
fun ReportSheet(
    toolName: String?,
    hasToken: Boolean,
    onSend: (note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by remember { mutableStateOf("") }
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().background(Color.Black).clickable(enabled = false, onClick = {}),
        ) {
            Rule()
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text("SEND A REPORT?", style = MaterialTheme.typography.labelLarge, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (toolName != null) {
                        "About $toolName: what the page did, its log, and a picture of the screen go to the tracker."
                    } else {
                        "What the app was doing and a picture of the screen go to the tracker."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Dim,
                )
                if (!hasToken) {
                    Spacer(Modifier.height(6.dp))
                    Text("This build has no tracker key. The report is kept on the phone.", style = MaterialTheme.typography.bodySmall, color = Dim)
                }
                Spacer(Modifier.height(16.dp))
                Text("WHAT HAPPENED (OPTIONAL)", style = MaterialTheme.typography.labelSmall, color = Dim)
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = note,
                    onValueChange = { if (it.length <= 300) note = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    cursorBrush = SolidColor(Color.White),
                    maxLines = 3,
                    decorationBox = { inner ->
                        Column {
                            if (note.isEmpty()) Text("sign-in page never loads", style = MaterialTheme.typography.bodyMedium, color = RuleGrey)
                            inner()
                            Spacer(Modifier.height(6.dp))
                            Rule()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ActionBar(listOf(BarAction("NO", onDismiss), BarAction("SEND") { onSend(note) }))
        }
    }
}
