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
import androidx.compose.ui.unit.sp
import com.better.scyllanizer.model.UiState

@Composable
fun ResultScreen(state: UiState, onUndo: () -> Unit, onDone: () -> Unit) {
    val result = state.result
    val undo = state.undoResult

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = if (undo != null) "Restored" else "All done",
            fontSize = 19.0.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = buildString {
                if (undo != null) {
                    append("${undo.restored} items moved back")
                    if (undo.failed > 0) append(" · ${undo.failed} failed")
                } else {
                    if (result != null && result.moved > 0) append("${result.moved} moved · ")
                    if (result != null && result.deleted > 0) append("${result.deleted} deleted · ")
                    if (result != null && result.failed > 0) append("${result.failed} failed")
                }
            }.trim(' ', '·'),
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (result != null && result.errors.isNotEmpty() && undo == null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = result.errors.first(),
                fontSize = 11.0.sp,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(26.dp))

        if (undo == null && state.canUndo) {
            OutlinedButton(
                onClick = onUndo,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp).height(40.dp)
            ) {
                Text("Undo last sort", fontSize = 13.0.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = onDone,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp).height(40.dp)
        ) {
            Text("Done", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
