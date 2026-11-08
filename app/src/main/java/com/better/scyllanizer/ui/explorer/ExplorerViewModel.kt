package com.better.scyllanizer.ui.explorer

import android.app.Application
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.better.scyllanizer.data.CategoryMapper
import java.util.Locale
import java.io.File
import java.util.ArrayDeque
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class CategoryStorageUsage(val category: String, val bytes: Long)

data class ExplorerUiState(
    val leftPath: String = Environment.getExternalStorageDirectory().path,
    val rightPath: String = Environment.getExternalStorageDirectory().path,
    val leftFiles: List<File> = emptyList(),
    val rightFiles: List<File> = emptyList(),
    val selectedFiles: Set<String> = emptySet(),
    val clipboard: List<File> = emptyList(),
    val isCutMode: Boolean = false,
    val isProcessing: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val searchResults: List<File> = emptyList(),
    val isSearching: Boolean = false,
    val searchScannedCount: Int = 0,
    val searchWasLimited: Boolean = false,
    val storageCategories: List<CategoryStorageUsage> = emptyList(),
    val storageTotalBytes: Long = 0L,
    val storageAvailableBytes: Long = 0L,
    val isStorageScanning: Boolean = false
)

class ExplorerViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(ExplorerUiState())
    val state = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        loadDirectory(isLeft = true, path = _state.value.leftPath)
        loadDirectory(isLeft = false, path = _state.value.rightPath)
        refreshStorageOverview()
    }


    /** Returns the primary shared-storage root plus mounted removable-storage roots. */
    private fun storageRoots(): List<File> {
        val roots = mutableListOf(File(Environment.getExternalStorageDirectory().absolutePath))
        runCatching {
            getApplication<Application>().getExternalFilesDirs(null).filterNotNull().forEach { appFilesDir ->
                var volumeRoot: File? = appFilesDir
                repeat(4) { volumeRoot = volumeRoot?.parentFile }
                volumeRoot?.takeIf { it.isDirectory }?.let { roots.add(it) }
            }
        }
        return roots.mapNotNull { runCatching { it.canonicalFile }.getOrNull() }.distinctBy { it.path }
    }

    /** Searches every accessible shared-storage root, not just the currently open folder. */
    fun searchStorage(rawQuery: String) {
        val query = rawQuery.trim().lowercase(Locale.ROOT)
        searchJob?.cancel()
        if (query.length < 2) {
            _state.update { it.copy(searchResults = emptyList(), isSearching = false, searchScannedCount = 0, searchWasLimited = false) }
            return
        }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSearching = true, searchResults = emptyList(), searchScannedCount = 0, searchWasLimited = false) }
            val queue = ArrayDeque<File>()
            val visited = HashSet<String>()
            val matches = ArrayList<File>()
            var scanned = 0
            val maxMatches = 2000
            storageRoots().forEach { queue.add(it) }
            while (queue.isNotEmpty() && matches.size < maxMatches) {
                currentCoroutineContext().ensureActive()
                val dir = queue.removeFirst()
                val canonical = runCatching { dir.canonicalPath }.getOrNull() ?: continue
                if (!visited.add(canonical)) continue
                val children = runCatching { dir.listFiles()?.toList() }.getOrNull() ?: continue
                for (child in children) {
                    scanned++
                    if (child.name.lowercase(Locale.ROOT).contains(query)) matches.add(child)
                    if (child.isDirectory) {
                        val childCanonical = runCatching { child.canonicalPath }.getOrNull()
                        if (childCanonical != null && childCanonical != canonical && !visited.contains(childCanonical)) queue.add(child)
                    }
                    if (matches.size >= maxMatches) break
                }
                if (scanned % 500 == 0) {
                    _state.update { it.copy(searchScannedCount = scanned) }
                }
            }
            val limited = matches.size >= maxMatches && queue.isNotEmpty()
            val sorted = matches.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
            _state.update { it.copy(searchResults = sorted, isSearching = false, searchScannedCount = scanned, searchWasLimited = limited) }
        }
    }

    fun clearStorageSearch() {
        searchJob?.cancel()
        _state.update { it.copy(searchResults = emptyList(), isSearching = false, searchScannedCount = 0, searchWasLimited = false) }
    }

    /** Calculates real file sizes by category and device storage capacity off the UI thread. */
    fun refreshStorageOverview() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isStorageScanning = true) }
            val primaryRoot = File(Environment.getExternalStorageDirectory().absolutePath)
            val totals = mutableMapOf<String, Long>()
            val queue = ArrayDeque<File>()
            val visited = HashSet<String>()
            storageRoots().forEach { queue.add(it) }
            while (queue.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val dir = queue.removeFirst()
                val canonical = runCatching { dir.canonicalPath }.getOrNull() ?: continue
                if (!visited.add(canonical)) continue
                val children = runCatching { dir.listFiles()?.toList() }.getOrNull() ?: continue
                for (child in children) {
                    if (child.isDirectory) {
                        queue.add(child)
                    } else if (child.isFile) {
                        val category = CategoryMapper.categoryFor(child.extension)
                        totals[category] = (totals[category] ?: 0L) + child.length().coerceAtLeast(0L)
                    }
                }
            }
            val stat = runCatching { StatFs(primaryRoot.absolutePath) }.getOrNull()
            val totalBytes = stat?.totalBytes ?: 0L
            val availableBytes = stat?.availableBytes ?: 0L
            val categories = totals.entries.map { CategoryStorageUsage(it.key, it.value) }
                .sortedByDescending { it.bytes }.take(8)
            _state.update { it.copy(storageCategories = categories, storageTotalBytes = totalBytes, storageAvailableBytes = availableBytes, isStorageScanning = false) }
        }
    }

    fun openSearchResult(file: File, isLeft: Boolean) {
        val destination = if (file.isDirectory) file else file.parentFile
        destination?.let { loadDirectory(isLeft, it.absolutePath) }
    }

    fun loadDirectory(isLeft: Boolean, path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory || !dir.canRead()) {
                _state.update { it.copy(error = "Directory is unavailable or cannot be read: $path") }
                return@launch
            }

            val files = dir.listFiles()
                ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase(java.util.Locale.ROOT) }))
                ?: emptyList()

            _state.update {
                if (isLeft) it.copy(leftPath = dir.absolutePath, leftFiles = files, selectedFiles = emptySet())
                else it.copy(rightPath = dir.absolutePath, rightFiles = files, selectedFiles = emptySet())
            }
        }
    }

    fun toggleSelection(filePath: String) {
        _state.update { current ->
            val newSet = current.selectedFiles.toMutableSet()
            if (newSet.contains(filePath)) newSet.remove(filePath) else newSet.add(filePath)
            current.copy(selectedFiles = newSet)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedFiles = emptySet()) }
    }

    fun copySelected() {
        val files = _state.value.selectedFiles.map { File(it) }.filter { it.exists() }
        _state.update { it.copy(clipboard = files, isCutMode = false, selectedFiles = emptySet(), message = "Copied ${files.size} items") }
    }

    fun cutSelected() {
        val files = _state.value.selectedFiles.map { File(it) }.filter { it.exists() }
        _state.update { it.copy(clipboard = files, isCutMode = true, selectedFiles = emptySet(), message = "Cut ${files.size} items") }
    }

    fun clearClipboard() {
        _state.update { it.copy(clipboard = emptyList(), isCutMode = false) }
    }

    fun pasteInto(isLeft: Boolean) {
        val currentState = _state.value
        if (currentState.clipboard.isEmpty() || currentState.isProcessing) return

        val targetDir = File(if (isLeft) currentState.leftPath else currentState.rightPath)
        if (!targetDir.isDirectory || !targetDir.canWrite()) {
            _state.update { it.copy(error = "The destination folder is not writable") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isProcessing = true, error = null) }
            var success = 0
            val failures = mutableListOf<String>()

            for (source in currentState.clipboard) {
                try {
                    if (!source.exists()) {
                        failures.add("${source.name}: source no longer exists")
                        continue
                    }
                    val sourcePath = source.canonicalPath
                    val destinationPath = targetDir.canonicalPath
                    if (sourcePath == destinationPath || (source.isDirectory && destinationPath.startsWith(sourcePath + File.separator))) {
                        failures.add("${source.name}: cannot paste a folder into itself")
                        continue
                    }

                    val target = File(targetDir, source.name)
                    if (target.exists()) {
                        failures.add("${source.name}: an item with that name already exists")
                        continue
                    }

                    if (currentState.isCutMode && source.renameTo(target)) {
                        success++
                        continue
                    }

                    val copied = if (source.isDirectory) {
                        runCatching { source.copyRecursively(target, overwrite = false) }.getOrDefault(false)
                    } else {
                        runCatching { source.copyTo(target, overwrite = false); true }.getOrDefault(false)
                    }
                    if (!copied) {
                        target.deleteRecursively()
                        failures.add("${source.name}: copy failed")
                        continue
                    }

                    if (currentState.isCutMode && !source.deleteRecursively()) {
                        // Keep the copied destination if source cleanup partially fails.
                        // This may leave two copies, but avoids losing partially deleted data.
                        failures.add("${source.name}: copied, but original cleanup was incomplete; destination was kept")
                        continue
                    }
                    success++
                } catch (e: Exception) {
                    failures.add("${source.name}: ${e.message ?: "operation failed"}")
                }
            }

            _state.update {
                it.copy(
                    isProcessing = false,
                    clipboard = if (currentState.isCutMode && failures.isEmpty()) emptyList() else currentState.clipboard,
                    message = "Pasted $success of ${currentState.clipboard.size} items",
                    error = failures.take(3).takeIf { list -> list.isNotEmpty() }?.joinToString("; ")
                )
            }
            loadDirectory(isLeft, targetDir.path)
            refreshStorageOverview()
            if (currentState.isCutMode) {
                val otherPath = if (isLeft) currentState.rightPath else currentState.leftPath
                loadDirectory(!isLeft, otherPath)
            }
        }
    }

    fun deleteSelected() {
        val files = _state.value.selectedFiles.map { File(it) }.filter { it.exists() }
        if (files.isEmpty() || _state.value.isProcessing) return

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isProcessing = true, error = null) }
            var success = 0
            val failedNames = mutableListOf<String>()
            for (file in files) {
                if (runCatching { file.deleteRecursively() }.getOrDefault(false)) success++
                else failedNames.add(file.name)
            }
            _state.update {
                it.copy(
                    isProcessing = false,
                    selectedFiles = emptySet(),
                    message = "Deleted $success of ${files.size} items",
                    error = failedNames.take(3).takeIf { names -> names.isNotEmpty() }?.joinToString(", ", prefix = "Could not delete: ")
                )
            }
            val current = _state.value
            loadDirectory(true, current.leftPath)
            loadDirectory(false, current.rightPath)
            refreshStorageOverview()
        }
    }

    fun createFolder(isLeft: Boolean, name: String) {
        val safeName = name.trim()
        if (safeName.isEmpty() || safeName == "." || safeName == ".." ||
            safeName.contains('/') || safeName.contains('\\') || File(safeName).name != safeName) {
            _state.update { it.copy(error = "Enter a valid folder name") }
            return
        }

        val path = if (isLeft) _state.value.leftPath else _state.value.rightPath
        val parent = File(path)
        val newDir = File(parent, safeName)
        if (newDir.parentFile?.canonicalPath != parent.canonicalPath) {
            _state.update { it.copy(error = "Invalid folder name") }
        } else if (newDir.exists()) {
            _state.update { it.copy(error = "An item with that name already exists") }
        } else if (newDir.mkdir()) {
            loadDirectory(isLeft, path)
            _state.update { it.copy(message = "Folder created") }
        } else {
            _state.update { it.copy(error = "Could not create folder") }
        }
    }

    fun extractZip(file: File, isLeft: Boolean) {
        if (_state.value.isProcessing) return
        val parent = file.parentFile ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isProcessing = true, error = null) }
            var destDir: File? = null
            try {
                if (!file.isFile || !file.canRead()) throw IllegalArgumentException("Archive cannot be read")
                val baseName = file.nameWithoutExtension.ifBlank { "Extracted" }
                var candidate = File(parent, baseName)
                var suffix = 1
                while (candidate.exists()) {
                    candidate = File(parent, "$baseName ($suffix)")
                    suffix++
                }
                if (!candidate.mkdir()) throw IllegalStateException("Could not create extraction folder")
                destDir = candidate
                val canonicalRoot = candidate.canonicalFile
                var extractedCount = 0

                ZipInputStream(file.inputStream().buffered()).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val normalizedName = entry.name.replace('\\', '/')
                        val segments = normalizedName.split('/')
                        if (normalizedName.startsWith('/') || segments.any { it == ".." || it == "." }) {
                            throw SecurityException("Archive contains an unsafe path")
                        }
                        val cleanName = normalizedName.trimEnd('/')
                        if (cleanName.isNotBlank()) {
                            val output = File(canonicalRoot, cleanName).canonicalFile
                            val rootPrefix = canonicalRoot.path + File.separator
                            if (output.path != canonicalRoot.path && !output.path.startsWith(rootPrefix)) {
                                throw SecurityException("Archive entry escapes the extraction folder")
                            }
                            if (entry.isDirectory) {
                                if (!output.exists() && !output.mkdirs()) throw IllegalStateException("Could not create ${entry.name}")
                            } else {
                                output.parentFile?.mkdirs()
                                if (output.exists()) throw IllegalStateException("Archive contains a duplicate path: ${entry.name}")
                                output.outputStream().buffered().use { out -> zis.copyTo(out) }
                                extractedCount++
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                _state.update { it.copy(isProcessing = false, message = "Extracted $extractedCount files") }
            } catch (e: Exception) {
                destDir?.deleteRecursively()
                _state.update { it.copy(isProcessing = false, error = "Extraction failed: ${e.message ?: "invalid archive"}") }
            }
            loadDirectory(isLeft, if (isLeft) _state.value.leftPath else _state.value.rightPath)
            refreshStorageOverview()
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
    fun clearMessage() { _state.update { it.copy(message = null) } }
}
