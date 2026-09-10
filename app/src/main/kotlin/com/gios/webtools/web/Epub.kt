package com.gios.webtools.web

import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * One article as one EPUB, for the Library. The smallest valid book: `mimetype` first and
 * stored, `META-INF/container.xml`, an OPF with title, author, source and one spine item, an
 * EPUB 3 nav, and the article's XHTML wrapped in a page with the house's plain styling. The
 * Library's parser is lenient, but the file is correct anyway so any reader takes it.
 */
object Epub {
    data class Article(
        val title: String,
        val byline: String,
        val site: String,
        val url: String,
        val lang: String,
        val xhtml: String,
    )

    fun write(article: Article, target: File) {
        val id = UUID.randomUUID().toString()
        val title = article.title.ifBlank { article.site.ifBlank { "Article" } }
        val author = article.byline.ifBlank { article.site }
        val lang = article.lang.ifBlank { "en" }.take(8)
        ZipOutputStream(FileOutputStream(target)).use { zip ->
            // The first entry, uncompressed, exactly this content: that is how a reader knows.
            val mime = "application/epub+zip".toByteArray()
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED; size = mime.size.toLong(); compressedSize = mime.size.toLong()
                crc = CRC32().also { it.update(mime) }.value
            })
            zip.write(mime); zip.closeEntry()
            put(zip, "META-INF/container.xml", CONTAINER)
            put(zip, "OEBPS/content.opf", opf(id, title, author, lang, article.url))
            put(zip, "OEBPS/nav.xhtml", nav(title))
            put(zip, "OEBPS/article.xhtml", page(title, author, article))
        }
    }

    private fun put(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
    }

    fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private const val CONTAINER = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>
"""

    private fun opf(id: String, title: String, author: String, lang: String, url: String) = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="uid">urn:uuid:$id</dc:identifier>
    <dc:title>${esc(title)}</dc:title>
    <dc:creator>${esc(author)}</dc:creator>
    <dc:language>${esc(lang)}</dc:language>
    <dc:source>${esc(url)}</dc:source>
    <meta property="dcterms:modified">${java.time.Instant.now().toString().substringBefore('.')}Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="article" href="article.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine><itemref idref="article"/></spine>
</package>
"""

    private fun nav(title: String) = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>${esc(title)}</title></head>
<body><nav epub:type="toc"><ol><li><a href="article.xhtml">${esc(title)}</a></li></ol></nav></body></html>
"""

    private fun page(title: String, author: String, a: Article) = """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>${esc(title)}</title>
<style>body{font-family:serif;line-height:1.45;margin:1em}img{max-width:100%}pre{white-space:pre-wrap}</style></head>
<body><h1>${esc(title)}</h1><p><em>${esc(author)}</em><br/><a href="${esc(a.url)}">${esc(a.url)}</a></p>
${a.xhtml}
</body></html>
"""
}
