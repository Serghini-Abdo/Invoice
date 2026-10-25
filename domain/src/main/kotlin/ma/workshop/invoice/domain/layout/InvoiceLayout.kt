package ma.workshop.invoice.domain.layout

import ma.workshop.invoice.domain.format.ArabicTerms
import ma.workshop.invoice.domain.format.DocumentRow
import ma.workshop.invoice.domain.format.InvoiceDocument
import ma.workshop.invoice.domain.palette.Palette

/** Text styles of the invoice. Sizes are layout units (1 unit = 1 dp on the on-screen preview). */
enum class TextStyle(val size: Float, val bold: Boolean) {
    TITLE(22f, true),
    HEADING(16f, true),
    BODY(14f, false),
    BODY_BOLD(14f, true),
    SMALL(12f, false),
    SMALL_BOLD(12f, true),
    TOTAL(20f, true),

    /** The deposit on the last page: green and slightly smaller than [TOTAL]. */
    DEPOSIT(18f, true),
    BADGE(15f, true),
}

enum class HAlign { LEFT, CENTER, RIGHT }

/** One drawing instruction. All coordinates are layout units from the top-left of the page. */
sealed interface DrawOp {
    data class Rect(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val fill: Long? = null,
        val stroke: Long? = null,
        val strokeWidth: Float = 0f,
        val cornerRadius: Float = 0f,
    ) : DrawOp

    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Long, val width: Float) : DrawOp

    /**
     * A right-to-left paragraph laid out inside the box starting at ([x], [y]) that is [width] wide.
     * [align] is physical (LEFT/RIGHT of the page), not start/end, so the renderer needs no bidi logic
     * for placement. The box height is whatever [TextMeasurer] reported for the same text and width.
     */
    data class Text(
        val x: Float,
        val y: Float,
        val width: Float,
        val text: String,
        val style: TextStyle,
        val align: HAlign,
        val color: Long,
    ) : DrawOp

    /** A built-in illustration drawn into a square of side [size]. */
    data class Image(val illustration: String, val x: Float, val y: Float, val size: Float) : DrawOp
}

class LayoutPage(val height: Float, val ops: List<DrawOp>)

class InvoiceLayout(val width: Float, val pages: List<LayoutPage>)

/** Measures wrapped text. The Android implementation uses StaticLayout with the real font. */
interface TextMeasurer {
    /** Height in layout units of [text] wrapped to [maxWidth], as a right-to-left paragraph. */
    fun measureHeight(text: String, style: TextStyle, maxWidth: Float): Float
}

data class LayoutSpec(
    /** Page width in layout units. The renderer scales units to pixels or points. */
    val width: Float,
    /** Fixed page height for PDF pagination; null produces one tall page (PNG). */
    val pageHeight: Float? = null,
    val margin: Float = 16f,
    /** Product thumbnail box. 0 = no pictures on the shared invoice (the name takes the space). */
    val thumbnail: Float = THUMBNAIL,
) {
    companion object {
        const val THUMBNAIL = 0f
    }
}

/** The printed invoice's colours, all taken from the shared [Palette] so screens and paper match. */
object InvoiceColors {
    const val INK = Palette.INK
    const val MUTED = Palette.GRAY_STRONG
    const val ACCENT = Palette.PRIMARY
    const val DIVIDER = Palette.OUTLINE_SOFT
    const val PANEL = Palette.SURFACE
    const val PAID = Palette.PRIMARY
    const val PAID_BG = Palette.PRIMARY_CONTAINER
    const val BALANCE_TEXT = Palette.SECONDARY_BORDER
    const val WHITE = Palette.CARD
}

/** The invoice table's column widths (layout units), left to right: price, measurement / formula, quantity, name. */
class TableColumns(contentWidth: Float) {
    val price = maxOf(66f, contentWidth * 0.17f)
    val quantity = maxOf(56f, contentWidth * 0.10f)
    val formula = contentWidth * 0.36f
    val name = contentWidth - price - quantity - formula

    companion object {
        /** The quantity column keeps its own margins: its text is centred between them. */
        const val QUANTITY_MARGIN = 8f
        const val CELL_PADDING = 4f
    }
}

/** One picture on one tall PNG, 3 px per unit: 360 units are a 1080 px wide image. */
object PngLimits {
    const val WIDTH_UNITS = 360f
    const val SCALE = 3f
    const val MAX_PIXELS = 8_000f

    /** True when the invoice would make an image taller than [MAX_PIXELS] (about 55 items). */
    fun isTooLong(heightUnits: Float): Boolean = heightUnits * SCALE > MAX_PIXELS
}

