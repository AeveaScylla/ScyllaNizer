package com.better.scyllanizer.model

import java.io.File

data class FileEntry(
    val file: File,
    val size: Long,
    val lastModified: Long,
    val parentPath: String,
) {
    val name: String get() = file.name
    val extension: String get() = file.extension.lowercase()
}

enum class ActionType { MOVE, DELETE_0BYTE, DELETE_DUPLICATE, DELETE_EMPTY_FOLDER }

data class MovePlan(
    val entry: FileEntry?,
    val category: String,
    val folderPath: String,
    val targetFile: File?,
    val actionType: ActionType = ActionType.MOVE,
    val folderToDelete: File? = null
) {
    val willRename: Boolean get() = targetFile != null && entry != null && targetFile.name != entry.name
    val uniqueId: String get() = entry?.file?.absolutePath ?: folderToDelete?.absolutePath ?: ""
}

data class PlanResult(
    val moves: List<MovePlan> = emptyList(),
    val alreadySorted: Int = 0,
    val scanned: Int = 0,
    val emptyFoldersFound: Int = 0,
    val zeroByteFilesFound: Int = 0,
    val duplicateFilesFound: Int = 0
)

data class UndoEntry(
    val originalPath: String,
    val targetPath: String,
    val isFolder: Boolean = false
)

data class ExecuteResult(
    val moved: Int = 0,
    val failed: Int = 0,
    val deleted: Int = 0,
    val errors: List<String> = emptyList(),
    val undoEntries: List<UndoEntry> = emptyList(),
)

data class UndoResult(val restored: Int = 0, val failed: Int = 0)

data class SortOptions(
    val recursive: Boolean = true,
    val skipHidden: Boolean = true,
    val dateSubfolders: Boolean = false,
    val extSubfolders: Boolean = false,
    val excludedPaths: Set<String> = emptySet(),
    val shredFiles: Boolean = false,
    val deleteEmptyFolders: Boolean = false,
    val deleteZeroByteFiles: Boolean = false,
    val deleteDuplicates: Boolean = false,
    val includeSystemFolders: Boolean = false // NEW
)

enum class Step { SETUP, PREVIEW, RESULT }

enum class OptionKey { 
    RECURSIVE, SKIP_HIDDEN, DATE_SUBFOLDERS, EXT_SUBFOLDERS, 
    DELETE_EMPTY, DELETE_ZERO, DELETE_DUPLICATES, INCLUDE_SYSTEM // NEW
}

data class UiState(
    val step: Step = Step.SETUP,
    val hasPermission: Boolean = false,
    val rootPath: String? = null,
    val options: SortOptions = SortOptions(),
    val isBusy: Boolean = false,
    val busyText: String = "",
    val progress: Float = 0f,
    val plan: PlanResult = PlanResult(),
    val selectedIds: Set<String> = emptySet(),
    val result: ExecuteResult? = null,
    val undoResult: UndoResult? = null,
    val canUndo: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)
