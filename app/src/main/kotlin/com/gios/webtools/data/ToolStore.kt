package com.gios.webtools.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The list of tools, one JSON file in the app's private storage. Small enough that a whole
 * rewrite on every change is the simplest correct thing.
 *
 * Bundles live beside it at `files/tools/<id>/`; a site's saved copy is `files/tools/<id>/copy.mht`.
 */
class ToolStore(private val context: Context) {

    private val file = File(context.filesDir, "tools.json")
    private val _tools = MutableStateFlow<List<Tool>>(emptyList())
    val tools: StateFlow<List<Tool>> = _tools

    val toolsDir: File get() = File(context.filesDir, "tools").also { it.mkdirs() }
    fun dirFor(id: String): File = File(toolsDir, id).also { it.mkdirs() }
    fun snapshotFile(id: String): File = File(dirFor(id), "copy.mht")

    fun load() {
        _tools.value = runCatching {
            val arr = JSONArray(file.readText())
            List(arr.length()) { Tool.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun save(list: List<Tool>) {
        _tools.value = list
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        val tmp = File(context.filesDir, "tools.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }

    fun get(id: String): Tool? = _tools.value.firstOrNull { it.id == id }

    /** Adds or replaces by id. */
    fun put(tool: Tool) {
        val rest = _tools.value.filterNot { it.id == tool.id }
        save(rest + tool)
    }

    fun update(id: String, f: (Tool) -> Tool) {
        val cur = get(id) ?: return
        put(f(cur))
    }

    fun remove(id: String) {
        save(_tools.value.filterNot { it.id == id })
        File(toolsDir, id).deleteRecursively()
    }

    fun touch(id: String, now: Long = System.currentTimeMillis()) = update(id) { it.copy(lastUsed = now) }

    /** Most recently used first; never used sink to the bottom in the order they were added. */
    fun ordered(): List<Tool> = _tools.value.sortedWith(
        compareByDescending<Tool> { it.lastUsed }.thenBy { it.added },
    )

    /**
     * Copies the bundles shipped in `assets/builtin/<id>/` into the tools directory the first
     * time, and refreshes their files on every launch so an app update reaches them. Their list
     * entries are only created once, so a removed built-in stays removed.
     */
    fun installBuiltIns() {
        val am = context.assets
        val ids = am.list("builtin")?.toList().orEmpty()
        val marker = File(context.filesDir, "builtins.installed")
        val already = runCatching { marker.readLines().toSet() }.getOrDefault(emptySet())
        val now = System.currentTimeMillis()
        for (id in ids) {
            val dest = dirFor(id)
            copyAssetDir(am, "builtin/$id", dest)
            if (id !in already) {
                val meta = runCatching { JSONObject(File(dest, "tool.json").readText()) }.getOrNull()
                val name = meta?.optString("name")?.ifEmpty { null } ?: id
                if (get(id) == null) {
                    put(
                        Tool(
                            id = id,
                            name = name,
                            kind = ToolKind.BUNDLE,
                            url = "https://$id.${Tool.BUNDLE_DOMAIN}/index.html",
                            origins = listOf("$id.${Tool.BUNDLE_DOMAIN}"),
                            added = now,
                            builtIn = true,
                        ),
                    )
                }
            }
        }
        marker.writeText((already + ids).joinToString("\n"))
    }

    private fun copyAssetDir(am: android.content.res.AssetManager, path: String, dest: File) {
        val children = am.list(path).orEmpty()
        if (children.isEmpty()) {
            // A file.
            am.open(path).use { input -> dest.outputStream().use { input.copyTo(it) } }
            return
        }
        dest.mkdirs()
        for (c in children) copyAssetDir(am, "$path/$c", File(dest, c))
    }
}
