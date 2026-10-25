package ma.workshop.invoice.platform.backup

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import ma.workshop.invoice.domain.backup.BackupFailure
import ma.workshop.invoice.domain.backup.BackupFiles
import ma.workshop.invoice.domain.backup.StoredBackup
import java.io.File
import java.io.IOException
import java.time.ZoneId

class LocalBackupException(val failure: BackupFailure, val detail: String) : Exception("$failure: $detail")

/** Where a local backup really ended up. [inPrivateFolder] means the public folder could not be used. */
class LocalWrite(val id: String, val name: String, val inPrivateFolder: Boolean, val fallbackReason: String? = null)

/** The phone-side destination. An interface so the worker tests can run without touching real storage. */
interface LocalBackupStore {
    /** Names already used in the folder that the next write would go to (so names never collide). */
    fun existingNames(): Set<String>

    /** Writes the file completely and verifies it before returning; a half-written file is never left behind. */
    fun write(name: String, bytes: ByteArray): LocalWrite

    fun list(inPrivateFolder: Boolean): List<StoredBackup>

    fun delete(id: String, inPrivateFolder: Boolean): Boolean
}

/**
 * Backups go to `Documents/Fawatir Warsha/`, a stable folder that is created automatically.
 *
 * - Android 10+ (API 29+): MediaStore with a relative path. No permission is needed for the app's own files.
 *   The app can only see and prune files it created itself; after a reinstall earlier files stay untouched
 *   (they are never deleted, only no longer counted).
 * - Android 7 to 9 (API 24 to 28): the public Documents directory. Needs WRITE_EXTERNAL_STORAGE, requested
 *   by the screen; without it the copy goes to the app's own storage and the screen says so.
 */
class AppFolderBackupStore(private val context: Context, private val zone: () -> ZoneId = { ZoneId.systemDefault() }) : LocalBackupStore {

