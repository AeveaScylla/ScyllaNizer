package com.better.scyllanizer.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.better.scyllanizer.data.CategoryMapper
import com.better.scyllanizer.data.StorageAccess
import com.better.scyllanizer.model.OptionKey
import com.better.scyllanizer.model.UiState
import com.better.scyllanizer.ui.theme.ThemeMode
import com.better.scyllanizer.util.formatBytes
import java.io.File
import java.net.NetworkInterface

@Composable
fun SetupScreen(
    state: UiState,
    onFolderPicked: (String) -> Unit,
    onToggle: (OptionKey) -> Unit,
    onPreview: () -> Unit,
    onAddExclusion: (String) -> Unit,
    onRemoveExclusion: (String) -> Unit,
    onToggleShred: (Boolean) -> Unit,
    onUndo: () -> Unit,
    onToggleTheme: () -> Unit,
    wifiServerActive: Boolean,
    wifiServerIp: String?,
    onToggleWifiServer: (Boolean) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    var showSystemWarning by remember { mutableStateOf(false) }

    val subFolders = remember(state.rootPath) {
        val root = File(state.rootPath ?: "")
        if (root.exists() && root.isDirectory) {
            root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() } ?: emptyList()
        } else emptyList()
    }

    if (showSystemWarning) {
        AlertDialog(
            onDismissRequest = { showSystemWarning = false },
            title = {
                Text(
                    "System folders",
                    fontSize = 16.0.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    "Modifying folders such as Android/data or Android/obb can crash installed apps or destroy their data.\n\nOnly continue if you know exactly what you are doing.",
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSystemWarning = false
                    onToggle(OptionKey.INCLUDE_SYSTEM)
                }) {
                    Text("I understand", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 13.0.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSystemWarning = false }) {
                    Text("Cancel", fontSize = 13.0.sp)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            val wide = maxWidth > 720.dp

            val leftBlock: @Composable ColumnScope.() -> Unit = {
                AppHeader(state.themeMode, onToggleTheme)
                TransferGraphic()
                if (state.hasPermission) {
                    FolderCard(state) { showPicker = true }
                } else {
                    PermissionCard()
                }
                if (state.canUndo) UndoBar(onUndo)
                if (!wide && state.plan.scanned > 0) StorageCard(state)
                
                SortingCard(state, onToggle) {
                    if (state.options.includeSystemFolders) onToggle(OptionKey.INCLUDE_SYSTEM)
                    else showSystemWarning = true
                }
                CleanupCard(state, onToggle, onToggleShred)
            }

            val rightBlock: @Composable ColumnScope.() -> Unit = {
                if (wide) {
                    if (state.plan.scanned > 0) StorageCard(state)
                }
                WifiCard(state, wifiServerActive, wifiServerIp, onToggleWifiServer)
                ExclusionsCard(state, subFolders, onAddExclusion, onRemoveExclusion)
                CategoriesCard()
                Spacer(Modifier.height(8.dp))
            }

            if (wide) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        content = leftBlock
                    )
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        content = rightBlock
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    leftBlock()
                    rightBlock()
                }
            }
        }
        
        BottomDock(
            text = "Preview plan",
            enabled = state.hasPermission && state.rootPath != null,
            onClick = onPreview
        )
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
private fun AppHeader(mode: ThemeMode, onToggleTheme: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "ScyllaNizer",
            fontSize = 18.0.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.4).sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleTheme, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = if (mode == ThemeMode.DARK) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                contentDescription = "Switch theme",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun TransferGraphic() {
    val infiniteTransition = rememberInfiniteTransition(label = "transfer")

    val cycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cycle"
    )

    val p = cycle * 2f

    val arrow1Pos = when {
        p < 0.5f -> p * 2f
        p < 1.5f -> 1f
        else -> 4f - 2f * p
    }.coerceIn(0f, 1f)

    val arrow2Pos = when {
        p < 0.5f -> 0f
        p < 1.0f -> (p - 0.5f) * 2f
        p < 1.5f -> 3f - 2f * p
        else -> 0f
    }.coerceIn(0f, 1f)

    val arrow1RightAlpha = if (p < 0.5f) 1f else 0.25f
    val arrow1LeftAlpha = if (p >= 1.5f) 1f else 0.25f
    val arrow2RightAlpha = if (p >= 0.5f && p < 1.0f) 1f else 0.25f
    val arrow2LeftAlpha = if (p >= 1.0f && p < 1.5f) 1f else 0.25f

    val accent = MaterialTheme.colorScheme.primary
    val phoneBlue = Color(0xFF60A5FA)
    val folderYellow = Color(0xFFFBBF24)
    val pcGreen = Color(0xFF4ADE80)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Smartphone,
            contentDescription = "Phone",
            tint = phoneBlue,
            modifier = Modifier.size(32.dp)
        )

        BidirectionalArrow(
            progress = arrow1Pos,
            rightAlpha = arrow1RightAlpha,
            leftAlpha = arrow1LeftAlpha,
            color = accent,
            modifier = Modifier.weight(1f).height(20.dp).padding(horizontal = 8.dp)
        )

        Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = "Folder",
            tint = folderYellow,
            modifier = Modifier.size(32.dp)
        )

        BidirectionalArrow(
            progress = arrow2Pos,
            rightAlpha = arrow2RightAlpha,
            leftAlpha = arrow2LeftAlpha,
            color = accent,
            modifier = Modifier.weight(1f).height(20.dp).padding(horizontal = 8.dp)
        )

        Icon(
            imageVector = Icons.Outlined.Computer,
            contentDescription = "PC",
            tint = pcGreen,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun BidirectionalArrow(
    progress: Float,
    rightAlpha: Float,
    leftAlpha: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val y = size.height / 2f
        val headLen = 5.dp.toPx()
        val headSize = 3.dp.toPx()
        val usable = (size.width - headLen * 2f).coerceAtLeast(1f)
        val stroke = 1.5.dp.toPx()

        drawLine(
            color = color.copy(alpha = 0.18f),
            start = Offset(headLen, y),
            end = Offset(size.width - headLen, y),
            strokeWidth = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 5f), 0f),
            cap = StrokeCap.Round
        )

        val leftHead = Path().apply {
            moveTo(headLen, y - headSize)
            lineTo(0f, y)
            lineTo(headLen, y + headSize)
        }
        drawPath(
            leftHead,
            color = color.copy(alpha = leftAlpha),
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val rightHead = Path().apply {
            moveTo(size.width - headLen, y - headSize)
            lineTo(size.width, y)
            lineTo(size.width - headLen, y + headSize)
        }
        drawPath(
            rightHead,
            color = color.copy(alpha = rightAlpha),
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val dotX = headLen + progress.coerceIn(0f, 1f) * usable
        val center = Offset(dotX, y)

        drawCircle(color = color.copy(alpha = 0.10f), radius = 6.dp.toPx(), center = center)
        drawCircle(color = color.copy(alpha = 0.22f), radius = 3.5.dp.toPx(), center = center)
        drawCircle(color = color, radius = 1.8.dp.toPx(), center = center)
    }
}

