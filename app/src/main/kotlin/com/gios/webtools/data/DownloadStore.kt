package com.gios.webtools.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.io.File

/** The downloads folder and its index: `files/downloads/` plus `downloads.json`, newest first. */
class DownloadStore(private val context: Context) {
    val dir: File get() = File(context.filesDir, "downloads").also { it.mkdirs() }
    private val index = File(context.filesDir, "downloads.json")
    private val _items = MutableStateFlow<List<Download>>(emptyList())
    val items: StateFlow<List<Download>> = _items

    fun load() {
        val list = runCatching {
            val arr = JSONArray(index.readText())
            (0 until arr.length()).map { Download.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        // A file gone behind our back leaves the list too.
        _items.value = list.filter { File(dir, it.name).exists() }.sortedByDescending { it.at }
    }

    private fun save(list: List<Download>) {
        _items.value = list.sortedByDescending { it.at }
        val arr = JSONArray()
        _items.value.forEach { arr.put(it.toJson()) }
        val tmp = File(context.filesDir, "downloads.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(index)
    }

    fun fileOf(d: Download): File = File(dir, d.name)

    fun add(d: Download) = save(_items.value.filterNot { it.name == d.name } + d)

    fun remove(d: Download) {
        runCatching { fileOf(d).delete() }
        save(_items.value.filterNot { it.name == d.name })
    }
}
