package com.better.scyllanizer.data

import com.better.scyllanizer.model.ActionType
import com.better.scyllanizer.model.ExecuteResult
import com.better.scyllanizer.model.FileEntry
import com.better.scyllanizer.model.MovePlan
import com.better.scyllanizer.model.PlanResult
import com.better.scyllanizer.model.SortOptions
import com.better.scyllanizer.model.UndoEntry
import com.better.scyllanizer.model.UndoResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Calendar
import java.util.Locale

class Organizer {

    fun scan(root: File, options: SortOptions, onProgress: (String) -> Unit = {}): List<FileEntry> {
        val files = ArrayList<FileEntry>()
        
        fun walk(dir: File, parentPath: String) {
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (child.isDirectory) {
                    if (child.name.startsWith(".")) continue
                    if (!options.recursive) continue
                    if (child.name.equals("Android", true) || child.name.equals("LOST.DIR", true) || child.name.equals(".ScyllaTrash", true)) continue
                    if (child.absolutePath in options.excludedPaths) continue
                    val childPath = if (parentPath.isEmpty()) child.name else "$parentPath/${child.name}"
                    walk(child, childPath)
                } else {
                    if (options.skipHidden && child.name.startsWith(".")) continue
                    if (child.name.lowercase(Locale.ROOT) in SYSTEM_NAMES) continue
                    val dot = child.name.lastIndexOf('.')
                    if (dot <= 0 || dot == child.name.length - 1) continue
                    
                    files.add(FileEntry(child, child.length(), child.lastModified(), parentPath))
                    if (files.size % 40 == 0) onProgress("Scanning... ${files.size} files")
                }
            }
        }
        walk(root, "")
        onProgress("Scanning complete. Building plan...")
        return files
    }

    fun buildPlan(root: File, options: SortOptions, files: List<FileEntry>, onProgress: (String) -> Unit = {}): PlanResult {
        val moves = ArrayList<MovePlan>()
        val processedPaths = HashSet<String>()
        var alreadySorted = 0
        var emptyFolders = 0
        var zeroBytes = 0
        var duplicates = 0

        val sizeMap = HashMap<Long, MutableList<FileEntry>>()

        for (file in files) {
            if (options.deleteZeroByteFiles && file.size == 0L) {
                moves.add(MovePlan(file, "Trash", "Trash", null, ActionType.DELETE_0BYTE))
                processedPaths.add(file.file.absolutePath)
                zeroBytes++
                continue
            }

            if (options.deleteDuplicates) {
                sizeMap.getOrPut(file.size) { mutableListOf() }.add(file)
            }

            val segments = folderSegments(file, options)
            val path = segments.joinToString("/")
            if (file.parentPath == path) {
                alreadySorted++
                continue
            }
            val targetDir = File(root, path)
            val targetFile = uniqueTarget(targetDir, file.file)
            moves.add(MovePlan(file, segments.first(), path, targetFile, ActionType.MOVE))
            processedPaths.add(file.file.absolutePath)
        }

        if (options.deleteDuplicates) {
            onProgress("Checking for duplicates...")
            for ((_, sameSizeFiles) in sizeMap) {
                if (sameSizeFiles.size > 1) {
                    val hashMap = HashMap<String, FileEntry>()
                    for (entry in sameSizeFiles) {
                        if (processedPaths.contains(entry.file.absolutePath)) continue
                        val hash = getFileHash(entry.file)
                        if (hash != null) {
                            if (hashMap.containsKey(hash)) {
                                moves.add(MovePlan(entry, "Trash", "Trash", null, ActionType.DELETE_DUPLICATE))
                                processedPaths.add(entry.file.absolutePath)
                                duplicates++
                            } else {
                                hashMap[hash] = entry
                            }
                        }
                    }
                }
            }
        }

        if (options.deleteEmptyFolders) {
            onProgress("Checking for empty folders...")
            val dirs = mutableListOf<File>()
            
            fun collectDirs(dir: File) {
                val children = dir.listFiles() ?: return
                for (child in children) {
                    if (child.isDirectory && !child.name.startsWith(".") && child.name != ".ScyllaTrash" && child.absolutePath !in options.excludedPaths) {
                        collectDirs(child)
                        dirs.add(child)
                    }
                }
            }
            collectDirs(root)

            for (dir in dirs) {
                val children = dir.listFiles() ?: continue
                var isEmpty = true
                for (child in children) {
                    if (child.isFile) {
                        if (!processedPaths.contains(child.absolutePath)) {
                            isEmpty = false
                            break
                        }
                    } else if (child.isDirectory) {
                        val isMarkedForDeletion = moves.any { it.actionType == ActionType.DELETE_EMPTY_FOLDER && it.folderToDelete?.absolutePath == child.absolutePath }
                        if (!isMarkedForDeletion) {
                            isEmpty = false
                            break
                        }
                    }
                }
                if (isEmpty) {
                    moves.add(MovePlan(null, "Trash", "Trash", null, ActionType.DELETE_EMPTY_FOLDER, dir))
                    emptyFolders++
                }
            }
        }

        return PlanResult(
            moves = moves,
            alreadySorted = alreadySorted,
            scanned = files.size,
            emptyFoldersFound = emptyFolders,
            zeroByteFilesFound = zeroBytes,
            duplicateFilesFound = duplicates
        )
    }

    fun execute(plan: List<MovePlan>, options: SortOptions, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): ExecuteResult {
        val undoEntries = ArrayList<UndoEntry>(plan.size)
        val errors = ArrayList<String>()
        var moved = 0
        var deleted = 0
        var failed = 0
        
        val trashDir = File(File(android.os.Environment.getExternalStorageDirectory().absolutePath), ".ScyllaTrash")
        trashDir.mkdirs()

        plan.forEachIndexed { index, item ->
            try {
                when (item.actionType) {
                    ActionType.MOVE -> {
                        val targetDir = item.targetFile!!.parentFile
                        if (targetDir != null && !targetDir.exists()) targetDir.mkdirs()
                        val finalTarget = if (item.targetFile.exists()) uniqueTarget(targetDir!!, item.entry!!.file) else item.targetFile
                        
                        if (moveFile(item.entry!!.file, finalTarget, false)) {
                            moved++
                            undoEntries.add(UndoEntry(item.entry.file.absolutePath, finalTarget.absolutePath, isFolder = false))
                        } else {
                            failed++
                            errors.add("Could not move ${item.entry.name}")
                        }
                    }
                    ActionType.DELETE_0BYTE, ActionType.DELETE_DUPLICATE -> {
                        val file = item.entry!!.file
                        if (options.shredFiles) {
                            if (shredFile(file)) deleted++ else failed++
                        } else {
                            val target = uniqueTarget(trashDir, file)
                            if (moveFile(file, target, false)) {
                                deleted++
                                undoEntries.add(UndoEntry(file.absolutePath, target.absolutePath, isFolder = false))
                            } else failed++
                        }
                    }
                    ActionType.DELETE_EMPTY_FOLDER -> {
                        val folder = item.folderToDelete!!
                        if (folder.delete()) {
                            deleted++
                            undoEntries.add(UndoEntry(folder.absolutePath, "", isFolder = true))
                        } else {
                            failed++
                            errors.add("Could not delete empty folder ${folder.name}")
                        }
                    }
                }
            } catch (t: Throwable) {
                failed++
                errors.add("Action failed on ${item.entry?.name ?: item.folderToDelete?.name}: ${t.message}")
            }
            onProgress(index + 1, plan.size)
        }

        return ExecuteResult(moved, failed, deleted, errors.take(25), undoEntries)
    }

    fun undo(entries: List<UndoEntry>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): UndoResult {
        var restored = 0
        var failed = 0
        entries.asReversed().forEachIndexed { index, entry ->
            try {
                if (entry.isFolder) {
                    val folder = File(entry.originalPath)
                    if (!folder.exists() && folder.mkdirs()) {
                        restored++
                    } else if (folder.exists()) {
                        restored++ // Already exists, count as success
                    } else {
                        failed++
                    }
                } else {
                    val source = File(entry.targetPath)
                    val target = File(entry.originalPath)
                    if (source.exists()) {
                        target.parentFile?.mkdirs()
                        if (moveFile(source, target, false)) restored++ else failed++
                    } else failed++
                }
            } catch (_: Throwable) { failed++ }
            onProgress(index + 1, entries.size)
        }
        return UndoResult(restored, failed)
    }

    private fun getFileHash(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
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
        private val SYSTEM_NAMES = setOf(".nomedia", "desktop.ini", "thumbs.db", "lost.dir", ".ds_store", ".directory")
    }
}
