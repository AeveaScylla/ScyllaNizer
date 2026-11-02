package com.better.scyllanizer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.better.scyllanizer.model.Step
import com.better.scyllanizer.ui.explorer.ExplorerScreen
import com.better.scyllanizer.vm.OrganizerViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScyllaNizerApp(vm: OrganizerViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

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
        bottomBar = {
            if (state.step == Step.SETUP) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Organizer") },
                        label = { Text("Organizer") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Explore, contentDescription = "Explorer") },
                        label = { Text("Explorer") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
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
                        onUndo = vm::undo
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
            title = { Text("Notice") },
            text = { Text(state.error!!) },
            confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } },
        )
    }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(2_500)
            vm.consumeMessage()
        }
    }
}
