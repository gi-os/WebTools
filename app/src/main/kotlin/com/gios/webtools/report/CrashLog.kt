package com.gios.webtools.report

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Writes an uncaught exception to a file from the dying process, and nothing else. The next
 * healthy launch reads it and files the report. Nothing is sent from a process that is crashing.
 */
object CrashLog {

    private fun file(context: Context) = File(context.filesDir, "last-crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                file(app).writeText(
                    "thread: ${thread.name}\npid: ${android.os.Process.myPid()}\nwhen: ${System.currentTimeMillis()}\n\n$sw",
                )
            }
            previous?.uncaughtException(thread, e)
        }
    }

    /** The last trace, once. Reading it deletes it. */
    fun take(context: Context): String? {
        val f = file(context)
        if (!f.exists()) return null
        val text = runCatching { f.readText() }.getOrNull()
        f.delete()
        return text
    }
}
