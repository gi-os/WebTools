package com.gios.webtools

import com.gios.webtools.data.Download
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadTest {
    @Test
    fun dispositionWins() {
        assertEquals("Ticket 4421.pdf", Download.fileName("attachment; filename=\"Ticket 4421.pdf\"", "https://x.org/dl?id=9", "application/pdf"))
        assertEquals("résumé.pdf", Download.fileName("attachment; filename*=UTF-8''r%C3%A9sum%C3%A9.pdf", "https://x.org/dl", null))
    }

    @Test
    fun urlThenType() {
        assertEquals("brochure.pdf", Download.fileName(null, "https://x.org/files/brochure.pdf?v=2#p", null))
        assertEquals("download.pdf", Download.fileName(null, "https://x.org/", "application/pdf"))
        assertEquals("download", Download.fileName(null, "https://x.org", "application/octet-stream"))
        assertEquals("show.ics", Download.fileName(null, "https://x.org/cal/show", "text/calendar; charset=utf-8"))
    }

    @Test
    fun noPathsNoControlCharacters() {
        assertEquals("a_b_c.txt", Download.fileName("filename=../a/b\\c.txt", "https://x.org/", null))
        assertEquals("download.txt", Download.fileName("filename=\"..\"", "https://x.org/", "text/plain"))
        assertEquals(120, Download.fileName("filename=" + "x".repeat(300), "https://x.org/", null).length)
    }

    @Test
    fun sizes() {
        assertEquals("512 B", Download.sizeLabel(512))
        assertEquals("3 KB", Download.sizeLabel(3 * 1024))
        assertEquals("1.5 MB", Download.sizeLabel(1_572_864))
    }
}
