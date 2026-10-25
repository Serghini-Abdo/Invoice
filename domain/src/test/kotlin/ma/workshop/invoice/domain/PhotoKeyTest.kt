package ma.workshop.invoice.domain

import ma.workshop.invoice.domain.catalog.PhotoKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoKeyTest {
    @Test
    fun `only well formed photo keys are photos`() {
        assertTrue(PhotoKey.isPhoto("photo:p-abc123.jpg"))
        assertEquals("p-abc123.jpg", PhotoKey.fileName("photo:p-abc123.jpg"))
        assertFalse(PhotoKey.isPhoto("door"))
        assertFalse(PhotoKey.isPhoto("photo:../secret.jpg"), "no path tricks")
        assertFalse(PhotoKey.isPhoto("photo:a/b.jpg"))
        assertFalse(PhotoKey.isPhoto("photo:x.png"))
        assertNull(PhotoKey.fileName("window"))
    }

    @Test
    fun `references are found in plain columns and in json text`() {
        val found = PhotoKey.referencedIn(
            listOf("door", "photo:p-1.jpg", """{"items":[{"illustration":"photo:i-2.jpg"}],"x":"photo:i-2.jpg"}"""),
        )
        assertEquals(setOf("p-1.jpg", "i-2.jpg"), found)
        assertEquals("photo:p-1.jpg", PhotoKey.keyFor("p-1.jpg"))
    }
}
