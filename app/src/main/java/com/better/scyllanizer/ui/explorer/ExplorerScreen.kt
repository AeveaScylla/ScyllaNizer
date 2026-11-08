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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.better.scyllanizer.data.CategoryMapper
import com.better.scyllanizer.ui.MiniCheckbox
import com.better.scyllanizer.util.formatBytes
import kotlinx.coroutines.launch
import java.io.File

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
            title = { Text("Error", fontSize = 16.0.sp, fontWeight = FontWeight.Bold) },
            text = { Text(state.error!!, fontSize = 12.5.sp) },
            confirmButton = { TextButton(onClick = { vm.clearError() }) { Text("OK", fontSize = 13.0.sp, fontWeight = FontWeight.Bold) } },
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

            if (state.selectedFiles.isNotEmpty() || state.clipboard.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.selectedFiles.isNotEmpty()) {
                            Text("${state.selectedFiles.size} selected", fontSize = 12.0.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Row {
                                IconButton(onClick = { vm.copySelected() }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { vm.cutSelected() }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.ContentCut, contentDescription = "Cut", modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { vm.deleteSelected() }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${state.clipboard.size} copied", fontSize = 12.0.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = { vm.clearClipboard() }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                }
                            }
                            Button(
                                onClick = { vm.pasteInto(isLeftPaneActive) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Paste", fontSize = 12.0.sp)
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (state.selectedFiles.isNotEmpty() || state.clipboard.isNotEmpty()) 64.dp else 12.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { 
                    if (canNavigateUp) {
                        currentDir.parentFile?.let { onNavigate(it.absolutePath) }
                    } else {
                        onRootAttempt()
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, 
                    contentDescription = "Up",
                    tint = if (canNavigateUp) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = currentDir.name.ifEmpty { "Storage" },
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showSwapButton) {
                IconButton(onClick = onSwap, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Swap Pane", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(24.dp)
                .background(xrayColor.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
        )
        
        Spacer(Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
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
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                fontSize = 12.0.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isDir) {
                    Box(modifier = Modifier.size(5.dp).background(xrayColor, RoundedCornerShape(50)))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = category,
                        fontSize = 10.0.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "•",
                        fontSize = 10.0.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = formatBytes(file.length()),
                        fontSize = 10.0.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Folder",
                        fontSize = 10.0.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isZip) {
            IconButton(
                onClick = onExtractZip,
                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp)).size(28.dp)
            ) {
                Icon(Icons.Default.Archive, contentDescription = "Extract", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            }
        }
    }
}
