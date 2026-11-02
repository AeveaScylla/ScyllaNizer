package com.better.scyllanizer.ui.explorer

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

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
    val message: String? = null
)

class ExplorerViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(ExplorerUiState())
    val state = _state.asStateFlow()

    init {
        loadDirectory(isLeft = true, path = _state.value.leftPath)
        loadDirectory(isLeft = false, path = _state.value.rightPath)
    }

    fun loadDirectory(isLeft: Boolean, path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) {
                _state.update { it.copy(error = "Invalid directory: $path") }
                return@launch
            }

            val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
            
            _state.update {
                if (isLeft) it.copy(leftPath = path, leftFiles = files, selectedFiles = emptySet())
                else it.copy(rightPath = path, rightFiles = files, selectedFiles = emptySet())
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
        val files = _state.value.selectedFiles.map { File(it) }
        _state.update { it.copy(clipboard = files, isCutMode = false, selectedFiles = emptySet(), message = "Copied ${files.size} items") }
    }

    fun cutSelected() {
        val files = _state.value.selectedFiles.map { File(it) }
        _state.update { it.copy(clipboard = files, isCutMode = true, selectedFiles = emptySet(), message = "Cut ${files.size} items") }
    }

    fun clearClipboard() {
        _state.update { it.copy(clipboard = emptyList(), isCutMode = false) }
    }

    fun pasteInto(isLeft: Boolean) {
        val currentState = _state.value
        if (currentState.clipboard.isEmpty()) return
        
        val targetDir = File(if (isLeft) currentState.leftPath else currentState.rightPath)
        if (!targetDir.isDirectory) return

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isProcessing = true) }
            var success = 0
            
            try {
                for (source in currentState.clipboard) {
                    val target = File(targetDir, source.name)
                    if (source.path == target.path) continue
                    
                    if (currentState.isCutMode) {
                        if (source.renameTo(target)) success++
                    } else {
                        if (source.isDirectory) {
                            source.copyRecursively(target, overwrite = false)
                            success++
                        } else {
                            source.copyTo(target, overwrite = false)
                            success++
                        }
                    }
                }
                
                _state.update { 
                    it.copy(
                        isProcessing = false, 
                        clipboard = if (currentState.isCutMode) emptyList() else currentState.clipboard,
                        message = "Pasted $success items"
                    ) 
                }
                loadDirectory(isLeft, targetDir.path)
                if (currentState.isCutMode) {
                    loadDirectory(!isLeft, if (!isLeft) currentState.leftPath else currentState.rightPath)
                }
            } catch (e: Exception) {
                _state.update { it.copy(isProcessing = false, error = "Paste failed: ${e.message}") }
            }
        }
    }

    fun deleteSelected() {
        val files = _state.value.selectedFiles.map { File(it) }
        if (files.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isProcessing = true) }
            var success = 0
            for (file in files) {
                if (file.deleteRecursively()) success++
            }
            _state.update { 
                it.copy(
                    isProcessing = false, 
                    selectedFiles = emptySet(),
                    message = "Deleted $success items"
                ) 
            }
            val current = _state.value
            loadDirectory(true, current.leftPath)
            loadDirectory(false, current.rightPath)
        }
    }

    fun createFolder(isLeft: Boolean, name: String) {
        val path = if (isLeft) _state.value.leftPath else _state.value.rightPath
        val newDir = File(path, name)
        if (newDir.mkdirs()) {
            loadDirectory(isLeft, path)
            _state.update { it.copy(message = "Folder created") }
        } else {
            _state.update { it.copy(error = "Could not create folder") }
        }
    }

    fun extractZip(file: File, isLeft: Boolean) {
        val destDir = File(file.parentFile, file.nameWithoutExtension)
        destDir.mkdirs()
        
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isProcessing = true) }
            try {
                ZipInputStream(file.inputStream()).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val newFile = File(destDir, entry.name)
                        if (entry.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            newFile.parentFile?.mkdirs()
                            newFile.outputStream().use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                _state.update { it.copy(isProcessing = false, message = "Extracted successfully") }
                loadDirectory(isLeft, if (isLeft) _state.value.leftPath else _state.value.rightPath)
            } catch (e: Exception) {
                _state.update { it.copy(isProcessing = false, error = "Extraction failed: ${e.message}") }
            }
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
    fun clearMessage() { _state.update { it.copy(message = null) } }
}
