package ma.workshop.invoice.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.workshop.invoice.R

object AppColors {
    val Rust = Color(0xFFB8541A)
    val RustDark = Color(0xFF8A3B0F)
    val RustLight = Color(0xFFFFE3D2)
    val Steel = Color(0xFF37474F)
    val SteelMid = Color(0xFF607D8B)
    val SteelPale = Color(0xFFE3E9EC)
    val Paper = Color(0xFFFAF7F2)
    val Green = Color(0xFF1B6B3A)
    val GreenPale = Color(0xFFE3F4E8)
    val Red = Color(0xFFB3261E)
    val RedPale = Color(0xFFFCE4E1)
    val Amber = Color(0xFF8A5A00)
    val AmberPale = Color(0xFFFFF0CC)
}

val CairoFamily = FontFamily(
    Font(R.font.cairo_regular, FontWeight.Normal),
    Font(R.font.cairo_bold, FontWeight.Bold),
)

private val lightColors = lightColorScheme(
    primary = AppColors.Rust,
    onPrimary = Color.White,
    primaryContainer = AppColors.RustLight,
    onPrimaryContainer = AppColors.RustDark,
    secondary = AppColors.Steel,
    onSecondary = Color.White,
    secondaryContainer = AppColors.SteelPale,
    onSecondaryContainer = AppColors.Steel,
    tertiary = AppColors.Green,
    onTertiary = Color.White,
    tertiaryContainer = AppColors.GreenPale,
    onTertiaryContainer = AppColors.Green,
    background = AppColors.Paper,
    onBackground = Color(0xFF1B1B1B),
    surface = Color.White,
    onSurface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFFEFE9E2),
    onSurfaceVariant = Color(0xFF4A4F54),
    outline = Color(0xFF7A8085),
    outlineVariant = Color(0xFFCFC8BF),
    error = AppColors.Red,
    onError = Color.White,
    errorContainer = AppColors.RedPale,
    onErrorContainer = AppColors.Red,
)

private fun text(size: Int, bold: Boolean = false) = TextStyle(
    fontFamily = CairoFamily,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = (size * 1.5f).sp,
)

/** Large type throughout: the owner reads little, so every size is a step above the Material default. */
private val typography = Typography(
    displayLarge = text(40, true),
    displayMedium = text(36, true),
    displaySmall = text(32, true),
    headlineLarge = text(32, true),
    headlineMedium = text(28, true),
    headlineSmall = text(24, true),
    titleLarge = text(24, true),
    titleMedium = text(20, true),
    titleSmall = text(18, true),
    bodyLarge = text(20),
    bodyMedium = text(18),
    bodySmall = text(16),
    labelLarge = text(20, true),
    labelMedium = text(16, true),
    labelSmall = text(14),
)

private val shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

/** The whole app is Arabic: layout is forced right-to-left regardless of the phone's language. */
@Composable
fun InvoiceTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = lightColors, typography = typography, shapes = shapes, content = content)
    }
}

/** Minimum touch target, comfortably above the 48dp accessibility minimum. */
val TouchTarget = 56.dp
