package com.better.scyllanizer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun FilePickerDialog(startPath: String, confirmText: String = "Select this folder", onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var currentDir by remember { mutableStateOf(File(startPath)) }
    val items = remember(currentDir) {
        val dirs = currentDir.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }?.sortedBy { it.name.lowercase() } ?: emptyList()
        val list = mutableListOf<File>()
        if (currentDir.parentFile != null && currentDir.absolutePath != "/") list.add(File(currentDir, ".."))
        list.addAll(dirs)
        list
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(text = currentDir.absolutePath, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(items) { dir ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { if (dir.name == "..") currentDir = currentDir.parentFile ?: currentDir else currentDir = dir }.padding(vertical = 12.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(text = if (dir.name == "..") ".. (Up)" else dir.name, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSelect(currentDir.absolutePath) }) { Text(confirmText, color = MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    )
}
