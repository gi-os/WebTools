package com.gios.webtools.web

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Where the block list comes from and how it stays current.
 *
 * `assets/adhosts.txt` is Peter Lowe's list as shipped; `assets/appbanners.txt` is ours. Once a
 * week, on a launch with network, the ad list is fetched again into `files/adhosts.txt` and used
 * from there. A fetch that fails, or comes back implausibly small, changes nothing.
 */
object BlockLists {

    private const val SOURCE = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=nohtml&showintro=0&mimetype=plaintext"
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    private const val MIN_LINES = 1000

    @Volatile private var cached: BlockList? = null

    fun get(context: Context): BlockList {
        cached?.let { return it }
        val updated = File(context.filesDir, "adhosts.txt")
        val ads = if (updated.exists()) updated.readText() else context.assets.open("adhosts.txt").bufferedReader().readText()
        val banners = context.assets.open("appbanners.txt").bufferedReader().readText()
        return BlockList.of(ads, banners).also { cached = it }
    }

    /** Call off the main thread. Returns true when a fresh list was written. */
    fun refreshIfStale(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val target = File(context.filesDir, "adhosts.txt")
        if (target.exists() && now - target.lastModified() < WEEK_MS) return false
        return runCatching {
            val conn = URL(SOURCE).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "WebTools/1.1 (Light Phone III)")
            val text = conn.inputStream.bufferedReader().readText()
            if (text.lineSequence().count { it.isNotBlank() } < MIN_LINES) return false
            val tmp = File(context.filesDir, "adhosts.txt.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(target)) return false
            cached = null
            true
        }.getOrDefault(false)
    }
}
