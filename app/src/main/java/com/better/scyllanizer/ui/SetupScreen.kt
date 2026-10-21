package com.better.scyllanizer.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.better.scyllanizer.data.CategoryMapper
import com.better.scyllanizer.data.LocalServer
import com.better.scyllanizer.data.StorageAccess
import com.better.scyllanizer.model.OptionKey
import com.better.scyllanizer.model.UiState
import java.io.File
import java.net.NetworkInterface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    state: UiState,
    onFolderPicked: (String) -> Unit,
    onToggle: (OptionKey) -> Unit,
    onPreview: () -> Unit,
    onAddExclusion: (String) -> Unit,
    onRemoveExclusion: (String) -> Unit,
    onToggleShred: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf<LocalServer?>(null) }
    var serverIp by remember { mutableStateOf<String?>(null) }

    val subFolders = remember(state.rootPath) {
        val root = File(state.rootPath ?: "")
        if (root.exists() && root.isDirectory) {
            root.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }?.sortedBy { it.name.lowercase() } ?: emptyList()
        } else emptyList()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(title = { Text("ScyllaNizer", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                // FIXED: Using safeDrawing to perfectly avoid the system nav bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = onPreview,
                        enabled = state.hasPermission && state.rootPath != null,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .height(54.dp),
                    ) {
                        Text("Preview plan", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val isTablet = maxWidth > 600.dp

            if (isTablet) {
                Row(
                    modifier = Modifier.fillMaxSize().widthIn(max = 1200.dp).align(Alignment.TopCenter).padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // LEFT COLUMN
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PermissionOrFolder(state, onPickFolder = { showPicker = true })
                        OptionsCard(state, onToggle, onToggleShred)
                        if (state.plan.scanned > 0) StorageCard(state)
                    }

                    // RIGHT COLUMN
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        WifiDropCard(state, server, serverIp, onToggleServer = { isChecked ->
                            if (isChecked && state.rootPath != null) {
                                serverIp = getLocalIpAddress()
                                server = LocalServer(8080, File(state.rootPath)).also { it.start() }
                                Toast.makeText(context, "Server started at $serverIp:8080", Toast.LENGTH_LONG).show()
                            } else { server?.stop(); server = null }
                        })
                        
                        ExclusionsCard(state, subFolders, onAddExclusion, onRemoveExclusion)
                        CategoriesCard()
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PermissionOrFolder(state, onPickFolder = { showPicker = true })
                    if (state.plan.scanned > 0) StorageCard(state)
                    WifiDropCard(state, server, serverIp, onToggleServer = { isChecked ->
                        if (isChecked && state.rootPath != null) {
                            serverIp = getLocalIpAddress()
                            server = LocalServer(8080, File(state.rootPath)).also { it.start() }
                            Toast.makeText(context, "Server started at $serverIp:8080", Toast.LENGTH_LONG).show()
                        } else { server?.stop(); server = null }
                    })
                    OptionsCard(state, onToggle, onToggleShred)
                    ExclusionsCard(state, subFolders, onAddExclusion, onRemoveExclusion)
                    CategoriesCard()
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (showPicker) {
        FilePickerDialog(
            startPath = state.rootPath ?: "/storage/emulated/0",
            confirmText = "Select root folder",
            onDismiss = { showPicker = false },
            onSelect = { path -> showPicker = false; onFolderPicked(path) }
        )
    }
}

@Composable
private fun PermissionOrFolder(state: UiState, onPickFolder: () -> Unit) {
    val context = LocalContext.current
    if (!state.hasPermission) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("Permission Required", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            Text("To organize root folders, Android requires \"All Files Access\".", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { StorageAccess.requestAllFilesAccess(context) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Grant Permission") }
        }
    } else {
        FolderCard(state, onPickFolder)
    }
}

@Composable
private fun OptionsCard(state: UiState, onToggle: (OptionKey) -> Unit, onToggleShred: (Boolean) -> Unit) {
    SectionCard {
        CardTitle("Options")
        Spacer(Modifier.height(4.dp))
        OptionRow("Recursive scan", "Include files inside subfolders", state.options.recursive) { onToggle(OptionKey.RECURSIVE) }
        OptionRow("Skip hidden files", "Ignore dot-files and system artefacts", state.options.skipHidden) { onToggle(OptionKey.SKIP_HIDDEN) }
        OptionRow("Per-extension subfolders", "Images/JPG/, Documents/PDF/, …", state.options.extSubfolders) { onToggle(OptionKey.EXT_SUBFOLDERS) }
        OptionRow("Date subfolders", "Images/2026-10/ based on modified date", state.options.dateSubfolders) { onToggle(OptionKey.DATE_SUBFOLDERS) }
        OptionRow("Shred deleted files", "Securely overwrite data when deleting", state.options.shredFiles) { onToggleShred(!state.options.shredFiles) }
    }
}

@Composable
private fun WifiDropCard(state: UiState, server: LocalServer?, serverIp: String?, onToggleServer: (Boolean) -> Unit) {
    SectionCard {
        CardTitle("Wi-Fi Drop")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Local Web Server", style = MaterialTheme.typography.bodyLarge)
                Text(if (server != null) "http://$serverIp:8080" else "Upload from PC browser", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = server != null, onCheckedChange = onToggleServer, enabled = state.rootPath != null)
        }
    }
}

@Composable
private fun ExclusionsCard(state: UiState, subFolders: List<File>, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    SectionCard {
        CardTitle("Excluded Folders")
        Spacer(Modifier.height(4.dp))
        Text("Tick folders to IGNORE during sorting.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        
        if (subFolders.isEmpty()) {
            Text("No subfolders found.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            Column {
                subFolders.forEach { folder ->
                    val isExcluded = state.options.excludedPaths.contains(folder.absolutePath)
                    Row(modifier = Modifier.fillMaxWidth().clickable { if (isExcluded) onRemove(folder.absolutePath) else onAdd(folder.absolutePath) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isExcluded, onCheckedChange = { checked -> if (checked) onAdd(folder.absolutePath) else onRemove(folder.absolutePath) }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.error))
                        Text(folder.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageCard(state: UiState) {
    SectionCard {
        CardTitle("Storage Breakdown")
        Spacer(Modifier.height(16.dp))
        DonutChart(state)
    }
}

@Composable
private fun CategoriesCard() {
    SectionCard {
        CardTitle("Destination categories")
        Spacer(Modifier.height(10.dp))
        FlowRowHelper()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowHelper() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CategoryMapper.categories.forEach { category ->
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
    }
}

@Composable
fun DonutChart(state: UiState) {
    val categorySizes = state.plan.moves.groupBy { it.category }.mapValues { entry -> entry.value.sumOf { it.entry.size } }
    val totalSize = categorySizes.values.sum().toFloat()
    if (totalSize == 0f) return
    val colors = listOf(Color(0xFF06B6D4), Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFF59E0B), Color(0xFFEF4444))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(120.dp)) {
            var startAngle = -90f
            categorySizes.entries.forEachIndexed { index, entry ->
                val sweepAngle = (entry.value / totalSize) * 360f
                drawArc(color = colors[index % colors.size], startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = 30f, cap = androidx.compose.ui.graphics.StrokeCap.Round), size = Size(size.width, size.height), topLeft = Offset(30f, 30f))
                startAngle += sweepAngle
            }
        }
        Spacer(Modifier.width(24.dp))
        Column {
            categorySizes.entries.forEachIndexed { index, entry ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Box(Modifier.size(10.dp).background(colors[index % colors.size], RoundedCornerShape(50)))
                    Spacer(Modifier.width(6.dp))
                    Text(entry.key, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun FolderCard(state: UiState, onPickFolder: () -> Unit) {
    SectionCard {
        CardTitle("Folder to organise")
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(text = state.rootPath?.substringAfterLast("/") ?: "No folder selected", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = state.rootPath ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onPickFolder, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Text("Change folder") }
    }
}

fun getLocalIpAddress(): String? {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && address is java.net.Inet4Address) return address.hostAddress
            }
        }
    } catch (e: Exception) {}
    return null
}