    private val resolver get() = context.contentResolver
    private val modern get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private val privateDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, FOLDER_NAME).also { it.mkdirs() }

    @Suppress("DEPRECATION")
    private val legacyPublicDir: File
        get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), FOLDER_NAME)

    override fun existingNames(): Set<String> =
        (runCatching { list(false) }.getOrDefault(emptyList()) + runCatching { list(true) }.getOrDefault(emptyList())).map { it.name }.toSet()

    override fun write(name: String, bytes: ByteArray): LocalWrite {
        try {
            return if (modern) writeMediaStore(name, bytes) else writeLegacy(name, bytes)
        } catch (e: LocalBackupException) {
            // A full disk will not be better in another folder; every other problem falls back to private storage.
            if (e.failure == BackupFailure.LOCAL_FULL) throw e
            return writePrivate(name, bytes, reason = "${e.failure}: ${e.detail}")
        }
    }

    override fun list(inPrivateFolder: Boolean): List<StoredBackup> = when {
        inPrivateFolder -> listFiles(privateDir)
        modern -> listMediaStore()
        else -> if (hasLegacyPermission()) listFiles(legacyPublicDir) else emptyList()
    }

    override fun delete(id: String, inPrivateFolder: Boolean): Boolean = try {
        if (inPrivateFolder || !modern) File(id).delete() else resolver.delete(Uri.parse(id), null, null) > 0
    } catch (e: Exception) {
        false
    }

    // ---- API 29+ ----

    private fun writeMediaStore(name: String, bytes: ByteArray): LocalWrite {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Files.getContentUri("external_primary"), values)
            ?: throw LocalBackupException(BackupFailure.LOCAL_WRITE_FAILED, "mediastore insert returned null")
        try {
            resolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                ?: throw LocalBackupException(BackupFailure.LOCAL_WRITE_FAILED, "no output stream")
            val stored = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
            if (stored != bytes.size.toLong()) throw LocalBackupException(BackupFailure.LOCAL_WRITE_FAILED, "size mismatch $stored")
            // Only now does the file become visible to other apps.
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return LocalWrite(uri.toString(), name, inPrivateFolder = false)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw asLocalFailure(e)
        }
    }

    private fun listMediaStore(): List<StoredBackup> {
        val result = ArrayList<StoredBackup>()
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_MODIFIED)
        val base = MediaStore.Files.getContentUri("external_primary")
        resolver.query(base, projection, "${MediaStore.MediaColumns.RELATIVE_PATH}=?", arrayOf("$RELATIVE_PATH/"), null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1) ?: continue
                val modified = cursor.getLong(2) * 1000
                result += StoredBackup(
                    id = android.content.ContentUris.withAppendedId(base, cursor.getLong(0)).toString(),
                    name = name,
                    timestamp = BackupFiles.timestampOf(name, zone()) ?: modified,
                )
            }
        }
        return result
    }

    // ---- API 24 to 28 ----

    private fun hasLegacyPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun writeLegacy(name: String, bytes: ByteArray): LocalWrite {
        if (!hasLegacyPermission()) throw LocalBackupException(BackupFailure.LOCAL_PERMISSION, "WRITE_EXTERNAL_STORAGE not granted")
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            throw LocalBackupException(BackupFailure.LOCAL_WRITE_FAILED, "storage not mounted")
        }
        val file = writeFile(legacyPublicDir, name, bytes)
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(MIME), null)
        return LocalWrite(file.absolutePath, name, inPrivateFolder = false)
    }

    // ---- app-private fallback ----

    private fun writePrivate(name: String, bytes: ByteArray, reason: String): LocalWrite {
        val file = writeFile(privateDir, name, bytes)
        return LocalWrite(file.absolutePath, name, inPrivateFolder = true, fallbackReason = reason)
    }

    // ---- shared ----

    /** Writes next to the final name first and renames only once complete and verified. */
    private fun writeFile(directory: File, name: String, bytes: ByteArray): File {
        try {
            if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
                throw LocalBackupException(
                    BackupFailure.LOCAL_WRITE_FAILED,
                    "cannot create folder (parent exists=${directory.parentFile?.isDirectory}, parent writable=${directory.parentFile?.canWrite()}, storage=${Environment.getExternalStorageState()})",
                )
            }
            val temp = File(directory, "$name.tmp")
            val target = File(directory, name)
            temp.outputStream().use { it.write(bytes) }
            if (temp.length() != bytes.size.toLong()) {
                temp.delete()
                throw LocalBackupException(BackupFailure.LOCAL_WRITE_FAILED, "size mismatch ${temp.length()}")
            }
            if (!temp.renameTo(target)) {
                temp.delete()
                throw LocalBackupException(BackupFailure.LOCAL_WRITE_FAILED, "rename failed")
            }
            return target
        } catch (e: LocalBackupException) {
            throw e
        } catch (e: Exception) {
            throw asLocalFailure(e)
        }
    }

    private fun listFiles(directory: File): List<StoredBackup> =
        directory.listFiles().orEmpty().filter { it.isFile }.map {
            StoredBackup(it.absolutePath, it.name, BackupFiles.timestampOf(it.name, zone()) ?: it.lastModified())
        }

    private fun asLocalFailure(e: Exception): LocalBackupException {
        if (e is LocalBackupException) return e
        val full = e is IOException && (e.message.orEmpty().contains("ENOSPC") || e.message.orEmpty().contains("No space left"))
        return LocalBackupException(if (full) BackupFailure.LOCAL_FULL else BackupFailure.LOCAL_WRITE_FAILED, e.javaClass.simpleName + ": " + e.message.orEmpty())
    }

    companion object {
        /** The folder shown in the phone's file manager. Latin on purpose: other apps and computers handle it best. */
        const val FOLDER_NAME = "Fawatir Warsha"
        const val RELATIVE_PATH = "Documents/$FOLDER_NAME"
        private const val MIME = "application/json"

        /** Makes the system file picker open in the backup folder (honoured on Android 8+, ignored before). */
        fun pickerStartUri(): Uri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:$RELATIVE_PATH",
        )
    }
}
