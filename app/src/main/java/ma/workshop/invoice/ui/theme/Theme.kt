package ma.workshop.invoice.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
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
import ma.workshop.invoice.domain.palette.Palette

/**
 * Colours that have a meaning beyond the Material roles. Read them with [MaterialTheme.appPalette]; no screen
 * uses a raw colour. Every value comes from the shared domain [Palette], like the printed invoice's.
 */
@Immutable
data class AppPalette(
    /** Green means "paid" and nothing else among amounts. */
    val paid: Color = Color(Palette.PRIMARY),
    val paidContainer: Color = Color(Palette.PRIMARY_CONTAINER),
    /** The yellow of the "+ إضافة منتوج" button and, later, the balance band. Always with dark text. */
    val balance: Color = Color(Palette.SECONDARY),
    val balancePressed: Color = Color(Palette.SECONDARY_PRESSED),
    val balanceBorder: Color = Color(Palette.SECONDARY_BORDER),
    val onBalance: Color = Color(Palette.INK),
    val error: Color = Color(Palette.ERROR),
    val errorContainer: Color = Color(Palette.ERROR_CONTAINER),
    val onErrorContainer: Color = Color(Palette.ON_ERROR_CONTAINER),
    val gray: Color = Color(Palette.GRAY),
    /** Gray for small text on the surface (gray on surface is only 4.4 : 1). */
    val grayStrong: Color = Color(Palette.GRAY_STRONG),
    val surface: Color = Color(Palette.SURFACE),
    val ink: Color = Color(Palette.INK),
    /** Text and icons on a green, red or gray fill. Never on yellow. */
    val onColor: Color = Color(Palette.ON_COLOR),
    /** Information banners: neutral, so they never read as "paid". */
    val info: Color = Color(Palette.GRAY_STRONG),
    val infoContainer: Color = Color(Palette.OUTLINE_SOFT),
    /** Warnings: dark yellow-brown text on pale yellow. */
    val warning: Color = Color(Palette.SECONDARY_BORDER),
    val onWarningContainer: Color = Color(0xFF6B4F00),
    val warningContainer: Color = Color(Palette.SECONDARY_CONTAINER),
)

private val LocalAppPalette = staticCompositionLocalOf { AppPalette() }

/** The app's own colour roles, next to [MaterialTheme.colorScheme]. */
val MaterialTheme.appPalette: AppPalette
    @Composable @ReadOnlyComposable get() = LocalAppPalette.current

val CairoFamily = FontFamily(
    Font(R.font.cairo_regular, FontWeight.Normal),
    Font(R.font.cairo_bold, FontWeight.Bold),
)

private val lightColors = lightColorScheme(
    primary = Color(Palette.PRIMARY),
    onPrimary = Color(Palette.ON_COLOR),
    primaryContainer = Color(Palette.PRIMARY_CONTAINER),
    onPrimaryContainer = Color(Palette.ON_PRIMARY_CONTAINER),
    secondary = Color(Palette.SECONDARY),
    onSecondary = Color(Palette.INK),
    secondaryContainer = Color(Palette.OUTLINE_SOFT),
    onSecondaryContainer = Color(Palette.INK),
    tertiary = Color(Palette.PRIMARY),
    onTertiary = Color(Palette.ON_COLOR),
    tertiaryContainer = Color(Palette.PRIMARY_CONTAINER),
    onTertiaryContainer = Color(Palette.ON_PRIMARY_CONTAINER),
    background = Color(Palette.SURFACE),
    onBackground = Color(Palette.INK),
    surface = Color(Palette.CARD),
    onSurface = Color(Palette.INK),
    surfaceVariant = Color(Palette.SURFACE),
    onSurfaceVariant = Color(Palette.GRAY_STRONG),
    outline = Color(Palette.GRAY),
    outlineVariant = Color(Palette.OUTLINE),
    error = Color(Palette.ERROR),
    onError = Color(Palette.ON_COLOR),
    errorContainer = Color(Palette.ERROR_CONTAINER),
    onErrorContainer = Color(Palette.ON_ERROR_CONTAINER),
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

/**
 * The layout is forced left-to-right whatever the phone's language: back on the left, forward arrows
 * pointing right, digits in phone order. Arabic text keeps its own direction and right alignment through
 * [ma.workshop.invoice.ui.components.ArabicText]. The printed invoice stays right-to-left (its renderer).
 */
@Composable
fun InvoiceTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, LocalAppPalette provides AppPalette()) {
        MaterialTheme(colorScheme = lightColors, typography = typography, shapes = shapes, content = content)
    }
}

/** Minimum touch target, comfortably above the 48dp accessibility minimum. */
val TouchTarget = 56.dp
