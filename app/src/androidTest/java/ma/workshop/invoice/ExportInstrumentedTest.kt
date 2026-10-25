package ma.workshop.invoice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import ma.workshop.invoice.domain.invoice.Invoice
import ma.workshop.invoice.domain.invoice.InvoiceVersioning
import ma.workshop.invoice.domain.layout.InvoiceColors
import ma.workshop.invoice.domain.settings.AppSettings
import ma.workshop.invoice.domain.settings.ExportFormat
import ma.workshop.invoice.platform.export.ExportFiles
import ma.workshop.invoice.platform.export.InvoiceExporter
import ma.workshop.invoice.domain.palette.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneId
import kotlin.math.abs

/** Generates real PDF and PNG files on a device and checks that they open and are not blank. */
@RunWith(AndroidJUnit4::class)
class ExportInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val exporter = InvoiceExporter(context)
    private val zone = ZoneId.of("Africa/Casablanca")

    private fun settings(format: ExportFormat) = AppSettings(
        workshopName = "ورشة الأمل للحدادة",
        workshopPhone = "0522000000",
        exportFormat = format,
    )

    private fun invoice(itemCount: Int): Invoice {
        val items = (1..itemCount).map { i ->
            if (i % 3 == 0) TestData.hinges("h$i", 4, "35") else TestData.door("d$i", "1.20", "2.10", "${400 + i}")
        }
        return InvoiceVersioning.create(TestData.draft(items, deposit = "100"), "inv-export", number = 7, now = 1_792_929_600_000)
    }

    private fun export(invoice: Invoice, format: ExportFormat): ExportFiles =
        runBlocking { exporter.export(invoice, settings(format), zone) }

    private fun Bitmap.fractionNotWhite(): Double {
        var count = 0
        val row = IntArray(width)
        for (y in 0 until height step 2) {
            getPixels(row, 0, width, 0, y, width, 1)
            for (pixel in row) if (pixel != Color.WHITE && Color.alpha(pixel) > 0) count++
        }
        return count.toDouble() / (width.toDouble() * (height / 2.0))
    }

    private fun Bitmap.hasPixelNear(rgb: Int, fromY: Int = 0, toY: Int = height, tolerance: Int = 24): Boolean {
        val row = IntArray(width)
        for (y in fromY until toY.coerceAtMost(height) step 2) {
            getPixels(row, 0, width, 0, y, width, 1)
            for (pixel in row) {
                if (abs(Color.red(pixel) - Color.red(rgb)) <= tolerance &&
                    abs(Color.green(pixel) - Color.green(rgb)) <= tolerance &&
                    abs(Color.blue(pixel) - Color.blue(rgb)) <= tolerance
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun renderPdfPages(file: File): List<Bitmap> =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                (0 until renderer.pageCount).map { index ->
                    renderer.openPage(index).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        }

    /**
     * The payment summary is the tallest block filled with the invoice panel colour. The column-title strip
     * has the same fill but is only one small line high. (The unpaid balance is ink now, so it has no colour
     * of its own to look for.)
     */
    private fun Bitmap.hasPaymentSummary(fromY: Int = 0): Boolean = tallestPanelBlock(fromY) > width / 10

    private fun Bitmap.tallestPanelBlock(fromY: Int): Int {
        val panel = InvoiceColors.PANEL.toInt()
        val row = IntArray(width)
        var best = 0
        var run = 0
        for (y in fromY until height) {
            getPixels(row, 0, width, 0, y, width, 1)
            var span = 0
            var longest = 0
            for (pixel in row) {
                val near = abs(Color.red(pixel) - Color.red(panel)) <= 3 &&
                    abs(Color.green(pixel) - Color.green(panel)) <= 3 &&
                    abs(Color.blue(pixel) - Color.blue(panel)) <= 3
                span = if (near) span + 1 else 0
                if (span > longest) longest = span
            }
            run = if (longest > width / 3) run + 1 else 0
            if (run > best) best = run
        }
        return best
    }
    private val doorSteel = 0x37474F // door thumbnail frame

    // Scenario 16
    @Test
    fun pngHasTheRightSizeNameThumbnailsAndPaymentSummary() {
        val files = export(invoice(4), ExportFormat.PNG)
        assertNull(files.pdf)
        val file = files.png!!
        assertEquals("invoice-0007-2026-10-25.png", file.name)

        val bitmap = BitmapFactory.decodeFile(file.path)
        assertNotNull("the PNG must decode", bitmap)
        assertEquals(1080, bitmap.width)
        assertTrue("tall enough for four items and the summary", bitmap.height > 900)
        val ink = bitmap.fractionNotWhite()
        assertTrue("not blank (ink=$ink)", ink > 0.02)
        assertTrue("not a solid fill (ink=$ink)", ink < 0.6)

        Shots.savePng("sample-invoice-png", bitmap)
        assertTrue("product thumbnails are drawn", bitmap.hasPixelNear(doorSteel))
        // the total, deposit and balance box sits in the lower half of the image
        assertTrue("payment summary is drawn", bitmap.hasPaymentSummary(fromY = bitmap.height / 2))
        assertNotNull(files.preview)
    }

    @Test
    fun pdfOpensAndIsA4WithContent() {
        val files = export(invoice(4), ExportFormat.PDF)
        assertNull(files.png)
        val pdf = files.pdf!!
        assertEquals("invoice-0007-2026-10-25.pdf", pdf.name)
        assertTrue(pdf.length() > 2_000)

        val pages = renderPdfPages(pdf)
        pages.forEachIndexed { index, page -> Shots.savePng("sample-invoice-pdf-page${index + 1}", page) }
        assertEquals("a four-item invoice fits one A4 page", 1, pages.size)
        assertEquals(595, pages[0].width)
        assertEquals(842, pages[0].height)
        assertTrue("PDF page has content", pages[0].fractionNotWhite() > 0.01)
        assertTrue("PDF shows the thumbnails", pages[0].hasPixelNear(doorSteel))
        assertNotNull(files.preview)
    }

    @Test
    fun bothFormatsAreGeneratedTogether() {
        val files = export(invoice(2), ExportFormat.BOTH)
        assertNotNull(files.png)
        assertNotNull(files.pdf)
    }

    // Scenario 17
    @Test
    fun aLongInvoiceIsOneTallPngAndSeveralPdfPagesWithTheSummaryOnTheLastOnly() {
        val files = export(invoice(45), ExportFormat.BOTH)

        assertFalse(files.pngTooLong)
        val png = BitmapFactory.decodeFile(files.png!!.path)
        assertEquals(1080, png.width)
        assertTrue("PNG height ${png.height} stays under 8,000 px", png.height in 1..8_000)
        assertTrue("the summary is on the one PNG", png.hasPaymentSummary())

        val pages = renderPdfPages(files.pdf!!)
        assertTrue("expected several pages, got ${pages.size}", pages.size >= 3)
        pages.forEachIndexed { index, page ->
            assertEquals(595, page.width)
            assertEquals(842, page.height)
            assertTrue("page ${index + 1} has content", page.fractionNotWhite() > 0.005)
        }
        Shots.savePng("sample-long-pdf-page1", pages.first())
        Shots.savePng("sample-long-pdf-last-page", pages.last())
        assertTrue("payment summary on the last page", pages.last().hasPaymentSummary())
        assertTrue("no payment summary on the first page", !pages.first().hasPaymentSummary())
        // the yellow balance band is on the last page only
        assertTrue(pages.last().hasPixelNear(Palette.SECONDARY.toInt(), tolerance = 2))
        assertTrue(!pages.first().hasPixelNear(Palette.SECONDARY.toInt(), tolerance = 2))
    }

    @Test
    fun aTooLongInvoiceHasNoPngAndFallsBackToThePdfEvenWhenImageIsChosen() {
        val files = export(invoice(90), ExportFormat.PNG)
        assertTrue(files.pngTooLong)
        assertNull(files.png)
        val pdf = files.pdf!!
        assertTrue(renderPdfPages(pdf).size >= 4)
        assertNotNull(files.preview)
        val exports = File(context.cacheDir, "exports").listFiles { f -> f.name.startsWith("invoice-0007") }.orEmpty()
        assertTrue("no PNG file is written", exports.none { it.name.endsWith(".png") })
    }

    @Test
    fun filesAreSharedThroughContentUrisNeverRawPaths() {
        val files = export(invoice(2), ExportFormat.PNG)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", files.png!!)
        assertEquals("content", uri.scheme)
        assertTrue(!uri.toString().contains(context.cacheDir.path))
        context.contentResolver.openInputStream(uri).use { stream ->
            assertNotNull(stream)
            assertTrue(stream!!.read() >= 0)
        }
    }

    @Test
    fun exportingAnEditedInvoiceReplacesThePreviousFile() {
        val first = invoice(2)
        export(first, ExportFormat.PNG)
        val edited = InvoiceVersioning.edit(
            first,
            TestData.draft(first.items + TestData.hinges("new", 1, "10"), deposit = "100", replacing = first.id),
            "rev",
            now = 2,
        ).first
        val again = export(edited, ExportFormat.PNG)
        val exports = File(context.cacheDir, "exports").listFiles { f -> f.name.startsWith("invoice-0007") }.orEmpty()
        assertEquals(1, exports.size)
        assertEquals(again.png!!.name, exports.single().name)
    }
}
