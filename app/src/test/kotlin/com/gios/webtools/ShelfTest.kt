package com.gios.webtools

import com.gios.webtools.data.Shelf
import com.gios.webtools.data.Tool
import com.gios.webtools.data.ToolKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ShelfTest {
    private fun t(id: String, folder: String = "", used: Long = 0L, added: Long = 0L) =
        Tool(id = id, name = id, kind = ToolKind.SITE, url = "https://$id.com/", origins = listOf("$id.com"), lastUsed = used, added = added, folder = folder)

    @Test
    fun foldersFirstThenLooseTools() {
        val tools = listOf(
            t("weather", used = 50),
            t("axs", folder = "Tickets", used = 10),
            t("dice", folder = "Tickets", used = 30),
            t("mta", used = 40),
        )
        val e = Shelf.entries(tools)
        assertEquals(3, e.size)
        val folder = e[0] as Shelf.Entry.Folder
        assertEquals("Tickets", folder.name)
        // The folder sits where its best tool would: dice at 30 beats mta at 40? No — 40 wins.
        assertEquals(listOf("dice", "axs"), folder.tools.map { it.id })
        assertEquals(listOf("weather", "mta"), e.drop(1).map { (it as Shelf.Entry.Single).tool.id })
    }

    @Test
    fun aFolderIsOnlyANameOnItsTools() {
        val tools = listOf(t("axs", folder = "Tickets"))
        assertEquals(listOf("Tickets"), Shelf.folders(tools))
        assertEquals(listOf("axs"), Shelf.inFolder(tools, "Tickets").map { it.id })
        // Take the name off the last tool and the folder is simply gone.
        assertEquals(emptyList(), Shelf.folders(listOf(t("axs"))))
        assertEquals("axs · dice", Shelf.Entry.Folder("Tickets", listOf(t("axs"), t("dice"))).detail())
    }

    @Test
    fun foldersKeepNames() {
        assertEquals("Tickets", Tool.folderName("  Tickets  "))
        assertEquals("Live music", Tool.folderName("Live   music"))
        assertEquals("", Tool.folderName("   "))
        assertEquals(24, Tool.folderName("x".repeat(40)).length)
    }
}
