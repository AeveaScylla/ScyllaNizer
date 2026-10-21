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

data class MovePlan(
    val entry: FileEntry,
    val category: String,
    val folderPath: String,
    val targetFile: File,
) {
    val willRename: Boolean get() = targetFile.name != entry.name
}

data class PlanResult(
    val moves: List<MovePlan> = emptyList(),
    val alreadySorted: Int = 0,
    val scanned: Int = 0,
)

data class UndoEntry(
    val originalPath: String,
    val targetPath: String,
)

data class ExecuteResult(
    val moved: Int = 0,
    val failed: Int = 0,
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
    val shredFiles: Boolean = false
)

enum class Step { SETUP, PREVIEW, RESULT }

enum class OptionKey { RECURSIVE, SKIP_HIDDEN, DATE_SUBFOLDERS, EXT_SUBFOLDERS }

data class UiState(
    val step: Step = Step.SETUP,
    val hasPermission: Boolean = false,
    val rootPath: String? = null,
    val options: SortOptions = SortOptions(),
    val isBusy: Boolean = false,
    val busyText: String = "",
    val progress: Float = 0f,
    val plan: PlanResult = PlanResult(),
    val result: ExecuteResult? = null,
    val undoResult: UndoResult? = null,
    val error: String? = null,
    val message: String? = null,
)
