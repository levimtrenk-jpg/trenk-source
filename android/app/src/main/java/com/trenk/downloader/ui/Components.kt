package com.trenk.downloader.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import coil.compose.AsyncImage

/** תג קטן של הפלטפורמה (צבע + סימן), כדי לא להסתמך על צבע בלבד */
@Composable
fun PlatformBadge(platform: String, size: Dp = 16.dp) {
    val (bg, glyph) = when (platform) {
        "youtube" -> Brush.linearGradient(listOf(Color(0xFFE3342F), Color(0xFFE3342F))) to "▶"
        "tiktok" -> Brush.linearGradient(listOf(Color(0xFF111111), Color(0xFF111111))) to "♪"
        "instagram" -> Brush.linearGradient(listOf(Color(0xFFF9A03F), Color(0xFFE1306C), Color(0xFF833AB4))) to "◎"
        else -> Brush.linearGradient(listOf(SurfaceHigh, SurfaceHigh)) to "•"
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.25f))
            .background(bg)
            .then(if (platform == "tiktok") Modifier.border(1.dp, Color(0xFF3A3F4C), RoundedCornerShape(size * 0.25f)) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = Color.White, fontSize = (size.value * 0.55f).sp, fontWeight = FontWeight.Bold)
    }
}

fun platformName(platform: String) = when (platform) {
    "youtube" -> "YouTube"
    "tiktok" -> "TikTok"
    "instagram" -> "Instagram"
    else -> platform
}

/** טקסט בגרדיאנט המותג (לפעולה ראשית קטנה, למשל "הורד") */
@Composable
fun GradientText(text: String, modifier: Modifier = Modifier, size: Int = 14) {
    Text(text, modifier, style = TextStyle(brush = TrenkGradient, fontSize = size.sp, fontWeight = FontWeight.SemiBold, fontFamily = Rubik))
}

/** טקסט שמאל-לימין בתוך ממשק עברי (קישורים, גדלים, מהירות) – כדי שהסימנים לא יקפצו */
@Composable
fun LtrText(text: String, color: Color = OnSurfaceVar, size: Int = 12, modifier: Modifier = Modifier, maxLines: Int = 1) {
    Text(
        text, modifier, color = color, fontSize = size.sp, maxLines = maxLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
    )
}

@Composable
fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) SurfaceHigh else Container)
            .then(if (selected) Modifier.border(1.dp, Outline, CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 12.sp, color = if (selected) OnSurface else OnSurfaceVar)
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 7.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** תמונה ממוזערת עם רקע כהה בזמן טעינה ותג אופציונלי (משך) */
@Composable
fun Thumb(url: String?, modifier: Modifier, radius: Dp = 10.dp, badge: String? = null) {
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(Brush.linearGradient(listOf(Color(0xFF2E2840), Color(0xFF1E2433)))),
    ) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        if (badge != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd) // ב-RTL: פינה שמאלית תחתונה
                    .padding(4.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xB8000000))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) { LtrText(badge, color = Color.White, size = 10) }
        }
    }
}

/** כפתור עגול עם אייקון: 40dp נראה, 48dp מגע */
@Composable
fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit, background: Color = Color.Transparent, tint: Color = OnSurfaceVar) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}

/** מסגרת גרדיאנט סביב תוכן (האפשרות המומלצת) */
@Composable
fun GradientBorder(radius: Dp, width: Dp = 2.dp, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(RoundedCornerShape(radius)).background(TrenkGradient).padding(width)) {
        Box(Modifier.clip(RoundedCornerShape(radius - width)).background(Container), content = content)
    }
}

/** בלוק שלד שמהבהב בזמן טעינה (במקום ספינר) */
@Composable
fun Skeleton(modifier: Modifier, radius: Dp = 10.dp) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Box(modifier.alpha(a).clip(RoundedCornerShape(radius)).background(SurfaceHigh))
}

/** כפיית RTL בתוך תת-עץ (למקרה שמשהו עוטף ב-LTR) */
@Composable
fun Rtl(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
}

@Composable
fun MutedLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, fontSize = 12.sp, color = OnSurfaceMute)
}

@Composable
fun RowCenter(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, content = content)
}
