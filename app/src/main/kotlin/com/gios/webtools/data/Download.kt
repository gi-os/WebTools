package com.gios.webtools.data

import org.json.JSONObject
import java.io.File
import java.net.URLDecoder
import java.text.DateFormat
import java.util.Date

/** One file the engine handed over instead of showing: where it is, what it is, where it came from. */
data class Download(
    val name: String,
    val mime: String,
    val size: Long,
    val from: String,
    val at: Long,
) {
    val isPdf: Boolean get() = mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)
    val isImage: Boolean get() = mime.startsWith("image/")

    /** Whether the engine can show it itself: PDFs through its own viewer, pictures, plain text. */
    val viewable: Boolean get() = isPdf || isImage || mime.startsWith("text/") || mime == "application/json"

    fun detail(): String {
        val kind = when {
            isPdf -> "PDF"
            isImage -> mime.substringAfter('/').uppercase()
            mime.isNotBlank() -> mime.substringAfter('/').take(12).uppercase()
            else -> "FILE"
        }
        return "$kind · ${sizeLabel(size)} · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))}"
    }

    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("mime", mime).put("size", size).put("from", from).put("at", at)

    companion object {
        fun fromJson(o: JSONObject) = Download(
            name = o.getString("name"), mime = o.optString("mime"), size = o.optLong("size"),
            from = o.optString("from"), at = o.optLong("at"),
        )

        fun sizeLabel(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }

        /**
         * The name to save under: the server's `Content-Disposition` filename if it gave one
         * (the RFC 5987 `filename*` form first), else the last path segment of the address, else
         * a name made from the type. Path separators and control characters go; the result is
         * never empty and never longer than 120 characters.
         */
        fun fileName(disposition: String?, url: String, mime: String?): String {
            var name: String? = null
            if (disposition != null) {
                val star = Regex("""filename\*\s*=\s*(?:[^']*'[^']*')?([^;]+)""", RegexOption.IGNORE_CASE).find(disposition)
                val plain = Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE).find(disposition)
                name = star?.groupValues?.get(1)?.trim()?.let { decode(it) }
                    ?: plain?.groupValues?.get(1)?.trim()
            }
            if (name.isNullOrBlank()) {
                // The path only: the host is not a file name, and "https://x.org" has no path.
                val path = url.substringBefore('?').substringBefore('#').substringAfter("://", url).substringAfter('/', "").trimEnd('/')
                val last = path.substringAfterLast('/')
                if (last.isNotBlank()) name = decode(last)
            }
            var clean = (name ?: "download")
                .replace(Regex("""[\\/\p{Cntrl}]"""), "_")
                .trim().trim('.', '_', ' ')
            if (clean.isBlank()) clean = "download"
            if (!clean.contains('.')) extensionFor(mime)?.let { clean += ".$it" }
            return clean.take(120)
        }

        private fun decode(s: String): String = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)

        fun extensionFor(mime: String?): String? = when (mime?.substringBefore(';')?.trim()?.lowercase()) {
            "application/pdf" -> "pdf"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "text/plain" -> "txt"
            "text/calendar" -> "ics"
            "text/csv" -> "csv"
            "application/json" -> "json"
            "application/zip" -> "zip"
            "application/epub+zip" -> "epub"
            "application/vnd.android.package-archive" -> "apk"
            else -> null
        }

        /** `name.pdf` taken → `name (2).pdf`, and so on. */
        fun unique(dir: File, name: String): File {
            var f = File(dir, name)
            if (!f.exists()) return f
            val stem = name.substringBeforeLast('.', name)
            val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
            var n = 2
            while (true) {
                f = File(dir, "$stem ($n)$ext")
                if (!f.exists()) return f
                n++
            }
        }
    }
}
