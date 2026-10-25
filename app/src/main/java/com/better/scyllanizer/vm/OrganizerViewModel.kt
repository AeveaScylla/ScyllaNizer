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

    private val _state = MutableStateFlow(UiState(canUndo = undoStore.hasEntries()))
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
        _state.update { it.copy(rootPath = path, plan = PlanResult(), selectedIds = emptySet(), error = null) }
    }

    fun addExclusion(path: String) {
        _state.update { c -> c.copy(options = c.options.copy(excludedPaths = c.options.excludedPaths + path)) }
    }

    fun removeExclusion(path: String) {
        _state.update { c -> c.copy(options = c.options.copy(excludedPaths = c.options.excludedPaths - path)) }
    }

    fun toggleOption(key: OptionKey) {
        _state.update { current ->
            val o = current.options
            current.copy(options = when (key) {
                OptionKey.RECURSIVE -> o.copy(recursive = !o.recursive)
                OptionKey.SKIP_HIDDEN -> o.copy(skipHidden = !o.skipHidden)
                OptionKey.DATE_SUBFOLDERS -> o.copy(dateSubfolders = !o.dateSubfolders)
                OptionKey.EXT_SUBFOLDERS -> o.copy(extSubfolders = !o.extSubfolders)
                OptionKey.DELETE_EMPTY -> o.copy(deleteEmptyFolders = !o.deleteEmptyFolders)
                OptionKey.DELETE_ZERO -> o.copy(deleteZeroByteFiles = !o.deleteZeroByteFiles)
                OptionKey.DELETE_DUPLICATES -> o.copy(deleteDuplicates = !o.deleteDuplicates)
                OptionKey.INCLUDE_SYSTEM -> o.copy(includeSystemFolders = !o.includeSystemFolders)
                else -> o
            })
        }
    }

    fun toggleShred(enabled: Boolean) {
        _state.update { current -> current.copy(options = current.options.copy(shredFiles = enabled)) }
    }

    fun toggleSelection(id: String) {
        _state.update { current ->
            val newSet = current.selectedIds.toMutableSet()
            if (newSet.contains(id)) newSet.remove(id) else newSet.add(id)
            current.copy(selectedIds = newSet)
        }
    }

    fun toggleSelectAll() {
        _state.update { current ->
            val allIds = current.plan.moves.map { it.uniqueId }.toSet()
            val newSet = if (current.selectedIds.size == allIds.size) emptySet() else allIds
            current.copy(selectedIds = newSet)
        }
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
                    val files = organizer.scan(root, snapshot.options) { msg ->
                        _state.update { it.copy(busyText = msg) }
                    }
                    organizer.buildPlan(root, snapshot.options, files) { msg ->
                        _state.update { it.copy(busyText = msg) }
                    }
                }
            }

            outcome.onSuccess { plan ->
                _state.update {
                    when {
                        plan.moves.isEmpty() -> it.copy(isBusy = false, progress = 0f, plan = plan, error = "Everything is already clean and sorted.")
                        else -> {
                            val initialSelected = plan.moves.map { it.uniqueId }.toSet()
                            it.copy(isBusy = false, progress = 0f, plan = plan, selectedIds = initialSelected, step = Step.PREVIEW, error = null)
                        }
                    }
                }
            }.onFailure { t ->
                _state.update { it.copy(isBusy = false, error = t.message ?: "Scan failed") }
            }
        }
    }

    fun confirmMove() {
        val snapshot = _state.value
        val movesToExecute = snapshot.plan.moves.filter { snapshot.selectedIds.contains(it.uniqueId) }
        
        if (movesToExecute.isEmpty() || snapshot.isBusy) return

        _state.update { it.copy(isBusy = true, busyText = "Executing…", progress = 0f, error = null) }

        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    organizer.execute(movesToExecute, snapshot.options) { done, total ->
                        _state.update { it.copy(progress = if (total == 0) 1f else done.toFloat() / total, busyText = "Processing $done / $total") }
                    }
                }
            }

            outcome.onSuccess { result ->
                withContext(Dispatchers.IO) { undoStore.save(result.undoEntries) }
                _state.update { 
                    it.copy(
                        isBusy = false, 
                        progress = 0f, 
                        plan = PlanResult(), 
                        selectedIds = emptySet(), 
                        result = result, 
                        undoResult = null, 
                        step = Step.RESULT,
                        canUndo = result.undoEntries.isNotEmpty()
                    ) 
                }
            }.onFailure { t ->
                _state.update { it.copy(isBusy = false, error = t.message ?: "Execution failed") }
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
                _state.update { 
                    it.copy(
                        isBusy = false, 
                        progress = 0f, 
                        undoResult = result, 
                        message = "Restored ${result.restored} items",
                        canUndo = false,
                        step = Step.RESULT
                    ) 
                }
            }.onFailure { t ->
                _state.update { it.copy(isBusy = false, error = t.message ?: "Undo failed") }
            }
        }
    }

    fun backToSetup() { _state.update { it.copy(step = Step.SETUP, plan = PlanResult(), selectedIds = emptySet()) } }
    fun reset() { _state.update { it.copy(step = Step.SETUP, plan = PlanResult(), selectedIds = emptySet(), result = null, undoResult = null, progress = 0f) } }
    fun clearError() { _state.update { it.copy(error = null) } }
    fun consumeMessage() { _state.update { it.copy(message = null) } }
}
