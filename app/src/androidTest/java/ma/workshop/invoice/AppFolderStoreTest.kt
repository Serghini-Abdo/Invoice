package ma.workshop.invoice

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    /**
     * Android 7 to 9 apply a storage grant to processes started after it, so the public-folder tests only run
     * when the permission was already granted when this process started (the CI script grants it with `pm grant`
     * and starts a fresh instrumentation run). Android 10+ needs no permission.
     */
    private val publicFolderUsable get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || granted

    private fun uniqueName() = BackupFiles.nameFor(System.currentTimeMillis(), ZoneOffset.UTC, store.existingNames())

    /**
     * Why the public folder is not writable, seen from inside this process: ids and groups, which storage view is
     * mounted, and the permissions of the folders on the way. Only used in failure messages (CI log).
     */
    private fun storageDiagnostics(): String {
        fun lines(path: String, keep: (String) -> Boolean = { true }) =
            runCatching { File(path).readLines().filter(keep).joinToString("\n    ") }.getOrElse { "unreadable: $it" }
        fun shell(command: String) = runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "$command 2>&1"))
            process.inputStream.bufferedReader().readText().trim().also { process.waitFor() }
        }.getOrElse { "failed: $it" }
        @Suppress("DEPRECATION")
        val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return buildString {
            appendLine()
            appendLine("diagnostics (inside the app process):")
            appendLine("  pid/uid: ${android.os.Process.myPid()}/${android.os.Process.myUid()}, permission granted=$granted")
            appendLine("  /proc/self/status ids: " + lines("/proc/self/status") { it.startsWith("Uid") || it.startsWith("Gid") || it.startsWith("Groups") })
            appendLine("  mounts of the storage view:\n    " + lines("/proc/self/mountinfo") { "emulated" in it || "sdcard" in it || "/storage" in it })
            appendLine("  documents dir=$documents exists=${documents.isDirectory} canWrite=${documents.canWrite()} parent canWrite=${documents.parentFile?.canWrite()}")
            appendLine("  ls -ld:\n    " + shell("ls -ld /sdcard /storage/emulated/0 '${documents.absolutePath}' '${documents.absolutePath}/${AppFolderBackupStore.FOLDER_NAME}'"))
            appendLine("  id: " + shell("id"))
            append("  can create a plain file in Documents: " + runCatching { File(documents, ".probe-${System.nanoTime()}").also { it.createNewFile(); it.delete() }.let { "yes" } }.getOrElse { it.toString() })
        }
    }

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
        assumeTrue("needs the storage permission granted before the process started", publicFolderUsable)
        val name = uniqueName()
        val bytes = "{\"hello\":\"world\"}".toByteArray()
        val written = store.write(name, bytes)

        assertFalse("written to the public folder, but fell back because: ${written.fallbackReason}${if (written.inPrivateFolder) storageDiagnostics() else ""}", written.inPrivateFolder)
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
        assumeTrue("needs the storage permission granted before the process started", publicFolderUsable)
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
