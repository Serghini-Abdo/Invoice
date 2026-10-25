package ma.workshop.invoice.domain

import ma.workshop.invoice.domain.format.DocumentRow
import ma.workshop.invoice.domain.format.InvoiceDocument
import ma.workshop.invoice.domain.layout.DrawOp
import ma.workshop.invoice.domain.layout.HAlign
import ma.workshop.invoice.domain.layout.InvoiceColors
import ma.workshop.invoice.domain.layout.InvoiceLayout
import ma.workshop.invoice.domain.layout.InvoiceLayoutEngine
import ma.workshop.invoice.domain.layout.LayoutPage
import ma.workshop.invoice.domain.layout.LayoutSpec
import ma.workshop.invoice.domain.layout.PngLimits
import ma.workshop.invoice.domain.layout.TableColumns
import ma.workshop.invoice.domain.layout.TextMeasurer
import ma.workshop.invoice.domain.layout.TextStyle
import ma.workshop.invoice.domain.palette.Palette
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

    private fun row(i: Int, name: String = "باب حديد", quantity: Int = 1) = DocumentRow(
        position = i, illustration = "door", name = name, measures = "1,20 × 2,10 م", quantity = quantity,
        rate = "500 DH / م²", calculation = "2,52 م² × 500 DH × $quantity", formula = "2,52 م² × 500 DH",
        total = "1 260 DH", totalNumber = "1 260",
    )

    private fun doc(
        rows: Int = 2,
        paid: Boolean = false,
        customer: Boolean = true,
        name: String = "باب حديد",
        thankYou: String? = "شكراً لثقتكم",
        hasDeposit: Boolean = true,
    ) = InvoiceDocument(
        title = "فاتورة", workshopName = "ورشة الأمل للحدادة", workshopPhone = "0522000000",
        number = "0007", date = "25/10/2026", updatedNote = null,
        customerName = if (customer) "أحمد" else null, customerPhone = if (customer) "0612345678" else null,
        rows = (1..rows).map { row(it, name) },
        total = "1 380 DH", deposit = "500 DH", remaining = if (paid) "0 DH" else "880 DH",
        hasDeposit = hasDeposit, isFullyPaid = paid, thankYou = thankYou,
    )

    private val phone = LayoutSpec(width = 360f)

    /** A4 as the renderer lays it out: one layout unit is one point. */
    private val a4Width = 595f
    private val a4 = LayoutSpec(width = a4Width, pageHeight = a4Width * 842f / 595f, margin = 28f)

    private fun texts(page: LayoutPage) = page.ops.filterIsInstance<DrawOp.Text>()
    private fun images(page: LayoutPage) = page.ops.filterIsInstance<DrawOp.Image>()
    private fun rowCount(page: LayoutPage) = texts(page).count { it.text == "1 260" }
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

    // ---- table option A ----

    @Test
    fun `table columns run price, formula, quantity, name from left to right`() {
        val layout = engine.layout(doc(rows = 1), phone)
        val page = texts(layout.pages[0])
        val price = page.first { it.text == "1 260" }
        val formula = page.first { it.text == "2,52 م² × 500 DH" }
        val measures = page.first { it.text == "1,20 × 2,10 م" }
        val quantity = page.first { it.text == "1" && it.style == TextStyle.SMALL_BOLD }
        val name = page.first { it.text == "باب حديد" }

        assertTrue(price.x + price.width <= formula.x + 0.01f, "price column is left of the formula column")
        assertTrue(formula.x + formula.width <= quantity.x + 0.01f, "formula column is left of the quantity column")
        assertTrue(quantity.x + quantity.width <= name.x + 0.01f, "quantity column is left of the name column")
        assertEquals(formula.x, measures.x, "measures and calculation share one cell")
        assertEquals(HAlign.LEFT, price.align)
        assertEquals(HAlign.RIGHT, formula.align)
        assertEquals(HAlign.RIGHT, name.align)
    }

    @Test
    fun `the unit price is inside the formula cell and the quantity has its own centred column with margins`() {
        val layout = engine.layout(doc(rows = 3).copy(rows = (1..3).map { row(it, quantity = it + 1) }), phone)
        val page = texts(layout.pages[0])
        val formulas = page.filter { it.text.contains("500 DH") && it.style == TextStyle.SMALL }
        assertEquals(3, formulas.size)
        assertTrue(formulas.all { it.text == "2,52 م² × 500 DH" }, "no quantity inside the formula cell")

        val columns = TableColumns(phone.width - 2 * phone.margin)
        val quantities = page.filter { it.style == TextStyle.SMALL_BOLD && it.text in setOf("2", "3", "4") }
        assertEquals(3, quantities.size)
        for (q in quantities) {
            assertEquals(HAlign.CENTER, q.align)
            assertEquals(columns.quantity - 2 * TableColumns.QUANTITY_MARGIN, q.width, 0.01f, "own margins on both sides")
        }
        assertEquals(1, quantities.map { it.x }.toSet().size, "one column")
    }

    @Test
    fun `the currency is written once in the column title and rows show the bare number`() {
        val layout = engine.layout(doc(rows = 2), phone)
        val page = texts(layout.pages[0])
        assertTrue(page.any { it.text == "المبلغ (DH)" })
        assertEquals(2, page.count { it.text == "1 260" })
        assertFalse(page.any { it.text == "1 260 DH" }, "rows do not repeat the currency")
        assertTrue(page.any { it.text == "القياس والحساب" })
        assertTrue(page.any { it.text == "العدد" })
    }

    @Test
    fun `a long formula wraps inside its cell and grows the row`() {
        val long = row(1).copy(formula = "12,3456 م² × 1 250,50 DH أطول من خانة الحساب بكثير جداً")
        val normal = engine.layout(doc(rows = 1), phone).pages[0].height
        val wrapped = engine.layout(doc(rows = 1).copy(rows = listOf(long)), phone)
        assertTrue(wrapped.pages[0].height > normal)
        assertInsidePages(wrapped)
        val cell = TableColumns(phone.width - 2 * phone.margin)
        val text = texts(wrapped.pages[0]).first { it.text == long.formula }
        assertTrue(text.width < cell.formula)
    }

    @Test
    fun `the table has a thin divider between rows and none after the last`() {
        val layout = engine.layout(doc(rows = 6), phone)
        // the rule under the workshop name, plus one thin divider between each pair of rows
        assertEquals(1 + 5, layout.pages[0].ops.filterIsInstance<DrawOp.Line>().size)
        val a4Layout = engine.layout(doc(rows = 60), a4)
        val rowsAndDividers = a4Layout.pages.sumOf { page ->
            page.ops.filterIsInstance<DrawOp.Line>().count { it.width == 0.5f }
        }
        assertEquals(59, rowsAndDividers, "between 60 rows there are 59 dividers")
    }

    @Test
    fun `the shared invoice has no pictures and the name takes the space`() {
        val layout = engine.layout(doc(rows = 5), phone)
        assertEquals(0, images(layout.pages[0]).size)
        val cols = TableColumns(phone.width - 2 * phone.margin)
        val name = texts(layout.pages[0]).first { it.text == "باب حديد" }
        assertEquals(cols.name - 2 * TableColumns.CELL_PADDING, name.width, 0.01f)
    }

    // ---- totals ----

    @Test
    fun `the deposit is green and smaller than the total and the balance amount is yellow text at the total size`() {
        val layout = engine.layout(doc(), phone)
        val page = layout.pages[0]
        val totals = texts(page).filter { it.style == TextStyle.TOTAL }
        assertEquals(listOf("1 380 DH", "880 DH"), totals.map { it.text })
        val deposit = texts(page).single { it.style == TextStyle.DEPOSIT }
        assertEquals("500 DH", deposit.text)
        assertEquals(InvoiceColors.PAID, deposit.color)
        assertTrue(TextStyle.DEPOSIT.size < TextStyle.TOTAL.size)
        assertTrue(TextStyle.TOTAL.size > TextStyle.BODY_BOLD.size)
        assertTrue((totals + deposit).all { it.align == HAlign.LEFT })

        val balance = totals.last()
        assertEquals(InvoiceColors.BALANCE_TEXT, balance.color, "the balance amount itself is yellow")
        assertTrue(page.ops.filterIsInstance<DrawOp.Rect>().none { it.fill == Palette.SECONDARY }, "no yellow patch")
        val word = texts(page).single { it.text == "الباقي" }
        assertEquals(InvoiceColors.INK, word.color, "the word stays ink")
        assertEquals(InvoiceColors.INK, totals.first().color)
    }

    @Test
    fun `without a deposit only the total is shown`() {
        val layout = engine.layout(doc(hasDeposit = false), phone)
        val text = allTexts(layout).map { it.text }
        assertTrue("1 380 DH" in text)
        assertFalse("التسبيق" in text)
        assertFalse("الباقي" in text)
        assertFalse("500 DH" in text)
        assertFalse("880 DH" in text)
        assertTrue(texts(layout.pages[0]).none { it.style == TextStyle.DEPOSIT })
        assertTrue("شكراً لثقتكم" in text, "the thank-you line stays")
        assertInsidePages(layout)
        assertTrue(layout.pages[0].height < engine.layout(doc(), phone).pages[0].height)

        // with a deposit the three lines are back
        val withDeposit = allTexts(engine.layout(doc(), phone)).map { it.text }
        assertTrue("التسبيق" in withDeposit && "الباقي" in withDeposit)

        // on paper the total is still on the last page with the last row
        val pdf = engine.layout(doc(rows = 40, hasDeposit = false), a4)
        assertEquals(listOf(pdf.pages.lastIndex), pdf.pages.withIndex().filter { (_, p) -> texts(p).any { it.style == TextStyle.TOTAL } }.map { it.index })
        assertTrue(rowCount(pdf.pages.last()) > 0)
        assertInsidePages(pdf, a4.margin)
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
        assertTrue(name.width < TableColumns(phone.width - 2 * phone.margin).name)
    }

    // ---- one tall PNG ----

    // Scenario 17: long invoices export without clipping
    @Test
    fun `a very long invoice on one tall page is not clipped`() {
        val layout = engine.layout(doc(rows = 120), phone)
        assertEquals(1, layout.pages.size)
        assertEquals(120, rowCount(layout.pages[0]))
        assertInsidePages(layout)
        assertTrue(layout.pages[0].height > 1000f)
    }

    @Test
    fun `the png limit switches between one image and the pdf pointer at 8000 pixels`() {
        assertFalse(PngLimits.isTooLong(8_000f / PngLimits.SCALE))
        assertTrue(PngLimits.isTooLong(8_000f / PngLimits.SCALE + 1f))

        val short = engine.layout(doc(rows = 30), phone).pages[0].height
        val long = engine.layout(doc(rows = 90), phone).pages[0].height
        assertFalse(PngLimits.isTooLong(short), "30 items fit one image")
        assertTrue(PngLimits.isTooLong(long), "90 items do not")
        // a single row is about 42 units tall, so the limit falls near 55 items, as in the mock-up
        val rowHeight = (long - short) / 60f
        assertTrue(rowHeight in 28f..50f, "row height is $rowHeight")
        val items = (2_667f / rowHeight).toInt()
        assertTrue(items in 55..110, "limit at about $items items")
    }

    // ---- PDF pages ----

    @Test
    fun `a long invoice paginates without clipping or splitting rows`() {
        val layout = engine.layout(doc(rows = 120), a4)
        assertTrue(layout.pages.size > 2, "expected several pages, got ${layout.pages.size}")
        assertInsidePages(layout, a4.margin)
        assertTrue(layout.pages.all { it.height == a4.pageHeight })

        // every row appears exactly once, in order, with its picture and total on the same page
        assertEquals(120, layout.pages.sumOf { rowCount(it) })
        for (page in layout.pages) {
            val names = texts(page).count { it.text == "باب حديد" }
            assertEquals(names, rowCount(page), "a row's name and total must not be separated")
        }
    }

    @Test
    fun `a 45 item invoice paginates with more rows on later pages`() {
        val layout = engine.layout(doc(rows = 45), a4)
        val perPage = layout.pages.map { rowCount(it) }
        assertEquals(45, perPage.sum())
        assertTrue(perPage.size in 2..4, "pages: $perPage")
        assertTrue(perPage.first() in 8..40, "page 1 has ${perPage.first()} rows")
        assertTrue(perPage[1] > perPage.first(), "page 2 has more rows than page 1 (slim line instead of the full header)")
    }

    @Test
    fun `the full header is on page 1 only and later pages start with one slim line above the table title`() {
        val layout = engine.layout(doc(rows = 60), a4)
        assertTrue(layout.pages.size >= 3)
        val first = texts(layout.pages[0])
        assertTrue(first.any { it.text == "0522000000" }, "phone is in the full header")
        assertTrue(first.any { it.text == "الزبون: أحمد" })
        assertTrue(first.any { it.style == TextStyle.TITLE })

        for ((i, page) in layout.pages.withIndex().drop(1)) {
            val t = texts(page)
            assertFalse(t.any { it.style == TextStyle.TITLE }, "page ${i + 1}: no workshop title block")
            assertFalse(t.any { it.text == "0522000000" || it.text.startsWith("الزبون") }, "page ${i + 1}: no customer block")
            val slim = t.single { it.text == "ورشة الأمل للحدادة · فاتورة رقم 0007" }
            assertEquals(HAlign.RIGHT, slim.align)
            val date = t.single { it.text == "25/10/2026" }
            assertEquals(HAlign.LEFT, date.align)
            val title = t.first { it.text == "المنتوج" }
            assertTrue(slim.y < title.y, "page ${i + 1}: the slim line is above the column titles")
            assertEquals(a4.margin, slim.y, 0.01f, "the slim line is the first thing on the page")
        }
        // the date and invoice number occur once on page 1 as part of the full header, not as a slim line
        assertFalse(first.any { it.text == "ورشة الأمل للحدادة · فاتورة رقم 0007" })
    }

    @Test
    fun `every page has the footer page x of n`() {
        for (rows in listOf(0, 1, 10, 45, 120)) {
            val layout = engine.layout(doc(rows = rows), a4)
            val count = layout.pages.size
            for ((i, page) in layout.pages.withIndex()) {
                val footer = texts(page).filter { it.text.startsWith("صفحة") }
                assertEquals(1, footer.size, "$rows rows, page ${i + 1}")
                assertEquals("صفحة ${i + 1} / $count", footer.single().text)
                assertEquals(HAlign.CENTER, footer.single().align)
                val others = page.ops.filter { it !== footer.single() }
                val footerTop = footer.single().y
                // nothing but the footer sits in the footer strip
                for (op in others) {
                    val (_, _, _, bottom) = opBounds(op) { FakeMeasurer.measureHeight(it.text, it.style, it.width) }
                    assertTrue(bottom <= footerTop + 0.01f, "$rows rows, page ${i + 1}: $op overlaps the footer")
                }
            }
        }
        // the png has no footer
        assertFalse(allTexts(engine.layout(doc(rows = 10), phone)).any { it.text.startsWith("صفحة") })
    }

    @Test
    fun `total deposit and balance are on the last page only, with at least the last row`() {
        for (rows in listOf(1, 5, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 40, 57)) {
            val layout = engine.layout(doc(rows = rows), a4)
            assertInsidePages(layout, a4.margin)

            val summaryPages = layout.pages.withIndex().filter { (_, page) ->
                texts(page).any { it.style == TextStyle.TOTAL }
            }.map { it.index }
            assertEquals(listOf(layout.pages.lastIndex), summaryPages, "summary must be on the last page ($rows rows)")

            val last = layout.pages.last()
            assertEquals(2, texts(last).count { it.style == TextStyle.TOTAL }, "total and balance ($rows rows)")
            assertEquals(1, texts(last).count { it.style == TextStyle.DEPOSIT }, "deposit ($rows rows)")
            assertTrue(rowCount(last) > 0, "summary must share its page with an item row ($rows rows)")
            for (page in layout.pages.dropLast(1)) {
                assertTrue(texts(page).none { it.style == TextStyle.DEPOSIT }, "no deposit before the last page ($rows rows)")
                assertTrue(texts(page).none { it.color == InvoiceColors.BALANCE_TEXT }, "no balance before the last page ($rows rows)")
            }

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
        assertInsidePages(layout, a4.margin)
    }

    @Test
    fun `pdf prints table text at 12 points`() {
        val scale = 595f / a4.width
        assertEquals(12f, TextStyle.SMALL.size * scale, 0.01f)
    }
}
