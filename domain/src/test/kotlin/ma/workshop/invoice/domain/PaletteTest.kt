package ma.workshop.invoice.domain

import ma.workshop.invoice.domain.layout.InvoiceColors
import ma.workshop.invoice.domain.palette.Palette
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaletteTest {

    /** The printed receipt must never change colour when the app palette does (step 1.2). */
    @Test
    fun `the printed receipt colours are pinned to the values it was approved with`() {
        assertEquals(0xFF1B1B1BL, InvoiceColors.INK)
        assertEquals(0xFF4B5563L, InvoiceColors.MUTED)
        assertEquals(0xFF2E7D32L, InvoiceColors.ACCENT)
        assertEquals(0xFFE5E7EBL, InvoiceColors.DIVIDER)
        assertEquals(0xFFF3F4F6L, InvoiceColors.PANEL)
        assertEquals(0xFF2E7D32L, InvoiceColors.PAID)
        assertEquals(0xFFE8F5E9L, InvoiceColors.PAID_BG)
        assertEquals(0xFFA67C00L, InvoiceColors.BALANCE_TEXT)
        assertEquals(0xFFFFFFFFL, InvoiceColors.WHITE)
    }

    @Test
    fun `white text on the blue action colour is readable`() {
        assertTrue(contrast(Palette.ON_COLOR, Palette.PRIMARY) >= 4.5, "white on PRIMARY")
        assertTrue(contrast(Palette.ON_PRIMARY_CONTAINER, Palette.PRIMARY_CONTAINER) >= 4.5, "text on the pale blue")
        assertTrue(contrast(Palette.ON_PAID_CONTAINER, Palette.PAID_CONTAINER) >= 4.5, "text on the pale green")
    }

    @Test
    fun `blue is the action colour and green is separate`() {
        assertTrue(Palette.PRIMARY != Palette.PAID)
        assertEquals(0xFF1F5FAFL, Palette.PRIMARY)
    }

    /** WCAG 2.x contrast ratio between two opaque ARGB colours. */
    private fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
