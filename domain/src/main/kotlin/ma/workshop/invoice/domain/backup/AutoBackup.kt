package ma.workshop.invoice.domain.backup

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** How often backups run by themselves. */
enum class BackupFrequency(val id: String, val intervalMillis: Long) {
    OFF("off", 0L),
    DAILY("daily", 24L * 60 * 60 * 1000),
    WEEKLY("weekly", 7L * 24 * 60 * 60 * 1000);

    companion object {
        fun fromId(id: String?): BackupFrequency = entries.firstOrNull { it.id == id } ?: OFF
    }
}

enum class BackupDestination { LOCAL, DRIVE }

/**
 * What the owner chose. [scheduledSince] is when this choice was made; it is the starting point for the
 * "no backup for too long" warning of a destination that has never succeeded.
 */
data class BackupPlan(
    val frequency: BackupFrequency = BackupFrequency.OFF,
    val local: Boolean = true,
    val drive: Boolean = false,
    val scheduledSince: Long = 0L,
) {
    fun isEnabled(destination: BackupDestination): Boolean = frequency != BackupFrequency.OFF && when (destination) {
        BackupDestination.LOCAL -> local
        BackupDestination.DRIVE -> drive
    }

    /** True if both choices were made on purpose; also used to reset [scheduledSince] on a change. */
    fun sameChoiceAs(other: BackupPlan): Boolean = frequency == other.frequency && local == other.local && drive == other.drive
}

/** Why a backup did not happen. [needsUser] means retrying alone will never fix it. */
enum class BackupFailure(val needsUser: Boolean, val retryable: Boolean) {
    // Google Drive
    NO_PLAY_SERVICES(true, false),
    DEVELOPER_ERROR(true, false),
    DRIVE_API_DISABLED(true, false),
    NO_ACCOUNT(true, false),
    CONSENT_NEEDED(true, false),
    TOKEN_REJECTED(false, true),
    ACCESS_DENIED(true, false),
    NO_NETWORK(false, true),
    RATE_LIMITED(false, true),
    DRIVE_FULL(true, false),
    SERVER_ERROR(false, true),
    DRIVE_UNKNOWN(false, true),

    // This phone
    LOCAL_PERMISSION(true, false),
    LOCAL_FULL(true, false),
    LOCAL_WRITE_FAILED(false, true),

    // Either
    DATA_INVALID(false, false);

    companion object {
        fun fromId(id: String?): BackupFailure? = entries.firstOrNull { it.name == id }
    }
}

/** Translates what Google Play services and the Drive REST API report into a [BackupFailure]. */
object DriveFailureMapper {

    // Constants of com.google.android.gms.common.api.CommonStatusCodes / GoogleSignInStatusCodes.
    private const val SERVICE_MISSING = 1
    private const val SERVICE_VERSION_UPDATE_REQUIRED = 2
    private const val SERVICE_DISABLED = 3
    private const val SIGN_IN_REQUIRED = 4
    private const val INVALID_ACCOUNT = 5
    private const val RESOLUTION_REQUIRED = 6
    private const val NETWORK_ERROR = 7
    private const val DEVELOPER_ERROR = 10
    private const val SERVICE_INVALID = 9
    private const val TIMEOUT = 15
    private const val SIGN_IN_FAILED = 12500

    fun fromPlayStatus(code: Int): BackupFailure = when (code) {
        DEVELOPER_ERROR -> BackupFailure.DEVELOPER_ERROR
        SERVICE_MISSING, SERVICE_VERSION_UPDATE_REQUIRED, SERVICE_DISABLED, SERVICE_INVALID -> BackupFailure.NO_PLAY_SERVICES
        SIGN_IN_REQUIRED, INVALID_ACCOUNT -> BackupFailure.NO_ACCOUNT
        RESOLUTION_REQUIRED -> BackupFailure.CONSENT_NEEDED
        NETWORK_ERROR, TIMEOUT -> BackupFailure.NO_NETWORK
        SIGN_IN_FAILED -> BackupFailure.DEVELOPER_ERROR
        else -> BackupFailure.DRIVE_UNKNOWN
    }

    /** [reason] is the first `errors[].reason` of Google's JSON error body, if there was one. */
    fun fromHttp(status: Int, reason: String?): BackupFailure = when {
        status == 401 -> BackupFailure.TOKEN_REJECTED
        status == 403 || status == 429 -> when (reason) {
            "accessNotConfigured", "serviceDisabled" -> BackupFailure.DRIVE_API_DISABLED
            "storageQuotaExceeded", "quotaExceeded" -> BackupFailure.DRIVE_FULL
            "rateLimitExceeded", "userRateLimitExceeded", "dailyLimitExceeded", "sharingRateLimitExceeded" -> BackupFailure.RATE_LIMITED
            else -> if (status == 429) BackupFailure.RATE_LIMITED else BackupFailure.ACCESS_DENIED
        }
        status == 408 -> BackupFailure.NO_NETWORK
        status in 500..599 -> BackupFailure.SERVER_ERROR
        else -> BackupFailure.DRIVE_UNKNOWN
    }
}

/** A file already stored at a destination. [timestamp] decides which is newest. */
data class StoredBackup(val id: String, val name: String, val timestamp: Long)

