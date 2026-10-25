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
    TOTAL(20f, true),
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
    /** Product thumbnail box. 64 units on the 360-unit phone preview (the spec asks for 60-80 dp). */
    val thumbnail: Float = 64f,
)

/** The printed invoice's colours, all taken from the shared [Palette] so screens and paper match. */
object InvoiceColors {
    const val INK = Palette.INK
    const val MUTED = Palette.GRAY_STRONG
    const val ACCENT = Palette.PRIMARY
    const val DIVIDER = Palette.OUTLINE_SOFT
    const val PANEL = Palette.SURFACE
    const val PAID = Palette.PRIMARY
    const val PAID_BG = Palette.PRIMARY_CONTAINER
    const val WHITE = Palette.CARD
}

/**
 * Turns an [InvoiceDocument] into pages of [DrawOp]s.
 *
 * Rules (all covered by unit tests):
 *  - nothing is ever drawn outside the page or its margins;
 *  - an item row (thumbnail, name, measurements, total) is never split across pages;
 *  - the payment summary (total, deposit, remaining) is never split, and always shares its page with
 *    at least the last item row, so it is never stranded alone;
 *  - the table header repeats at the top of every page that continues the item list;
 *  - thumbnails have one fixed size and sit beside the text, so they never dominate the page.
 */
class InvoiceLayoutEngine(private val measurer: TextMeasurer) {

    private class Block(val height: Float, val ops: (top: Float) -> List<DrawOp>)

    fun layout(doc: InvoiceDocument, spec: LayoutSpec): InvoiceLayout {
        val contentWidth = spec.width - 2 * spec.margin
        val header = headerBlock(doc, spec, contentWidth)
        val tableHeader = tableHeaderBlock(spec, contentWidth)
        val rows = doc.rows.map { rowBlock(it, spec, contentWidth) }
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
        return paginate(spec, pageHeight, header, tableHeader, rows, summary)
    }

    private fun paginate(
        spec: LayoutSpec,
        pageHeight: Float,
        header: Block,
        tableHeader: Block,
        rows: List<Block>,
        summary: Block,
    ): InvoiceLayout {
        val limit = pageHeight - spec.margin
        val pages = ArrayList<LayoutPage>()
        var ops = ArrayList<DrawOp>()
        var y = spec.margin

        fun startPage() {
            pages += LayoutPage(pageHeight, ops)
            ops = ArrayList()
            y = spec.margin
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
            if (y + row.height > limit && y > spec.margin + 1f) {
                startPage()
                place(tableHeader)
            }
            place(row)
        }

        if (y + tailHeight > limit && y > spec.margin + tableHeader.height + 1f) {
            startPage()
            if (tailRows.isNotEmpty()) place(tableHeader)
        }
        tailRows.forEach(::place)
        place(summary)

        pages += LayoutPage(pageHeight, ops)
        return InvoiceLayout(spec.width, pages)
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
        doc.updatedNote?.let { addFull(it, TextStyle.SMALL, HAlign.RIGHT, InvoiceColors.MUTED) }
        addPair(
            doc.customerName?.let { "${ArabicTerms.CUSTOMER}: $it" },
            doc.customerPhone?.let { "${ArabicTerms.PHONE}: $it" },
            TextStyle.BODY,
        )
        height += 6f
        return Block(height) { top -> pieces.flatMap { it(top) } }
    }

    private fun tableHeaderBlock(spec: LayoutSpec, contentWidth: Float): Block {
        val h = measurer.measureHeight(ArabicTerms.PRODUCT, TextStyle.SMALL, contentWidth) + 10f
        return Block(h) { y ->
            listOf(
                DrawOp.Rect(spec.margin, y, contentWidth, h, fill = InvoiceColors.PANEL, cornerRadius = 4f),
                text(spec.margin + 8f, y + 5f, contentWidth / 2 - 8f, ArabicTerms.TOTAL, TextStyle.SMALL, HAlign.LEFT, InvoiceColors.MUTED),
                text(spec.margin + contentWidth / 2, y + 5f, contentWidth / 2 - 8f, ArabicTerms.PRODUCT, TextStyle.SMALL, HAlign.RIGHT, InvoiceColors.MUTED),
            )
        }
    }