/**
 * Turns an [InvoiceDocument] into pages of [DrawOp]s.
 *
 * Rules (all covered by unit tests):
 *  - nothing is ever drawn outside the page or its margins;
 *  - an item row (thumbnail, name, quantity, measurements, total) is never split across pages;
 *  - the table is Price | Measurement / Formula | Quantity | Name, with the unit price inside the formula cell,
 *    the currency in the column title only, and a thin divider between rows;
 *  - the full header is on page 1 only; later pages start with one slim line (workshop, invoice number, date);
 *  - every page of a paginated layout has the footer "صفحة 2 / 3";
 *  - the payment summary (total, deposit, balance) is on the last page only, never split, and shares its page
 *    with at least the last item row, so it is never stranded alone;
 *  - the table title repeats at the top of every page that continues the item list;
 *  - thumbnails have one fixed size and sit beside the text, so they never dominate the page.
 */
class InvoiceLayoutEngine(private val measurer: TextMeasurer) {

    private class Block(val height: Float, val ops: (top: Float) -> List<DrawOp>)

    fun layout(doc: InvoiceDocument, spec: LayoutSpec): InvoiceLayout {
        val contentWidth = spec.width - 2 * spec.margin
        val header = headerBlock(doc, spec, contentWidth)
        val tableHeader = tableHeaderBlock(spec, contentWidth)
        val rows = doc.rows.mapIndexed { index, row -> rowBlock(row, spec, contentWidth, divider = index < doc.rows.lastIndex) }
        val summary = summaryBlock(doc, spec, contentWidth)

        val pageHeight = spec.pageHeight
        if (pageHeight == null) {
            val ops = ArrayList<DrawOp>()
            var y = spec.margin
            for (block in listOf(header, tableHeader) + rows + summary) {
                ops += block.ops(y)
                y += block.height
            }
            return InvoiceLayout(spec.width, listOf(LayoutPage(y + spec.margin, ops)))
        }
        return paginate(doc, spec, pageHeight, header, tableHeader, rows, summary, contentWidth)
    }

    private fun paginate(
        doc: InvoiceDocument,
        spec: LayoutSpec,
        pageHeight: Float,
        header: Block,
        tableHeader: Block,
        rows: List<Block>,
        summary: Block,
        contentWidth: Float,
    ): InvoiceLayout {
        val footerHeight = measurer.measureHeight(pageLabel(9, 9), TextStyle.SMALL, contentWidth)
        val footerTop = pageHeight - spec.margin - footerHeight
        val limit = footerTop - FOOTER_GAP
        val slim = slimBlock(doc, spec, contentWidth)

        val pages = ArrayList<MutableList<DrawOp>>()
        var ops = ArrayList<DrawOp>()
        var y = spec.margin
        var rowsOnPage = 0

        fun startPage() {
            pages += ops
            ops = ArrayList()
            y = spec.margin
            rowsOnPage = 0
        }

        fun place(block: Block) {
            ops += block.ops(y)
            y += block.height
        }

        place(header)
        place(tableHeader)

        // The last row travels together with the summary so the summary never sits alone.
        val tailRows = if (rows.isNotEmpty()) listOf(rows.last()) else emptyList()
        val tailHeight = tailRows.sumOf { it.height.toDouble() }.toFloat() + summary.height
        val bodyRows = if (rows.isNotEmpty()) rows.dropLast(1) else rows

        for (row in bodyRows) {
            if (y + row.height > limit && rowsOnPage > 0) {
                startPage()
                place(slim)
                place(tableHeader)
            }
            place(row)
            rowsOnPage++
        }

        if (y + tailHeight > limit && rowsOnPage > 0) {
            startPage()
            place(slim)
            place(tableHeader)
        }
        tailRows.forEach(::place)
        place(summary)
        pages += ops

        val count = pages.size
        val laidOut = pages.mapIndexed { index, pageOps ->
            pageOps += DrawOp.Text(
                spec.margin, footerTop, contentWidth, pageLabel(index + 1, count),
                TextStyle.SMALL, HAlign.CENTER, InvoiceColors.MUTED,
            )
            LayoutPage(pageHeight, pageOps)
        }
        return InvoiceLayout(spec.width, laidOut)
    }

    // ---- blocks ----

    private fun text(x: Float, y: Float, w: Float, text: String, style: TextStyle, align: HAlign, color: Long = InvoiceColors.INK) =
        DrawOp.Text(x, y, w, text, style, align, color)

