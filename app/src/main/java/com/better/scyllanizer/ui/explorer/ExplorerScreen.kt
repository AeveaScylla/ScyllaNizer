package com.better.scyllanizer.ui.explorer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.better.scyllanizer.data.CategoryMapper
import com.better.scyllanizer.util.formatBytes
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(vm: ExplorerViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var isLeftPaneActive by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val currentPath = if (isLeftPaneActive) state.leftPath else state.rightPath
    val currentDir = File(currentPath)
    
    val rootStoragePath = android.os.Environment.getExternalStorageDirectory().absolutePath
    val canNavigateUp = currentDir.absolutePath != rootStoragePath && currentDir.absolutePath.length > rootStoragePath.length

    val showRestrictedMessage: () -> Unit = {
        scope.launch {
            snackbarHostState.showSnackbar(
                message = "End of storage. Android restricts access beyond this point.",
                duration = SnackbarDuration.Short
            )
        }
    }

    BackHandler(enabled = true) {
        if (canNavigateUp) {
            currentDir.parentFile?.let {
                vm.loadDirectory(isLeftPaneActive, it.absolutePath)
            }
        } else {
            showRestrictedMessage()
        }
    }

    if (state.error != null) {
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            title = { Text("Error") },
            text = { Text(state.error!!) },
            confirmButton = { TextButton(onClick = { vm.clearError() }) { Text("OK") } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (state.message != null) {
        LaunchedEffect(state.message) {
            kotlinx.coroutines.delay(2000)
            vm.clearMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isTablet = maxWidth > 700.dp

                    if (isTablet) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            FilePane(
                                modifier = Modifier.weight(1f),
                                path = state.leftPath,
                                files = state.leftFiles,
                                selectedFiles = state.selectedFiles,
                                onNavigate = { vm.loadDirectory(true, it) },
                                onToggleSelect = { vm.toggleSelection(it) },
                                onExtractZip = { vm.extractZip(it, true) },
                                isActive = isLeftPaneActive,
                                onActiveClick = { isLeftPaneActive = true },
                                canNavigateUp = canNavigateUp,
                                onRootAttempt = showRestrictedMessage
                            )
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            FilePane(
                                modifier = Modifier.weight(1f),
                                path = state.rightPath,
                                files = state.rightFiles,
                                selectedFiles = state.selectedFiles,
                                onNavigate = { vm.loadDirectory(false, it) },
                                onToggleSelect = { vm.toggleSelection(it) },
                                onExtractZip = { vm.extractZip(it, false) },
                                isActive = !isLeftPaneActive,
                                onActiveClick = { isLeftPaneActive = false },
                                canNavigateUp = canNavigateUp,
                                onRootAttempt = showRestrictedMessage
                            )
                        }
                    } else {
                        FilePane(
                            modifier = Modifier.fillMaxSize(),
                            path = if (isLeftPaneActive) state.leftPath else state.rightPath,
                            files = if (isLeftPaneActive) state.leftFiles else state.rightFiles,
                            selectedFiles = state.selectedFiles,
                            onNavigate = { vm.loadDirectory(isLeftPaneActive, it) },
                            onToggleSelect = { vm.toggleSelection(it) },
                            onExtractZip = { vm.extractZip(it, isLeftPaneActive) },
                            isActive = true,
                            onActiveClick = {},
                            showSwapButton = true,
                            onSwap = { isLeftPaneActive = !isLeftPaneActive },
                            canNavigateUp = canNavigateUp,
                            onRootAttempt = showRestrictedMessage
                        )
                    }
                }
            }

            // Floating Action Bar
            if (state.selectedFiles.isNotEmpty() || state.clipboard.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.selectedFiles.isNotEmpty()) {
                            Text("${state.selectedFiles.size} selected", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Row {
                                IconButton(onClick = { vm.copySelected() }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                }
                                IconButton(onClick = { vm.cutSelected() }) {
                                    Icon(Icons.Default.ContentCut, contentDescription = "Cut")
                                }
                                IconButton(onClick = { vm.deleteSelected() }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${state.clipboard.size} copied", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = { vm.clearClipboard() }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            Button(
                                onClick = { vm.pasteInto(isLeftPaneActive) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Paste")
                            }
                        }
                    }
                }
            }
        }

        // Snackbar Host overlaid at the bottom
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (state.selectedFiles.isNotEmpty() || state.clipboard.isNotEmpty()) 80.dp else 16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePane(
    modifier: Modifier = Modifier,
    path: String,
    files: List<File>,
    selectedFiles: Set<String>,
    onNavigate: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onExtractZip: (File) -> Unit,
    isActive: Boolean,
    onActiveClick: () -> Unit,
    canNavigateUp: Boolean,
    onRootAttempt: () -> Unit,
    showSwapButton: Boolean = false,
    onSwap: () -> Unit = {}
) {
    val currentDir = remember(path) { File(path) }
    
    Column(
        modifier = modifier
            .background(
                if (isActive) MaterialTheme.colorScheme.background 
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
            )
            .clickable { onActiveClick() }
    ) {
        // Clean Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { 
                    if (canNavigateUp) {
                        currentDir.parentFile?.let { onNavigate(it.absolutePath) }
                    } else {
                        onRootAttempt()
                    }
                }
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, 
                    contentDescription = "Up",
                    tint = if (canNavigateUp) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
            Text(
                text = currentDir.name.ifEmpty { "Storage" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showSwapButton) {
                IconButton(onClick = onSwap) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Swap Pane", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // File List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(files) { _, file: File ->
                FileRow(
                    file = file,
                    isSelected = selectedFiles.contains(file.absolutePath),
                    onClick = {
                        if (selectedFiles.isNotEmpty()) {
                            onToggleSelect(file.absolutePath)
                        } else {
                            if (file.isDirectory) onNavigate(file.absolutePath)
                        }
                    },
                    onLongClick = { onToggleSelect(file.absolutePath) },
                    onExtractZip = { onExtractZip(file) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    file: File,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onExtractZip: () -> Unit
) {
    val isDir = file.isDirectory
    val ext = file.extension.lowercase()
    val category = if (isDir) "Folder" else CategoryMapper.categoryFor(ext)
    val xrayColor = if (isDir) MaterialTheme.colorScheme.primary else CategoryMapper.getCategoryColor(category)
    val isZip = ext == "zip"

    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // X-Ray Indicator Pill
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(32.dp)
                .background(xrayColor.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
        )
        
        Spacer(Modifier.width(16.dp))

        // Icon or Thumbnail
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!isDir && (ext == "jpg" || ext == "jpeg" || ext == "png" || ext == "webp")) {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = if (isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = if (isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        // File Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isDir) {
                    Box(modifier = Modifier.size(8.dp).background(xrayColor, RoundedCornerShape(50)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatBytes(file.length()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Folder",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Actions
        if (isZip) {
            IconButton(
                onClick = onExtractZip,
                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)).size(36.dp)
            ) {
                Icon(Icons.Default.Archive, contentDescription = "Extract", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