    private fun rowBlock(row: DocumentRow, spec: LayoutSpec, contentWidth: Float): Block {
        val pad = 8f
        val thumb = spec.thumbnail
        val totalColumn = contentWidth * 0.27f
        val gap = 8f
        val textRight = spec.margin + contentWidth - thumb - gap
        val textLeft = spec.margin + totalColumn + gap
        val textWidth = textRight - textLeft

        // name / measurements and quantity / calculation (which contains the unit rate). Measurements and
        // quantity share a line so a row stays short: a typical invoice then fits on one A4 page.
        val lines = ArrayList<Pair<String, TextStyle>>()
        lines += row.name to TextStyle.BODY_BOLD
        lines += listOfNotNull(row.measures, "${ArabicTerms.QUANTITY}: ${row.quantity}").joinToString("  ·  ") to TextStyle.BODY
        lines += row.calculation to TextStyle.SMALL

        val heights = lines.map { (t, s) -> measurer.measureHeight(t, s, textWidth) }
        val stack = heights.sum() + 2f * (lines.size - 1)
        val contentHeight = maxOf(thumb, stack)
        val height = contentHeight + 2 * pad
        val totalHeight = measurer.measureHeight(row.total, TextStyle.BODY_BOLD, totalColumn)

        return Block(height) { y ->
            buildList {
                add(DrawOp.Image(row.illustration, spec.margin + contentWidth - thumb, y + pad + (contentHeight - thumb) / 2, thumb))
                var ty = y + pad + (contentHeight - stack) / 2
                lines.forEachIndexed { i, (t, s) ->
                    val color = if (i == lines.lastIndex) InvoiceColors.MUTED else InvoiceColors.INK
                    add(text(textLeft, ty, textWidth, t, s, HAlign.RIGHT, color))
                    ty += heights[i] + 2f
                }
                add(text(spec.margin, y + pad + (contentHeight - totalHeight) / 2, totalColumn, row.total, TextStyle.BODY_BOLD, HAlign.LEFT))
                add(DrawOp.Line(spec.margin, y + height - 0.5f, spec.margin + contentWidth, y + height - 0.5f, InvoiceColors.DIVIDER, 1f))
            }
        }
    }

    private fun summaryBlock(doc: InvoiceDocument, spec: LayoutSpec, contentWidth: Float): Block {
        val inner = 12f
        val boxWidth = contentWidth
        val valueWidth = boxWidth * 0.5f - inner
        val labelWidth = boxWidth * 0.5f - inner
        val top = 10f

        class Line(val label: String, val value: String, val labelStyle: TextStyle, val valueStyle: TextStyle, val color: Long)

        val lines = listOf(
            Line(ArabicTerms.TOTAL, doc.total, TextStyle.HEADING, TextStyle.TOTAL, InvoiceColors.INK),
            Line(ArabicTerms.DEPOSIT, doc.deposit, TextStyle.HEADING, TextStyle.TOTAL, InvoiceColors.INK),
            Line(ArabicTerms.REMAINING, doc.remaining, TextStyle.HEADING, TextStyle.TOTAL, if (doc.isFullyPaid) InvoiceColors.PAID else InvoiceColors.INK),
        )
        val lineHeights = lines.map {
            maxOf(
                measurer.measureHeight(it.label, it.labelStyle, labelWidth),
                measurer.measureHeight(it.value, it.valueStyle, valueWidth),
            )
        }
        val rowGap = 8f
        val badgeHeight = if (doc.isFullyPaid) measurer.measureHeight(ArabicTerms.FULLY_PAID, TextStyle.BADGE, boxWidth) + 14f else 0f
        val boxHeight = inner * 2 + lineHeights.sum() + rowGap * (lines.size - 1) + if (doc.isFullyPaid) badgeHeight + rowGap else 0f
        val thanks = doc.thankYou
        val thanksHeight = thanks?.let { measurer.measureHeight(it, TextStyle.BODY, contentWidth) } ?: 0f
        val height = top + boxHeight + if (thanks != null) 14f + thanksHeight else 0f

        return Block(height) { y ->
            buildList {
                val boxTop = y + top
                add(DrawOp.Rect(spec.margin, boxTop, boxWidth, boxHeight, fill = InvoiceColors.PANEL, stroke = InvoiceColors.DIVIDER, strokeWidth = 1f, cornerRadius = 8f))
                var ly = boxTop + inner
                lines.forEachIndexed { i, line ->
                    add(text(spec.margin + boxWidth - inner - labelWidth, ly, labelWidth, line.label, line.labelStyle, HAlign.RIGHT))
                    add(text(spec.margin + inner, ly, valueWidth, line.value, line.valueStyle, HAlign.LEFT, line.color))
                    ly += lineHeights[i] + rowGap
                    if (i == 1) {
                        add(DrawOp.Line(spec.margin + inner, ly - rowGap / 2, spec.margin + boxWidth - inner, ly - rowGap / 2, InvoiceColors.DIVIDER, 1f))
                    }
                }
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
}
