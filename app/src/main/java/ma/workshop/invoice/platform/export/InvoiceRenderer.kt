package ma.workshop.invoice.platform.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import ma.workshop.invoice.R
import ma.workshop.invoice.domain.catalog.IllustrationPart
import ma.workshop.invoice.domain.catalog.Illustrations
import ma.workshop.invoice.domain.format.InvoiceDocument
import ma.workshop.invoice.domain.layout.DrawOp
import ma.workshop.invoice.domain.layout.HAlign
import ma.workshop.invoice.domain.layout.InvoiceLayout
import ma.workshop.invoice.domain.layout.InvoiceLayoutEngine
import ma.workshop.invoice.domain.layout.LayoutSpec
import ma.workshop.invoice.domain.layout.TextMeasurer
import ma.workshop.invoice.domain.layout.TextStyle
import java.io.File
import kotlin.math.ceil

/** Wraps and measures text with the real Cairo font, as right-to-left paragraphs. */
class AndroidTextMeasurer(private val regular: Typeface, private val bold: Typeface) : TextMeasurer {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    override fun measureHeight(text: String, style: TextStyle, maxWidth: Float): Float {
        paint.typeface = if (style.bold) bold else regular
        paint.textSize = style.size
        return buildLayout(text, paint, maxWidth, Layout.Alignment.ALIGN_NORMAL).height.toFloat()
    }

    companion object {
        fun buildLayout(text: CharSequence, paint: TextPaint, width: Float, alignment: Layout.Alignment): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, ceil(width).toInt().coerceAtLeast(1))
                .setAlignment(alignment)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setIncludePad(false)
                .setLineSpacing(0f, 1f)
                .build()
    }
}

/**
 * Draws the layout display list onto an Android [Canvas]. PNG and PDF both go through exactly this
 * code, so the two formats carry the same content, thumbnails and calculations.
 *
 * Layout units are scaled to pixels (PNG, 3 px per unit for a 1080 px wide image) or to points
 * (PDF, A4 595 x 842 pt).
 */
class InvoiceRenderer(context: Context) {

    private val regular: Typeface = ResourcesCompat.getFont(context, R.font.cairo_regular) ?: Typeface.DEFAULT
    private val bold: Typeface = ResourcesCompat.getFont(context, R.font.cairo_bold) ?: Typeface.DEFAULT_BOLD
    private val engine = InvoiceLayoutEngine(AndroidTextMeasurer(regular, bold))

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private class PartPath(val part: IllustrationPart, val path: Path)

    private val illustrationPaths = HashMap<String, List<PartPath>>()

    // ---- layouts ----

    fun layoutForPng(doc: InvoiceDocument): InvoiceLayout {
        val single = engine.layout(doc, LayoutSpec(width = PNG_WIDTH_UNITS))
        // Very long invoices become several images instead of one gigantic bitmap.
        return if (single.pages[0].height * PNG_SCALE <= PNG_MAX_PIXELS) {
            single
        } else {
            engine.layout(doc, LayoutSpec(width = PNG_WIDTH_UNITS, pageHeight = PNG_MAX_PIXELS / PNG_SCALE))
        }
    }

    fun layoutForPdf(doc: InvoiceDocument): InvoiceLayout =
        engine.layout(doc, LayoutSpec(width = PDF_WIDTH_UNITS, pageHeight = PDF_PAGE_HEIGHT / PDF_SCALE))

    // ---- outputs ----

