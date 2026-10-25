package ma.workshop.invoice

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ma.workshop.invoice.domain.backup.BackupFiles
import ma.workshop.invoice.platform.backup.AppFolderBackupStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.time.ZoneOffset

/**
 * The real storage path on the emulator: MediaStore on Android 10+, the public Documents directory with a
 * runtime permission on Android 7 to 9. (Sorted by name: the fallback test must run before the permission is granted.)
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppFolderStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = AppFolderBackupStore(context) { ZoneOffset.UTC }
    private val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
    private val granted get() = ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** `pm grant` through the shell: UiAutomation.grantRuntimePermission only exists from Android 8. */
    private fun grantStoragePermission() {
        val pipe = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant ${context.packageName} $permission")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pipe).use { it.readBytes() } // waits for the command to finish
        assertTrue("permission granted", granted)
    }

    private fun uniqueName() = BackupFiles.nameFor(System.currentTimeMillis(), ZoneOffset.UTC, store.existingNames())

    @Test
    fun a_withoutStoragePermissionOnOldAndroidTheCopyStaysInsideTheApp() {
        assumeTrue("only Android 7 to 9 need the permission", Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
        assumeTrue("permission already granted by an earlier run", !granted)
        val name = uniqueName()
        val written = store.write(name, "{\"x\":1}".toByteArray())
        assertTrue("falls back to the private folder", written.inPrivateFolder)
        assertTrue(store.list(inPrivateFolder = true).any { it.name == name })
        assertTrue(store.delete(written.id, inPrivateFolder = true))
    }

    @Test
    fun b_backupIsWrittenToTheAppFolderListedAndDeleted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            grantStoragePermission()
        }
        val name = uniqueName()
        val bytes = "{\"hello\":\"world\"}".toByteArray()
        val written = store.write(name, bytes)

        assertFalse("written to the public folder, but fell back because: ${written.fallbackReason}", written.inPrivateFolder)
        assertTrue(store.list(inPrivateFolder = false).any { it.name == name })
        assertTrue(name in store.existingNames())
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            val file = File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), AppFolderBackupStore.FOLDER_NAME), name)
            assertTrue("file exists in Documents/${AppFolderBackupStore.FOLDER_NAME}", file.isFile)
            assertEquals(bytes.size.toLong(), file.length())
        } else {
            val uri = android.net.Uri.parse(written.id)
            val read = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            assertEquals(String(bytes), String(read))
        }
        assertTrue(store.delete(written.id, inPrivateFolder = false))
        assertFalse(store.list(inPrivateFolder = false).any { it.name == name })
    }

    @Test
    fun c_twoBackupsInTheSameMinuteDoNotOverwriteEachOther() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            grantStoragePermission()
        }
        val now = System.currentTimeMillis()
        val first = BackupFiles.nameFor(now, ZoneOffset.UTC, store.existingNames())
        val a = store.write(first, "{\"n\":1}".toByteArray())
        val second = BackupFiles.nameFor(now, ZoneOffset.UTC, store.existingNames())
        val b = store.write(second, "{\"n\":2}".toByteArray())
        try {
            assertTrue(first != second)
            val names = store.list(inPrivateFolder = false).map { it.name }
            assertTrue(first in names && second in names)
        } finally {
            store.delete(a.id, a.inPrivateFolder)
            store.delete(b.id, b.inPrivateFolder)
        }
    }
}
