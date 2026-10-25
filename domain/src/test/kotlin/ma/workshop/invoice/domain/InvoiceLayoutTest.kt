package ma.workshop.invoice.domain

import ma.workshop.invoice.domain.format.DocumentRow
import ma.workshop.invoice.domain.format.InvoiceDocument
import ma.workshop.invoice.domain.layout.DrawOp
import ma.workshop.invoice.domain.layout.HAlign
import ma.workshop.invoice.domain.layout.InvoiceLayout
import ma.workshop.invoice.domain.layout.InvoiceLayoutEngine
import ma.workshop.invoice.domain.layout.LayoutPage
import ma.workshop.invoice.domain.layout.LayoutSpec
import ma.workshop.invoice.domain.layout.TextMeasurer
import ma.workshop.invoice.domain.layout.TextStyle
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InvoiceLayoutTest {

    /** Deterministic stand-in for StaticLayout: fixed glyph width, greedy word wrap, 1.4 line height. */
    private object FakeMeasurer : TextMeasurer {
        override fun measureHeight(text: String, style: TextStyle, maxWidth: Float): Float {
            val charWidth = style.size * 0.55f
            val perLine = maxOf(1, (maxWidth / charWidth).toInt())
            var lines = 0
            for (paragraph in text.split('\n')) {
                var used = 0
                var paragraphLines = 1
                for (word in paragraph.split(' ')) {
                    val len = word.length
                    if (used == 0) used = len
                    else if (used + 1 + len <= perLine) used += 1 + len
                    else { paragraphLines++; used = len }
                    while (used > perLine) { paragraphLines++; used -= perLine }
                }
                lines += paragraphLines
            }
            return ceil(lines * style.size * 1.4f)
        }
    }

    private val engine = InvoiceLayoutEngine(FakeMeasurer)

    private fun row(i: Int, name: String = "باب حديد") = DocumentRow(
        position = i, illustration = "door", name = name, measures = "1,20 × 2,10 م", quantity = 1,
        rate = "500 DH / م²", calculation = "2,52 م² × 500 DH × 1", total = "1 260 DH",
    )

    private fun doc(
        rows: Int = 2,
        paid: Boolean = false,
        customer: Boolean = true,
        name: String = "باب حديد",
        thankYou: String? = "شكراً لثقتكم",
    ) = InvoiceDocument(
        title = "فاتورة", workshopName = "ورشة الأمل للحدادة", workshopPhone = "0522000000",
        number = "0007", date = "25/10/2026", updatedNote = null,
        customerName = if (customer) "أحمد" else null, customerPhone = if (customer) "0612345678" else null,
        rows = (1..rows).map { row(it, name) },
        total = "1 380 DH", deposit = "500 DH", remaining = if (paid) "0 DH" else "880 DH",
        isFullyPaid = paid, thankYou = thankYou,
    )

    private val phone = LayoutSpec(width = 360f)
    private val a4 = LayoutSpec(width = 520f, pageHeight = 520f * 842f / 595f)

    private fun texts(page: LayoutPage) = page.ops.filterIsInstance<DrawOp.Text>()
    private fun images(page: LayoutPage) = page.ops.filterIsInstance<DrawOp.Image>()
    private fun allTexts(layout: InvoiceLayout) = layout.pages.flatMap { texts(it) }

    private fun opBounds(op: DrawOp, measureText: (DrawOp.Text) -> Float): List<Float> = when (op) {
        is DrawOp.Rect -> listOf(op.x, op.y, op.x + op.width, op.y + op.height)
        is DrawOp.Line -> listOf(minOf(op.x1, op.x2), minOf(op.y1, op.y2), maxOf(op.x1, op.x2), maxOf(op.y1, op.y2))
        is DrawOp.Image -> listOf(op.x, op.y, op.x + op.size, op.y + op.size)
        is DrawOp.Text -> listOf(op.x, op.y, op.x + op.width, op.y + measureText(op))
    }

    private fun assertInsidePages(layout: InvoiceLayout, margin: Float = 16f) {
        for ((index, page) in layout.pages.withIndex()) {
            for (op in page.ops) {
                val (x1, y1, x2, y2) = opBounds(op) { FakeMeasurer.measureHeight(it.text, it.style, it.width) }
                val where = "page ${index + 1}: $op"
                assertTrue(x1 >= margin - 0.01f, "left of margin, $where")
                assertTrue(x2 <= layout.width - margin + 0.01f, "right of margin, $where")
                assertTrue(y1 >= 0f, "above page, $where")
                assertTrue(y2 <= page.height + 0.01f, "below page bottom ($y2 > ${page.height}), $where")
            }
        }
    }

    @Test
    fun `a short invoice is one page containing every figure`() {
        val layout = engine.layout(doc(), phone)
        assertEquals(1, layout.pages.size)
        val text = allTexts(layout).map { it.text }
        for (expected in listOf("ورشة الأمل للحدادة", "0522000000", "فاتورة رقم 0007", "25/10/2026",
            "الزبون: أحمد", "الهاتف: 0612345678", "1 380 DH", "500 DH", "880 DH", "المجموع", "التسبيق", "الباقي", "شكراً لثقتكم")) {
            assertTrue(expected in text, "missing '$expected'")
        }
        assertInsidePages(layout)
    }

    @Test
    fun `thumbnails are small fixed size squares aligned with their row`() {
        val layout = engine.layout(doc(rows = 5), phone)
        val thumbs = images(layout.pages[0])
        assertEquals(5, thumbs.size)
        assertTrue(thumbs.all { it.size == 64f }, "all thumbnails must have the same size")
        assertTrue(thumbs.all { it.size in 60f..80f }, "thumbnail size must stay within the 60-80 dp guideline")
        val xs = thumbs.map { it.x }.toSet()
        assertEquals(1, xs.size, "thumbnails must share one alignment")
        assertTrue(thumbs.all { it.x + it.size <= phone.width - phone.margin + 0.01f })
        // each thumbnail is beside its own name and total, not above/below
        val names = texts(layout.pages[0]).filter { it.text == "باب حديد" }
        for ((thumb, name) in thumbs.zip(names)) {
            val nameBottom = name.y + FakeMeasurer.measureHeight(name.text, name.style, name.width)
            assertTrue(name.y <= thumb.y + thumb.size && nameBottom >= thumb.y, "name must sit beside its thumbnail")
            assertTrue(name.x + name.width <= thumb.x, "text must not overlap thumbnail")
        }
    }

    @Test
    fun `the payment summary stays highly readable and is the largest text`() {
        val layout = engine.layout(doc(), phone)
        val totalValues = texts(layout.pages[0]).filter { it.style == TextStyle.TOTAL }
        assertEquals(listOf("1 380 DH", "500 DH", "880 DH"), totalValues.map { it.text })
        assertTrue(TextStyle.TOTAL.size > TextStyle.BODY_BOLD.size)
        assertTrue(totalValues.all { it.align == HAlign.LEFT })
    }

    @Test
    fun `a fully paid invoice shows an explicit paid badge as text`() {
        val paid = engine.layout(doc(paid = true), phone)
        assertTrue(allTexts(paid).any { it.text == "خالص" && it.style == TextStyle.BADGE })
        val unpaid = engine.layout(doc(paid = false), phone)
        assertFalse(allTexts(unpaid).any { it.text == "خالص" })
    }

    @Test
    fun `optional customer and thank-you blocks disappear cleanly`() {
        val bare = engine.layout(doc(customer = false, thankYou = null), phone)
        val text = allTexts(bare).map { it.text }
        assertFalse(text.any { it.startsWith("الزبون") || it.startsWith("الهاتف") })
        assertFalse("شكراً لثقتكم" in text)
        assertInsidePages(bare)
        assertTrue(bare.pages[0].height < engine.layout(doc(), phone).pages[0].height)
    }

    @Test
    fun `long product names wrap inside their column and grow the row`() {
        val long = "باب حديد مزخرف بزخارف عربية تقليدية مع نافذة زجاجية علوية وقفل أمان إضافي"
        val normal = engine.layout(doc(rows = 1), phone).pages[0].height
        val wrapped = engine.layout(doc(rows = 1, name = long), phone)
        assertTrue(wrapped.pages[0].height > normal)
        assertInsidePages(wrapped)
        val name = texts(wrapped.pages[0]).first { it.text == long }
        assertTrue(name.width < phone.width - 2 * phone.margin - 64f)
    }

    // Scenario 17: long invoices export without clipping
    @Test
    fun `a very long invoice on one tall page is not clipped`() {
        val layout = engine.layout(doc(rows = 120), phone)
        assertEquals(1, layout.pages.size)
        assertEquals(120, images(layout.pages[0]).size)
        assertInsidePages(layout)
        assertTrue(layout.pages[0].height > 1000f)
    }

    @Test
    fun `a long invoice paginates without clipping or splitting rows`() {
        val layout = engine.layout(doc(rows = 120), a4)
        assertTrue(layout.pages.size > 2, "expected several pages, got ${layout.pages.size}")
        assertInsidePages(layout)
        assertTrue(layout.pages.all { it.height == a4.pageHeight })

        // every row appears exactly once, in order, with its thumbnail and total on the same page
        assertEquals(120, layout.pages.sumOf { images(it).size })
        for (page in layout.pages) {
            val thumbs = images(page)
            val totals = texts(page).filter { it.text == "1 260 DH" }
            assertEquals(thumbs.size, totals.size, "a row's thumbnail and total must not be separated")
        }
    }

    @Test
    fun `the summary is never split or stranded and the table header repeats`() {
        for (rows in listOf(1, 5, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 40, 57)) {
            val layout = engine.layout(doc(rows = rows), a4)
            assertInsidePages(layout)

            val summaryPages = layout.pages.withIndex().filter { (_, page) ->
                texts(page).any { it.style == TextStyle.TOTAL }
            }.map { it.index }
            assertEquals(listOf(layout.pages.lastIndex), summaryPages, "summary must be on the last page ($rows rows)")

            val last = layout.pages.last()
            assertEquals(3, texts(last).count { it.style == TextStyle.TOTAL }, "total, deposit and remaining together ($rows rows)")
            assertTrue(images(last).isNotEmpty(), "summary must share its page with an item row ($rows rows)")

            for ((i, page) in layout.pages.withIndex()) {
                if (i > 0) {
                    assertTrue(texts(page).any { it.text == "المنتوج" }, "page ${i + 1} must repeat the table header ($rows rows)")
                }
            }
        }
    }

    @Test
    fun `an invoice with no rows still lays out`() {
        val layout = engine.layout(doc(rows = 0), a4)
        assertEquals(1, layout.pages.size)
        assertInsidePages(layout)
    }

    @Test
    fun `pdf scaling keeps thumbnails proportionate`() {
        // 520 layout units onto A4 (595 pt) is a scale of 1.144, so a 64 unit thumbnail prints at ~73 pt (2.6 cm)
        val scale = 595f / a4.width
        val printedPoints = a4.thumbnail * scale
        assertTrue(printedPoints in 60f..90f, "printed thumbnail is $printedPoints pt")
    }
}
