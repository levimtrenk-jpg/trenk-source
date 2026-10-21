package com.trenk.downloader

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** אפשרות הורדה שמוצגת למשתמש. formatId: mp4-<height> | mp4-best | mp3 (כמו במנוע של המחשב) */
/** quality = הצלע הקצרה (1080 גם לסרטון אנכי). null ל-MP3 */
data class FormatOption(val formatId: String, val label: String, val sizeApprox: Long?, val quality: Int? = null)

data class Probe(
    val url: String,
    val platform: String,
    val id: String?,
    val title: String?,
    val uploader: String?,
    val thumbnail: String?,
    val duration: Int,
    val options: List<FormatOption>,
)

/** סרטון בפלייליסט (מרשימה שטוחה: בלי איכויות) */
data class PlaylistEntry(val id: String, val title: String, val url: String, val duration: Int, val thumbnail: String?)

/** כתוביות שזמינות לסרטון: id (he/en/auto) ← ערך ל---sub-langs */
data class SubChoice(val id: String, val langs: String)

sealed interface EngineState {
    data object Initializing : EngineState
    data class Ready(val version: String?) : EngineState
    data class Failed(val message: String) : EngineState
}

/** עטיפה ל-youtubedl-android: אתחול, שליפת פורמטים, בניית בקשת הורדה, ועדכון yt-dlp אוטומטי + תיקון עצמי. */
object Engine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<EngineState>(EngineState.Initializing)
    val state: StateFlow<EngineState> = _state
    private val updateLock = Mutex()

    fun initAsync(context: Context) {
        val app = context.applicationContext
        scope.launch {
            _state.value = try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                EngineState.Ready(YoutubeDL.getInstance().version(app))
            } catch (e: Throwable) {
                EngineState.Failed(e.message ?: e.toString())
            }
        }
    }

    /** מחכה שהאתחול יסתיים. זורק שגיאה אם נכשל. */
    suspend fun awaitReady() {
        when (val s = _state.first { it !is EngineState.Initializing }) {
            is EngineState.Failed -> throw IllegalStateException("engine init failed: ${s.message}")
            else -> Unit
        }
    }

    fun cookiesFile(context: Context) = File(context.filesDir, "instagram_cookies.txt")

    private fun baseRequest(context: Context, url: String, platform: String): YoutubeDLRequest {
        val req = YoutubeDLRequest(url)
        req.addOption("--no-playlist")
        req.addOption("--playlist-items", "1") // פוסט אינסטגרם עם כמה סרטונים: הראשון
        val cookies = cookiesFile(context)
        if (platform == "instagram" && cookies.exists()) req.addOption("--cookies", cookies.absolutePath)
        return req
    }

    /** onRepair נקרא כשהחילוץ נכשל ומתחילים תיקון עצמי (עדכון yt-dlp + ניסיון נוסף) */
    suspend fun probe(context: Context, url: String, onRepair: () -> Unit = {}): Probe = withContext(Dispatchers.IO) {
        val platform = Links.platformOf(url) ?: throw IllegalArgumentException(context.getString(R.string.err_unsupported))
        awaitReady()
        val info: VideoInfo = try {
            withRepair(context, platform, onRepair) { YoutubeDL.getInstance().getInfo(baseRequest(context, url, platform)) }
        } catch (e: Exception) {
            Telemetry.downloadError("probe", platform, e)
            throw RuntimeException(friendlyError(context, e.message, platform), e)
        }
        Probe(
            url = info.webpageUrl ?: url,
            platform = platform,
            id = info.id,
            title = info.title,
            uploader = info.uploader,
            thumbnail = info.thumbnail,
            duration = info.duration,
            options = buildOptions(info),
        )
    }

    /** אילו כתוביות יש לסרטון (עברית / אנגלית / אוטומטיות בשפת המקור). ריק אם אין או אם הבדיקה נכשלה. */
    suspend fun probeSubs(context: Context, url: String): List<SubChoice> = withContext(Dispatchers.IO) {
        runCatching {
            val platform = Links.platformOf(url) ?: return@runCatching emptyList()
            awaitReady()
            val req = baseRequest(context, url, platform)
            req.addOption("--skip-download")
            req.addOption("--print", "%(subtitles)j")
            req.addOption("--print", "%(automatic_captions)j")
            val lines = YoutubeDL.getInstance().execute(req).out.lineSequence().map { it.trim() }.filter { it.startsWith("{") }.toList()
            fun keys(i: Int): Set<String> = runCatching {
                val j = org.json.JSONObject(lines[i]); j.keys().asSequence().toSet()
            }.getOrDefault(emptySet())
            val all = keys(0) + keys(1)
            val out = mutableListOf<SubChoice>()
            if (all.any { it.startsWith("he") || it.startsWith("iw") }) out += SubChoice("he", "he,iw")
            if (all.any { it == "en" || it.startsWith("en-") }) out += SubChoice("en", "en,en-orig")
            keys(1).firstOrNull { it.endsWith("-orig") }?.let { out += SubChoice("auto", "$it,${it.removeSuffix("-orig")}") }
            out
        }.getOrDefault(emptyList())
    }

    /** קישור לפלייליסט עצמו (youtube.com/playlist?list=...). קישור לסרטון בודד נשאר סרטון בודד. */
    fun isPlaylist(url: String): Boolean {
        val uri = android.net.Uri.parse(url)
        val host = uri.host?.lowercase() ?: return false
        return (host == "youtube.com" || host.endsWith(".youtube.com")) &&
            uri.path?.startsWith("/playlist") == true && !uri.getQueryParameter("list").isNullOrBlank()
    }

    /** רשימת הסרטונים בפלייליסט (עד 200), בלי לשלוף איכויות לכל סרטון */
    suspend fun probePlaylist(context: Context, url: String, onRepair: () -> Unit = {}): List<PlaylistEntry> = withContext(Dispatchers.IO) {
        val platform = Links.platformOf(url) ?: throw IllegalArgumentException(context.getString(R.string.err_unsupported))
        awaitReady()
        val out = try {
            withRepair(context, platform, onRepair) {
                val req = YoutubeDLRequest(url)
                req.addOption("--flat-playlist")
                req.addOption("--dump-json")
                req.addOption("--yes-playlist")
                req.addOption("--playlist-end", "200")
                YoutubeDL.getInstance().execute(req).out
            }
        } catch (e: Exception) {
            Telemetry.downloadError("probe", platform, e)
            throw RuntimeException(friendlyError(context, e.message, platform), e)
        }
        out.lineSequence().map { it.trim() }.filter { it.startsWith("{") }.mapNotNull { line ->
            runCatching {
                val j = org.json.JSONObject(line)
                val id = j.optString("id")
                val link = j.optString("url").takeIf { it.startsWith("http") } ?: "https://www.youtube.com/watch?v=$id"
                val thumbs = j.optJSONArray("thumbnails")
                PlaylistEntry(
                    id = id,
                    title = j.optString("title").ifBlank { link },
                    url = link,
                    duration = j.optDouble("duration", 0.0).toInt(),
                    thumbnail = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url"),
                )
            }.getOrNull()
        }.toList()
    }

    private fun buildOptions(info: VideoInfo): List<FormatOption> {
        val formats = info.formats.orEmpty()
        val audioOnly = formats.filter { it.vcodec == "none" && it.acodec != null && it.acodec != "none" }
        val bestAudio = audioOnly.maxOfOrNull { size(it.fileSize, it.fileSizeApproximate) ?: 0L } ?: 0L

        val byHeight = sortedMapOf<Int, Pair<Int, Long?>>(compareByDescending { it }) // height → (צלע קצרה, גודל)
        for (f in formats) {
            if (f.vcodec == "none" || f.height <= 0) continue
            var s = size(f.fileSize, f.fileSizeApproximate)
            if (s != null && (f.acodec == null || f.acodec == "none") && bestAudio > 0) s += bestAudio
            val short = if (f.width > 0) minOf(f.width, f.height) else f.height
            val prev = byHeight[f.height]
            // מעדיפים את הגודל של h264, כי זה מה שיורד
            if (prev == null || (f.vcodec?.startsWith("avc") == true && s != null)) byHeight[f.height] = short to (s ?: prev?.second)
        }
        val options = byHeight.map { (h, v) -> FormatOption("mp4-$h", "MP4 ${v.first}p", v.second, v.first) }.toMutableList()
        if (options.isEmpty()) options += FormatOption("mp4-best", "MP4", size(info.fileSize, info.fileSizeApproximate))
        options += FormatOption("mp3", "MP3", if (bestAudio > 0) (bestAudio * 1.3).toLong() else null)
        return options
    }

    private fun size(exact: Long, approx: Long): Long? = when {
        exact > 0 -> exact
        approx > 0 -> approx
        else -> null
    }

    /** בקשת הורדה לתיקייה זמנית. השם הסופי ניתן אחר כך ב-MediaStore. */
    fun downloadRequest(
        context: Context, url: String, formatId: String, outDir: File, name: String,
        startSec: Int = -1, endSec: Int = -1, subLangs: String = "",
    ): YoutubeDLRequest {
        val platform = Links.platformOf(url) ?: "video"
        val req = baseRequest(context, url, platform)
        req.addOption("--no-mtime")
        // חיתוך (הפיצ'ר "חיתוך"): אותו דבר כמו download_ranges ב-API של yt-dlp
        if (startSec >= 0 || endSec > 0) {
            req.addOption("--download-sections", "*${startSec.coerceAtLeast(0)}-${if (endSec > 0) endSec.toString() else "inf"}")
        }
        req.addOption("-o", File(outDir, name.replace("%", "%%") + ".%(ext)s").absolutePath)
        if (formatId == "mp3") {
            req.addOption("-f", "bestaudio/best")
            req.addOption("-x")
            req.addOption("--audio-format", "mp3")
            req.addOption("--audio-quality", "192K")
        } else {
            // כתוביות (הפיצ'ר "כתוביות"): נשמרות ליד הסרטון כ-SRT. ב-MP3 מתעלמים
            if (subLangs.isNotBlank()) {
                req.addOption("--write-subs")
                req.addOption("--write-auto-subs")
                req.addOption("--sub-langs", subLangs)
                req.addOption("--convert-subs", "srt")
            }
            val h = formatId.removePrefix("mp4-").toIntOrNull()
            val fmt = if (h == null) "bv*+ba/b"
            else "bv*[height=$h]+ba/b[height=$h]/bv*[height<=$h]+ba/b[height<=$h]/b"
            req.addOption("-f", fmt)
            req.addOption("-S", "vcodec:h264,acodec:aac") // מתנגן בכל נגן
            req.addOption("--merge-output-format", "mp4")
            req.addOption("--remux-video", "mp4")
        }
        return req
    }

    /** עדכון yt-dlp (ערוץ STABLE) בשקט. true אם הותקנה גרסה חדשה. */
    suspend fun update(context: Context): Boolean = withContext(Dispatchers.IO) {
        awaitReady()
        updateLock.withLock {
            val app = context.applicationContext
            val status = YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
            _state.value = EngineState.Ready(YoutubeDL.getInstance().version(app))
            status == YoutubeDL.UpdateStatus.DONE
        }
    }

    /** בפתיחת האפליקציה: עדכון ברקע, בלי הודעות. כישלון (אין רשת וכו') פשוט נבלע. */
    fun autoUpdateAsync(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { update(app) } }
    }

    /**
     * תיקון עצמי: אם block נכשל בשגיאה שעדכון עשוי לפתור (האתר השתנה), מעדכנים את yt-dlp
     * ומנסים שוב פעם אחת. שגיאות כמו "פרטי" או "אין רשת" נזרקות מיד.
     */
    suspend fun <T> withRepair(context: Context, platform: String, onRepair: () -> Unit, block: suspend () -> T): T =
        try {
            block()
        } catch (e: Exception) {
            if (!isRepairable(e.message, platform)) throw e
            onRepair()
            runCatching { update(context) }
            block()
        }

    private fun isRepairable(raw: String?, platform: String): Boolean {
        val low = raw.orEmpty().lowercase()
        if (platform == "instagram" && listOf("login", "log in", "cookies").any { it in low }) return false
        return listOf("private", "unsupported url", "unable to resolve host", "network is unreachable", "no address associated", "canceled")
            .none { it in low }
    }

    fun friendlyError(context: Context, raw: String?, platform: String): String {
        val text = raw.orEmpty().replace("ERROR: ", "").trim()
        val low = text.lowercase()
        return when {
            platform == "instagram" && listOf("login", "log in", "rate-limit", "cookies").any { it in low } ->
                context.getString(R.string.err_instagram_login)
            "private" in low -> context.getString(R.string.err_private)
            "unsupported url" in low -> context.getString(R.string.err_unsupported)
            "unable to resolve host" in low || "network" in low -> context.getString(R.string.err_no_internet)
            "removed" in low || "unavailable" in low || "not available" in low -> context.getString(R.string.err_unavailable)
            // כאן כבר ניסינו לעדכן את המנוע ולנסות שוב
            else -> context.getString(R.string.err_generic)
        }
    }
}
