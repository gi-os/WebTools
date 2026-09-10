package com.gios.webtools.web

import android.os.Handler
import android.os.Looper
import com.gios.webtools.data.Download
import com.gios.webtools.data.DownloadStore
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.io.FileOutputStream

/**
 * Saves what the engine will not show. GeckoView hands a response it cannot render (a PDF link,
 * an .ics, a picture served as an attachment) to `onExternalResponse` with the body still
 * open; this reads it into `files/downloads/` on a plain thread and reports on the main one.
 * One at a time is plenty on this phone.
 */
class Downloader(private val store: DownloadStore) {
    interface Listener {
        fun onProgress(name: String, bytes: Long, total: Long)
        fun onDone(download: Download)
        fun onFailed(name: String, why: String)
    }

    private val main = Handler(Looper.getMainLooper())

    fun start(response: WebResponse, listener: Listener) {
        val headers = response.headers.mapKeys { it.key.lowercase() }
        val mime = headers["content-type"]?.substringBefore(';')?.trim().orEmpty()
        val total = headers["content-length"]?.trim()?.toLongOrNull() ?: -1L
        val name = Download.fileName(headers["content-disposition"], response.uri, mime)
        val body = response.body
        if (body == null) { listener.onFailed(name, "the server sent nothing"); return }
        val target: File = Download.unique(store.dir, name)
        Thread({
            var written = 0L
            val ok = runCatching {
                body.use { input ->
                    FileOutputStream(target).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var last = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            written += n
                            if (written - last > 256 * 1024) {
                                last = written
                                main.post { listener.onProgress(target.name, written, total) }
                            }
                        }
                    }
                }
            }
            main.post {
                if (ok.isSuccess) {
                    val d = Download(
                        name = target.name, mime = mime, size = target.length(),
                        from = response.uri, at = System.currentTimeMillis(),
                    )
                    store.add(d)
                    listener.onDone(d)
                } else {
                    runCatching { target.delete() }
                    listener.onFailed(target.name, ok.exceptionOrNull()?.message ?: "could not save it")
                }
            }
        }, "download").start()
    }
}