    private fun headerBlock(doc: InvoiceDocument, spec: LayoutSpec, contentWidth: Float): Block {
        val x = spec.margin
        val pieces = ArrayList<(Float) -> List<DrawOp>>()
        var height = 0f

        fun addFull(text: String, style: TextStyle, align: HAlign, color: Long = InvoiceColors.INK, gap: Float = 4f) {
            val h = measurer.measureHeight(text, style, contentWidth)
            val top = height
            pieces += { y -> listOf(text(x, y + top, contentWidth, text, style, align, color)) }
            height += h + gap
        }

        fun addPair(rightText: String?, leftText: String?, style: TextStyle, color: Long = InvoiceColors.INK, gap: Float = 4f) {
            if (rightText == null && leftText == null) return
            val rightWidth = if (leftText == null) contentWidth else contentWidth * 0.58f
            val leftWidth = if (rightText == null) contentWidth else contentWidth * 0.42f
            val rh = rightText?.let { measurer.measureHeight(it, style, rightWidth) } ?: 0f
            val lh = leftText?.let { measurer.measureHeight(it, style, leftWidth) } ?: 0f
            val top = height
            pieces += { y ->
                buildList {
                    if (rightText != null) add(text(x + contentWidth - rightWidth, y + top, rightWidth, rightText, style, HAlign.RIGHT, color))
                    if (leftText != null) add(text(x, y + top, leftWidth, leftText, style, HAlign.LEFT, color))
                }
            }
            height += maxOf(rh, lh) + gap
        }

        if (doc.workshopName.isNotEmpty()) addFull(doc.workshopName, TextStyle.TITLE, HAlign.CENTER, InvoiceColors.ACCENT)
        if (doc.workshopPhone.isNotEmpty()) addFull(doc.workshopPhone, TextStyle.BODY, HAlign.CENTER, InvoiceColors.MUTED, gap = 8f)
        if (doc.workshopName.isEmpty() && doc.workshopPhone.isEmpty()) height += 4f

        val ruleTop = height
        pieces += { y -> listOf(DrawOp.Line(x, y + ruleTop, x + contentWidth, y + ruleTop, InvoiceColors.DIVIDER, 1f)) }
        height += 9f

        addPair("${doc.title} ${ArabicTerms.NUMBER} ${doc.number}", doc.date, TextStyle.HEADING)
        addPair(
            doc.customerName?.let { "${ArabicTerms.CUSTOMER}: $it" },
            doc.customerPhone?.let { "${ArabicTerms.PHONE}: $it" },
            TextStyle.BODY,
        )
        height += 6f
        return Block(height) { top -> pieces.flatMap { it(top) } }
    }

    /** Pages 2 and later: workshop, invoice number and date on one slim line above the table title. */
    private fun slimBlock(doc: InvoiceDocument, spec: LayoutSpec, contentWidth: Float): Block {
        val rightText = listOf(doc.workshopName, "${doc.title} ${ArabicTerms.NUMBER} ${doc.number}")
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
        val rightWidth = contentWidth * 0.68f
        val leftWidth = contentWidth * 0.32f
        val h = maxOf(
            measurer.measureHeight(rightText, TextStyle.SMALL, rightWidth),
            measurer.measureHeight(doc.date, TextStyle.SMALL, leftWidth),
        )
        return Block(h + 8f) { y ->
            listOf(
                text(spec.margin + contentWidth - rightWidth, y, rightWidth, rightText, TextStyle.SMALL, HAlign.RIGHT, InvoiceColors.MUTED),
                text(spec.margin, y, leftWidth, doc.date, TextStyle.SMALL, HAlign.LEFT, InvoiceColors.MUTED),
                DrawOp.Line(spec.margin, y + h + 3f, spec.margin + contentWidth, y + h + 3f, InvoiceColors.DIVIDER, 1f),
            )
        }
    }

