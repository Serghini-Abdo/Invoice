package ma.workshop.invoice.ui.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ma.workshop.invoice.AppContainer
import ma.workshop.invoice.BuildConfig
import ma.workshop.invoice.domain.backup.BackupCodec
import ma.workshop.invoice.domain.backup.BackupData
import ma.workshop.invoice.domain.backup.BackupFormat
import ma.workshop.invoice.domain.backup.BackupParseResult
import ma.workshop.invoice.domain.backup.BackupProblemCode
import ma.workshop.invoice.platform.drive.DriveAuthResult
import ma.workshop.invoice.platform.drive.DriveException
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.components.BannerKind
import kotlin.coroutines.cancellation.CancellationException

data class BackupMessage(val text: String, val kind: BannerKind)

/** A validated backup waiting for the user's confirmation. Nothing local has been touched yet. */
class PendingRestore(
    val invoiceCount: Int,
    val productCount: Int,
    val createdAt: Long,
    val data: BackupData,
)

data class BackupUiState(
    val working: Boolean = false,
    val message: BackupMessage? = null,
    val pendingRestore: PendingRestore? = null,
)

private enum class DriveAction { BACKUP, RESTORE }

/**
 * Backup and restore, via Google Drive (explicit user action, needs internet and consent) or a plain
 * file. Restoring is staged: download/read, then full validation, then user confirmation, then a
 * safety copy of the current data, then one atomic database transaction. A bad file therefore can
 * never touch existing data.
 */
class BackupViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private val consentChannel = Channel<PendingIntent>(Channel.BUFFERED)
    val consentRequests = consentChannel.receiveAsFlow()

    private var pendingAction: DriveAction? = null

    // ---- Google Drive ----

    fun backupToDrive() = startDrive(DriveAction.BACKUP)

    fun restoreFromDrive() = startDrive(DriveAction.RESTORE)

    private fun startDrive(action: DriveAction) {
        if (_state.value.working) return
        pendingAction = action
        _state.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            when (val auth = container.drive.authorize()) {
                is DriveAuthResult.Token -> runDrive(action, auth.accessToken)
                is DriveAuthResult.NeedsConsent -> consentChannel.trySend(auth.pendingIntent)
                DriveAuthResult.PlayServicesMissing -> finish(Strings.PLAY_SERVICES_MISSING, BannerKind.Warning)
                is DriveAuthResult.Failed -> finish(Strings.AUTH_NEEDED, BannerKind.Warning)
            }
        }
    }

    /** Called with the result of Google's one-time consent screen. */
    fun onConsentResult(data: Intent?) {
        val action = pendingAction ?: return
        val token = container.drive.tokenFromConsent(data)
        if (token == null) {
            finish(Strings.AUTH_NEEDED, BannerKind.Warning)
            return
        }
        viewModelScope.launch { runDrive(action, token) }
    }

    private suspend fun runDrive(action: DriveAction, token: String) {
        try {
            when (action) {
                DriveAction.BACKUP -> {
                    container.drive.upload(token, buildBackupJson())
                    finish(Strings.BACKUP_SUCCESS, BannerKind.Success)
                }
                DriveAction.RESTORE -> {
                    val json = container.drive.download(token)
                    if (json == null) finish(Strings.BACKUP_NOT_FOUND, BannerKind.Warning) else prepareRestore(json)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: DriveException) {
            finish(
                when (e.kind) {
                    DriveException.Kind.NETWORK -> Strings.NO_INTERNET
                    DriveException.Kind.AUTH -> Strings.AUTH_NEEDED
                    DriveException.Kind.NOT_FOUND -> Strings.BACKUP_NOT_FOUND
                    else -> Strings.DRIVE_ERROR
                },
                BannerKind.Error,
            )
        } catch (e: Exception) {
            finish(Strings.DRIVE_ERROR, BannerKind.Error)
        }
    }

    // ---- files ----

    fun exportToFile(context: Context, uri: Uri) {
        if (_state.value.working) return
        _state.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            try {
                val json = buildBackupJson()
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("cannot open output")
                    stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                finish(Strings.BACKUP_SUCCESS, BannerKind.Success)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                finish(Strings.FILE_ERROR, BannerKind.Error)
            }
        }
    }

    fun importFromFile(context: Context, uri: Uri) {
        if (_state.value.working) return
        _state.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { readLimited(context, uri) }
                if (text == null) finish(Strings.BACKUP_FILE_INVALID, BannerKind.Error) else prepareRestore(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                finish(Strings.BACKUP_FILE_INVALID, BannerKind.Error)
            }
        }
    }

    private fun readLimited(context: Context, uri: Uri): String? {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { stream ->
            val bytes = stream.readNBytesCompat(BackupFormat.MAX_BYTES + 1)
            if (bytes.size > BackupFormat.MAX_BYTES) return null
            return String(bytes, Charsets.UTF_8)
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        var total = 0
        while (total < limit) {
            val n = read(chunk, 0, minOf(chunk.size, limit - total))
            if (n < 0) break
            out.write(chunk, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    // ---- staged restore ----

    /** Validates fully; only a valid backup ever reaches the confirmation step. */
    private fun prepareRestore(json: String) {
        when (val result = BackupCodec.parse(json)) {
            is BackupParseResult.Valid -> _state.update {
                it.copy(
                    working = false,
                    pendingRestore = PendingRestore(
                        invoiceCount = result.data.invoices.size,
                        productCount = result.data.products.size,
                        createdAt = result.document.createdAt,
                        data = result.data,
                    ),
                )
            }
            is BackupParseResult.Invalid -> {
                val newer = result.problems.any { it.code == BackupProblemCode.UNSUPPORTED_VERSION }
                finish(if (newer) Strings.BACKUP_FILE_NEWER else Strings.BACKUP_FILE_INVALID, BannerKind.Error)
            }
        }
    }

    fun cancelRestore() {
        _state.update { it.copy(pendingRestore = null) }
    }

    fun confirmRestore() {
        val pending = _state.value.pendingRestore ?: return
        _state.update { it.copy(pendingRestore = null, working = true, message = null) }
        viewModelScope.launch {
            try {
                // Not cancellable halfway: either the whole restore applies or none of it does.
                withContext(NonCancellable) {
                    val safety = container.restoreSafety
                    val current = BackupCodec.encode(BackupCodec.toDocument(container.backups.export(), System.currentTimeMillis(), BuildConfig.VERSION_NAME))
                    safety.writeSafetyCopy(current, System.currentTimeMillis())
                    safety.begin()
                    container.backups.restore(pending.data)
                    safety.finish()
                }
                finish(Strings.RESTORE_SUCCESS, BannerKind.Success)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The transaction rolled back, so local data is exactly as it was.
                container.restoreSafety.finish()
                finish(Strings.RESTORE_FAILED, BannerKind.Error)
            }
        }
    }

    private suspend fun buildBackupJson(): String =
        BackupCodec.encode(BackupCodec.toDocument(container.backups.export(), System.currentTimeMillis(), BuildConfig.VERSION_NAME))

    private fun finish(text: String, kind: BannerKind) {
        pendingAction = null
        _state.update { it.copy(working = false, message = BackupMessage(text, kind)) }
    }
}
