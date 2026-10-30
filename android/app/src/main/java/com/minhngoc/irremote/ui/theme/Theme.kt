package com.minhngoc.irremote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.minhngoc.irremote.R

/**
 * "Molded remote" palette: a cool porcelain room, a lighter remote body on top
 * of it, raised keys on the body. Accent colors come from the device category.
 */
@Immutable
data class RemotePalette(
    val room: Color,        // screen background
    val body: Color,        // remote body slab
    val key: Color,         // raised key
    val keyPressed: Color,
    val ink: Color,         // primary text / key glyphs
    val inkSoft: Color,     // secondary text
    val groove: Color,      // recessed areas (rocker tracks, D-pad ring)
    val shadow: Color,
    val power: Color,
)

private val LightPalette = RemotePalette(
    room = Color(0xFFE8ECF0),
    body = Color(0xFFF9FAFB),
    key = Color(0xFFFFFFFF),
    keyPressed = Color(0xFFDCE2E8),
    ink = Color(0xFF1D232B),
    inkSoft = Color(0xFF66707C),
    groove = Color(0xFFEDF0F3),
    shadow = Color(0x332A3542),
    power = Color(0xFFE5484D),
)

private val DarkPalette = RemotePalette(
    room = Color(0xFF16191D),
    body = Color(0xFF22262C),
    key = Color(0xFF2E333A),
    keyPressed = Color(0xFF3B414A),
    ink = Color(0xFFEDF0F3),
    inkSoft = Color(0xFF9AA4AF),
    groove = Color(0xFF1B1E23),
    shadow = Color(0x66000000),
    power = Color(0xFFF2555A),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

val BeVietnamPro = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
    Font(R.font.be_vietnam_pro_bold, FontWeight.Bold),
)

private fun style(size: Int, weight: FontWeight, line: Int, tracking: Double = 0.0) =
    TextStyle(fontFamily = BeVietnamPro, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.em)

// Modular scale ~1.25 from a 15sp body.
private val AppTypography = Typography(
    displayLarge = style(72, FontWeight.SemiBold, 76, -0.03),
    displayMedium = style(48, FontWeight.SemiBold, 52, -0.02),
    headlineMedium = style(28, FontWeight.SemiBold, 34, -0.01),
    headlineSmall = style(23, FontWeight.SemiBold, 30, -0.01),
    titleLarge = style(19, FontWeight.SemiBold, 26),
    titleMedium = style(16, FontWeight.SemiBold, 22),
    titleSmall = style(14, FontWeight.Medium, 20),
    bodyLarge = style(16, FontWeight.Normal, 24),
    bodyMedium = style(15, FontWeight.Normal, 22),
    bodySmall = style(13, FontWeight.Normal, 18),
    labelLarge = style(15, FontWeight.Medium, 20),
    labelMedium = style(13, FontWeight.Medium, 16),
    labelSmall = style(11, FontWeight.Medium, 14, 0.02),
)

@Composable
fun IrTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) DarkPalette else LightPalette
    val accent = Color(0xFF2F5BEA)
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF8AA6FF), onPrimary = Color(0xFF0B1A4A),
            background = palette.room, surface = palette.body, onBackground = palette.ink, onSurface = palette.ink,
            surfaceVariant = palette.key, onSurfaceVariant = palette.inkSoft,
            surfaceContainer = palette.body, surfaceContainerLow = palette.body, surfaceContainerHigh = palette.key,
            error = palette.power,
        )
    } else {
        lightColorScheme(
            primary = accent, onPrimary = Color.White,
            background = palette.room, surface = palette.body, onBackground = palette.ink, onSurface = palette.ink,
            surfaceVariant = palette.groove, onSurfaceVariant = palette.inkSoft,
            surfaceContainer = palette.body, surfaceContainerLow = palette.body, surfaceContainerHigh = palette.key,
            error = palette.power,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