/** Names and retention of backup files. Only files this app named are ever candidates for deletion. */
object BackupFiles {
    /** `backup-2026-10-25-1430.json`, with `-2`, `-3`... only if that minute is already taken. */
    private val pattern = Regex("""^backup-(\d{4})-(\d{2})-(\d{2})-(\d{2})(\d{2})(?:-(\d+))?\.json$""")
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")

    /** Name used before automatic backups existed; still recognised so old Drive copies are pruned and restorable. */
    const val LEGACY_DRIVE_NAME = "workshop-invoice-backup.json"

    fun nameFor(epochMillis: Long, zone: ZoneId, taken: Set<String> = emptySet()): String {
        val base = "backup-${stamp.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone))}"
        var candidate = "$base.json"
        var n = 2
        while (candidate in taken) candidate = "$base-${n++}.json"
        return candidate
    }

    fun isManagedName(name: String): Boolean = pattern.matches(name) || name == LEGACY_DRIVE_NAME

    /** The moment encoded in a managed name, or null for any other file. */
    fun timestampOf(name: String, zone: ZoneId): Long? {
        val m = pattern.matchEntire(name) ?: return null
        val g = m.groupValues
        return try {
            LocalDateTime.of(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt()).atZone(zone).toInstant().toEpochMilli()
        } catch (e: java.time.DateTimeException) {
            null
        }
    }
}

object BackupRetention {
    /**
     * Which stored backups may be deleted after [justCreatedId] was written successfully.
     *
     * Rules, in order of importance:
     *  - the new copy is never deleted, even if clock changes make it look old;
     *  - at least one copy is always kept ([keepNewest] below 1 is treated as 1);
     *  - only files with a name this app produced are candidates, so a user's own files in the folder are untouched;
     *  - of those, the newest [keepNewest] stay and the rest go.
     */
    fun toDelete(existing: List<StoredBackup>, justCreatedId: String, keepNewest: Int): List<StoredBackup> {
        val keep = maxOf(keepNewest, 1)
        val managed = existing.filter { BackupFiles.isManagedName(it.name) }
        val newestFirst = managed.sortedWith(compareByDescending<StoredBackup> { it.timestamp }.thenByDescending { it.name })
        val kept = LinkedHashSet<String>()
        kept += justCreatedId
        for (candidate in newestFirst) {
            if (kept.size >= keep) break
            kept += candidate.id
        }
        return newestFirst.filter { it.id !in kept }
    }

    const val KEEP_LOCAL = 10
    const val KEEP_DRIVE = 5
}

/** The outcome of one destination so far. */
data class DestinationStatus(
    val lastSuccessAt: Long? = null,
    val lastAttemptAt: Long? = null,
    val lastFailure: BackupFailure? = null,
    /** Technical detail for the owner's helper, e.g. "api 10" or "HTTP 403 accessNotConfigured". */
    val lastDetail: String = "",
    /** Where the last local copy really went when it was not the public folder. */
    val usedPrivateFolder: Boolean = false,
)

data class BackupStatus(
    val local: DestinationStatus = DestinationStatus(),
    val drive: DestinationStatus = DestinationStatus(),
) {
    operator fun get(destination: BackupDestination): DestinationStatus = when (destination) {
        BackupDestination.LOCAL -> local
        BackupDestination.DRIVE -> drive
    }

    fun with(destination: BackupDestination, status: DestinationStatus): BackupStatus = when (destination) {
        BackupDestination.LOCAL -> copy(local = status)
        BackupDestination.DRIVE -> copy(drive = status)
    }
}

enum class AlertKind {
    /** The owner has to do something (reconnect, grant a permission, fix the setup). */
    NEEDS_ACTION,

    /** No successful backup for more than twice the chosen interval. */
    OVERDUE,
}

data class BackupAlert(val destination: BackupDestination, val kind: AlertKind, val failure: BackupFailure?)

object BackupHealth {

    /** Problems worth showing on the home screen and at the top of the backup screen. */
    fun alerts(plan: BackupPlan, status: BackupStatus, now: Long): List<BackupAlert> {
        val result = ArrayList<BackupAlert>()
        for (destination in BackupDestination.entries) {
            if (!plan.isEnabled(destination)) continue
            val s = status[destination]
            val failure = s.lastFailure
            if (failure != null && failure.needsUser) {
                result += BackupAlert(destination, AlertKind.NEEDS_ACTION, failure)
            } else if (isOverdue(plan, s, now)) {
                result += BackupAlert(destination, AlertKind.OVERDUE, failure)
            }
        }
        return result
    }

    /** More than twice the interval since the last success (or since the schedule was set up). */
    fun isOverdue(plan: BackupPlan, status: DestinationStatus, now: Long): Boolean {
        if (plan.frequency == BackupFrequency.OFF) return false
        val reference = status.lastSuccessAt ?: plan.scheduledSince
        return now - reference > 2 * plan.frequency.intervalMillis
    }

    /** A catch-up run is worthwhile when the phone was off or Android postponed the job. */
    fun isDue(plan: BackupPlan, destination: BackupDestination, status: BackupStatus, now: Long): Boolean {
        if (!plan.isEnabled(destination)) return false
        val reference = status[destination].lastSuccessAt ?: plan.scheduledSince
        return now - reference >= plan.frequency.intervalMillis
    }
}
