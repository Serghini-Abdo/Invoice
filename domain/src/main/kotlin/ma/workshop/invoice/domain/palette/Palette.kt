package ma.workshop.invoice.domain.palette

/**
 * The one colour table of the app (ARGB). The screen theme and the printed invoice both read it, so they
 * cannot drift apart. Contrast checks are in docs/day2/design-review.html ("Theme tokens").
 *
 * Rules: never white text on [SECONDARY] (1.8 : 1), so yellow fills get dark text and a [SECONDARY_BORDER]
 * edge; [PRIMARY_LIGHT] only behind dark text; green means "paid" and nothing else among amounts.
 */
object Palette {
    const val PRIMARY = 0xFF2E7D32L
    const val PRIMARY_LIGHT = 0xFF66BB6AL
    const val PRIMARY_CONTAINER = 0xFFE8F5E9L
    const val ON_PRIMARY_CONTAINER = 0xFF1B5E20L
    const val SECONDARY = 0xFFF2B705L
    const val SECONDARY_PRESSED = 0xFFD9A404L
    const val SECONDARY_BORDER = 0xFFA67C00L
    const val SECONDARY_CONTAINER = 0xFFFFF6D6L
    const val ERROR = 0xFFD32F2FL
    const val ERROR_CONTAINER = 0xFFFDECECL
    const val ON_ERROR_CONTAINER = 0xFF9B1C1CL
    const val GRAY = 0xFF6B7280L
    const val GRAY_STRONG = 0xFF4B5563L
    const val OUTLINE = 0xFF9CA3AFL
    const val OUTLINE_SOFT = 0xFFE5E7EBL
    const val SURFACE = 0xFFF3F4F6L
    const val CARD = 0xFFFFFFFFL
    const val INK = 0xFF1B1B1BL
    const val ON_COLOR = 0xFFFFFFFFL
}
