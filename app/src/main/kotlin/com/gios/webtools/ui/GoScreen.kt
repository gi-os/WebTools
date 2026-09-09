package com.gios.webtools.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gios.webtools.ui.theme.Dim

/**
 * One field. Type an address, open it once. No suggestions, no history, no search engine
 * standing behind the field. What you type is where you go.
 */
@Composable
fun GoScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        TopBar("Go")
        Rule()
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp)) {
            Text("ADDRESS", style = MaterialTheme.typography.labelSmall, color = Dim)
            Spacer(Modifier.height(10.dp))
            BasicTextField(
                value = typed,
                onValueChange = { typed = it.replace("\n", "") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (typed.isNotBlank()) onOpen(typed) }),
                decorationBox = { inner ->
                    Column {
                        if (typed.isEmpty()) Text("nytimes.com", style = MaterialTheme.typography.titleMedium, color = Dim)
                        inner()
                        Spacer(Modifier.height(8.dp))
                        Rule()
                    }
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "Opens once and is not kept. Links may go anywhere. To keep a page, and keep it inside " +
                    "its site, use ADD instead.",
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
            )
        }
        ActionBar(
            listOf(
                BarAction("BACK", onBack),
                BarAction("OPEN") { if (typed.isNotBlank()) onOpen(typed) },
            ),
        )
    }
}
