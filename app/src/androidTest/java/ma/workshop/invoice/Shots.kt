package ma.workshop.invoice

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Saves pictures from the instrumented tests into the directory Android Gradle Plugin copies back to the
 * build machine (`additionalTestOutputDir`). They are inspected by people (and by CI on manual runs) to
 * check how the real app and the real PDF/PNG output look; no test asserts on them.
 */
object Shots {

    private val directory: File? =
        InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let { File(it).apply { mkdirs() } }

    /** Saves a downscaled JPEG (small enough to review comfortably). */
    fun saveScaled(name: String, bitmap: Bitmap, maxWidth: Int = 540) {
        val dir = directory ?: return
        val scaled = if (bitmap.width > maxWidth) {
            Bitmap.createScaledBitmap(bitmap, maxWidth, (bitmap.height * maxWidth.toFloat() / bitmap.width).toInt(), true)
        } else {
            bitmap
        }
        File(dir, "$name.jpg").outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }

    /** Saves a bitmap losslessly; used for invoice samples where small Arabic text must stay crisp. */
    fun savePng(name: String, bitmap: Bitmap) {
        val dir = directory ?: return
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
