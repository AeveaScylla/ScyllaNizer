package com.better.scyllanizer.vm

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.better.scyllanizer.data.Organizer
import com.better.scyllanizer.data.StorageAccess
import com.better.scyllanizer.data.UndoStore
import com.better.scyllanizer.model.OptionKey
import com.better.scyllanizer.model.PlanResult
import com.better.scyllanizer.model.Step
import com.better.scyllanizer.model.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class OrganizerViewModel(app: Application) : AndroidViewModel(app) {

    private val organizer = Organizer()
    private val undoStore = UndoStore(app)
    private val prefs = app.getSharedPreferences("scyllanizer_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        checkPermission()
        val savedPath = prefs.getString("root_path", null)
        if (savedPath != null && File(savedPath).exists()) {
            _state.update { it.copy(rootPath = savedPath) }
        } else {
            _state.update { it.copy(rootPath = android.os.Environment.getExternalStorageDirectory().absolutePath) }
        }
    }

    fun checkPermission() {
        _state.update { it.copy(hasPermission = StorageAccess.hasAllFilesAccess(getApplication())) }
    }

    fun onFolderPicked(path: String) {
        prefs.edit().putString("root_path", path).apply()
        _state.update { it.copy(rootPath = path, plan = PlanResult(), error = null) }
    }

    fun addExclusion(path: String) {
        _state.update { c -> c.copy(options = c.options.copy(excludedPaths = c.options.excludedPaths + path)) }
    }

    fun removeExclusion(path: String) {
        _state.update { c -> c.copy(options = c.options.copy(excludedPaths = c.options.excludedPaths - path)) }
    }

    fun toggleOption(key: OptionKey) {
        _state.update { current ->
            val options = current.options
            current.copy(options = when (key) {
                OptionKey.RECURSIVE -> options.copy(recursive = !options.recursive)
                OptionKey.SKIP_HIDDEN -> options.copy(skipHidden = !options.skipHidden)
                OptionKey.DATE_SUBFOLDERS -> options.copy(dateSubfolders = !options.dateSubfolders)
                OptionKey.EXT_SUBFOLDERS -> options.copy(extSubfolders = !options.extSubfolders)
            })
        }
    }

    fun toggleShred(enabled: Boolean) {
        _state.update { current -> current.copy(options = current.options.copy(shredFiles = enabled)) }
    }

    fun startPreview() {
        val snapshot = _state.value
        val rootPath = snapshot.rootPath ?: return
        if (snapshot.isBusy || !snapshot.hasPermission) return
        val root = File(rootPath)
        if (!root.exists() || !root.isDirectory) {
            _state.update { it.copy(error = "Folder does not exist or is not accessible.") }
            return
        }

        _state.update { it.copy(isBusy = true, busyText = "Scanning…", progress = 0f, error = null) }

        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val files = organizer.scan(root, snapshot.options) { count ->
                        _state.update { it.copy(busyText = "Scanning… $count files") }
                    }
                    organizer.buildPlan(root, snapshot.options, files)
                }
            }

            outcome.onSuccess { plan ->
                _state.update {
                    when {
                        plan.moves.isEmpty() -> it.copy(isBusy = false, progress = 0f, plan = plan, error = if (plan.scanned == 0) "No sortable files found." else "Everything is already sorted.")
                        else -> it.copy(isBusy = false, progress = 0f, plan = plan, step = Step.PREVIEW, error = null)
                    }
                }
            }.onFailure { t ->
                _state.update { it.copy(isBusy = false, error = t.message ?: "Scan failed") }
            }
        }
    }

    fun confirmMove() {
        val snapshot = _state.value
        val moves = snapshot.plan.moves
        if (moves.isEmpty() || snapshot.isBusy) return

        _state.update { it.copy(isBusy = true, busyText = "Moving…", progress = 0f, error = null) }

        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    organizer.execute(moves, snapshot.options) { done, total ->
                        _state.update { it.copy(progress = if (total == 0) 1f else done.toFloat() / total, busyText = "Moving $done / $total") }
                    }
                }
            }

            outcome.onSuccess { result ->
                withContext(Dispatchers.IO) { undoStore.save(result.undoEntries) }
                _state.update { it.copy(isBusy = false, progress = 0f, plan = PlanResult(), result = result, undoResult = null, step = Step.RESULT) }
            }.onFailure { t ->
                _state.update { it.copy(isBusy = false, error = t.message ?: "Move failed") }
            }
        }
    }

    fun undo() {
        val entries = undoStore.load()
        if (entries.isEmpty() || _state.value.isBusy) return

        _state.update { it.copy(isBusy = true, busyText = "Restoring…", progress = 0f) }

        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    organizer.undo(entries) { done, total ->
                        _state.update { it.copy(progress = if (total == 0) 1f else done.toFloat() / total, busyText = "Restoring $done / $total") }
                    }
                }
            }

            outcome.onSuccess { result ->
                withContext(Dispatchers.IO) { undoStore.clear() }
                _state.update { it.copy(isBusy = false, progress = 0f, undoResult = result, message = "Restored ${result.restored} files") }
            }.onFailure { t ->
                _state.update { it.copy(isBusy = false, error = t.message ?: "Undo failed") }
            }
        }
    }

    fun backToSetup() { _state.update { it.copy(step = Step.SETUP, plan = PlanResult()) } }
    fun reset() { _state.update { it.copy(step = Step.SETUP, plan = PlanResult(), result = null, undoResult = null, progress = 0f) } }
    fun clearError() { _state.update { it.copy(error = null) } }
    fun consumeMessage() { _state.update { it.copy(message = null) } }
}