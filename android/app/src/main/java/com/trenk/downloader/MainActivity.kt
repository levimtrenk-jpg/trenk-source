package com.trenk.downloader

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.Switch
import androidx.core.os.LocaleListCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.trenk.downloader.ui.Bg
import com.trenk.downloader.ui.ThemeMode
import com.trenk.downloader.ui.CircleIconButton
import com.trenk.downloader.ui.Container
import com.trenk.downloader.ui.Err
import com.trenk.downloader.ui.ErrBg
import com.trenk.downloader.ui.FilterChip
import com.trenk.downloader.ui.GradientText
import com.trenk.downloader.ui.LtrText
import com.trenk.downloader.ui.MutedLabel
import com.trenk.downloader.ui.Ok
import com.trenk.downloader.ui.OnSurface
import com.trenk.downloader.ui.OnSurfaceMute
import com.trenk.downloader.ui.OnSurfaceVar
import com.trenk.downloader.ui.Outline
import com.trenk.downloader.ui.PlatformBadge
import com.trenk.downloader.ui.Rubik
import com.trenk.downloader.ui.SectionTitle
import com.trenk.downloader.ui.StatusDot
import com.trenk.downloader.ui.SurfaceHigh
import com.trenk.downloader.ui.SurfaceLow
import com.trenk.downloader.ui.Thumb
import com.trenk.downloader.ui.TrenkGradient
import com.trenk.downloader.ui.TrenkLabel
import com.trenk.downloader.ui.TrenkOrange
import com.trenk.downloader.ui.TrenkRing
import com.trenk.downloader.ui.TrenkTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** מסך ראשי: הדבקה + פעולה ראשית אחת, הורדות פעילות, היסטוריה. הגדרות במסך נפרד. */
class MainActivity : AppCompatActivity() {
    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeMode.sync(this)
        // הרשאת התראות נשאלת בהורדה הראשונה (ShareActivity), עם הסבר
        val needed = buildList {
            if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) askPermissions.launch(needed.toTypedArray())
        setContent {
            TrenkTheme {
                // 0 ראשי, 1 הגדרות, 2 רישיונות
                var screen by rememberSaveable { mutableStateOf(0) }
                // מי שכבר הוריד משהו (עדכון מגרסה ישנה) לא צריך מסך פתיחה
                var onboarded by rememberSaveable { mutableStateOf(Onboarding.done(this@MainActivity) || History.items.value.isNotEmpty()) }
                BackHandler(enabled = screen != 0) { screen = if (screen == 2) 1 else 0 }
                Box(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) {
                    when {
                        !onboarded -> OnboardingScreen(onDone = { Onboarding.markDone(this@MainActivity); onboarded = true })
                        screen == 1 -> SettingsScreen(onBack = { screen = 0 }, onLicenses = { screen = 2 })
                        screen == 2 -> LicensesScreen(onBack = { screen = 1 })
                        else -> MainScreen(onSettings = { screen = 1 })
                    }
                }
            }
        }
    }

    // ---------- פעולות ----------

    private fun openDownload(text: String) {
        if (Links.extractUrl(text) == null) {
            Toast.makeText(this, getString(R.string.toast_no_link), Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, ShareActivity::class.java).putExtra(Intent.EXTRA_TEXT, text))
    }

    private fun openFile(item: HistoryItem) {
        val uri = item.uri ?: return
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), item.mime ?: "*/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_cant_open), Toast.LENGTH_SHORT).show()
        }
    }

    private fun retry(item: HistoryItem) =
        DownloadService.startRaw(this, item.url, item.formatId, item.title, item.thumbnail, item.label)

    private fun clipboardText(): String? =
        getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()

    /** חותמת הזמן של מה שבלוח, בלי לקרוא את התוכן (קריאת התוכן מקפיצה הודעה באנדרואיד 13+) */
    private fun clipboardStamp(): Long =
        getSystemService(ClipboardManager::class.java).primaryClipDescription?.timestamp ?: 0L

    // ---------- מסך ראשי ----------

    @Composable
    private fun MainScreen(onSettings: () -> Unit) {
        val history by History.items.collectAsState()
        val active = history.filter { it.active }
        val finished = history.filter { !it.active }
        var filter by rememberSaveable { mutableStateOf(0) } // 0 הכל, 1 וידאו, 2 אודיו
        val shown = finished.filter {
            when (filter) {
                1 -> it.formatId != "mp3"
                2 -> it.formatId == "mp3"
                else -> true
            }
        }

        // קישור מהלוח: נבדק כשחוזרים לאפליקציה, ורק אם הלוח השתנה
        var clipUrl by remember { mutableStateOf<String?>(null) }
        var lastStamp by remember { mutableLongStateOf(-1L) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val stamp = clipboardStamp()
                if (stamp != lastStamp) {
                    lastStamp = stamp
                    val url = Links.extractUrl(clipboardText())
                    clipUrl = url?.takeIf { u -> history.none { it.url == u && it.status == Status.DONE } }
                }
            }
        }

        // בדיקת עדכון לאפליקציה עצמה, פעם אחת בפתיחה
        LaunchedEffect(Unit) { AppUpdate.check(this@MainActivity) }
        val update by AppUpdate.state.collectAsState()
        val tipUrl by AppUpdate.tipUrl.collectAsState()
        var tipDone by remember { mutableStateOf(AppUpdate.tipPromptDismissed(this@MainActivity)) }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { TopBar(onSettings) }
            if (update !is AppUpdate.State.Idle) item { UpdateCard(update) }
            item { Hero(clipUrl, onDownload = { text -> openDownload(text); if (text == clipUrl) clipUrl = null }) }

            // הצעת טיפ עדינה: רק אם יש קישור, רק אחרי 5 הורדות שהצליחו, ופעם אחת בלבד
            if (tipUrl != null && !tipDone && history.count { it.status == Status.DONE } >= 5) {
                item { TipPrompt(onDone = { AppUpdate.dismissTipPrompt(this@MainActivity); tipDone = true }) }
            }

            if (active.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.downloading_now), Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp)) }
                items(active, key = { it.id }) { ActiveCard(it) }
            }

            if (finished.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionTitle(stringResource(R.string.history), Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(stringResource(R.string.filter_all), filter == 0) { filter = 0 }
                            FilterChip(stringResource(R.string.video), filter == 1) { filter = 1 }
                            FilterChip(stringResource(R.string.audio), filter == 2) { filter = 2 }
                        }
                    }
                }
                items(shown, key = { it.id }) { HistoryRow(it) }
            }

            if (history.isEmpty()) item { EmptyState() }
        }
    }

    @Composable
    private fun TopBar(onSettings: () -> Unit) {
        val engine by Engine.state.collectAsState()
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                TrenkLabel(9f)
                Text("Universal Downloader", fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                // כדי לראות בטלפון איזו גרסה מותקנת
                Text(stringResource(R.string.version_x, remember { AppUpdate.installedName(this@MainActivity) }), fontSize = 11.sp, color = OnSurfaceMute)
            }
            val (text, color) = when (engine) {
                EngineState.Initializing -> stringResource(R.string.engine_preparing_short) to TrenkOrange
                is EngineState.Ready -> stringResource(R.string.engine_ready_short) to Ok
                is EngineState.Failed -> stringResource(R.string.engine_error_short) to Err
            }
            Row(
                Modifier.clip(CircleShape).background(Container).clickable(onClick = onSettings).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(color)
                Spacer(Modifier.width(6.dp))
                Text(text, fontSize = 12.sp, color = OnSurfaceVar)
            }
            CircleIconButton(Icons.Filled.Settings, stringResource(R.string.settings), onSettings)
        }
    }

    /** "עדכן אפליקציה": גרסה חדשה של ה-APK (המנוע מתעדכן לבד, בנפרד) */
    @Composable
    private fun UpdateCard(state: AppUpdate.State) {
        val scope = rememberCoroutineScope()
        val installed = remember { AppUpdate.installedName(this) }
        val name = when (state) {
            is AppUpdate.State.Available -> state.name
            is AppUpdate.State.Downloading -> state.name
            is AppUpdate.State.Ready -> state.name
            is AppUpdate.State.Failed -> state.name
            AppUpdate.State.Idle -> return
        }
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
            com.trenk.downloader.ui.GradientBorder(radius = 18.dp, width = 1.5.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.update_available), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(stringResource(R.string.update_new_installed, name, installed), color = OnSurfaceVar, fontSize = 12.sp)
                        }
                        when (state) {
                            is AppUpdate.State.Downloading -> Text("${(state.progress * 100).toInt()}%", color = OnSurfaceVar, fontSize = 13.sp)
                            else -> PillButton(
                                stringResource(if (state is AppUpdate.State.Ready) R.string.install else if (state is AppUpdate.State.Failed) R.string.retry else R.string.update_app),
                                primary = true,
                            ) { scope.launch { AppUpdate.downloadAndInstall(this@MainActivity) } }
                        }
                    }
                    if (state is AppUpdate.State.Downloading) {
                        Spacer(Modifier.height(10.dp))
                        ProgressBar(state.progress)
                    }
                    if (state is AppUpdate.State.Failed) {
                        Spacer(Modifier.height(6.dp))
                        Text(state.message, color = Err, fontSize = 12.sp)
                    }
                    if (state is AppUpdate.State.Ready) {
                        Spacer(Modifier.height(6.dp))
                        MutedLabel(stringResource(R.string.update_unknown_sources_hint))
                    }
                }
            }
        }
    }

    @Composable
    private fun TipPrompt(onDone: () -> Unit) {
        Column(
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Container)
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("☕", fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tip_prompt_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(stringResource(R.string.tip_prompt_body), fontSize = 13.sp, color = OnSurfaceVar, lineHeight = 18.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(stringResource(R.string.buy_coffee), primary = true) { AppUpdate.openTip(this@MainActivity); onDone() }
                PillButton(stringResource(R.string.not_now), primary = false, onClick = onDone)
            }
        }
    }

    @Composable
    private fun Hero(clipUrl: String?, onDownload: (String) -> Unit) {
        var text by rememberSaveable { mutableStateOf("") }
        val submit = {
            val input = text.ifBlank { clipboardText().orEmpty() }
            onDownload(input)
            if (Links.extractUrl(input) != null) text = ""
        }
        Column(
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 18.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Container)
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceLow)
                        .border(1.dp, Outline, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.isEmpty()) Text(stringResource(R.string.paste_link), color = OnSurfaceMute, fontSize = 14.sp)
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        textStyle = TextStyle(color = OnSurface, fontSize = 14.sp, fontFamily = Rubik, textDirection = TextDirection.Ltr),
                        cursorBrush = SolidColor(OnSurface),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(10.dp))
                // הפעולה הראשית היחידה במסך: שדה ריק = הקישור מהלוח
                Box(Modifier.size(56.dp).clip(CircleShape).clickable(onClick = submit), contentAlignment = Alignment.Center) {
                    TrenkRing(56.dp)
                }
            }
            if (clipUrl != null) {
                Row(
                    Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceHigh)
                        .clickable { onDownload(clipUrl) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlatformBadge(Links.platformOf(clipUrl) ?: "")
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.clipboard_found), fontSize = 12.sp, color = OnSurfaceVar)
                        LtrText(clipUrl.removePrefix("https://").removePrefix("www."), color = OnSurface, size = 13)
                    }
                    Spacer(Modifier.width(8.dp))
                    GradientText(stringResource(R.string.download))
                }
            }
        }
    }

    @Composable
    private fun ActiveCard(item: HistoryItem) {
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Container)
                .padding(12.dp),
        ) {
            Thumb(item.thumbnail, Modifier.size(width = 96.dp, height = 58.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box(Modifier.size(32.dp).clip(CircleShape).clickable { DownloadService.cancel(this@MainActivity, item.id) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel_download), tint = OnSurfaceMute, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                ProgressBar(if (item.status == Status.DOWNLOADING) item.progress / 100f else null)
                Spacer(Modifier.height(6.dp))
                Row {
                    val state = when (item.status) {
                        Status.QUEUED -> stringResource(R.string.queued)
                        Status.SAVING -> stringResource(R.string.dl_saving)
                        else -> "${item.progress.toInt()}%"
                    }
                    Text(listOfNotNull(state, item.label).joinToString(" · "), fontSize = 12.sp, color = OnSurfaceVar, modifier = Modifier.weight(1f))
                    val eta = if (item.eta > 0) formatDuration(item.eta.toInt()) else null
                    LtrText(listOfNotNull(item.speed, eta).joinToString(" · "))
                }
            }
        }
    }

    /** פס התקדמות בגרדיאנט. null = לא ידוע (פס מלא חלש) */
    @Composable
    private fun ProgressBar(fraction: Float?) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(SurfaceHigh)) {
            val f = fraction?.coerceIn(0.02f, 1f) ?: 1f
            Box(
                Modifier
                    .fillMaxWidth(f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (fraction == null) SolidColor(Outline) else TrenkGradient),
            )
        }
    }

    @Composable
    private fun HistoryRow(item: HistoryItem) {
        val failed = item.status == Status.ERROR
        Row(
            Modifier
                .padding(horizontal = if (failed) 8.dp else 16.dp, vertical = 7.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (failed) ErrBg else Color.Transparent)
                .clickable(enabled = item.status == Status.DONE) { openFile(item) }
                .padding(horizontal = if (failed) 8.dp else 0.dp, vertical = if (failed) 8.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Thumb(item.thumbnail, Modifier.size(width = 64.dp, height = 40.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (failed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Err, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(item.error ?: stringResource(R.string.failed), fontSize = 12.sp, color = Err, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlatformBadge(item.platform, 14.dp)
                        Spacer(Modifier.width(5.dp))
                        val parts = listOfNotNull(
                            item.label ?: if (item.formatId == "mp3") "MP3" else "MP4",
                            if (item.sizeBytes > 0) formatBytes(item.sizeBytes) else null,
                            whenText(item.time),
                        )
                        Text(parts.joinToString(" · "), fontSize = 12.sp, color = OnSurfaceVar, maxLines = 1)
                    }
                }
            }
            if (failed) {
                Text(
                    stringResource(R.string.retry), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { retry(item) }.padding(horizontal = 10.dp, vertical = 12.dp),
                )
            } else {
                CircleIconButton(Icons.Filled.PlayArrow, stringResource(R.string.open), { openFile(item) }, background = Container, tint = OnSurface)
            }
        }
    }

    private fun whenText(time: Long): String {
        val clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
        return when {
            DateUtils.isToday(time) -> getString(R.string.today_at, clock)
            DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS) -> getString(R.string.yesterday_at, clock)
            else -> SimpleDateFormat("d/M", Locale.getDefault()).format(Date(time))
        }
    }

    /** מצב ריק: מה זה, למה ריק, ומה עושים */
    @Composable
    private fun EmptyState() {
        Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TrenkRing(72.dp, ringWidth = 3.dp)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.empty_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.empty_body),
                fontSize = 14.sp, color = OnSurfaceVar, lineHeight = 21.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    // ---------- הגדרות ----------

    @Composable
    private fun SettingsScreen(onBack: () -> Unit, onLicenses: () -> Unit) {
        val engine by Engine.state.collectAsState()
        var loggedIn by remember { mutableStateOf(InstagramCookies.isLoggedIn(this)) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { loggedIn = InstagramCookies.isLoggedIn(this@MainActivity) }
        }
        val version = remember { runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "" }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onBack)
                Text(stringResource(R.string.settings), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TrenkRing(44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.engine_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        val (text, color) = when (val s = engine) {
                            EngineState.Initializing -> stringResource(R.string.engine_preparing) to TrenkOrange
                            is EngineState.Ready -> stringResource(R.string.engine_ready, s.version ?: "?") to Ok
                            is EngineState.Failed -> stringResource(R.string.engine_error, s.message) to Err
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(color)
                            Spacer(Modifier.width(6.dp))
                            Text(text, fontSize = 13.sp, color = color)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                MutedLabel(stringResource(R.string.engine_auto))
            }

            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlatformBadge("instagram", 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.instagram), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(stringResource(if (loggedIn) R.string.ig_connected else R.string.ig_not_connected), fontSize = 13.sp, color = OnSurfaceVar)
                    }
                    if (loggedIn) {
                        PillButton(stringResource(R.string.logout), primary = false) { InstagramCookies.logout(this@MainActivity); loggedIn = false }
                    } else {
                        PillButton(stringResource(R.string.login), primary = true) { startActivity(Intent(this@MainActivity, InstagramLoginActivity::class.java)) }
                    }
                }
            }

            AppUpdateCard(version)

            val tip by AppUpdate.tipUrl.collectAsState()
            if (tip != null) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("☕", fontSize = 26.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.tip_card_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(stringResource(R.string.tip_card_body), fontSize = 13.sp, color = OnSurfaceVar)
                        }
                        PillButton(stringResource(R.string.buy_coffee), primary = true) { AppUpdate.openTip(this@MainActivity) }
                    }
                }
            }

            MutedLabel(stringResource(R.string.where_saved), Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp))
            Card(top = 0.dp) {
                SettingRow(stringResource(R.string.video), "Movies/Downloader")
                Spacer(Modifier.height(12.dp))
                SettingRow(stringResource(R.string.audio), "Music/Downloader")
            }

            Card {
                Row(Modifier.fillMaxWidth().clickable { History.clearFinished(); Toast.makeText(this@MainActivity, getString(R.string.history_cleared), Toast.LENGTH_SHORT).show() }) {
                    Text(stringResource(R.string.clear_history), fontSize = 15.sp, modifier = Modifier.weight(1f))
                    MutedLabel(stringResource(R.string.files_stay))
                }
            }

            AppearanceCard()
            FeaturesCard()
            ShareTrenkCard()
            LegalCard(onLicenses)

            Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TrenkLabel(10f)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.version_x, version), fontSize = 12.sp, color = OnSurfaceMute)
            }
        }
    }

    /** ערכת נושא (מוחלת מייד דרך ThemeMode) ושפה (AppCompat יוצר את המסכים מחדש ושומר את הבחירה) */
    @Composable
    private fun AppearanceCard() {
        Card {
            Text(stringResource(R.string.theme), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            Choices(
                listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
                selected = ThemeMode.mode,
            ) { ThemeMode.set(this@MainActivity, it) }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.language), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            val tags = listOf("", "he", "en")
            val current = AppCompatDelegate.getApplicationLocales().get(0)?.language?.let { if (it == "iw") "he" else it } ?: ""
            Choices(
                listOf(stringResource(R.string.lang_system), stringResource(R.string.lang_he), stringResource(R.string.lang_en)),
                selected = tags.indexOf(current).coerceAtLeast(0),
            ) { i ->
                AppCompatDelegate.setApplicationLocales(
                    if (tags[i].isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tags[i])
                )
            }
        }
    }

    /** פיצ'רים אופציונליים: כל מתג כבוי כברירת מחדל */
    @Composable
    private fun FeaturesCard() {
        Card {
            Text(stringResource(R.string.opts_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            FeatureSwitch(Options.QUEUE, R.string.opt_queue, R.string.opt_queue_desc)
            FeatureSwitch(Options.QUALITY, R.string.opt_quality, R.string.opt_quality_desc)
            FeatureSwitch(Options.TRIM, R.string.opt_trim, R.string.opt_trim_desc)
            FeatureSwitch(Options.PLAYLIST, R.string.opt_playlist, R.string.opt_playlist_desc)
        }
    }

    @Composable
    private fun FeatureSwitch(key: String, title: Int, desc: Int) {
        var on by remember { mutableStateOf(Options.get(this, key)) }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), fontSize = 14.sp)
                Text(stringResource(desc), fontSize = 12.sp, color = OnSurfaceVar)
            }
            Switch(checked = on, onCheckedChange = { on = it; Options.set(this@MainActivity, key, it) })
        }
    }

    /** בורר מקטעים: אפשרות אחת נבחרת */
    @Composable
    private fun Choices(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceHigh).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (on) Container else Color.Transparent)
                        .then(if (on) Modifier.border(1.dp, Outline, RoundedCornerShape(9.dp)) else Modifier)
                        .clickable(enabled = !on) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) OnSurface else OnSurfaceVar, maxLines = 1)
                }
            }
        }
    }

    @Composable
    private fun Card(top: androidx.compose.ui.unit.Dp = 16.dp, content: @Composable () -> Unit) {
        Column(
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = top)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Container)
                .padding(16.dp),
        ) { content() }
    }

    /** "עדכון אפליקציה" בהגדרות: בדיקה ידנית עם תשובה ברורה (גם כשאין עדכון) */
    @Composable
    private fun AppUpdateCard(installed: String) {
        val scope = rememberCoroutineScope()
        val update by AppUpdate.state.collectAsState()
        var checking by remember { mutableStateOf(false) }
        var result by remember { mutableStateOf<AppUpdate.CheckResult?>(null) }
        Card {
            Text(stringResource(R.string.app_update_title), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(stringResource(R.string.installed_version, installed), fontSize = 13.sp, color = OnSurfaceVar)
            Spacer(Modifier.height(12.dp))
            when (val s = update) {
                is AppUpdate.State.Available, is AppUpdate.State.Failed, is AppUpdate.State.Ready -> {
                    val name = (s as? AppUpdate.State.Available)?.name ?: (s as? AppUpdate.State.Failed)?.name ?: (s as AppUpdate.State.Ready).name
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.version_available, name), fontSize = 14.sp, modifier = Modifier.weight(1f))
                        PillButton(stringResource(if (s is AppUpdate.State.Ready) R.string.install else R.string.update_app), primary = true) {
                            scope.launch { AppUpdate.downloadAndInstall(this@MainActivity) }
                        }
                    }
                    if (s is AppUpdate.State.Failed) Text(s.message, color = Err, fontSize = 12.sp)
                }
                is AppUpdate.State.Downloading -> {
                    Text(stringResource(R.string.downloading_version, s.name, (s.progress * 100).toInt()), fontSize = 13.sp, color = OnSurfaceVar)
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(s.progress)
                }
                AppUpdate.State.Idle -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceHigh)
                            .clickable(enabled = !checking) {
                                checking = true
                                scope.launch {
                                    result = AppUpdate.check(this@MainActivity)
                                    checking = false
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(if (checking) R.string.checking else R.string.check_updates), fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                    when (val r = result) {
                        is AppUpdate.CheckResult.UpToDate -> Text(stringResource(R.string.up_to_date, r.latest), fontSize = 12.sp, color = Ok, modifier = Modifier.padding(top = 8.dp))
                        is AppUpdate.CheckResult.Failed -> Text(r.message, fontSize = 12.sp, color = Err, modifier = Modifier.padding(top = 8.dp))
                        else -> Unit
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingRow(label: String, value: String) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
            LtrText(value, size = 13)
        }
    }
}