    private fun tableHeaderBlock(spec: LayoutSpec, contentWidth: Float): Block {
        val cols = TableColumns(contentWidth)
        val pad = TableColumns.CELL_PADDING
        val margin = TableColumns.QUANTITY_MARGIN
        val h = measurer.measureHeight(ArabicTerms.PRODUCT, TextStyle.SMALL_BOLD, cols.name - 2 * pad) + 10f
        val priceX = spec.margin
        val formulaX = priceX + cols.price
        val quantityX = formulaX + cols.formula
        val nameX = quantityX + cols.quantity
        return Block(h + 4f) { y ->
            val top = y + 2f
            listOf(
                DrawOp.Rect(spec.margin, top, contentWidth, h, fill = InvoiceColors.PANEL, cornerRadius = 4f),
                text(priceX + pad, top + 5f, cols.price - 2 * pad, ArabicTerms.AMOUNT_HEADER, TextStyle.SMALL_BOLD, HAlign.LEFT, InvoiceColors.MUTED),
                text(formulaX + pad, top + 5f, cols.formula - 2 * pad, ArabicTerms.MEASURE_AND_CALCULATION, TextStyle.SMALL_BOLD, HAlign.RIGHT, InvoiceColors.MUTED),
                text(quantityX + margin, top + 5f, cols.quantity - 2 * margin, ArabicTerms.QUANTITY, TextStyle.SMALL_BOLD, HAlign.CENTER, InvoiceColors.MUTED),
                text(nameX + pad, top + 5f, cols.name - 2 * pad, ArabicTerms.PRODUCT, TextStyle.SMALL_BOLD, HAlign.RIGHT, InvoiceColors.MUTED),
            )
        }
    }

    private fun rowBlock(row: DocumentRow, spec: LayoutSpec, contentWidth: Float, divider: Boolean): Block {
        val vPad = 6f
        val thumb = spec.thumbnail
        val cols = TableColumns(contentWidth)
        val pad = TableColumns.CELL_PADDING
        val qtyMargin = TableColumns.QUANTITY_MARGIN
        val priceX = spec.margin
        val formulaX = priceX + cols.price
        val quantityX = formulaX + cols.formula
        val nameX = quantityX + cols.quantity
        val thumbX = nameX + cols.name - thumb
        val nameWidth = cols.name - thumb - 2 * pad
        val formulaWidth = cols.formula - 2 * pad
        val priceWidth = cols.price - 2 * pad
        val quantityWidth = cols.quantity - 2 * qtyMargin

        // Measurement on top, then the calculation with the unit price inside ("2,52 م² × 500 DH"). A
        // long formula wraps inside its own cell. Per-piece items have the calculation line only.
        val formulaLines = listOfNotNull(row.measures?.let { it to InvoiceColors.INK }, row.formula to InvoiceColors.MUTED)
        val formulaHeights = formulaLines.map { (t, _) -> measurer.measureHeight(t, TextStyle.SMALL, formulaWidth) }
        val formulaStack = formulaHeights.sum() + 2f * (formulaLines.size - 1)
        val nameHeight = measurer.measureHeight(row.name, TextStyle.SMALL_BOLD, nameWidth)
        val quantityText = row.quantity.toString()
        val quantityHeight = measurer.measureHeight(quantityText, TextStyle.SMALL_BOLD, quantityWidth)
        val priceHeight = measurer.measureHeight(row.totalNumber, TextStyle.SMALL_BOLD, priceWidth)

        val contentHeight = maxOf(thumb, formulaStack, nameHeight)
        val height = contentHeight + 2 * vPad

        return Block(height) { y ->
            val mid = y + vPad + contentHeight / 2
            buildList {
                if (thumb > 0f) add(DrawOp.Image(row.illustration, thumbX, mid - thumb / 2, thumb))
                add(text(nameX + pad, mid - nameHeight / 2, nameWidth, row.name, TextStyle.SMALL_BOLD, HAlign.RIGHT))
                add(text(quantityX + qtyMargin, mid - quantityHeight / 2, quantityWidth, quantityText, TextStyle.SMALL_BOLD, HAlign.CENTER))
                var ty = mid - formulaStack / 2
                formulaLines.forEachIndexed { i, (t, color) ->
                    add(text(formulaX + pad, ty, formulaWidth, t, TextStyle.SMALL, HAlign.RIGHT, color))
                    ty += formulaHeights[i] + 2f
                }
                add(text(priceX + pad, mid - priceHeight / 2, priceWidth, row.totalNumber, TextStyle.SMALL_BOLD, HAlign.LEFT))
                if (divider) {
                    val lineY = y + height - 0.25f
                    add(DrawOp.Line(spec.margin, lineY, spec.margin + contentWidth, lineY, InvoiceColors.DIVIDER, 0.5f))
                }
            }
        }
    }

