package ma.workshop.invoice.domain

import ma.workshop.invoice.domain.catalog.Illustrations
import ma.workshop.invoice.domain.format.InvoiceDocumentBuilder
import ma.workshop.invoice.domain.invoice.CustomerInfo
import ma.workshop.invoice.domain.layout.DrawOp
import ma.workshop.invoice.domain.layout.HAlign
import ma.workshop.invoice.domain.layout.InvoiceLayout
import ma.workshop.invoice.domain.layout.InvoiceLayoutEngine
import ma.workshop.invoice.domain.layout.LayoutSpec
import ma.workshop.invoice.domain.layout.TextMeasurer
import ma.workshop.invoice.domain.layout.TextStyle
import ma.workshop.invoice.domain.pricing.PricingFormula
import java.io.File
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Developer aid, not an assertion-heavy test: renders the layout display list to HTML so the invoice
 * design (RTL placement, Arabic text, thumbnail proportions) can be looked at in a browser.
 * Output: domain/build/invoice-preview-*.html
 */
class InvoicePreviewTest {

    private val measurer = object : TextMeasurer {
        override fun measureHeight(text: String, style: TextStyle, maxWidth: Float): Float {
            val perLine = maxOf(1, (maxWidth / (style.size * 0.6f)).toInt())
            val lines = ceil(text.length / perLine.toDouble()).toInt().coerceAtLeast(1)
            return ceil(lines * style.size * 1.5f)
        }
    }

    private fun css(argb: Long): String =
        "rgba(${(argb shr 16) and 0xFF},${(argb shr 8) and 0xFF},${argb and 0xFF},${((argb shr 24) and 0xFF) / 255.0})"

    private fun illustrationSvg(key: String, x: Float, y: Float, size: Float): String = buildString {
        append("<svg style='position:absolute;left:${x}px;top:${y}px' viewBox='0 0 100 100' width='$size' height='$size'>")
        for (part in Illustrations.get(key).parts) {
            append("<path d='${part.pathData}' fill='${part.fill?.let(::css) ?: "none"}'")
            if (part.stroke != null) append(" stroke='${css(part.stroke!!)}' stroke-width='${part.strokeWidth}'")
            if (part.evenOdd) append(" fill-rule='evenodd'")
            append("/>")
        }
        append("</svg>")
    }

    private fun html(layout: InvoiceLayout, scale: Float, fontDir: File): String = buildString {
        append("<html><head><meta charset='utf-8'><style>")
        append("@font-face{font-family:Cairo;font-weight:400;src:url('file://${fontDir.absolutePath}/cairo_regular.ttf')}")
        append("@font-face{font-family:Cairo;font-weight:700;src:url('file://${fontDir.absolutePath}/cairo_bold.ttf')}")
        append("body{margin:0;background:#888;font-family:Cairo} .page{position:relative;background:#fff;margin:0 0 12px 0;overflow:hidden}")
        append(".t{position:absolute;direction:rtl;line-height:1.5;white-space:normal;outline:0px solid rgba(255,0,0,.25)}")
        append("</style></head><body>")
        for (page in layout.pages) {
            append("<div class='page' style='width:${layout.width * scale}px;height:${page.height * scale}px'>")
            append("<div style='transform:scale($scale);transform-origin:0 0;position:absolute;left:0;top:0;width:${layout.width}px;height:${page.height}px'>")
            for (op in page.ops) {
                when (op) {
                    is DrawOp.Rect -> append(
                        "<div style='position:absolute;left:${op.x}px;top:${op.y}px;width:${op.width}px;height:${op.height}px;" +
                            "background:${op.fill?.let(::css) ?: "transparent"};border-radius:${op.cornerRadius}px;" +
                            "box-sizing:border-box;${if (op.stroke != null) "border:${op.strokeWidth}px solid ${css(op.stroke!!)};" else ""}'></div>",
                    )
                    is DrawOp.Line -> append(
                        "<div style='position:absolute;left:${minOf(op.x1, op.x2)}px;top:${minOf(op.y1, op.y2)}px;" +
                            "width:${maxOf(op.width, kotlin.math.abs(op.x2 - op.x1))}px;height:${maxOf(op.width, kotlin.math.abs(op.y2 - op.y1))}px;" +
                            "background:${css(op.color)}'></div>",
                    )
                    is DrawOp.Image -> append(illustrationSvg(op.illustration, op.x, op.y, op.size))
                    is DrawOp.Text -> append(
                        "<div class='t' style='left:${op.x}px;top:${op.y}px;width:${op.width}px;font-size:${op.style.size}px;" +
                            "font-weight:${if (op.style.bold) 700 else 400};color:${css(op.color)};" +
                            "text-align:${when (op.align) { HAlign.LEFT -> "left"; HAlign.CENTER -> "center"; HAlign.RIGHT -> "right" }}'>${op.text}</div>",
                    )
                }
            }
            append("</div></div>")
        }
        append("</body></html>")
    }

    @Test
    fun `render invoice previews`() {
        val fontDir = File("../app/src/main/res/font")
        if (!fontDir.resolve("cairo_regular.ttf").exists()) return // preview needs the bundled font

        val zone = ZoneId.of("Africa/Casablanca")
        val items = listOf(
            Fixtures.doorItem("a", "1.20", "2.10", "500"),
            Fixtures.doorItem("b", "1.00", "2.00", "600"),
            Fixtures.item("c", "إطار حديد", "frame", Fixtures.pricing(PricingFormula.PERIMETER, Fixtures.cm("150"), Fixtures.cm("200"), 3, rate = "80")),
            Fixtures.hingesItem("d", 6, "35"),
            Fixtures.item("e", "نافذة ألمنيوم", "window", Fixtures.pricing(PricingFormula.AREA, Fixtures.m("1.5"), Fixtures.m("1.2"), 2, rate = "450")),
        )
        val invoice = Fixtures.invoice(items = items, deposit = Fixtures.dh("1000"), now = 1_792_929_600_000)
        val doc = InvoiceDocumentBuilder.build(invoice, Fixtures.settings(), zone)
        val engine = InvoiceLayoutEngine(measurer)

        val png = engine.layout(doc, LayoutSpec(width = 360f))
        File("build/invoice-preview-png.html").writeText(html(png, 2f, fontDir))

        val paidInvoice = Fixtures.invoice(items = items.take(2), deposit = Fixtures.dh("2460"), customer = CustomerInfo(), now = 1_792_929_600_000)
        val paid = engine.layout(InvoiceDocumentBuilder.build(paidInvoice, Fixtures.settings(), zone), LayoutSpec(width = 360f))
        File("build/invoice-preview-paid.html").writeText(html(paid, 2f, fontDir))

        val many = Fixtures.invoice(items = (1..14).map { Fixtures.doorItem("x$it", "1.20", "2.10", "${400 + it * 10}") }, deposit = Fixtures.dh("500"), now = 1_792_929_600_000)
        val pdf = engine.layout(InvoiceDocumentBuilder.build(many, Fixtures.settings(), zone), LayoutSpec(width = 520f, pageHeight = 520f * 842f / 595f))
        File("build/invoice-preview-pdf.html").writeText(html(pdf, 1f, fontDir))

        assertTrue(png.pages.size == 1 && pdf.pages.size >= 2)
    }
}
