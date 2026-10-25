package ma.workshop.invoice.domain

import ma.workshop.invoice.domain.backup.AlertKind
import ma.workshop.invoice.domain.backup.BackupDestination
import ma.workshop.invoice.domain.backup.BackupFailure
import ma.workshop.invoice.domain.backup.BackupFiles
import ma.workshop.invoice.domain.backup.BackupFrequency
import ma.workshop.invoice.domain.backup.BackupHealth
import ma.workshop.invoice.domain.backup.BackupPlan
import ma.workshop.invoice.domain.backup.BackupRetention
import ma.workshop.invoice.domain.backup.BackupStatus
import ma.workshop.invoice.domain.backup.DestinationStatus
import ma.workshop.invoice.domain.backup.DriveFailureMapper
import ma.workshop.invoice.domain.backup.StoredBackup
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoBackupTest {

    private val utc = ZoneOffset.UTC
    private val day = 24L * 60 * 60 * 1000

    // ---- names ----

    @Test
    fun fileNamesAreAsciiSortableAndRoundTrip() {
        val t = java.time.LocalDateTime.of(2026, 10, 5, 9, 7).toInstant(ZoneOffset.UTC).toEpochMilli()
        val name = BackupFiles.nameFor(t, utc)
        assertEquals("backup-2026-10-05-0907.json", name)
        assertTrue(name.all { it.code < 128 })
        assertEquals(t, BackupFiles.timestampOf(name, utc))
        val later = BackupFiles.nameFor(t + 60_000, utc)
        assertTrue(later > name, "names must sort in time order")
    }

    @Test
    fun aNameTakenInTheSameMinuteGetsASuffixInsteadOfOverwriting() {
        val t = 1_700_000_000_000L
        val first = BackupFiles.nameFor(t, utc)
        val second = BackupFiles.nameFor(t, utc, setOf(first))
        val third = BackupFiles.nameFor(t, utc, setOf(first, second))
        assertEquals(3, setOf(first, second, third).size)
        assertTrue(BackupFiles.isManagedName(second) && BackupFiles.isManagedName(third))
        assertEquals(BackupFiles.timestampOf(first, utc), BackupFiles.timestampOf(second, utc))
    }

    @Test
    fun onlyOurOwnNamesAreManaged() {
        assertTrue(BackupFiles.isManagedName("backup-2026-01-01-0000.json"))
        assertTrue(BackupFiles.isManagedName(BackupFiles.LEGACY_DRIVE_NAME))
        assertFalse(BackupFiles.isManagedName("backup-notes.json"))
        assertFalse(BackupFiles.isManagedName("invoice-0001.pdf"))
        assertFalse(BackupFiles.isManagedName("backup-2026-13-45-9999.json.tmp"))
        assertNull(BackupFiles.timestampOf("backup-2026-13-45-2599.json", utc))
    }

    @Test
    fun theZoneChangesTheNameButNotTheMoment() {
        val t = 1_700_000_000_000L
        val casablanca = ZoneId.of("Africa/Casablanca")
        val back = BackupFiles.timestampOf(BackupFiles.nameFor(t, casablanca), casablanca)!!
        assertTrue(t - back in 0 until 60_000, "names have minute precision")
    }

    // ---- retention ----

    private fun stored(id: String, minute: Int) = StoredBackup(id, BackupFiles.nameFor(minute * 60_000L, utc), minute * 60_000L)

    @Test
    fun keepsTheNewestNAndDeletesTheRest() {
        val all = (1..12).map { stored("id$it", it) }
        val deleted = BackupRetention.toDelete(all, justCreatedId = "id12", keepNewest = 10).map { it.id }
        assertEquals(listOf("id2", "id1"), deleted)
    }

    @Test
    fun theNewCopyIsNeverDeletedEvenIfTheClockWentBackwards() {
        val all = listOf(stored("a", 100), stored("b", 101), stored("c", 102), stored("new", 5))
        val deleted = BackupRetention.toDelete(all, "new", keepNewest = 2).map { it.id }
        assertFalse("new" in deleted)
        assertEquals(2, all.size - deleted.size)
    }

    @Test
    fun atLeastOneCopyIsAlwaysKept() {
        val all = listOf(stored("a", 1), stored("b", 2))
        assertEquals(listOf("a"), BackupRetention.toDelete(all, "b", keepNewest = 0).map { it.id })
        assertTrue(BackupRetention.toDelete(listOf(stored("only", 1)), "only", keepNewest = 1).isEmpty())
    }

    @Test
    fun filesWeDidNotNameAreNeverDeleted() {
        val mine = (1..4).map { stored("id$it", it) }
        val foreign = StoredBackup("x", "my-own-notes.json", 0)
        val deleted = BackupRetention.toDelete(mine + foreign, "id4", keepNewest = 2).map { it.id }
        assertEquals(listOf("id2", "id1"), deleted)
    }

    @Test
    fun theLegacyDriveFileIsPrunedLikeAnyOtherOldCopy() {
        val legacy = StoredBackup("old", BackupFiles.LEGACY_DRIVE_NAME, 1)
        val fresh = (10..14).map { stored("n$it", it) }
        val deleted = BackupRetention.toDelete(listOf(legacy) + fresh, "n14", keepNewest = 5).map { it.id }
        assertEquals(listOf("old"), deleted)
    }

    @Test
    fun nothingIsDeletedWhenThereAreFewerCopiesThanTheLimit() {
        assertTrue(BackupRetention.toDelete((1..3).map { stored("i$it", it) }, "i3", 10).isEmpty())
    }

    // ---- error mapping ----

    @Test
    fun playServicesStatusCodesBecomeSpecificFailures() {
        assertEquals(BackupFailure.DEVELOPER_ERROR, DriveFailureMapper.fromPlayStatus(10))
        assertEquals(BackupFailure.DEVELOPER_ERROR, DriveFailureMapper.fromPlayStatus(12500))
        assertEquals(BackupFailure.NO_PLAY_SERVICES, DriveFailureMapper.fromPlayStatus(1))
        assertEquals(BackupFailure.NO_ACCOUNT, DriveFailureMapper.fromPlayStatus(4))
        assertEquals(BackupFailure.NO_NETWORK, DriveFailureMapper.fromPlayStatus(7))
        assertEquals(BackupFailure.CONSENT_NEEDED, DriveFailureMapper.fromPlayStatus(6))
        assertEquals(BackupFailure.DRIVE_UNKNOWN, DriveFailureMapper.fromPlayStatus(99999))
    }

    @Test
    fun httpStatusesBecomeSpecificFailures() {
        assertEquals(BackupFailure.TOKEN_REJECTED, DriveFailureMapper.fromHttp(401, null))
        assertEquals(BackupFailure.DRIVE_API_DISABLED, DriveFailureMapper.fromHttp(403, "accessNotConfigured"))
        assertEquals(BackupFailure.DRIVE_FULL, DriveFailureMapper.fromHttp(403, "storageQuotaExceeded"))
        assertEquals(BackupFailure.RATE_LIMITED, DriveFailureMapper.fromHttp(403, "userRateLimitExceeded"))
        assertEquals(BackupFailure.RATE_LIMITED, DriveFailureMapper.fromHttp(429, null))
        assertEquals(BackupFailure.ACCESS_DENIED, DriveFailureMapper.fromHttp(403, "insufficientPermissions"))
        assertEquals(BackupFailure.SERVER_ERROR, DriveFailureMapper.fromHttp(503, null))
        assertEquals(BackupFailure.DRIVE_UNKNOWN, DriveFailureMapper.fromHttp(418, null))
    }

    @Test
    fun failuresThatNeedThePersonAreNeverRetried() {
        for (f in BackupFailure.entries) {
            assertFalse(f.needsUser && f.retryable, "$f cannot be both")
        }
        assertTrue(BackupFailure.NO_NETWORK.retryable)
        assertTrue(BackupFailure.DEVELOPER_ERROR.needsUser)
        assertTrue(BackupFailure.CONSENT_NEEDED.needsUser)
    }

    // ---- health ----

    private val plan = BackupPlan(BackupFrequency.DAILY, local = true, drive = true, scheduledSince = 0)

    @Test
    fun nothingIsReportedWhenAutomaticBackupIsOff() {
        val off = plan.copy(frequency = BackupFrequency.OFF)
        assertTrue(BackupHealth.alerts(off, BackupStatus(), 100 * day).isEmpty())
    }

    @Test
    fun aDestinationThatIsNotChosenIsNotChecked() {
        val localOnly = plan.copy(drive = false)
        val status = BackupStatus(local = DestinationStatus(lastSuccessAt = 10 * day))
        assertTrue(BackupHealth.alerts(localOnly, status, 10 * day + 1).isEmpty())
    }

    @Test
    fun staleMeansMoreThanTwiceTheInterval() {
        val status = BackupStatus(
            local = DestinationStatus(lastSuccessAt = 10 * day),
            drive = DestinationStatus(lastSuccessAt = 10 * day),
        )
        assertTrue(BackupHealth.alerts(plan, status, 12 * day).isEmpty(), "exactly 2 days is still fine")
        val alerts = BackupHealth.alerts(plan, status, 12 * day + 1)
        assertEquals(setOf(BackupDestination.LOCAL, BackupDestination.DRIVE), alerts.map { it.destination }.toSet())
        assertTrue(alerts.all { it.kind == AlertKind.OVERDUE })
    }

    @Test
    fun weeklyUsesAWeeklyThreshold() {
        val weekly = plan.copy(frequency = BackupFrequency.WEEKLY, drive = false)
        val status = BackupStatus(local = DestinationStatus(lastSuccessAt = 0))
        assertTrue(BackupHealth.alerts(weekly, status, 14 * day).isEmpty())
        assertEquals(1, BackupHealth.alerts(weekly, status, 14 * day + 1).size)
    }

    @Test
    fun neverSucceededCountsFromWhenTheScheduleWasSet() {
        val p = plan.copy(drive = false, scheduledSince = 100 * day)
        assertTrue(BackupHealth.alerts(p, BackupStatus(), 102 * day).isEmpty())
        assertEquals(AlertKind.OVERDUE, BackupHealth.alerts(p, BackupStatus(), 102 * day + 1).single().kind)
    }

    @Test
    fun aFailureThatNeedsThePersonIsReportedImmediately() {
        val status = BackupStatus(
            drive = DestinationStatus(lastSuccessAt = 5 * day, lastFailure = BackupFailure.CONSENT_NEEDED),
            local = DestinationStatus(lastSuccessAt = 5 * day),
        )
        val alert = BackupHealth.alerts(plan, status, 5 * day + 1000).single()
        assertEquals(BackupDestination.DRIVE, alert.destination)
        assertEquals(AlertKind.NEEDS_ACTION, alert.kind)
        assertEquals(BackupFailure.CONSENT_NEEDED, alert.failure)
    }

    @Test
    fun aTransientFailureIsNotAnAlertUntilItIsOverdue() {
        val status = BackupStatus(
            local = DestinationStatus(lastSuccessAt = 5 * day),
            drive = DestinationStatus(lastSuccessAt = 5 * day, lastFailure = BackupFailure.NO_NETWORK),
        )
        assertTrue(BackupHealth.alerts(plan, status, 5 * day + 1000).isEmpty())
        val later = BackupHealth.alerts(plan, status, 8 * day)
        assertEquals(AlertKind.OVERDUE, later.first { it.destination == BackupDestination.DRIVE }.kind)
    }

    @Test
    fun catchUpIsDueOnceAFullIntervalPassed() {
        val status = BackupStatus(local = DestinationStatus(lastSuccessAt = 10 * day))
        assertFalse(BackupHealth.isDue(plan, BackupDestination.LOCAL, status, 11 * day - 1))
        assertTrue(BackupHealth.isDue(plan, BackupDestination.LOCAL, status, 11 * day))
        assertFalse(BackupHealth.isDue(plan.copy(frequency = BackupFrequency.OFF), BackupDestination.LOCAL, status, 99 * day))
        assertFalse(BackupHealth.isDue(plan.copy(local = false), BackupDestination.LOCAL, status, 99 * day))
    }
}
