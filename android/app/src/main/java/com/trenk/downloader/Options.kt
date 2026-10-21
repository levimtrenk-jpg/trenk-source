package com.trenk.downloader

import android.content.Context

/** מתגי הפיצ'רים האופציונליים בהגדרות. הכול כבוי כברירת מחדל, וכבוי = ההתנהגות הרגילה. */
object Options {
    const val QUEUE = "opt_queue"
    const val QUALITY = "opt_quality"
    const val TRIM = "opt_trim"
    const val PLAYLIST = "opt_playlist"
    const val SUBS = "opt_subs"

    fun get(context: Context, key: String): Boolean =
        context.getSharedPreferences("app", Context.MODE_PRIVATE).getBoolean(key, false)

    fun set(context: Context, key: String, value: Boolean) {
        context.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putBoolean(key, value).apply()
    }
}
