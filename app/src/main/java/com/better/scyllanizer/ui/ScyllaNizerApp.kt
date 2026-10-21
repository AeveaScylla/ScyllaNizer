package com.better.scyllanizer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.better.scyllanizer.model.Step
import com.better.scyllanizer.vm.OrganizerViewModel
import kotlinx.coroutines.delay

@Composable
fun ScyllaNizerApp(vm: OrganizerViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.checkPermission()
    }

    BackHandler(enabled = state.step != Step.SETUP) {
        when (state.step) {
            Step.PREVIEW -> vm.backToSetup()
            Step.RESULT -> vm.reset()
            else -> {}
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when (state.step) {
                Step.SETUP -> SetupScreen(
                    state = state,
                    onFolderPicked = vm::onFolderPicked,
                    onToggle = vm::toggleOption,
                    onPreview = vm::startPreview,
                    onAddExclusion = vm::addExclusion,
                    onRemoveExclusion = vm::removeExclusion,
                    onToggleShred = vm::toggleShred
                )
                Step.PREVIEW -> PreviewScreen(
                    state = state,
                    onBack = vm::backToSetup,
                    onConfirm = vm::confirmMove
                )
                Step.RESULT -> ResultScreen(
                    state = state,
                    onUndo = vm::undo,
                    onDone = vm::reset
                )
            }

            if (state.isBusy) {
                BusyOverlay(text = state.busyText, progress = state.progress)
            }
        }
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = vm::clearError,
            title = { Text("Something went wrong") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } },
        )
    }

    LaunchedEffect(state.message) {
        if (state.message != null) { delay(2_500); vm.consumeMessage() }
    }
}
