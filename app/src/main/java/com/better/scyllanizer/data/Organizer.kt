package com.better.scyllanizer.data

import com.better.scyllanizer.model.ExecuteResult
import com.better.scyllanizer.model.FileEntry
import com.better.scyllanizer.model.MovePlan
import com.better.scyllanizer.model.PlanResult
import com.better.scyllanizer.model.SortOptions
import com.better.scyllanizer.model.UndoEntry
import com.better.scyllanizer.model.UndoResult
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale

class Organizer {

    fun scan(root: File, options: SortOptions, onProgress: (Int) -> Unit = {}): List<FileEntry> {
        val files = ArrayList<FileEntry>()
        fun walk(dir: File, parentPath: String) {
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (child.isDirectory) {
                    if (child.name.startsWith(".")) continue
                    if (!options.recursive) continue
                    if (child.name.equals("Android", true) || child.name.equals("LOST.DIR", true)) continue
                    if (child.absolutePath in options.excludedPaths) continue
                    val childPath = if (parentPath.isEmpty()) child.name else "$parentPath/${child.name}"
                    walk(child, childPath)
                } else {
                    if (options.skipHidden && child.name.startsWith(".")) continue
                    if (child.name.lowercase(Locale.ROOT) in SYSTEM_NAMES) continue
                    val dot = child.name.lastIndexOf('.')
                    if (dot <= 0 || dot == child.name.length - 1) continue
                    files.add(FileEntry(child, child.length(), child.lastModified(), parentPath))
                    if (files.size % PROGRESS_EVERY == 0) onProgress(files.size)
                }
            }
        }
        walk(root, "")
        onProgress(files.size)
        return files
    }

    fun buildPlan(root: File, options: SortOptions, files: List<FileEntry>): PlanResult {
        val moves = ArrayList<MovePlan>(files.size)
        var alreadySorted = 0
        for (file in files) {
            val segments = folderSegments(file, options)
            val path = segments.joinToString("/")
            if (file.parentPath == path) { alreadySorted++; continue }
            val targetDir = File(root, path)
            val targetFile = uniqueTarget(targetDir, file.file)
            moves.add(MovePlan(file, segments.first(), path, targetFile))
        }
        return PlanResult(moves, alreadySorted, files.size)
    }

    fun execute(plan: List<MovePlan>, options: SortOptions, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): ExecuteResult {
        val undoEntries = ArrayList<UndoEntry>(plan.size)
        val errors = ArrayList<String>()
        var moved = 0
        var failed = 0

        plan.forEachIndexed { index, item ->
            try {
                val targetDir = item.targetFile.parentFile
                if (targetDir != null && !targetDir.exists()) targetDir.mkdirs()
                val finalTarget = if (item.targetFile.exists()) uniqueTarget(targetDir!!, item.entry.file) else item.targetFile
                if (moveFile(item.entry.file, finalTarget, options.shredFiles)) {
                    moved++
                    undoEntries.add(UndoEntry(item.entry.file.absolutePath, finalTarget.absolutePath))
                } else {
                    failed++
                    errors.add("Could not move ${item.entry.name}")
                }
            } catch (t: Throwable) {
                failed++
                errors.add("${item.entry.name}: ${t.message ?: "unknown error"}")
            }
            onProgress(index + 1, plan.size)
        }
        return ExecuteResult(moved, failed, errors.take(25), undoEntries)
    }

    fun undo(entries: List<UndoEntry>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): UndoResult {
        var restored = 0
        var failed = 0
        entries.asReversed().forEachIndexed { index, entry ->
            try {
                val source = File(entry.targetPath)
                val target = File(entry.originalPath)
                if (source.exists()) {
                    target.parentFile?.mkdirs()
                    if (moveFile(source, target, false)) restored++ else failed++
                } else failed++
            } catch (_: Throwable) { failed++ }
            onProgress(index + 1, entries.size)
        }
        return UndoResult(restored, failed)
    }

    fun shredFile(file: File): Boolean {
        if (!file.exists()) return true
        return try {
            val length = file.length()
            if (length > 0) {
                FileOutputStream(file).use { fos ->
                    val random = java.util.Random()
                    var remaining = length
                    val buffer = ByteArray(4096)
                    while (remaining > 0) {
                        random.nextBytes(buffer)
                        val toWrite = if (remaining < buffer.size) remaining.toInt() else buffer.size
                        fos.write(buffer, 0, toWrite)
                        remaining -= toWrite
                    }
                    fos.flush()
                }
            }
            file.delete()
        } catch (e: Exception) { false }
    }

    private fun folderSegments(file: FileEntry, options: SortOptions): List<String> {
        val segments = ArrayList<String>(3)
        segments.add(CategoryMapper.categoryFor(file.extension))
        if (options.extSubfolders && file.extension.isNotEmpty()) segments.add(file.extension.uppercase(Locale.ROOT))
        if (options.dateSubfolders && file.lastModified > 0L) {
            val cal = Calendar.getInstance().apply { timeInMillis = file.lastModified }
            segments.add(String.format(Locale.ROOT, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1))
        }
        return segments
    }

    private fun uniqueTarget(dir: File, source: File): File {
        var target = File(dir, source.name)
        if (!target.exists()) return target
        val base = source.nameWithoutExtension
        val ext = source.extension
        val suffix = if (ext.isEmpty()) "" else ".$ext"
        var counter = 1
        while (target.exists()) {
            target = File(dir, "$base ($counter)$suffix")
            counter++
        }
        return target
    }

    private fun moveFile(source: File, target: File, shredSource: Boolean): Boolean {
        if (source.renameTo(target)) return true
        return try {
            source.copyTo(target, overwrite = false)
            if (shredSource) shredFile(source) else source.delete()
            true
        } catch (e: Exception) {
            if (target.exists()) target.delete()
            false
        }
    }

    companion object {
        private const val PROGRESS_EVERY = 40
        private val SYSTEM_NAMES = setOf(".nomedia", "desktop.ini", "thumbs.db", "lost.dir", ".ds_store", ".directory")
    }
}