@Composable
private fun FolderCard(state: UiState, onPick: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.rootPath?.substringAfterLast("/")?.ifBlank { "Storage" } ?: "No folder",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.rootPath ?: "—",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))
            LinkButton("Change", onPick)
        }
    }
}

@Composable
private fun PermissionCard() {
    val context = LocalContext.current
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "All Files Access required",
                fontSize = 13.0.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Android needs full storage access before ScyllaNizer can organise anything.",
            fontSize = 11.0.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { StorageAccess.requestAllFilesAccess(context) },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().height(36.dp)
        ) {
            Text("Grant permission", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun UndoBar(onUndo: () -> Unit) {
    OutlinedButton(
        onClick = onUndo,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier.fillMaxWidth().height(36.dp)
    ) {
        Text("Undo last sort", fontSize = 12.0.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SortingCard(state: UiState, onToggle: (OptionKey) -> Unit, onSystemClick: () -> Unit) {
    SectionCard {
        CardLabel("Sorting Options")
        Spacer(Modifier.height(4.dp))
        CompactOptionRow(
            "Recursive scan",
            "Include files inside subfolders",
            state.options.recursive
        ) { onToggle(OptionKey.RECURSIVE) }
        CompactOptionRow(
            "Skip hidden files",
            "Ignore dot-files and system artefacts",
            state.options.skipHidden
        ) { onToggle(OptionKey.SKIP_HIDDEN) }
        CompactOptionRow(
            "Subfolders for ALL files",
            "Images/JPG/, Videos/MP4/, etc.",
            state.options.extSubfolders
        ) { onToggle(OptionKey.EXT_SUBFOLDERS) }
        CompactOptionRow(
            "Date subfolders",
            "Images/2026-10/ based on modified date",
            state.options.dateSubfolders
        ) { onToggle(OptionKey.DATE_SUBFOLDERS) }
        CompactOptionRow(
            "Include System Folders",
            "Unlock Android/data (Dangerous)",
            state.options.includeSystemFolders,
            onSystemClick
        )
    }
}

@Composable
private fun CleanupCard(state: UiState, onToggle: (OptionKey) -> Unit, onToggleShred: (Boolean) -> Unit) {
    SectionCard {
        CardLabel("Cleanup")
        Spacer(Modifier.height(8.dp))
        ChipGrid(
            listOf(
                ChipOption("0-byte files", state.options.deleteZeroByteFiles) { onToggle(OptionKey.DELETE_ZERO) },
                ChipOption("Duplicates", state.options.deleteDuplicates) { onToggle(OptionKey.DELETE_DUPLICATES) },
                ChipOption("Empty dirs", state.options.deleteEmptyFolders) { onToggle(OptionKey.DELETE_EMPTY) },
                ChipOption("Shred", state.options.shredFiles) { onToggleShred(!state.options.shredFiles) },
            )
        )
    }
}

@Composable
private fun WifiCard(state: UiState, serverActive: Boolean, serverIp: String?, onToggleServer: (Boolean) -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Wi-Fi Drop", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (serverActive) "http://$serverIp:8080" else "Upload from a PC browser",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = serverActive,
                onCheckedChange = onToggleServer,
                enabled = state.rootPath != null,
                modifier = Modifier.scale(0.8f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun ExclusionsCard(
    state: UiState,
    subFolders: List<File>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    SectionCard {
        CardLabel("Excluded folders")
        Spacer(Modifier.height(6.dp))
        if (subFolders.isEmpty()) {
            Text(
                "No subfolders found",
                fontSize = 11.0.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                subFolders.forEach { folder ->
                    val isSystem = folder.name.equals("Android", true) || folder.name.equals("LOST.DIR", true)
                    val enabled = !isSystem || state.options.includeSystemFolders
                    val excluded = state.options.excludedPaths.contains(folder.absolutePath)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = enabled) {
                                if (excluded) onRemove(folder.absolutePath) else onAdd(folder.absolutePath)
                            }
                            .padding(vertical = 5.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiniCheckbox(checked = excluded, enabled = enabled)
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = folder.name,
                            fontSize = 12.0.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        )
                        if (isSystem && !state.options.includeSystemFolders) {
                            Text(
                                "LOCKED",
                                fontSize = 9.0.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoriesCard() {
    SectionCard {
        CardLabel("Destinations")
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            CategoryMapper.categories.forEach { category ->
                Text(
                    text = category,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun StorageCard(state: UiState) {
    SectionCard {
        CardLabel("Storage")
        Spacer(Modifier.height(10.dp))
        DonutChart(state)
    }
}

@Composable
private fun DonutChart(state: UiState) {
    val categorySizes = state.plan.moves
        .groupBy { it.category }
        .mapValues { entry -> entry.value.sumOf { it.entry?.size ?: 0L } }
    val total = categorySizes.values.sum().toFloat()
    if (total <= 0f) return

    val palette = listOf(
        Color(0xFF22D3EE),
        Color(0xFF60A5FA),
        Color(0xFFC084FC),
        Color(0xFFFBBF24),
        Color(0xFF64748B)
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(56.dp)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2f
            var startAngle = -90f
            categorySizes.entries.forEachIndexed { index, entry ->
                val sweep = (entry.value / total) * 360f
                drawArc(
                    color = palette[index % palette.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke)
                )
                startAngle += sweep
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            categorySizes.entries.forEachIndexed { index, entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(palette[index % palette.size], RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${entry.key} · ${formatBytes(entry.value)}",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
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
                if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                    return address.hostAddress
                }
            }
        }
    } catch (_: Exception) {
    }
    return null
}
