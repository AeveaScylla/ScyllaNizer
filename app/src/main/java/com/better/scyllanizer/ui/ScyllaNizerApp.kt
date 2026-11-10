package com.better.scyllanizer.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.better.scyllanizer.data.WifiDropService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.better.scyllanizer.model.Step
import com.better.scyllanizer.ui.explorer.ExplorerScreen
import com.better.scyllanizer.ui.theme.FileFlowTheme
import com.better.scyllanizer.vm.OrganizerViewModel
import kotlinx.coroutines.delay

@Composable
fun ScyllaNizerApp(vm: OrganizerViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val wifiServerActive by WifiDropService.isRunning.collectAsState()
    var wifiServerIp by remember { mutableStateOf<String?>(null) }

    val toggleWifiServer: (Boolean) -> Unit = { enabled ->
        if (enabled && state.rootPath != null) {
            wifiServerIp = getLocalIpAddress()
            val intent = Intent(context, WifiDropService::class.java).apply {
                action = WifiDropService.ACTION_START
                putExtra(WifiDropService.EXTRA_ROOT_PATH, state.rootPath)
            }
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(Intent(context, WifiDropService::class.java).apply {
                action = WifiDropService.ACTION_STOP
            })
            wifiServerIp = null
        }
    }

    FileFlowTheme(mode = state.themeMode) {
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            vm.checkPermission()
        }

        BackHandler(enabled = state.step != Step.SETUP && selectedTab == 0) {
            when (state.step) {
                Step.PREVIEW -> vm.backToSetup()
                Step.RESULT -> vm.reset()
                else -> {}
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            floatingActionButton = {},
            bottomBar = {
                if (state.step == Step.SETUP) {
                    BottomTabs(selected = selectedTab, onSelect = { selectedTab = it })
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (state.step == Step.SETUP) {
                    if (selectedTab == 0) {
                        SetupScreen(
                            state = state,
                            onFolderPicked = vm::onFolderPicked,
                            onToggle = vm::toggleOption,
                            onPreview = vm::startPreview,
                            onAddExclusion = vm::addExclusion,
                            onRemoveExclusion = vm::removeExclusion,
                            onToggleShred = vm::toggleShred,
                            onUndo = vm::undo,
                            onToggleTheme = vm::toggleTheme,
                            wifiServerActive = wifiServerActive,
                            wifiServerIp = wifiServerIp,
                            onToggleWifiServer = toggleWifiServer
                        )
                    } else {
                        ExplorerScreen()
                    }
                } else {
                    when (state.step) {
                        Step.PREVIEW -> PreviewScreen(
                            state = state,
                            onBack = vm::backToSetup,
                            onConfirm = vm::confirmMove,
                            onToggleSelection = vm::toggleSelection,
                            onToggleSelectAll = vm::toggleSelectAll
                        )
                        Step.RESULT -> ResultScreen(
                            state = state,
                            onUndo = vm::undo,
                            onDone = vm::reset
                        )
                        else -> {}
                    }
                }

                if (state.isBusy) {
                    BusyOverlay(text = state.busyText, progress = state.progress)
                }
            }
        }

        if (state.error != null) {
            AlertDialog(
                onDismissRequest = vm::clearError,
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Notice", fontSize = 16.0.sp, fontWeight = FontWeight.Bold) },
                text = { Text(state.error!!, fontSize = 12.5.sp, lineHeight = 17.sp) },
                confirmButton = {
                    TextButton(onClick = vm::clearError) {
                        Text("OK", fontSize = 13.0.sp, fontWeight = FontWeight.Bold)
                    }
                },
            )
        }

        LaunchedEffect(state.message) {
            if (state.message != null) {
                delay(2_500)
                vm.consumeMessage()
            }
        }
    }
}

@Composable
private fun BottomTabs(selected: Int, onSelect: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .height(52.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabItem(
                    label = "Organizer",
                    icon = Icons.Default.Folder,
                    active = selected == 0,
                    modifier = Modifier.weight(1f)
                ) { onSelect(0) }
                TabItem(
                    label = "Explorer",
                    icon = Icons.Default.Explore,
                    active = selected == 1,
                    modifier = Modifier.weight(1f)
                ) { onSelect(1) }
            }
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    icon: ImageVector,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = color
        )
    }
}
