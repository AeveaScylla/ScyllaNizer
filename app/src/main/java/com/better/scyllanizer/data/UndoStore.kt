package com.better.scyllanizer.data

import android.content.Context
import com.better.scyllanizer.model.UndoEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class UndoStore(context: Context) {
    private val file = File(context.filesDir, "undo_log.json")

    fun save(entries: List<UndoEntry>) {
        if (entries.isEmpty()) { clear(); return }
        try {
            val array = JSONArray()
            entries.forEach { entry ->
                array.put(JSONObject().apply {
                    put("original", entry.originalPath)
                    put("target", entry.targetPath)
                })
            }
            file.writeText(array.toString())
        } catch (_: Throwable) {}
    }

    fun load(): List<UndoEntry> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val original = obj.optString("original")
                    val target = obj.optString("target")
                    if (original.isNotBlank() && target.isNotBlank()) add(UndoEntry(original, target))
                }
            }
        } catch (_: Throwable) { emptyList() }
    }

    fun clear() { runCatching { file.delete() } }
}
