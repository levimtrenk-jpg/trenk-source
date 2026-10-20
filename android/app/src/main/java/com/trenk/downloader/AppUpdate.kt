package com.trenk.downloader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * עדכון האפליקציה עצמה (נפרד מ"עדכן מנוע").
 * הריפו של הקוד פרטי, לכן הבדיקה מול דף ההורדה הציבורי: ה-workflow כותב שם version.json
 * עם versionCode של ה-Release האחרון, ואת ה-APK בשם קבוע.
 */
object AppUpdate {
    private const val SITE = "https://levimtrenk-jpg.github.io/trenk"
    private const val VERSION_URL = "$SITE/version.json"
    private const val APK_URL = "$SITE/UniversalDownloader.apk"

    sealed interface State {
        data object Idle : State
        data class Available(val name: String, val code: Long, val sha256: String) : State
        data class Downloading(val name: String, val progress: Float) : State
        data class Ready(val name: String, val file: File) : State
        data class Failed(val name: String, val message: String, val sha256: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    // ---------- כפתור טיפ ----------
    // הקישור מגיע מ-version.json (config/tip.json בריפו). ריק = אין כפתור. נשמר מקומית כדי שיעבוד גם בלי רשת.
    private const val PREFS = "app"
    private val _tipUrl = MutableStateFlow<String?>(null)
    val tipUrl: StateFlow<String?> = _tipUrl

    fun loadCachedTip(context: Context) {
        _tipUrl.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tip_url", null)
    }

    private fun saveTip(context: Context, url: String?) {
        val clean = url?.trim()?.takeIf { Regex("""^https://\S+$""").matches(it) }
        _tipUrl.value = clean
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("tip_url", clean).apply()
    }

    /** הצעת הטיפ במסך הראשי מוצגת פעם אחת בלבד */
    fun tipPromptDismissed(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("tip_prompt_done", false)

    fun dismissTipPrompt(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("tip_prompt_done", true).apply()

    fun openTip(context: Context) {
        val url = _tipUrl.value ?: return
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun installedName(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

    private fun installedCode(context: Context): Long =
        runCatching { PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)) }.getOrDefault(0)

    /** בפתיחת האפליקציה. שקט אם אין רשת או אין עדכון. */
    /** תוצאת בדיקה ידנית, כדי להראות למשתמש שהבדיקה באמת קרתה */
    sealed interface CheckResult {
        data class Newer(val name: String) : CheckResult
        data class UpToDate(val latest: String) : CheckResult
        data class Failed(val message: String) : CheckResult
    }

    /** בפתיחת האפליקציה (שקט) ומכפתור "בדוק עדכונים" בהגדרות (מחזיר תוצאה) */
    suspend fun check(context: Context): CheckResult = withContext(Dispatchers.IO) {
        when (val s = _state.value) {
            is State.Downloading -> return@withContext CheckResult.Newer(s.name)
            is State.Ready -> return@withContext CheckResult.Newer(s.name)
            else -> Unit
        }
        try {
            val conn = URL("$VERSION_URL?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.useCaches = false
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            saveTip(context, json.optString("tip", ""))
            val name = json.getString("version")
            // "code" נוסף בגרסה 1.0.7; לפני כן: המספר האחרון של versionName (= מספר הריצה)
            val code = json.optLong("code", name.substringAfterLast('.').toLongOrNull() ?: 0)
            // ה-workflow מחשב sha256 של ה-APK; לפניו לא מתקינים (בלי sha = לא מציעים עדכון)
            val sha = json.optString("sha256", "").lowercase()
            if (code > installedCode(context) && Regex("^[0-9a-f]{64}$").matches(sha)) {
                _state.value = State.Available(name, code, sha)
                CheckResult.Newer(name)
            } else {
                _state.value = State.Idle
                CheckResult.UpToDate(name)
            }
        } catch (e: Exception) {
            CheckResult.Failed(context.getString(R.string.update_check_failed))
        }
    }

    /** מוריד את ה-APK לתיקייה זמנית ופותח את מסך ההתקנה של אנדרואיד */
    suspend fun downloadAndInstall(context: Context) {
        val (name, expected) = when (val s = _state.value) {
            is State.Available -> s.name to s.sha256
            is State.Failed -> s.name to s.sha256
            is State.Ready -> return install(context, s.file, s.name)
            else -> return
        }
        val file = File(context.cacheDir, "updates/update.apk")
        try {
            withContext(Dispatchers.IO) {
                file.parentFile?.mkdirs()
                val conn = URL(APK_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                val total = conn.contentLengthLong
                var done = 0L
                var lastShown = -1
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val pct = if (total > 0) (done * 100 / total).toInt() else 0
                            if (pct != lastShown) {
                                lastShown = pct
                                _state.value = State.Downloading(name, pct / 100f)
                            }
                        }
                    }
                }
                if (total > 0 && done != total) throw IllegalStateException("download cut off")
            }
            // אבטחה: מתקינים רק קובץ שה-sha256 שלו זהה למה שה-workflow פרסם ב-version.json
            val actual = withContext(Dispatchers.IO) { sha256(file) }
            if (!actual.equals(expected, ignoreCase = true)) {
                file.delete()
                _state.value = State.Failed(name, context.getString(R.string.update_hash_mismatch), expected)
                return
            }
            _state.value = State.Ready(name, file)
            install(context, file, name)
        } catch (e: Exception) {
            file.delete()
            _state.value = State.Failed(name, context.getString(R.string.update_download_failed), expected)
        }
    }

    private fun sha256(file: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun install(context: Context, file: File, name: String) {
        // מאנדרואיד 8: צריך אישור חד-פעמי "התקנת אפליקציות לא ידועות" לאפליקציה הזו
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            _state.value = State.Ready(name, file)
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
