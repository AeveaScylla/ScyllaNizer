package com.better.scyllanizer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.better.scyllanizer.model.MovePlan
import com.better.scyllanizer.model.UiState
import com.better.scyllanizer.util.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(state: UiState, onBack: () -> Unit, onConfirm: () -> Unit) {
    val grouped = remember(state.plan) { state.plan.moves.groupBy { it.folderPath }.toList().sortedBy { it.first } }

    // Root Column instead of Scaffold to guarantee bottom padding control
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        
        // 1. Top Bar
        TopAppBar(
            title = { Text("Preview") },
            navigationIcon = { 
                IconButton(onClick = onBack) { 
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") 
                } 
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        // 2. The List (Takes up remaining space)
        Box(
            modifier = Modifier
                .weight(1f) // Pushes the bottom button to the very bottom
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 800.dp), // Keeps layout constrained on 13" tablet
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item { SummaryCard(state) }
                grouped.forEach { (folderPath, plans) ->
                    item(key = "header-$folderPath") { FolderHeader(folderPath, plans.size) }
                    items(items = plans, key = { it.entry.file.absolutePath }) { plan -> MoveRow(plan) }
                }
            }
        }

        // 3. The Bottom Button (Guaranteed to be above the nav bar)
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding() // Forces it above system nav
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp) // Keeps the button centered on tablet
                        .height(54.dp)
                ) {
                    Text("Move ${state.plan.moves.size} files", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: UiState) {
    SectionCard {
        Text("${state.plan.moves.size} files will be moved", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("${state.plan.alreadySorted} already in place \u00B7 ${state.plan.scanned} scanned \u00B7 ${formatBytes(state.plan.moves.sumOf { it.entry.size })} total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FolderHeader(path: String, count: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(path, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
            Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
}

@Composable
private fun MoveRow(plan: MovePlan) {
    Row(modifier = Modifier.fillMaxWidth().background(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(plan.entry.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (plan.willRename) {
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(plan.targetFile.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                }
                Text(formatBytes(plan.entry.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.background(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(plan.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
