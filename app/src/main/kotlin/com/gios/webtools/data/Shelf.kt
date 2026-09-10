package com.gios.webtools.data

/**
 * What the shelf shows: folders first, then the tools that are in none.
 *
 * A folder is a name written on its tools, so this is a group-by and nothing else — no folder
 * record to keep in step with the tools, no empty folder left behind when the last tool leaves
 * it, and nothing to migrate. The cost is that a folder cannot exist before its first tool,
 * which is the right trade for a shelf of six things.
 */
object Shelf {
    sealed interface Entry {
        val name: String

        data class Folder(override val name: String, val tools: List<Tool>) : Entry {
            val count: Int get() = tools.size
            /** The names inside, in order, for the folder's own second line. */
            fun detail(): String = tools.joinToString(" · ") { it.name }
        }

        data class Single(val tool: Tool) : Entry {
            override val name: String get() = tool.name
        }
    }

    /**
     * Folders in the order their most recently used tool was used, each holding its own tools in
     * that same order; then the loose tools. A folder rises when anything in it is opened, which
     * is what makes "Tickets" sit at the top the week of a show and sink again afterwards.
     */
    fun entries(tools: List<Tool>): List<Entry> {
        val order = compareByDescending<Tool> { it.lastUsed }.thenBy { it.added }
        val sorted = tools.sortedWith(order)
        val folders = LinkedHashMap<String, MutableList<Tool>>()
        val loose = mutableListOf<Tool>()
        for (t in sorted) {
            val f = t.folder.trim()
            if (f.isEmpty()) loose += t else folders.getOrPut(f) { mutableListOf() } += t
        }
        val out = mutableListOf<Entry>()
        folders.forEach { (name, inside) -> out += Entry.Folder(name, inside) }
        loose.forEach { out += Entry.Single(it) }
        return out
    }

    /** Every folder that exists, most recently used first. What the "put in a folder" list shows. */
    fun folders(tools: List<Tool>): List<String> =
        entries(tools).filterIsInstance<Entry.Folder>().map { it.name }

    /** The tools in one folder, in the shelf's order. */
    fun inFolder(tools: List<Tool>, folder: String): List<Tool> =
        entries(tools).filterIsInstance<Entry.Folder>().firstOrNull { it.name == folder }?.tools.orEmpty()
}
