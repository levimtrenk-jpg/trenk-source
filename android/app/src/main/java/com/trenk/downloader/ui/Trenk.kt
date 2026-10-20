package com.trenk.downloader.ui

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.trenk.downloader.R

// ---------- טוקנים (docs/DESIGN.md) ----------
// פלטה כהה שנבנתה בפני עצמה (לא היפוך): משטחים מתבהרים ככל שהם "עולים"
val Bg = Color(0xFF0E1015)
val SurfaceLow = Color(0xFF151821)
val Container = Color(0xFF1B1F2A)
val SurfaceHigh = Color(0xFF232835)
val Outline = Color(0xFF2E3442)
val OnSurface = Color(0xFFE8EAF0)
val OnSurfaceVar = Color(0xFFA1A7B8)
val OnSurfaceMute = Color(0xFF757C8F)
val Ok = Color(0xFF5BD08A)
val Err = Color(0xFFFF7A7A)
val ErrBg = Color(0xFF2A1A1F)
val TrenkOrange = Color(0xFFFFB84D)
val TrenkPink = Color(0xFFFF5E8A)
val TrenkPurple = Color(0xFF8B6CFF)
/** הגרדיאנט: רק לרגעי מותג ולפעולה הראשית (~10% מהמסך) */
val TrenkGradient = Brush.linearGradient(listOf(TrenkOrange, TrenkPink, TrenkPurple))
private val ringColors = listOf(TrenkOrange, TrenkPink, TrenkPurple, TrenkOrange)

// Rubik (OFL) – גופן משתנה אחד בכל המשקלים
@OptIn(ExperimentalTextApi::class)
private fun rubik(weight: Int) =
    Font(R.font.rubik, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

@OptIn(ExperimentalTextApi::class)
val Rubik = FontFamily(rubik(400), rubik(500), rubik(600), rubik(700))

private val AppTypography = Typography().let { t ->
    fun TextStyle.r() = copy(fontFamily = Rubik)
    t.copy(
        displayLarge = t.displayLarge.r(), displayMedium = t.displayMedium.r(), displaySmall = t.displaySmall.r(),
        headlineLarge = t.headlineLarge.r(), headlineMedium = t.headlineMedium.r(), headlineSmall = t.headlineSmall.r(),
        titleLarge = t.titleLarge.r(), titleMedium = t.titleMedium.r(), titleSmall = t.titleSmall.r(),
        bodyLarge = t.bodyLarge.r(), bodyMedium = t.bodyMedium.r(), bodySmall = t.bodySmall.r(),
        labelLarge = t.labelLarge.r(), labelMedium = t.labelMedium.r(), labelSmall = t.labelSmall.r(),
    )
}

/** ערכת עיצוב כהה, ממשק בעברית מימין לשמאל בלי קשר לשפת הטלפון */
@Composable
fun TrenkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TrenkPink,
            secondary = TrenkPurple,
            tertiary = TrenkOrange,
            background = Bg,
            surface = Container,
            surfaceContainerLowest = Bg,
            surfaceContainerLow = SurfaceLow,
            surfaceContainer = Container,
            surfaceContainerHigh = SurfaceHigh,
            surfaceContainerHighest = SurfaceHigh,
            onBackground = OnSurface,
            onSurface = OnSurface,
            onSurfaceVariant = OnSurfaceVar,
            outline = Outline,
            outlineVariant = Outline,
            error = Err,
            onPrimary = Color.White,
        ),
        typography = AppTypography,
    ) {
        // כיוון לפי שפת המחרוזות שנבחרה (עברית = RTL, אנגלית = LTR), גם במכשיר בשפה שלישית
        val rtl = androidx.compose.ui.res.booleanResource(com.trenk.downloader.R.bool.rtl)
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr, content = content)
    }
}

/** המילה TRENK הזעירה בגרדיאנט */
@Composable
fun TrenkLabel(size: Float = 6.5f) {
    Text(
        "TRENK",
        style = TextStyle(
            brush = TrenkGradient,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = size.sp,
            letterSpacing = 0.35.em,
        ),
    )
}

/** מסגרת גרדיאנט מסתובבת (3 שניות לסיבוב). כבויה אם המשתמש ביטל אנימציות במערכת. */
@Composable
private fun rememberSpin(): Float {
    val context = LocalContext.current
    val animationsOff = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    if (animationsOff) return 0f
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "angle",
    )
    return angle
}

/** עיגול TRENK: טבעת גרדיאנט מסתובבת וחץ הורדה לבן באמצע (אותו SVG כמו בתוסף) */
@Composable
fun TrenkRing(size: Dp, modifier: Modifier = Modifier, ringWidth: Dp = 2.dp, arrow: Boolean = true) {
    val angle = rememberSpin()
    Canvas(modifier.size(size)) {
        val ring = ringWidth.toPx()
        rotate(angle) { drawCircle(Brush.sweepGradient(ringColors)) }
        drawCircle(Color(0xFF111319), radius = this.size.minDimension / 2 - ring)
        if (arrow) {
            // M12 4v11 M7 10l5 5 5-5 M5 20h14 (viewBox 24), בגודל 45% מהעיגול
            val unit = this.size.minDimension * 0.45f / 24f
            val ox = center.x - 12 * unit
            val oy = center.y - 12 * unit
            fun p(x: Float, y: Float) = Offset(ox + x * unit, oy + y * unit)
            val w = 2.4f * unit
            drawLine(Color.White, p(12f, 4f), p(12f, 15f), w, StrokeCap.Round)
            drawLine(Color.White, p(7f, 10f), p(12f, 15f), w, StrokeCap.Round)
            drawLine(Color.White, p(12f, 15f), p(17f, 10f), w, StrokeCap.Round)
            drawLine(Color.White, p(5f, 20f), p(19f, 20f), w, StrokeCap.Round)
        }
    }
}