    /**
     * Total, deposit and balance. The deposit is green and a little smaller than the total; the balance sits in
     * a yellow amount (no patch) at the total's size. A fully paid invoice also gets the "خالص" badge.
     */
    private fun summaryBlock(doc: InvoiceDocument, spec: LayoutSpec, contentWidth: Float): Block {
        val inner = 12f
        val boxWidth = contentWidth
        val valueWidth = boxWidth * 0.5f - inner
        val labelWidth = boxWidth * 0.5f - inner
        val top = 10f
        val rowGap = 8f
        val bandPad = 4f

        fun lineHeight(label: String, labelStyle: TextStyle, value: String, valueStyle: TextStyle) =
            maxOf(measurer.measureHeight(label, labelStyle, labelWidth), measurer.measureHeight(value, valueStyle, valueWidth))

        val totalHeight = lineHeight(ArabicTerms.TOTAL, TextStyle.HEADING, doc.total, TextStyle.TOTAL)
        val depositHeight = lineHeight(ArabicTerms.DEPOSIT, TextStyle.BODY_BOLD, doc.deposit, TextStyle.DEPOSIT)
        val balanceHeight = lineHeight(ArabicTerms.REMAINING, TextStyle.HEADING, doc.remaining, TextStyle.TOTAL)
        val bandHeight = balanceHeight + 2 * bandPad
        val badgeHeight = if (doc.isFullyPaid) measurer.measureHeight(ArabicTerms.FULLY_PAID, TextStyle.BADGE, boxWidth) + 14f else 0f
        // Without a deposit the invoice shows the total only: no deposit line and no balance line.
        val boxHeight = inner * 2 + totalHeight +
            (if (doc.hasDeposit) rowGap + depositHeight + rowGap + bandHeight else 0f) +
            if (doc.hasDeposit && doc.isFullyPaid) rowGap + badgeHeight else 0f
        val thanks = doc.thankYou
        val thanksHeight = thanks?.let { measurer.measureHeight(it, TextStyle.BODY, contentWidth) } ?: 0f
        val height = top + boxHeight + if (thanks != null) 14f + thanksHeight else 0f

        return Block(height) { y ->
            buildList {
                val boxTop = y + top
                val labelX = spec.margin + boxWidth - inner - labelWidth
                val valueX = spec.margin + inner
                add(DrawOp.Rect(spec.margin, boxTop, boxWidth, boxHeight, fill = InvoiceColors.PANEL, stroke = InvoiceColors.DIVIDER, strokeWidth = 1f, cornerRadius = 8f))
                var ly = boxTop + inner
                add(text(labelX, ly, labelWidth, ArabicTerms.TOTAL, TextStyle.HEADING, HAlign.RIGHT))
                add(text(valueX, ly, valueWidth, doc.total, TextStyle.TOTAL, HAlign.LEFT))
                ly += totalHeight + rowGap
                if (!doc.hasDeposit) {
                    if (thanks != null) {
                        add(text(spec.margin, boxTop + boxHeight + 14f, contentWidth, thanks, TextStyle.BODY, HAlign.CENTER, InvoiceColors.MUTED))
                    }
                    return@buildList
                }
                add(text(labelX, ly, labelWidth, ArabicTerms.DEPOSIT, TextStyle.BODY_BOLD, HAlign.RIGHT))
                add(text(valueX, ly, valueWidth, doc.deposit, TextStyle.DEPOSIT, HAlign.LEFT, InvoiceColors.PAID))
                ly += depositHeight + rowGap
                // No patch: the balance amount itself is yellow (a dark yellow, so it still reads on paper).
                add(text(labelX, ly + bandPad, labelWidth, ArabicTerms.REMAINING, TextStyle.HEADING, HAlign.RIGHT))
                add(text(valueX, ly + bandPad, valueWidth, doc.remaining, TextStyle.TOTAL, HAlign.LEFT, InvoiceColors.BALANCE_TEXT))
                ly += bandHeight + rowGap
                if (doc.isFullyPaid) {
                    val badgeWidth = boxWidth - 2 * inner
                    add(DrawOp.Rect(spec.margin + inner, ly, badgeWidth, badgeHeight, fill = InvoiceColors.PAID_BG, stroke = InvoiceColors.PAID, strokeWidth = 1.5f, cornerRadius = 8f))
                    add(text(spec.margin + inner, ly + 7f, badgeWidth, ArabicTerms.FULLY_PAID, TextStyle.BADGE, HAlign.CENTER, InvoiceColors.PAID))
                }
                if (thanks != null) {
                    add(text(spec.margin, boxTop + boxHeight + 14f, contentWidth, thanks, TextStyle.BODY, HAlign.CENTER, InvoiceColors.MUTED))
                }
            }
        }
    }

    companion object {
        private const val FOOTER_GAP = 6f

        /** "صفحة 2 / 3" */
        fun pageLabel(page: Int, count: Int): String = "${ArabicTerms.PAGE} $page / $count"
    }
}
