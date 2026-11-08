package com.better.scyllanizer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.better.scyllanizer.model.ActionType
import com.better.scyllanizer.model.MovePlan
import com.better.scyllanizer.model.UiState
import com.better.scyllanizer.util.formatBytes

@Composable
fun PreviewScreen(
    state: UiState,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onToggleSelectAll: () -> Unit
) {
    val grouped = remember(state.plan) {
        state.plan.moves.groupBy { it.category }.toList().sortedBy { it.first }
    }
    val totalSize = remember(state.plan) {
        state.plan.moves.sumOf { it.entry?.size ?: 0L }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 12.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(Modifier.weight(1f).padding(start = 2.dp)) {
                Text(
                    text = "Preview",
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp
                )
                Text(
                    text = "${state.plan.moves.size} actions · ${formatBytes(totalSize)}",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinkButton(
                text = if (state.selectedIds.size == state.plan.moves.size) "Deselect all" else "Select all",
                onClick = onToggleSelectAll
            )
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().widthIn(max = 720.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                grouped.forEach { (category, plans) ->
                    item(key = "header-$category") {
                        GroupHeader(category, plans.size)
                    }
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

        BottomDock(
            text = "Execute ${state.selectedIds.size} actions",
            enabled = state.selectedIds.isNotEmpty(),
            onClick = onConfirm
        )
    }
}

@Composable
private fun GroupHeader(name: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name.uppercase(),
            fontSize = 10.0.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = count.toString(),
            fontSize = 10.0.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MoveRow(plan: MovePlan, isSelected: Boolean, onToggle: () -> Unit) {
    val isDelete = plan.actionType != ActionType.MOVE
    val accent = if (isDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    val bg = if (isDelete) accent.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surface
    val borderColor = if (isDelete) accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.outlineVariant

    val pillText = when (plan.actionType) {
        ActionType.MOVE -> plan.category
        ActionType.DELETE_0BYTE -> "0 BYTES"
        ActionType.DELETE_DUPLICATE -> "DUPLICATE"
        ActionType.DELETE_EMPTY_FOLDER -> "EMPTY DIR"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniCheckbox(checked = isSelected, accent = accent)
        Spacer(Modifier.width(8.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = plan.entry?.name ?: plan.folderToDelete?.name ?: "Unknown",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (plan.willRename && plan.targetFile != null) {
                Text(
                    text = "→ ${plan.targetFile.name}",
                    fontSize = 10.0.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(6.dp))

        if (plan.entry != null && plan.entry.size > 0) {
            Text(
                text = formatBytes(plan.entry.size),
                fontSize = 10.0.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
        }

        Pill(
            text = pillText,
            container = if (isDelete) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            content = accent
        )
    }
}
