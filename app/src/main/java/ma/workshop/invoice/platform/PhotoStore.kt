package ma.workshop.invoice.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import ma.workshop.invoice.domain.catalog.PhotoKey
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Photos picked for products. Each is downscaled to about 512 px and kept in the app's private storage,
 * so nothing depends on the gallery afterwards. An invoice item gets its own literal copy ([copyFor]).
 */
class PhotoStore(private val context: Context) {

    fun import(uri: Uri): String? = try {
        val bitmap = decode(uri) ?: return null
        val name = "p-${UUID.randomUUID().toString().replace("-", "").take(20)}.jpg"
        File(directory(context), name).outputStream().buffered().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)) { "JPEG encoding failed" }
        }
        bitmap.recycle()
        PhotoKey.keyFor(name)
    } catch (e: Exception) {
        null
    }

    /** A byte-for-byte copy under a new name, so editing or deleting the product photo never touches an invoice. */
    fun copyFor(key: String): String {
        val source = file(context, key) ?: return key
        val name = "i-${UUID.randomUUID().toString().replace("-", "").take(20)}.jpg"
        return try {
            source.copyTo(File(directory(context), name))
            PhotoKey.keyFor(name)
        } catch (e: Exception) {
            key
        }
    }

    /** Deletes every photo file that nothing refers to any more. */
    fun sweep(referenced: Set<String>) {
        directory(context).listFiles()?.forEach { if (it.name !in referenced) it.delete() }
    }

    private fun decode(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    companion object {
        const val MAX_SIDE = 512

        fun directory(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }

        /** The photo file of [key], or null when it is not a photo key or the file is gone. */
        fun file(context: Context, key: String): File? =
            PhotoKey.fileName(key)?.let { File(directory(context), it) }?.takeIf { it.isFile }
    }
}
