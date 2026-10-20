package com.trenk.downloader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trenk.downloader.ui.CircleIconButton
import com.trenk.downloader.ui.Container
import com.trenk.downloader.ui.GradientBorder
import com.trenk.downloader.ui.MutedLabel
import com.trenk.downloader.ui.OnSurface
import com.trenk.downloader.ui.OnSurfaceMute
import com.trenk.downloader.ui.OnSurfaceVar
import com.trenk.downloader.ui.Outline
import com.trenk.downloader.ui.SurfaceHigh
import com.trenk.downloader.ui.TrenkGradient
import com.trenk.downloader.ui.TrenkLabel
import com.trenk.downloader.ui.TrenkRing
import kotlinx.coroutines.launch

/** מסך הפתיחה מוצג פעם אחת בלבד */
object Onboarding {
    fun done(context: Context) = context.getSharedPreferences("app", Context.MODE_PRIVATE).getBoolean("onboarding_done", false)
    fun markDone(context: Context) =
        context.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putBoolean("onboarding_done", true).apply()
}

// ---------- מסך פתיחה: שתף ← TRENK ← בחר איכות ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == 2
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TrenkLabel(10f)
            Spacer(Modifier.weight(1f))
            if (!last) {
                Text(
                    stringResource(R.string.onb_skip), color = OnSurfaceVar, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onDone).padding(12.dp),
                )
            }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.height(240.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when (page) {
                        0 -> ShareVisual()
                        1 -> SheetVisual()
                        else -> QualityVisual()
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    stringResource(listOf(R.string.onb1_title, R.string.onb2_title, R.string.onb3_title)[page]),
                    fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(listOf(R.string.onb1_body, R.string.onb2_body, R.string.onb3_body)[page]),
                    fontSize = 15.sp, color = OnSurfaceVar, lineHeight = 22.sp, textAlign = TextAlign.Center,
                )
                if (page == 1) {
                    Spacer(Modifier.height(18.dp))
                    Row(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(Container).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("📌", fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.onb_pin_tip), fontSize = 13.sp, color = OnSurface, lineHeight = 18.sp)
                    }
                }
            }
        }
        // נקודות התקדמות
        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.Center) {
            repeat(3) { i ->
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = if (i == pager.currentPage) 22.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape)
                        .then(if (i == pager.currentPage) Modifier.background(TrenkGradient) else Modifier.background(Outline)),
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(TrenkGradient)
                    .clickable { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (last) R.string.onb_start else R.string.onb_next),
                    color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                )
            }
        }
    }
}

/** שקופית 1: סרטון וכפתור "שתף" */
@Composable
private fun ShareVisual() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(width = 220.dp, height = 124.dp).clip(RoundedCornerShape(18.dp)).background(SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = OnSurfaceVar, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(18.dp))
        GradientBorder(radius = 24.dp, width = 2.dp) {
            Row(Modifier.padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Share, contentDescription = null, tint = OnSurface, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.onb_share_word), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}

/** שקופית 2: חלון השיתוף, TRENK מודגש */
@Composable
private fun SheetVisual() {
    Column(
        Modifier.width(260.dp).clip(RoundedCornerShape(22.dp)).background(Container).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 40.dp, height = 4.dp).clip(CircleShape).background(Outline))
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TrenkRing(48.dp, ringWidth = 3.dp)
                Spacer(Modifier.height(6.dp))
                Text("TRENK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            repeat(3) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(SurfaceHigh))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.size(width = 30.dp, height = 8.dp).clip(CircleShape).background(SurfaceHigh))
                }
            }
        }
    }
}

/** שקופית 3: כרטיסי איכות */
@Composable
private fun QualityVisual() {
    Column(Modifier.width(250.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GradientBorder(radius = 14.dp, modifier = Modifier.fillMaxWidth()) {
            QualityRow("1080p", stringResource(R.string.recommended))
        }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Container)) { QualityRow("720p", null) }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Container)) { QualityRow("MP3", null) }
    }
}

