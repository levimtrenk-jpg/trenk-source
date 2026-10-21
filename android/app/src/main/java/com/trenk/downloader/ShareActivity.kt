package com.trenk.downloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.trenk.downloader.ui.Bg
import com.trenk.downloader.ui.ThemeMode
import com.trenk.downloader.ui.Container
import com.trenk.downloader.ui.TrenkPink
import com.trenk.downloader.ui.Err
import com.trenk.downloader.ui.GradientBorder
import com.trenk.downloader.ui.LtrText
import com.trenk.downloader.ui.MutedLabel
import com.trenk.downloader.ui.OnSurface
import com.trenk.downloader.ui.OnSurfaceVar
import com.trenk.downloader.ui.PlatformBadge
import com.trenk.downloader.ui.RowCenter
import com.trenk.downloader.ui.Skeleton
import com.trenk.downloader.ui.SurfaceHigh
import com.trenk.downloader.ui.Thumb
import com.trenk.downloader.ui.TrenkGradient
import com.trenk.downloader.ui.TrenkTheme
import com.trenk.downloader.ui.platformName

/**
 * נפתח מ"שתף" באפליקציה אחרת (או מהמסך הראשי): חלון תחתון שקוף עם פרטי הסרטון והאיכויות.
 * לחיצה על איכות מתחילה הורדה ב-DownloadService וסוגרת את החלון.
 */
