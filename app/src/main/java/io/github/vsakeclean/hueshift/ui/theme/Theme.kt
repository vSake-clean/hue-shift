package io.github.vsakeclean.hueshift.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** hue-shift's palette: deep, slightly violet blacks with a pink → violet → teal accent. */
object HueColors {
    val Background = Color(0xFF09090F)
    val BackgroundGlow = Color(0xFF15122A)
    val Surface = Color(0xFF14141D)
    val SurfaceRaised = Color(0xFF1B1B27)
    val SurfaceHigh = Color(0xFF252534)
    val Outline = Color(0xFF282837)
    val OutlineStrong = Color(0xFF3A3A4F)

    val TextPrimary = Color(0xFFF5F5FA)
    val TextSecondary = Color(0xFFA7A7BE)
    val TextTertiary = Color(0xFF6F6F88)

    val Pink = Color(0xFFFF6B9D)
    val Violet = Color(0xFFA78BFA)
    val Teal = Color(0xFF5EEAD4)

    val Success = Color(0xFF4ADE80)
    val Warning = Color(0xFFFBBF24)
    val Danger = Color(0xFFF87171)

    val BrandColors = listOf(Pink, Violet, Teal)
    val Brand: Brush = Brush.linearGradient(BrandColors)
}

private val ColorScheme = darkColorScheme(
    primary = HueColors.Violet,
    onPrimary = Color(0xFF140F2B),
    primaryContainer = Color(0xFF2C2552),
    onPrimaryContainer = HueColors.TextPrimary,
    secondary = HueColors.Teal,
    onSecondary = Color(0xFF062621),
    tertiary = HueColors.Pink,
    background = HueColors.Background,
    onBackground = HueColors.TextPrimary,
    surface = HueColors.Surface,
    onSurface = HueColors.TextPrimary,
    surfaceVariant = HueColors.SurfaceRaised,
    onSurfaceVariant = HueColors.TextSecondary,
    surfaceContainerLowest = HueColors.Background,
    surfaceContainerLow = HueColors.Surface,
    surfaceContainer = HueColors.Surface,
    surfaceContainerHigh = HueColors.SurfaceRaised,
    surfaceContainerHighest = HueColors.SurfaceHigh,
    outline = HueColors.OutlineStrong,
    outlineVariant = HueColors.Outline,
    error = HueColors.Danger,
)

private val BaseTypography = Typography()

private val HueTypography = Typography(
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.3).sp),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 21.sp),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.5.sp),
    titleSmall = BaseTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = BaseTypography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = BaseTypography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = BaseTypography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = BaseTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
)

private val HueShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

/** Text style with the brand gradient, used for the app name. */
val BrandTitleStyle: TextStyle
    @Composable get() = MaterialTheme.typography.headlineSmall.copy(brush = HueColors.Brand)

@Composable
fun HueShiftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = HueTypography,
        shapes = HueShapes,
    ) {
        // Text outside of Material surfaces should be light, not the default black.
        CompositionLocalProvider(LocalContentColor provides HueColors.TextPrimary, content = content)
    }
}
