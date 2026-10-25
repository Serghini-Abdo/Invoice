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
import ma.workshop.invoice.domain.backup.BackupDestination
import ma.workshop.invoice.domain.backup.BackupFailure
import ma.workshop.invoice.domain.backup.BackupFrequency
import ma.workshop.invoice.platform.backup.BackupOutcome
import ma.workshop.invoice.platform.drive.DriveAuthResult
import ma.workshop.invoice.platform.drive.DriveException
import ma.workshop.invoice.ui.Strings
import ma.workshop.invoice.ui.components.BannerKind
import kotlin.coroutines.cancellation.CancellationException

data class BackupMessage(val text: String, val kind: BannerKind, val detail: String = "")

/** A validated backup waiting for the user's confirmation. Nothing local has been touched yet. */
class PendingRestore(
    val invoiceCount: Int,
    val productCount: Int,
    val createdAt: Long,
    val data: BackupData,
)

data class BackupUiState(
    val working: Boolean = false,
    val messages: List<BackupMessage> = emptyList(),
    val pendingRestore: PendingRestore? = null,
)

private enum class DriveAction {
    /** Download the newest Drive copy and offer to restore it. */
    RESTORE,

    /** Ask for access, take a first backup, and only if that works switch Drive on. */
    ENABLE,

    /** Part of "back up now": the phone copy is already done, this is the Drive copy. */
    BACKUP_NOW,
}

/**
 * The backup screen: choose the automatic schedule and destinations, back up now, and restore.
 *
 * Restoring is staged: read/download, then full validation, then user confirmation, then a safety copy of
 * the current data, then one atomic database transaction. A bad file therefore can never touch existing data.
 * The actual backups are made by [ma.workshop.invoice.platform.backup.BackupService], the same code the
 * background worker uses.
 */
class BackupViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    val plan = container.backupPrefs.plan
    val status = container.backupPrefs.status

    val driveAccount: String? get() = container.backupPrefs.driveAccount

    private val consentChannel = Channel<PendingIntent>(Channel.BUFFERED)
    val consentRequests = consentChannel.receiveAsFlow()

    private var pendingAction: DriveAction? = null
    private val collected = ArrayList<BackupMessage>()

    // ---- schedule ----

    fun setFrequency(frequency: BackupFrequency) {
        val wasOff = plan.value.frequency == BackupFrequency.OFF
        changePlan { it.copy(frequency = frequency) }
        // Periodic work waits most of a period before its first run, so the first backup is taken right away.
        if (wasOff && frequency != BackupFrequency.OFF) backUpNow()
    }

    fun setLocal(enabled: Boolean) {
        if (!enabled && !plan.value.drive) return
        changePlan { it.copy(local = enabled) }
    }

    fun setDrive(enabled: Boolean) {
        if (enabled) {
            if (!_state.value.working) startDrive(DriveAction.ENABLE)
        } else {
            if (!plan.value.local) changePlan { it.copy(local = true, drive = false) } else changePlan { it.copy(drive = false) }
        }
    }

    fun reconnectDrive() {
        if (!_state.value.working) startDrive(DriveAction.ENABLE)
    }

    private fun changePlan(transform: (ma.workshop.invoice.domain.backup.BackupPlan) -> ma.workshop.invoice.domain.backup.BackupPlan) {
        container.backupPrefs.updatePlan(System.currentTimeMillis(), transform)
        container.backupScheduler.apply(plan.value)
    }

    // ---- back up now ----

    /** Backs up to the chosen destinations (the phone if none is chosen) and reports each result. */
    fun backUpNow() {
        if (_state.value.working) return
        _state.update { it.copy(working = true, messages = emptyList()) }
        collected.clear()
        viewModelScope.launch {
            val current = plan.value
            if (current.local || !current.drive) report(container.backupService.backUpLocal())
            if (current.drive) startDriveAuth(DriveAction.BACKUP_NOW) else finishCollected()
        }
    }

    private fun report(outcome: BackupOutcome) {
        collected += when (outcome) {
            is BackupOutcome.Done -> when {
                outcome.destination == BackupDestination.LOCAL && outcome.usedPrivateFolder ->
                    BackupMessage(Strings.PRIVATE_FOLDER_NOTE, BannerKind.Warning)
                outcome.destination == BackupDestination.LOCAL -> BackupMessage("${Strings.STATUS_PHONE}: ${Strings.BACKUP_SUCCESS}", BannerKind.Success)
                else -> BackupMessage("${Strings.STATUS_DRIVE}: ${Strings.BACKUP_SUCCESS}", BannerKind.Success)
            }
            is BackupOutcome.Failed -> failureMessage(outcome.destination, outcome.failure, outcome.detail)
        }
    }

    private fun failureMessage(destination: BackupDestination, failure: BackupFailure, detail: String) = BackupMessage(
        "${destination.label()}: ${failure.message()}. ${failure.advice()}",
        BannerKind.Error,
        detail,
    )

    private fun finishCollected() {
        pendingAction = null
        val messages = collected.toList()
        _state.update { it.copy(working = false, messages = messages) }
    }

    // ---- Google Drive ----

    fun restoreFromDrive() {
        if (_state.value.working) return
        collected.clear()
        _state.update { it.copy(working = true, messages = emptyList()) }
        viewModelScope.launch { startDriveAuth(DriveAction.RESTORE) }
    }

    private fun startDrive(action: DriveAction) {
        collected.clear()
        _state.update { it.copy(working = true, messages = emptyList()) }
        viewModelScope.launch { startDriveAuth(action) }
    }

    private suspend fun startDriveAuth(action: DriveAction) {
        pendingAction = action
        // Interactive: the owner is looking at the screen, so a consent or account screen is allowed here.
        when (val auth = container.drive.authorize(useSavedAccount = false)) {
            is DriveAuthResult.Token -> runDrive(action, auth.accessToken)
            is DriveAuthResult.NeedsConsent -> consentChannel.trySend(auth.pendingIntent)
            is DriveAuthResult.Failed -> onDriveFailure(action, auth.failure, auth.detail)
        }
    }

    /** Called with the result of Google's consent screen. */
    fun onConsentResult(data: Intent?) {
        val action = pendingAction ?: return
        val token = container.drive.tokenFromConsent(data)
        if (token == null) {
            viewModelScope.launch { onDriveFailure(action, BackupFailure.CONSENT_NEEDED, "consent declined") }
            return
        }
        viewModelScope.launch { runDrive(action, token.accessToken) }
    }

    private fun onDriveFailure(action: DriveAction, failure: BackupFailure, detail: String) {
        if (action == DriveAction.BACKUP_NOW) container.backupService.reportFailure(BackupDestination.DRIVE, failure, detail)
        collected += failureMessage(BackupDestination.DRIVE, failure, detail)
        finishCollected()
    }

    private suspend fun runDrive(action: DriveAction, token: String) {
        try {
            when (action) {
                DriveAction.BACKUP_NOW -> {
                    report(container.backupService.backUpDrive(token))
                    finishCollected()
                }
                DriveAction.ENABLE -> {
                    val outcome = container.backupService.backUpDrive(token)
                    // Drive is switched on only once a backup really reached it.
                    if (outcome is BackupOutcome.Done) changePlan { it.copy(drive = true) }
                    report(outcome)
                    finishCollected()
                }
                DriveAction.RESTORE -> {
                    val json = container.drive.downloadNewest(token)
                    if (json == null) finish(Strings.BACKUP_NOT_FOUND, BannerKind.Warning) else prepareRestore(json)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: DriveException) {
            collected += failureMessage(BackupDestination.DRIVE, e.failure, e.detail)
            finishCollected()
        } catch (e: Exception) {
            collected += failureMessage(BackupDestination.DRIVE, BackupFailure.DRIVE_UNKNOWN, e.javaClass.simpleName)
            finishCollected()
        }
    }

    // ---- files ----

    fun exportToFile(context: Context, uri: Uri) {
        if (_state.value.working) return
        _state.update { it.copy(working = true, messages = emptyList()) }
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
        _state.update { it.copy(working = true, messages = emptyList()) }
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
        _state.update { it.copy(pendingRestore = null, working = true, messages = emptyList()) }
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

    private suspend fun buildBackupJson(): String = container.backupService.buildJson()

    private fun finish(text: String, kind: BannerKind) {
        pendingAction = null
        _state.update { it.copy(working = false, messages = listOf(BackupMessage(text, kind))) }
    }
}
