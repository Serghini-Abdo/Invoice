package ma.workshop.invoice.platform.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ma.workshop.invoice.domain.backup.BackupCodec
import ma.workshop.invoice.domain.backup.BackupData
import ma.workshop.invoice.domain.backup.BackupDestination
import ma.workshop.invoice.domain.backup.BackupFailure
import ma.workshop.invoice.domain.backup.BackupFiles
import ma.workshop.invoice.domain.backup.BackupParseResult
import ma.workshop.invoice.domain.backup.BackupRetention
import ma.workshop.invoice.domain.backup.StoredBackup
import ma.workshop.invoice.platform.drive.DriveAuthResult
import ma.workshop.invoice.platform.drive.DriveBackupApi
import ma.workshop.invoice.platform.drive.DriveException
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException

sealed interface BackupOutcome {
    data class Done(val destination: BackupDestination, val usedPrivateFolder: Boolean = false) : BackupOutcome
    data class Failed(val destination: BackupDestination, val failure: BackupFailure, val detail: String) : BackupOutcome
}

/**
 * Takes one backup to one destination and records what happened in [BackupPrefs].
 *
 * The data goes through the same codec as manual backups and is parsed back (full validation) before it is
 * written, so a backup that could not be restored is never stored. A new copy is written and verified first;
 * only then are old ones removed (see [BackupRetention]), so the last good copy can never be lost.
 *
 * Used by the screen ("back up now") and by the background worker. It never shows UI.
 */
class BackupService(
    private val export: suspend () -> BackupData,
    private val prefs: BackupPrefs,
    private val local: () -> LocalBackupStore,
    private val drive: () -> DriveBackupApi,
    private val appVersion: String,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val keepLocal: Int = BackupRetention.KEEP_LOCAL,
    private val keepDrive: Int = BackupRetention.KEEP_DRIVE,
) {

    /** The encoded backup, proven restorable. Throws [IllegalStateException] if it is not. */
    suspend fun buildJson(): String {
        val json = BackupCodec.encode(BackupCodec.toDocument(export(), now(), appVersion))
        check(BackupCodec.parse(json) is BackupParseResult.Valid) { "the backup just built does not validate" }
        return json
    }

    suspend fun backUpLocal(): BackupOutcome = guarded(BackupDestination.LOCAL) {
        val bytes = buildJson().toByteArray(Charsets.UTF_8)
        withContext(Dispatchers.IO) {
            val store = local()
            val name = BackupFiles.nameFor(now(), zone(), store.existingNames())
            val written = store.write(name, bytes)
            // Verified and complete: now older copies in the same folder may go.
            prune(store.list(written.inPrivateFolder), written.id, keepLocal) { store.delete(it.id, written.inPrivateFolder) }
            prefs.recordSuccess(BackupDestination.LOCAL, now(), written.inPrivateFolder)
            BackupOutcome.Done(BackupDestination.LOCAL, written.inPrivateFolder)
        }
    }

    /** Uses Google's silent authorization; if the owner's consent is gone this fails with CONSENT_NEEDED and no UI. */
    suspend fun backUpDriveSilently(): BackupOutcome = when (val auth = drive().authorize(useSavedAccount = true)) {
        is DriveAuthResult.Token -> backUpDrive(auth.accessToken)
        is DriveAuthResult.NeedsConsent -> fail(BackupDestination.DRIVE, BackupFailure.CONSENT_NEEDED, "consent screen required")
        is DriveAuthResult.Failed -> fail(BackupDestination.DRIVE, auth.failure, auth.detail)
    }

    suspend fun backUpDrive(token: String): BackupOutcome = guarded(BackupDestination.DRIVE) {
        val json = buildJson()
        val api = drive()
        val existing = api.list(token)
        val name = BackupFiles.nameFor(now(), zone(), existing.map { it.name }.toSet())
        val created = api.create(token, name, json)
        val expected = json.toByteArray(Charsets.UTF_8).size.toLong()
        if (created.size != null && created.size != expected) {
            runCatching { api.delete(token, created.id) }
            throw DriveException(BackupFailure.DRIVE_UNKNOWN, "stored ${created.size} bytes, expected $expected")
        }
        val all = existing.map { StoredBackup(it.id, it.name, BackupFiles.timestampOf(it.name, zone()) ?: it.modifiedAt) } +
            StoredBackup(created.id, created.name, BackupFiles.timestampOf(created.name, zone()) ?: created.modifiedAt)
        prune(all, created.id, keepDrive) { api.delete(token, it.id) }
        prefs.recordSuccess(BackupDestination.DRIVE, now())
        BackupOutcome.Done(BackupDestination.DRIVE)
    }

    private suspend fun prune(all: List<StoredBackup>, newId: String, keep: Int, delete: suspend (StoredBackup) -> Unit) {
        for (old in BackupRetention.toDelete(all, newId, keep)) {
            try {
                delete(old)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // An old copy that cannot be removed now is removed next time; the new backup already succeeded.
            }
        }
    }

    private suspend fun guarded(destination: BackupDestination, block: suspend () -> BackupOutcome): BackupOutcome = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: DriveException) {
        fail(destination, e.failure, e.detail)
    } catch (e: LocalBackupException) {
        fail(destination, e.failure, e.detail)
    } catch (e: IllegalStateException) {
        fail(destination, BackupFailure.DATA_INVALID, e.message.orEmpty())
    } catch (e: Exception) {
        fail(destination, if (destination == BackupDestination.DRIVE) BackupFailure.DRIVE_UNKNOWN else BackupFailure.LOCAL_WRITE_FAILED, e.javaClass.simpleName + ": " + e.message.orEmpty())
    }

    /** Records a failure that happened before the service itself ran, such as a refused sign-in. */
    fun reportFailure(destination: BackupDestination, failure: BackupFailure, detail: String): BackupOutcome = fail(destination, failure, detail)

    private fun fail(destination: BackupDestination, failure: BackupFailure, detail: String): BackupOutcome {
        prefs.recordFailure(destination, now(), failure, detail)
        return BackupOutcome.Failed(destination, failure, detail)
    }
}
