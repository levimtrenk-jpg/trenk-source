package com.trenk.downloader

import android.net.Uri
import java.text.Normalizer

/** זיהוי קישורים ושמות קבצים. אותה לוגיקה כמו במנוע של המחשב (engine/downloader.py). */
object Links {
    private val URL_RE = Regex("""https?://[^\s<>"']+""")

    private val PLATFORMS = mapOf(
        "youtube.com" to "youtube",
        "youtu.be" to "youtube",
        "youtube-nocookie.com" to "youtube",
        "tiktok.com" to "tiktok",
        "instagram.com" to "instagram",
    )

    fun platformOf(url: String): String? {
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return null
        return PLATFORMS.entries.firstOrNull { (domain, _) -> host == domain || host.endsWith(".$domain") }?.value
    }

    /** הטקסט המשותף כולל לפעמים כותרת לפני הקישור ("צפו בזה! https://...") */
    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return URL_RE.findAll(text)
            .map { it.value.trimEnd('.', ',', ')', '!', '?') }
            .firstOrNull { platformOf(it) != null }
    }

    private val RESERVED = setOf("CON", "PRN", "AUX", "NUL") + (1..9).flatMap { listOf("COM$it", "LPT$it") }

    /** שם קובץ נקי מהכותרת: בלי האשטגים, אימוג'ים ותווים אסורים */
    fun cleanFileName(title: String?, uploader: String?, id: String?, platform: String): String {
        var t = title.orEmpty()
        if (Regex("^(video by |tiktok video)", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
            t = "${uploader ?: platform} ${id.orEmpty()}"
        }
        val noTags = t.replace(Regex("""#[^\s\d]\S*"""), " ")
        if (noTags.isNotBlank()) t = noTags
        t = Normalizer.normalize(t, Normalizer.Form.NFC)
        val sb = StringBuilder()
        var i = 0
        while (i < t.length) {
            val cp = t.codePointAt(i)
            val type = Character.getType(cp)
            val bad = type == Character.CONTROL.toInt() || type == Character.FORMAT.toInt() ||
                type == Character.OTHER_SYMBOL.toInt() || type == Character.SURROGATE.toInt() ||
                type == Character.PRIVATE_USE.toInt() || type == Character.UNASSIGNED.toInt() ||
                cp in 0xFE00..0xFE0F || "<>:\"/\\|?*".indexOf(cp.toChar()) >= 0
            if (bad) sb.append(' ') else sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        var name = sb.toString().replace(Regex("""\s+"""), " ").trim(' ', '.', '-', '_')
        if (name.length > 120) name = name.take(120).substringBeforeLast(' ').trim(' ', '.', '-', '_')
        if (name.isBlank()) name = "${platform}_${id ?: System.currentTimeMillis()}"
        if (name.uppercase() in RESERVED) name += "_"
        return name
    }
}
