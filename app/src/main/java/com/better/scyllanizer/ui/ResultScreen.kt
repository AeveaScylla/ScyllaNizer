package com.better.scyllanizer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.better.scyllanizer.model.UiState

@Composable
fun ResultScreen(state: UiState, onUndo: () -> Unit, onDone: () -> Unit) {
    val result = state.result
    val undo = state.undoResult

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(92.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(46.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text(if (undo != null) "Restored" else "All done", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = buildString {
                if (undo != null) {
                    append("${undo.restored} items moved back")
                    if (undo.failed > 0) append(" \u00B7 ${undo.failed} failed")
                } else {
                    if (result?.moved != null && result.moved > 0) append("${result.moved} moved · ")
                    if (result?.deleted != null && result.deleted > 0) append("${result.deleted} deleted · ")
                    if (result?.failed != null && result.failed > 0) append("${result.failed} failed")
                }
            }.trim(' ', '\u00B7'),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (result != null && result.errors.isNotEmpty() && undo == null) {
            Spacer(Modifier.height(16.dp))
            Text(result.errors.first(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(32.dp))
        if (undo == null && state.canUndo) {
            OutlinedButton(
                onClick = onUndo,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Undo last sort")
            }
            Spacer(Modifier.height(10.dp))
        }
        Button(
            onClick = onDone,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Done")
        }
    }
}
