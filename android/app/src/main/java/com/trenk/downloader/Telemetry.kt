package com.trenk.downloader

import android.content.Context
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid
import io.sentry.protocol.Message

/**
 * דוחות קריסה ושגיאות הורדה ל-Sentry (פרויקט trenk-android), בלי מידע אישי:
 * sendDefaultPii=false, בלי breadcrumbs, בלי IP, ובשגיאת הורדה רק שם האתר – הקישור והמזהה של הסרטון נמחקים.
 */
object Telemetry {
    private val URL_RE = Regex("""https?://\S+""")
    // yt-dlp: "[youtube] dQw4w9WgXcQ: Video unavailable" – המזהה הוא בעצם הקישור
    private val ID_RE = Regex("""\[([\w:]+)] [^\s:]+:""")

    fun init(context: Context) {
        if (BuildConfig.SENTRY_DSN.isBlank()) return
        SentryAndroid.init(context) { o ->
            o.dsn = BuildConfig.SENTRY_DSN
            o.isSendDefaultPii = false
            o.release = "trenk-android@${BuildConfig.VERSION_NAME}"
            o.environment = if (BuildConfig.DEBUG) "debug" else "production"
            o.maxBreadcrumbs = 0
            o.tracesSampleRate = null
            o.isAttachScreenshot = false
            o.isAttachViewHierarchy = false
            o.isEnableUserInteractionBreadcrumbs = false
            o.setBeforeSend { event, _ -> scrub(event) }
        }
        Sentry.setUser(null)
    }

    fun clean(text: String?): String =
        text.orEmpty().replace(URL_RE, "<url>").replace(ID_RE, "[$1] <id>:").take(500)

    private fun scrub(event: SentryEvent): SentryEvent {
        event.user = null
        event.serverName = null
        event.request = null
        event.message?.let { m -> m.formatted = m.formatted?.let(::clean); m.message = m.message?.let(::clean) }
        event.exceptions?.forEach { ex -> ex.value = ex.value?.let(::clean) }
        return event
    }

    /** שגיאת הורדה/חילוץ: רק שם האתר + השגיאה אחרי ניקוי. "בוטל" ו"אין רשת" לא נשלחים. */
    fun downloadError(stage: String, platform: String, error: Throwable) {
        val raw = (error.cause?.message ?: error.message).orEmpty()
        val low = raw.lowercase()
        if (listOf("canceled", "unable to resolve host", "network is unreachable", "no address associated").any { it in low }) return
        val event = SentryEvent().apply {
            level = SentryLevel.WARNING
            message = Message().apply { message = "$stage failed on $platform" }
            setTag("site", platform)
            setTag("stage", stage)
            setExtra("error", clean(raw))
            fingerprints = listOf(stage, platform, clean(raw).substringBefore('\n').take(80))
        }
        Sentry.captureEvent(event)
    }
}
