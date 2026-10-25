package ma.workshop.invoice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ma.workshop.invoice.domain.catalog.PhotoKey
import ma.workshop.invoice.platform.PhotoStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Photo import: downscale to 512 px, a literal copy per item, and removal of unreferenced files. */
@RunWith(AndroidJUnit4::class)
class PhotoStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = PhotoStore(context)

    @Before
    fun clean() {
        PhotoStore.directory(context).listFiles()?.forEach { it.delete() }
    }

    private fun bigPhoto(width: Int, height: Int): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 120, 40)) }
        val file = File(context.cacheDir, "picked.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file)
    }

    @Test
    fun importedPhotoIsDownscaledToAbout512PixelsAndStoredPrivately() {
        val key = store.import(bigPhoto(2000, 1000))!!
        assertTrue(PhotoKey.isPhoto(key))
        val file = PhotoStore.file(context, key)!!
        assertTrue(file.path.startsWith(context.filesDir.path))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        assertEquals(512, maxOf(bounds.outWidth, bounds.outHeight))
        assertEquals(256, minOf(bounds.outWidth, bounds.outHeight))
    }

    @Test
    fun anUnreadablePickGivesNoKey() {
        assertNull(store.import(Uri.fromFile(File(context.cacheDir, "does-not-exist.png"))))
    }

    @Test
    fun eachItemGetsItsOwnLiteralCopy() {
        val product = store.import(bigPhoto(800, 600))!!
        val copy = store.copyFor(product)
        assertNotEquals(product, copy)
        val a = PhotoStore.file(context, product)!!
        val b = PhotoStore.file(context, copy)!!
        assertTrue(a.readBytes().contentEquals(b.readBytes()))
        // deleting the product photo never touches the item's copy
        a.delete()
        assertNotNull(PhotoStore.file(context, copy))
        assertEquals("door", store.copyFor("door"))
    }

    @Test
    fun sweepKeepsReferencedPhotosAndRemovesTheRest() {
        val keep = store.import(bigPhoto(300, 300))!!
        val drop = store.import(bigPhoto(300, 300))!!
        store.sweep(PhotoKey.referencedIn(listOf(keep)))
        assertNotNull(PhotoStore.file(context, keep))
        assertNull(PhotoStore.file(context, drop))
    }
}
