package com.better.scyllanizer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.better.scyllanizer.model.ActionType
import com.better.scyllanizer.model.MovePlan
import com.better.scyllanizer.model.UiState
import com.better.scyllanizer.util.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    state: UiState,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onToggleSelectAll: () -> Unit
) {
    val grouped = remember(state.plan) { state.plan.moves.groupBy { it.category }.toList().sortedBy { it.first } }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Preview") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            actions = {
                TextButton(onClick = onToggleSelectAll) {
                    Text(if (state.selectedIds.size == state.plan.moves.size) "Deselect All" else "Select All", color = MaterialTheme.colorScheme.primary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().widthIn(max = 800.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item { SummaryCard(state) }
                grouped.forEach { (category, plans) ->
                    item(key = "header-$category") { FolderHeader(category, plans.size) }
                    items(items = plans, key = { it.uniqueId }) { plan -> 
                        MoveRow(
                            plan = plan,
                            isSelected = state.selectedIds.contains(plan.uniqueId),
                            onToggle = { onToggleSelection(plan.uniqueId) }
                        ) 
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onConfirm,
                    enabled = state.selectedIds.isNotEmpty(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp).height(54.dp)
                ) {
                    Text("Execute ${state.selectedIds.size} actions", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: UiState) {
    SectionCard {
        Text("${state.plan.moves.size} actions pending", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Uncheck items you want to keep.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
private fun MoveRow(plan: MovePlan, isSelected: Boolean, onToggle: () -> Unit) {
    val isDeletion = plan.actionType != ActionType.MOVE
    val bgColor = if (isDeletion) MaterialTheme.colorScheme.error.copy(alpha = if (isSelected) 0.15f else 0.05f) else MaterialTheme.colorScheme.surface
    val icon = if (isDeletion) Icons.Default.Delete else Icons.Default.ArrowForward
    val iconTint = if (isDeletion) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = bgColor, shape = RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = if (isDeletion) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        )
        
        Spacer(Modifier.width(4.dp))
        
        Column(Modifier.weight(1f)) {
            Text(
                text = plan.entry?.name ?: plan.folderToDelete?.name ?: "Unknown",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isDeletion) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = when (plan.actionType) {
                            ActionType.DELETE_0BYTE -> "Delete (0 bytes)"
                            ActionType.DELETE_DUPLICATE -> "Delete (duplicate)"
                            ActionType.DELETE_EMPTY_FOLDER -> "Delete (empty folder)"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (plan.willRename) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(plan.targetFile?.name ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (plan.entry != null && plan.entry.size > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(formatBytes(plan.entry.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.background(
                color = if (isDeletion) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            ).padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isDeletion) "TRASH" else plan.category,
                style = MaterialTheme.typography.labelSmall,
                color = if (isDeletion) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
