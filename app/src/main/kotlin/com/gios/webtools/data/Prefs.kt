package com.gios.webtools.data

import android.content.Context
import android.content.SharedPreferences
import com.gios.webtools.web.SearchEngine
import com.gios.webtools.web.Warmth

/** The few things the app remembers about how you want it: one file, plain keys. */
class Prefs(context: Context) {
    private val p: SharedPreferences = context.getSharedPreferences("webtools", Context.MODE_PRIVATE)

    var tutorialSeen: Boolean
        get() = p.getBoolean("tutorialSeen", false)
        set(v) = p.edit().putBoolean("tutorialSeen", v).apply()

    var engine: SearchEngine
        get() = SearchEngine.byName(p.getString("engine", null))
        set(v) = p.edit().putString("engine", v.name).apply()

    /** How long a page stays alive after the app leaves the screen, in milliseconds. */
    var graceMs: Long
        get() = p.getLong("graceMs", Warmth.GRACE_MS)
        set(v) = p.edit().putLong("graceMs", v).apply()
}
