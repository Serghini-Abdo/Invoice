package ma.workshop.invoice.platform.export

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ma.workshop.invoice.domain.format.InvoiceDocumentBuilder
import ma.workshop.invoice.domain.invoice.Invoice
import ma.workshop.invoice.domain.settings.AppSettings
import ma.workshop.invoice.domain.settings.ExportFormat
import java.io.File
import java.time.ZoneId

/** The generated files of one export and a preview of the first page. */
class ExportFiles(
    val png: List<File>,
    val pdf: File?,
    val preview: Bitmap?,
)

/**
 * Creates the PDF and/or PNG of a saved invoice on the device, with no network. Files go to the app's
 * cache directory and are only ever handed to other apps through FileProvider content:// URIs.
 *
 * Exporting is entirely separate from saving: a failure here is reported but the invoice is already
 * safe in the database.
 */
class InvoiceExporter(private val context: Context) {

    private val renderer by lazy { InvoiceRenderer(context) }

    private val directory: File
        get() = File(context.cacheDir, "exports").apply { mkdirs() }

    suspend fun export(
        invoice: Invoice,
        settings: AppSettings,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ExportFiles = withContext(Dispatchers.Default) {
        val doc = InvoiceDocumentBuilder.build(invoice, settings, zone)
        val base = InvoiceDocumentBuilder.baseFileName(invoice, zone)
        deleteStale(base)

        val wantPng = settings.exportFormat != ExportFormat.PDF
        val wantPdf = settings.exportFormat != ExportFormat.PNG

        val png = if (wantPng) renderer.writePng(doc, directory, base) else emptyList()
        val pdf = if (wantPdf) File(directory, "$base.pdf").also { renderer.writePdf(doc, it) } else null

        // The preview is the first page of the file that will actually be shared.
        val preview = when {
            png.isNotEmpty() -> decodePreview(png.first())
            pdf != null -> renderPdfFirstPage(pdf)
            else -> null
        }
        ExportFiles(png, pdf, preview)
    }

    /** Removes older exports of this invoice (an edited invoice reuses the same name). */
    private fun deleteStale(base: String) {
        directory.listFiles { file -> file.name.startsWith(base) }?.forEach { it.delete() }
        // Keep the cache small: drop any export older than a day.
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        directory.listFiles { file -> file.lastModified() < cutoff }?.forEach { it.delete() }
    }

    private fun decodePreview(file: File): Bitmap? {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= PREVIEW_WIDTH) sample *= 2
        val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        return android.graphics.BitmapFactory.decodeFile(file.path, options)
    }

    /** Opening the PDF with the platform renderer also proves the file is a valid, readable PDF. */
    private fun renderPdfFirstPage(file: File): Bitmap? =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(0).use { page ->
                    val scale = PREVIEW_WIDTH.toFloat() / page.width
                    val bitmap = Bitmap.createBitmap(PREVIEW_WIDTH, (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }

    companion object {
        private const val PREVIEW_WIDTH = 720
    }
}

object ShareHelper {

    /**
     * Opens Android's share sheet for [files]. Returns false when nothing can handle the share.
     * Opening the sheet does not mean anything was sent, and the UI never claims it was.
     */
    fun share(context: Context, files: List<File>): Boolean {
        if (files.isEmpty()) return false
        val authority = "${context.packageName}.fileprovider"
        val uris: List<Uri> = files.map { FileProvider.getUriForFile(context, authority, it) }

        val mimeTypes = files.map { mimeOf(it) }.toSet()
        val mime = if (mimeTypes.size == 1) mimeTypes.first() else "*/*"

        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.type = mime
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uris.first()).also { clip ->
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        }

        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun mimeOf(file: File): String = if (file.name.endsWith(".pdf")) "application/pdf" else "image/png"
}