@Composable
private fun QualityRow(label: String, tag: String?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (tag != null) {
            Text(
                tag, fontSize = 11.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(SurfaceHigh).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

// ---------- הגדרות: שיתוף ומידע משפטי ----------

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Container)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun ShareTrenkCard() {
    val context = LocalContext.current
    val site = stringResource(R.string.site_url)
    val text = stringResource(R.string.share_trenk_text, site)
    val chooser = stringResource(R.string.share_trenk)
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💛", fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.share_trenk), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(stringResource(R.string.share_trenk_sub), fontSize = 13.sp, color = OnSurfaceVar)
            }
            PillButton(stringResource(R.string.share), primary = true) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, chooser))
            }
        }
    }
}

@Composable
fun LegalCard(onLicenses: () -> Unit) {
    val context = LocalContext.current
    val site = stringResource(R.string.site_url)
    fun open(url: String) = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(context, url, Toast.LENGTH_LONG).show() }
    SettingsCard {
        LinkRow(stringResource(R.string.privacy)) { open(site + "privacy.html") }
        LinkRow(stringResource(R.string.terms)) { open(site + "terms.html") }
        LinkRow(stringResource(R.string.licenses), onClick = onLicenses)
        LinkRow(stringResource(R.string.contact), last = true) { open("mailto:" + BuildConfig.CONTACT_EMAIL) }
    }
}

@Composable
private fun LinkRow(text: String, last: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("›", fontSize = 18.sp, color = OnSurfaceMute)
    }
    if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(Outline))
}

// ---------- רישיונות וקרדיטים: THIRD_PARTY_NOTICES + הטקסטים המלאים (assets/legal) ----------

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val notices = remember {
        runCatching { context.assets.open("legal/THIRD_PARTY_NOTICES.md").bufferedReader().use { it.readText() } }.getOrDefault("")
            .split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }
    }
    val files = remember { context.assets.list("legal/licenses")?.sorted().orEmpty() }
    var openFile by rememberSaveable { mutableStateOf<String?>(null) }
    val fullText = remember(openFile) {
        openFile?.let { f ->
            runCatching { context.assets.open("legal/licenses/$f").bufferedReader().use { it.readText() } }.getOrDefault("")
                .split(Regex("\n\\s*\n"))
        }.orEmpty()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), { if (openFile != null) openFile = null else onBack() })
            Text(openFile ?: stringResource(R.string.licenses), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp)) {
            if (openFile != null) {
                // טקסטי רישיון באנגלית (המקור המחייב), תמיד משמאל לימין
                items(fullText) { para ->
                    Text(para.trim(), fontSize = 12.sp, color = OnSurfaceVar, fontFamily = FontFamily.Monospace,
                        style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                        modifier = Modifier.padding(bottom = 10.dp))
                }
            } else {
                item { MutedLabel(stringResource(R.string.licenses_intro), Modifier.padding(bottom = 14.dp)) }
                // קוד המקור של TRENK עצמו (GPL-3.0) בריפו הציבורי trenk-source
                item {
                    val url = stringResource(R.string.source_url)
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp)).background(Container)
                            .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(stringResource(R.string.open_source), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.open_source_link) + " ›", fontSize = 13.sp, color = OnSurfaceVar)
                    }
                }
                items(notices) { para ->
                    val title = para.startsWith("#")
                    Text(
                        para.trimStart('#', ' ').replace("**", ""),
                        fontSize = if (title) 16.sp else 13.sp,
                        fontWeight = if (title) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (title) OnSurface else OnSurfaceVar,
                        style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                        modifier = Modifier.padding(top = if (title) 14.dp else 0.dp, bottom = 8.dp),
                    )
                }
                item { Text(stringResource(R.string.licenses_full_texts), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)) }
                items(files) { f ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(Container)
                            .clickable { openFile = f }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(f.removeSuffix(".txt"), fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("›", fontSize = 18.sp, color = OnSurfaceMute)
                    }
                }
            }
        }
    }
}
