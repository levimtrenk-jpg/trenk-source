package com.trenk.downloader

import android.content.Context
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * ספירה אנונימית של התקנות: בפתיחה הראשונה בלבד, התראה ל-ntfy + אירוע ב-GoatCounter.
 * בלי מזהה מכשיר ובלי מידע אישי – רק גרסת האפליקציה וגרסת אנדרואיד. fire-and-forget.
 */
object Stats {
    private const val PREFS = "stats"
    private const val KEY_SENT = "first_open_sent"

    fun firstOpen(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SENT, false)) return
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        // עדכון מגרסה ישנה (בלי הדגל) הוא לא התקנה חדשה: רק מסמנים
        if (info != null && info.firstInstallTime != info.lastUpdateTime) {
            prefs.edit().putBoolean(KEY_SENT, true).apply()
            return
        }
        val version = info?.versionName ?: "?"
        thread(name = "stats") {
            val ok = runCatching {
                post("https://ntfy.sh/${BuildConfig.NTFY_TOPIC}", "🎉 התקנה חדשה של TRENK · גרסה $version · אנדרואיד ${Build.VERSION.RELEASE}")
            }.getOrDefault(false)
            runCatching { goatEvent("app-first-open", version) }
            if (ok) prefs.edit().putBoolean(KEY_SENT, true).apply() // אין רשת? ננסה בפתיחה הבאה
        }
    }

    private fun post(url: String, body: String): Boolean {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.requestMethod = "POST"
            c.connectTimeout = 5_000
            c.readTimeout = 5_000
            c.doOutput = true
            // בלי UTF-8 מפורש ntfy הופך את ההודעה לקובץ מצורף
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            c.responseCode in 200..299
        } finally {
            c.disconnect()
        }
    }

    /** ה-endpoint הציבורי /count של GoatCounter (כמו count.js), בלי טוקן ובלי עוגיות */
    private fun goatEvent(name: String, version: String) {
        val q = "p=${enc(name)}&t=${enc(name)}&e=true&rnd=${System.nanoTime()}"
        val c = URL("${BuildConfig.GOATCOUNTER}/count?$q").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 5_000
            c.readTimeout = 5_000
            // GoatCounter מסנן בקשות בלי User-Agent של דפדפן כבוטים
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}) TRENK/$version")
            c.responseCode
        } finally {
            c.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