    /** One bitmap per page of [layout]. The caller owns (and should recycle) the bitmaps. */
    fun renderBitmaps(layout: InvoiceLayout, scale: Float): List<Bitmap> =
        layout.pages.indices.map { index ->
            val page = layout.pages[index]
            val bitmap = Bitmap.createBitmap(
                ceil(layout.width * scale).toInt(),
                ceil(page.height * scale).toInt(),
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            canvas.scale(scale, scale)
            drawPage(canvas, layout, index)
            bitmap
        }

    /** Writes PNG file(s) and returns them in page order. Usually exactly one file. */
    fun writePng(doc: InvoiceDocument, directory: File, baseName: String): List<File> {
        val layout = layoutForPng(doc)
        val bitmaps = try {
            renderBitmaps(layout, PNG_SCALE)
        } catch (e: OutOfMemoryError) {
            // Report as a normal failure: the invoice is already saved, only this export is lost.
            throw IllegalStateException("not enough memory to draw the invoice image", e)
        }
        try {
            return bitmaps.mapIndexed { index, bitmap ->
                val name = if (bitmaps.size == 1) "$baseName.png" else "$baseName-${index + 1}.png"
                val file = File(directory, name)
                file.outputStream().buffered().use { out ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG encoding failed" }
                }
                file
            }
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    fun writePdf(doc: InvoiceDocument, file: File) {
        val layout = layoutForPdf(doc)
        val pdf = PdfDocument()
        try {
            layout.pages.indices.forEach { index ->
                val info = PdfDocument.PageInfo.Builder(PDF_PAGE_WIDTH.toInt(), PDF_PAGE_HEIGHT.toInt(), index + 1).create()
                val page = pdf.startPage(info)
                page.canvas.drawColor(Color.WHITE)
                page.canvas.scale(PDF_SCALE, PDF_SCALE)
                drawPage(page.canvas, layout, index)
                pdf.finishPage(page)
            }
            file.outputStream().buffered().use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }

    // ---- drawing ----

    private fun drawPage(canvas: Canvas, layout: InvoiceLayout, pageIndex: Int) {
        for (op in layout.pages[pageIndex].ops) {
            when (op) {
                is DrawOp.Rect -> drawRect(canvas, op)
                is DrawOp.Line -> {
                    strokePaint.color = op.color.toInt()
                    strokePaint.strokeWidth = op.width
                    canvas.drawLine(op.x1, op.y1, op.x2, op.y2, strokePaint)
                }
                is DrawOp.Text -> drawText(canvas, op)
                is DrawOp.Image -> drawIllustration(canvas, op)
            }
        }
    }

    private fun drawRect(canvas: Canvas, op: DrawOp.Rect) {
        val rect = RectF(op.x, op.y, op.x + op.width, op.y + op.height)
        op.fill?.let {
            fillPaint.color = it.toInt()
            if (op.cornerRadius > 0f) canvas.drawRoundRect(rect, op.cornerRadius, op.cornerRadius, fillPaint) else canvas.drawRect(rect, fillPaint)
        }
        op.stroke?.let {
            strokePaint.color = it.toInt()
            strokePaint.strokeWidth = op.strokeWidth
            val inset = op.strokeWidth / 2f
            val strokeRect = RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
            if (op.cornerRadius > 0f) canvas.drawRoundRect(strokeRect, op.cornerRadius, op.cornerRadius, strokePaint) else canvas.drawRect(strokeRect, strokePaint)
        }
    }

    private fun drawText(canvas: Canvas, op: DrawOp.Text) {
        textPaint.typeface = if (op.style.bold) bold else regular
        textPaint.textSize = op.style.size
        textPaint.color = op.color.toInt()
        // The paragraph is always right-to-left, so "normal" means the page's right edge.
        val alignment = when (op.align) {
            HAlign.RIGHT -> Layout.Alignment.ALIGN_NORMAL
            HAlign.LEFT -> Layout.Alignment.ALIGN_OPPOSITE
            HAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
        }
        val layout = AndroidTextMeasurer.buildLayout(op.text, textPaint, op.width, alignment)
        canvas.save()
        canvas.translate(op.x, op.y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawIllustration(canvas: Canvas, op: DrawOp.Image) {
        val parts = illustrationPaths.getOrPut(op.illustration) {
            Illustrations.get(op.illustration).parts.map { part ->
                val path = PathParser.createPathFromPathData(part.pathData)
                path.fillType = if (part.evenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                PartPath(part, path)
            }
        }
        canvas.save()
        canvas.translate(op.x, op.y)
        val scale = op.size / Illustrations.VIEWPORT
        canvas.scale(scale, scale)
        for (partPath in parts) {
            val part = partPath.part
            part.fill?.let {
                fillPaint.color = it.toInt()
                canvas.drawPath(partPath.path, fillPaint)
            }
            part.stroke?.let {
                strokePaint.color = it.toInt()
                strokePaint.strokeWidth = part.strokeWidth
                canvas.drawPath(partPath.path, strokePaint)
            }
        }
        canvas.restore()
    }

    companion object {
        /** 360 units at 3 px per unit is a 1080 px wide image, ideal for WhatsApp. */
        const val PNG_WIDTH_UNITS = 360f
        const val PNG_SCALE = 3f
        const val PNG_MAX_PIXELS = 8_000f

        /** A4 in PostScript points. The invoice is laid out 520 units wide and scaled to fit. */
        const val PDF_PAGE_WIDTH = 595f
        const val PDF_PAGE_HEIGHT = 842f
        const val PDF_WIDTH_UNITS = 520f
        const val PDF_SCALE = PDF_PAGE_WIDTH / PDF_WIDTH_UNITS
    }
}
