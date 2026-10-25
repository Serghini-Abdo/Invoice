package ma.workshop.invoice.platform.backup

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ma.workshop.invoice.domain.backup.BackupDestination
import ma.workshop.invoice.domain.backup.BackupFailure
import ma.workshop.invoice.domain.backup.BackupFrequency
import ma.workshop.invoice.domain.backup.BackupPlan
import ma.workshop.invoice.domain.backup.BackupStatus
import ma.workshop.invoice.domain.backup.DestinationStatus

/**
 * The schedule the owner chose and what happened so far. Kept in SharedPreferences, not in the invoice
 * database: it describes this phone (a restored backup must not switch another phone's schedule), and it
 * keeps the Room schema untouched. Background workers and the screens share one instance, so they agree.
 */
class BackupPrefs(private val prefs: SharedPreferences) {

    private val _plan = MutableStateFlow(readPlan())
    val plan: StateFlow<BackupPlan> = _plan.asStateFlow()

    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<BackupStatus> = _status.asStateFlow()

    /** The Google account that agreed to Drive access; lets background runs ask for it silently. */
    var driveAccount: String?
        get() = prefs.getString(KEY_ACCOUNT, null)
        set(value) {
            prefs.edit().putString(KEY_ACCOUNT, value).apply()
        }

    @Synchronized
    fun updatePlan(now: Long, transform: (BackupPlan) -> BackupPlan) {
        val old = _plan.value
        var new = transform(old)
        if (!new.sameChoiceAs(old)) new = new.copy(scheduledSince = now)
        prefs.edit()
            .putString(KEY_FREQUENCY, new.frequency.id)
            .putBoolean(KEY_LOCAL, new.local)
            .putBoolean(KEY_DRIVE, new.drive)
            .putLong(KEY_SINCE, new.scheduledSince)
            .apply()
        _plan.value = new
    }

    @Synchronized
    fun recordSuccess(destination: BackupDestination, now: Long, usedPrivateFolder: Boolean = false) {
        write(destination, DestinationStatus(lastSuccessAt = now, lastAttemptAt = now, usedPrivateFolder = usedPrivateFolder))
    }

    @Synchronized
    fun recordFailure(destination: BackupDestination, now: Long, failure: BackupFailure, detail: String) {
        val old = _status.value[destination]
        write(destination, old.copy(lastAttemptAt = now, lastFailure = failure, lastDetail = detail))
    }

    private fun write(destination: BackupDestination, status: DestinationStatus) {
        val p = destination.name.lowercase()
        prefs.edit().apply {
            putLong("$p.success", status.lastSuccessAt ?: -1L)
            putLong("$p.attempt", status.lastAttemptAt ?: -1L)
            putString("$p.failure", status.lastFailure?.name)
            putString("$p.detail", status.lastDetail)
            putBoolean("$p.private", status.usedPrivateFolder)
        }.apply()
        _status.value = _status.value.with(destination, status)
    }

    private fun readPlan() = BackupPlan(
        frequency = BackupFrequency.fromId(prefs.getString(KEY_FREQUENCY, null)),
        local = prefs.getBoolean(KEY_LOCAL, true),
        drive = prefs.getBoolean(KEY_DRIVE, false),
        scheduledSince = prefs.getLong(KEY_SINCE, 0L),
    )

    private fun readStatus() = BackupStatus(
        local = read(BackupDestination.LOCAL),
        drive = read(BackupDestination.DRIVE),
    )

    private fun read(destination: BackupDestination): DestinationStatus {
        val p = destination.name.lowercase()
        return DestinationStatus(
            lastSuccessAt = prefs.getLong("$p.success", -1L).takeIf { it >= 0 },
            lastAttemptAt = prefs.getLong("$p.attempt", -1L).takeIf { it >= 0 },
            lastFailure = BackupFailure.fromId(prefs.getString("$p.failure", null)),
            lastDetail = prefs.getString("$p.detail", "").orEmpty(),
            usedPrivateFolder = prefs.getBoolean("$p.private", false),
        )
    }

    private companion object {
        const val KEY_FREQUENCY = "plan.frequency"
        const val KEY_LOCAL = "plan.local"
        const val KEY_DRIVE = "plan.drive"
        const val KEY_SINCE = "plan.since"
        const val KEY_ACCOUNT = "drive.account"
    }
}