class ShareActivity : AppCompatActivity() {
    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    // הרשאת התראות (אנדרואיד 13+): מבקשים בהורדה הראשונה, אחרי הסבר קצר. בכל תשובה – ההורדה מתחילה.
    private var afterNotifPermission: (() -> Unit)? = null
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        afterNotifPermission?.invoke()
        afterNotifPermission = null
    }

    private fun needsNotificationAsk(): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !getSharedPreferences("app", MODE_PRIVATE).getBoolean("notif_asked", false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeMode.sync(this)
        val url = Links.extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.dataString)
        requestPermissionsIfNeeded()
        setContent { TrenkTheme { ShareSheet(url, onClose = { finish() }) } }
    }

    private fun requestPermissionsIfNeeded() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) askPermissions.launch(needed.toTypedArray())
    }

    private sealed interface UiState {
        data object Loading : UiState
        data object Repairing : UiState
        data class Error(val message: String) : UiState
        data class Ready(val probe: Probe, val subs: List<SubChoice> = emptyList()) : UiState
        data class Playlist(val items: List<PlaylistEntry>) : UiState
        data class AskNotifications(val start: () -> Unit) : UiState
    }

    /** "1:30" / "1:02:03" / "90" → שניות. null אם לא תקין */
    private fun parseTime(text: String): Int? {
        val parts = text.trim().split(":").map { it.trim().toIntOrNull() ?: return null }
        if (parts.isEmpty() || parts.size > 3 || parts.any { it < 0 }) return null
        return parts.fold(0) { acc, n -> acc * 60 + n }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ShareSheet(url: String?, onClose: () -> Unit) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var state by remember { mutableStateOf<UiState>(UiState.Loading) }
        var attempt by remember { mutableIntStateOf(0) }

        LaunchedEffect(url, attempt) {
            state = UiState.Loading
            state = if (url == null) UiState.Error(getString(R.string.share_no_link))
            else try {
                if (Options.get(this@ShareActivity, Options.PLAYLIST) && Engine.isPlaylist(url)) {
                    val items = Engine.probePlaylist(this@ShareActivity, url) { state = UiState.Repairing }
                    if (items.isEmpty()) UiState.Error(getString(R.string.err_generic)) else UiState.Playlist(items)
                } else {
                    val probe = Engine.probe(this@ShareActivity, url) { state = UiState.Repairing }
                    val subs = if (Options.get(this@ShareActivity, Options.SUBS)) Engine.probeSubs(this@ShareActivity, url) else emptyList()
                    UiState.Ready(probe, subs)
                }
            } catch (e: Exception) {
                UiState.Error(e.message ?: getString(R.string.err_unknown))
            }
        }

        // בהורדה הראשונה מבקשים הרשאת התראות (אחרי הסבר); אחרת מתחילים וסוגרים
        val begin: (() -> Unit) -> Unit = { start ->
            if (needsNotificationAsk()) state = UiState.AskNotifications(start) else { start(); onClose() }
        }

        ModalBottomSheet(
            onDismissRequest = onClose,
            sheetState = sheetState,
            containerColor = Bg,
            scrimColor = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 18.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                when (val s = state) {
                    UiState.Loading -> Loading()
                    UiState.Repairing -> Loading(stringResource(R.string.repairing))
                    is UiState.Error -> ErrorState(s.message, canRetry = url != null, onRetry = { attempt++ }, onClose = onClose)
                    is UiState.Ready -> Formats(s.probe, s.subs) { option, from, to, subLangs ->
                        begin { DownloadService.start(this@ShareActivity, s.probe, option, from, to, subLangs) }
                    }
                    is UiState.Playlist -> PlaylistView(s.items) { picked, mp3 ->
                        begin {
                            picked.forEach { e ->
                                DownloadService.startRaw(
                                    this@ShareActivity, e.url, if (mp3) "mp3" else "mp4-1080", e.title, e.thumbnail,
                                    if (mp3) "MP3" else "MP4", e.id,
                                )
                            }
                        }
                    }
                    is UiState.AskNotifications -> NotificationsAsk(
                        onAllow = {
                            getSharedPreferences("app", MODE_PRIVATE).edit().putBoolean("notif_asked", true).apply()
                            afterNotifPermission = { s.start(); onClose() }
                            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onSkip = {
                            getSharedPreferences("app", MODE_PRIVATE).edit().putBoolean("notif_asked", true).apply()
                            s.start()
                            onClose()
                        },
                    )
                }
            }
        }
    }

    /** פלייליסט: רשימה עם תיבות סימון; מה שנבחר נכנס לתור ההורדות (MP4 עד 1080p או MP3) */
    @Composable
    private fun ColumnScope.PlaylistView(items: List<PlaylistEntry>, onDownload: (List<PlaylistEntry>, Boolean) -> Unit) {
        var selected by remember { mutableStateOf(items.map { it.id }.toSet()) }
        RowCenter(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.playlist_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                MutedLabel(stringResource(R.string.playlist_selected, selected.size, items.size))
            }
            PillButton(stringResource(if (selected.size == items.size) R.string.playlist_none else R.string.playlist_all), primary = false) {
                selected = if (selected.size == items.size) emptySet() else items.map { it.id }.toSet()
            }
        }
        Spacer(Modifier.height(8.dp))
        items.forEach { e ->
            val on = e.id in selected
            RowCenter(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable { selected = if (on) selected - e.id else selected + e.id },
            ) {
                Checkbox(checked = on, onCheckedChange = { selected = if (on) selected - e.id else selected + e.id })
                Text(e.title, Modifier.weight(1f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                formatDuration(e.duration)?.let { LtrText(it, modifier = Modifier.padding(start = 8.dp)) }
            }
        }
        Spacer(Modifier.height(14.dp))
        val picked = items.filter { it.id in selected }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            PillButton(stringResource(R.string.playlist_mp4), primary = true) { if (picked.isNotEmpty()) onDownload(picked, false) }
            PillButton(stringResource(R.string.playlist_mp3), primary = false) { if (picked.isNotEmpty()) onDownload(picked, true) }
        }
    }

    /** הסבר קצר לפני בקשת הרשאת ההתראות של המערכת */
    @Composable
    private fun NotificationsAsk(onAllow: () -> Unit, onSkip: () -> Unit) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔔", fontSize = 40.sp)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.notif_title), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.notif_body), color = OnSurfaceVar, fontSize = 14.sp, lineHeight = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(stringResource(R.string.notif_allow), primary = true, onClick = onAllow)
                PillButton(stringResource(R.string.not_now), primary = false, onClick = onSkip)
            }
        }
    }

    /** שלד שמהבהב (במקום ספינר), באותה צורה של התוכן שיגיע */
    @Composable
    private fun ColumnScope.Loading(note: String? = null) {
        val engine by Engine.state.collectAsState()
        Skeleton(Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = 16.dp)
        Spacer(Modifier.height(14.dp))
        Skeleton(Modifier.fillMaxWidth(0.9f).height(18.dp), radius = 6.dp)
        Spacer(Modifier.height(8.dp))
        Skeleton(Modifier.fillMaxWidth(0.5f).height(14.dp), radius = 6.dp)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Skeleton(Modifier.weight(1f).height(58.dp), radius = 14.dp)
            Skeleton(Modifier.weight(1f).height(58.dp), radius = 14.dp)
        }
        Spacer(Modifier.height(14.dp))
        MutedLabel(
            note ?: stringResource(if (engine is EngineState.Initializing) R.string.share_preparing else R.string.share_loading),
            Modifier.align(Alignment.CenterHorizontally),
        )
    }

    @Composable
    private fun ErrorState(message: String, canRetry: Boolean, onRetry: () -> Unit, onClose: () -> Unit) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Err, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.share_load_failed), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(message, color = OnSurfaceVar, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canRetry) PillButton(stringResource(R.string.retry), primary = true, onClick = onRetry)
                PillButton(stringResource(R.string.close), primary = false, onClick = onClose)
            }
        }
    }

    @Composable
    private fun ColumnScope.Formats(probe: Probe, subs: List<SubChoice>, onDownload: (FormatOption, Int, Int, String) -> Unit) {
        val trimOn = remember { Options.get(this@ShareActivity, Options.TRIM) }
        val qualityOn = remember { Options.get(this@ShareActivity, Options.QUALITY) }
        var from by remember { mutableStateOf("") }
        var to by remember { mutableStateOf("") }
        var trimError by remember { mutableStateOf(false) }
        var preset by remember { mutableIntStateOf(2) }
        var subsOn by remember { mutableStateOf(false) }
        var subIdx by remember { mutableIntStateOf(0) }

        // מחזיר רק אם החיתוך תקין (או כבוי); אחרת מציג שגיאה
        val onPick: (FormatOption) -> Unit = pick@{ option ->
            var s = -1
            var e = -1
            if (trimOn) {
                val f = parseTime(from)
                val t = parseTime(to)
                val bad = (from.isNotBlank() && f == null) || (to.isNotBlank() && t == null) ||
                    (f != null && t != null && f >= t) || (f != null && probe.duration > 0 && f >= probe.duration)
                if (bad) { trimError = true; return@pick }
                trimError = false
                s = f ?: -1
                e = if (t != null && probe.duration > 0) minOf(t, probe.duration) else t ?: -1
            }
            val langs = if (subsOn && option.formatId != "mp3") subs.getOrNull(subIdx)?.langs.orEmpty() else ""
            onDownload(option, s, e, langs)
        }
        Thumb(
            probe.thumbnail, radius = 16.dp,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            badge = formatDuration(probe.duration),
        )
        Spacer(Modifier.height(12.dp))
        Text(probe.title ?: stringResource(R.string.untitled), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 23.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        RowCenter {
            PlatformBadge(probe.platform)
            Spacer(Modifier.width(6.dp))
            Text(listOfNotNull(probe.uploader, platformName(probe.platform)).joinToString(" · "), color = OnSurfaceVar, fontSize = 13.sp, maxLines = 1)
        }

        val video = probe.options.filter { it.formatId != "mp3" }
        val audio = probe.options.firstOrNull { it.formatId == "mp3" }
        // מומלץ: 1080p אם יש, אחרת הגבוה ביותר שמתחת ל-1080, אחרת הגבוה ביותר
        val recommended = video.firstOrNull { it.quality == 1080 }
            ?: video.filter { (it.quality ?: 0) < 1080 }.maxByOrNull { it.quality ?: 0 }
            ?: video.firstOrNull()

        if (trimOn) {
            MutedLabel(stringResource(R.string.trim_title), Modifier.padding(top = 16.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    from, { from = it; trimError = false }, Modifier.weight(1f), singleLine = true,
                    label = { Text(stringResource(R.string.trim_from)) }, placeholder = { Text("0:00") },
                    isError = trimError,
                )
                OutlinedTextField(
                    to, { to = it; trimError = false }, Modifier.weight(1f), singleLine = true,
                    label = { Text(stringResource(R.string.trim_to)) },
                    placeholder = { Text(formatDuration(probe.duration) ?: "mm:ss") },
                    isError = trimError,
                )
            }
            if (trimError) Text(stringResource(R.string.trim_invalid), color = Err, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }

        if (subs.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(subsOn, { subsOn = it })
                Text(stringResource(R.string.subs_title), fontSize = 14.sp, modifier = Modifier.clickable { subsOn = !subsOn })
            }
            if (subsOn) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    subs.forEachIndexed { i, c ->
                        val label = stringResource(when (c.id) { "he" -> R.string.subs_he; "en" -> R.string.subs_en; else -> R.string.subs_auto })
                        Box(
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .background(if (i == subIdx) TrenkPink.copy(alpha = 0.25f) else Container)
                                .clickable { subIdx = i }.padding(horizontal = 14.dp, vertical = 8.dp),
                        ) { Text(label, fontSize = 13.sp) }
                    }
                }
            }
        }

        if (video.isNotEmpty() && qualityOn) {
            // איכות מהירה: 360 / 720 / 1080 / הכי טוב. כל יעד ממופה לאיכות הקרובה שקיימת בסרטון
            val targets = listOf(360, 720, 1080, 0)
            val mapped = targets.map { t ->
                if (t == 0) video.first() else video.filter { (it.quality ?: 0) <= t }.maxByOrNull { it.quality ?: 0 } ?: video.last()
            }
            MutedLabel(stringResource(R.string.video), Modifier.padding(top = 16.dp, bottom = 8.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                targets.forEachIndexed { i, t ->
                    val label = if (t == 0) stringResource(R.string.q_best) else "${t}p"
                    val body: @Composable () -> Unit = {
                        Column(Modifier.fillMaxWidth().clickable { preset = i }.padding(horizontal = 6.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                            LtrText(mapped[i].sizeApprox?.let { "~" + formatBytes(it) } ?: " ")
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        if (i == preset) GradientBorder(radius = 14.dp) { body() }
                        else Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Container)) { body() }
                    }
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PillButton(stringResource(R.string.act_download), primary = true) { onPick(mapped[preset]) }
            }
        } else if (video.isNotEmpty()) {
            MutedLabel(stringResource(R.string.video), Modifier.padding(top = 16.dp, bottom = 8.dp))
            video.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { option ->
                        Box(Modifier.weight(1f)) { QualityCard(option, option == recommended) { onPick(option) } }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (audio != null) {
            MutedLabel(stringResource(R.string.audio), Modifier.padding(top = 6.dp, bottom = 8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Container)
                    .clickable { onPick(audio) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("MP3", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("192kbps", color = OnSurfaceVar, fontSize = 12.sp)
                }
                audio.sizeApprox?.let { LtrText("~" + formatBytes(it)) }
            }
        }
        MutedLabel(stringResource(R.string.share_pick_hint), Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp))
    }

    @Composable
    private fun QualityCard(option: FormatOption, recommended: Boolean, onClick: () -> Unit) {
        val q = option.quality
        val tag = when {
            q == null -> null
            q >= 2160 -> "4K"
            q >= 1440 -> "2K"
            q >= 720 -> "HD"
            else -> null
        }
        val body: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp)) {
                RowCenter(Modifier.fillMaxWidth()) {
                    Text(if (q != null) "${q}p" else option.label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    if (tag != null) {
                        Spacer(Modifier.width(4.dp))
                        Text(tag, color = OnSurfaceVar, fontSize = 10.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    if (recommended) {
                        Text(
                            stringResource(R.string.recommended), fontSize = 10.sp, color = OnSurface,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(SurfaceHigh).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                LtrText(option.sizeApprox?.let { "~" + formatBytes(it) } ?: " ")
            }
        }
        if (recommended) GradientBorder(radius = 14.dp) { body() }
        else Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Container)) { body() }
    }
}

@Composable
fun PillButton(text: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .then(if (primary) Modifier.background(TrenkGradient) else Modifier.background(SurfaceHigh))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (primary) Color.White else OnSurface)
    }
}

fun formatDuration(sec: Int): String? {
    if (sec <= 0) return null
    val h = sec / 3600; val m = sec % 3600 / 60; val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatBytes(n: Long): String = when {
    n >= 1L shl 30 -> "%.1f GB".format(n / (1L shl 30).toDouble())
    n >= 100L shl 20 -> "%.0f MB".format(n / (1L shl 20).toDouble())
    n >= 1L shl 20 -> "%.1f MB".format(n / (1L shl 20).toDouble())
    else -> "${maxOf(1, n / 1024)} KB"
}
