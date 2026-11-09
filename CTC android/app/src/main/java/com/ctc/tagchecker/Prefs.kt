package com.ctc.tagchecker

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("ctc", Context.MODE_PRIVATE)

    var plusDigit: Boolean
        get() = sp.getBoolean("plusDigit", false)
        set(v) { sp.edit().putBoolean("plusDigit", v).apply() }

    var letters: Boolean
        get() = sp.getBoolean("letters", true)
        set(v) { sp.edit().putBoolean("letters", v).apply() }

    var digits: Boolean
        get() = sp.getBoolean("digits", false)
        set(v) { sp.edit().putBoolean("digits", v).apply() }

    var delayMs: Int
        get() = sp.getInt("delayMs", 900)
        set(v) { sp.edit().putInt("delayMs", v).apply() }

    var startFrom: String
        get() = sp.getString("startFrom", "") ?: ""
        set(v) { sp.edit().putString("startFrom", v).apply() }

    var calibration: String
        get() = sp.getString("calibration", "") ?: ""
        set(v) { sp.edit().putString("calibration", v).apply() }

    var results: String
        get() = sp.getString("results", "") ?: ""
        set(v) { sp.edit().putString("results", v).apply() }

    fun addResult(tag: String) {
        val cur = results.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (tag in cur) return
        results = (cur + tag).joinToString("\n")
    }
}
