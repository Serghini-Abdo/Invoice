package ma.workshop.invoice

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.workDataOf
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import ma.workshop.invoice.domain.backup.AlertKind
import ma.workshop.invoice.domain.backup.BackupCodec
import ma.workshop.invoice.domain.backup.BackupFailure
import ma.workshop.invoice.domain.backup.BackupFiles
import ma.workshop.invoice.domain.backup.BackupFrequency
import ma.workshop.invoice.domain.backup.BackupHealth
import ma.workshop.invoice.domain.backup.BackupParseResult
import ma.workshop.invoice.domain.backup.BackupPlan
import ma.workshop.invoice.domain.backup.StoredBackup
import ma.workshop.invoice.platform.backup.BackupPrefs
import ma.workshop.invoice.platform.backup.BackupWorker
import ma.workshop.invoice.platform.backup.LocalBackupException
import ma.workshop.invoice.platform.backup.LocalBackupStore
import ma.workshop.invoice.platform.backup.LocalWrite
import ma.workshop.invoice.platform.drive.DriveAuthResult
import ma.workshop.invoice.platform.drive.DriveBackupApi
import ma.workshop.invoice.platform.drive.DriveException
import ma.workshop.invoice.platform.drive.RemoteBackup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset

/** In-memory phone storage so the worker is tested without touching real files. */
private class FakeLocalStore : LocalBackupStore {
    val files = LinkedHashMap<String, Pair<String, ByteArray>>() // id -> (name, bytes)
    var failWith: LocalBackupException? = null

    override fun existingNames() = files.values.map { it.first }.toSet()

    override fun write(name: String, bytes: ByteArray): LocalWrite {
        failWith?.let { throw it }
        files["id-$name"] = name to bytes
        return LocalWrite("id-$name", name, inPrivateFolder = false)
    }

    override fun list(inPrivateFolder: Boolean) = files.map { (id, v) -> StoredBackup(id, v.first, BackupFiles.timestampOf(v.first, ZoneOffset.UTC) ?: 0L) }

    override fun delete(id: String, inPrivateFolder: Boolean) = files.remove(id) != null
}

/** A scripted Google Drive. [calls] records the order of operations. */
private class FakeDrive(private val context: android.content.Context) : DriveBackupApi {
    var auth: DriveAuthResult = DriveAuthResult.Token("token")
    var createFails: DriveException? = null
    val remote = ArrayList<RemoteBackup>()
    val bodies = HashMap<String, String>()
    val calls = ArrayList<String>()

    override suspend fun authorize(useSavedAccount: Boolean) = auth
    override fun tokenFromConsent(data: Intent?): DriveAuthResult.Token? = null
    override suspend fun list(token: String): List<RemoteBackup> = remote.toList()

    override suspend fun create(token: String, name: String, json: String): RemoteBackup {
        createFails?.let { throw it }
        calls += "create:$name"
        val file = RemoteBackup("r-$name", name, System.currentTimeMillis(), json.toByteArray(Charsets.UTF_8).size.toLong())
        remote += file
        bodies[file.id] = json
        return file
    }

    override suspend fun delete(token: String, id: String) {
        calls += "delete:$id"
        remote.removeAll { it.id == id }
    }

    override suspend fun downloadNewest(token: String): String? = remote.lastOrNull()?.let { bodies[it.id] }

    fun consentIntent(): PendingIntent = PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
}

@RunWith(AndroidJUnit4::class)
class BackupWorkerTest {

    private val app = ApplicationProvider.getApplicationContext<InvoiceApp>()
    private val container = app.container
    private val local = FakeLocalStore()
    private val drive = FakeDrive(app)
    private var clock = 1_800_000_000_000L

    private lateinit var savedPrefs: BackupPrefs
    private lateinit var savedService: ma.workshop.invoice.platform.backup.BackupService
    private lateinit var savedLocal: LocalBackupStore
    private lateinit var savedDrive: DriveBackupApi

    @Before
    fun setUp() {
        savedPrefs = container.backupPrefs
        savedService = container.backupService
        savedLocal = container.localStore
        savedDrive = container.drive
        // A private, empty schedule and history for each test.
        val prefs = BackupPrefs(app.getSharedPreferences("backup-test-${System.nanoTime()}", android.content.Context.MODE_PRIVATE))
        container.backupPrefs = prefs
        container.localStore = local
        container.drive = drive
        container.backupService = container.newBackupService(prefs) { clock }
        runBlocking { container.products.ensureSeeded() }
    }

    @After
    fun tearDown() {
        container.backupPrefs = savedPrefs
        container.backupService = savedService
        container.localStore = savedLocal
        container.drive = savedDrive
    }

    private fun plan(local: Boolean = true, drive: Boolean = false, frequency: BackupFrequency = BackupFrequency.DAILY) {
        container.backupPrefs.updatePlan(clock) { BackupPlan(frequency, local, drive) }
    }

    private fun run(destination: String, attempt: Int = 0): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<BackupWorker>(app)
            .setInputData(workDataOf(BackupWorker.KEY_DESTINATION to destination))
            .setRunAttemptCount(attempt)
            .build()
            .doWork()
    }

    @Test
    fun localBackupIsWrittenValidatedAndRecorded() {
        plan()
        assertEquals(ListenableWorker.Result.success(), run("LOCAL"))

        val (name, bytes) = local.files.values.single()
        assertTrue("sortable ASCII name, got $name", Regex("backup-\\d{4}-\\d{2}-\\d{2}-\\d{4}\\.json").matches(name))
        assertTrue(BackupCodec.parse(String(bytes, Charsets.UTF_8)) is BackupParseResult.Valid)
        val status = container.backupPrefs.status.value.local
        assertEquals(clock, status.lastSuccessAt)
        assertNull(status.lastFailure)
    }

    @Test
    fun localRetentionKeepsTheNewestTenAndNeverTouchesForeignFiles() {
        plan()
        for (day in 1..12) {
            val name = BackupFiles.nameFor(1_700_000_000_000L + day * 86_400_000L, ZoneOffset.UTC)
            local.write(name, "{}".toByteArray())
        }
        local.write("my-own-notes.json", "{}".toByteArray())
        assertEquals(ListenableWorker.Result.success(), run("LOCAL"))

        val names = local.files.values.map { it.first }
        assertEquals(10 + 1, names.size)
        assertTrue("my-own-notes.json" in names)
        val newest = names.filter { BackupFiles.isManagedName(it) }.maxOrNull()!!
        assertTrue("the new backup is kept", BackupFiles.timestampOf(newest, ZoneOffset.UTC)!! > 1_700_000_000_000L + 12 * 86_400_000L)
        for (day in 1..3) {
            assertFalse("old copy $day is gone", BackupFiles.nameFor(1_700_000_000_000L + day * 86_400_000L, ZoneOffset.UTC) in names)
        }
    }

    @Test
    fun aWriteFailureRetriesThenGivesUpAndIsRecorded() {
        plan()
        local.failWith = LocalBackupException(BackupFailure.LOCAL_WRITE_FAILED, "disk error")
        assertEquals(ListenableWorker.Result.retry(), run("LOCAL", attempt = 0))
        assertEquals(ListenableWorker.Result.failure(), run("LOCAL", attempt = BackupWorker.MAX_ATTEMPTS - 1))
        val status = container.backupPrefs.status.value.local
        assertEquals(BackupFailure.LOCAL_WRITE_FAILED, status.lastFailure)
        assertEquals("disk error", status.lastDetail)
        assertNull(status.lastSuccessAt)
        assertTrue(local.files.isEmpty())
    }

    @Test
    fun nothingHappensWhenTheOwnerSwitchedBackupOffMeanwhile() {
        plan(frequency = BackupFrequency.OFF)
        assertEquals(ListenableWorker.Result.success(), run("LOCAL"))
        assertTrue(local.files.isEmpty())
    }

    @Test
    fun driveBackupCreatesTheNewCopyBeforeDeletingOldOnesAndKeepsFive() {
        plan(local = false, drive = true)
        for (day in 1..6) {
            val name = BackupFiles.nameFor(1_700_000_000_000L + day * 86_400_000L, ZoneOffset.UTC)
            drive.remote += RemoteBackup("r-$name", name, 1_700_000_000_000L + day * 86_400_000L, 2)
        }
        assertEquals(ListenableWorker.Result.success(), run("DRIVE"))

        assertTrue(drive.calls.first().startsWith("create:"))
        assertTrue(drive.calls.drop(1).all { it.startsWith("delete:") })
        assertEquals(5, drive.remote.size)
        val created = drive.calls.first().removePrefix("create:")
        assertTrue("the new copy survived", drive.remote.any { it.name == created })
        assertEquals(clock, container.backupPrefs.status.value.drive.lastSuccessAt)
    }

    @Test
    fun aFailedDriveUploadDeletesNothing() {
        plan(local = false, drive = true)
        val name = BackupFiles.nameFor(1_700_000_000_000L, ZoneOffset.UTC)
        drive.remote += RemoteBackup("r-$name", name, 1, 2)
        drive.createFails = DriveException(BackupFailure.NO_NETWORK, "UnknownHostException")
        assertEquals(ListenableWorker.Result.retry(), run("DRIVE", attempt = 0))
        assertEquals(1, drive.remote.size)
        assertTrue(drive.calls.none { it.startsWith("delete") })
        assertEquals(BackupFailure.NO_NETWORK, container.backupPrefs.status.value.drive.lastFailure)
    }

    @Test
    fun goneConsentIsRecordedWithoutAnyUiAndBecomesAHomeAlert() {
        plan(local = false, drive = true)
        drive.auth = DriveAuthResult.NeedsConsent(drive.consentIntent())
        assertEquals(ListenableWorker.Result.failure(), run("DRIVE"))

        val status = container.backupPrefs.status.value
        assertEquals(BackupFailure.CONSENT_NEEDED, status.drive.lastFailure)
        val alert = BackupHealth.alerts(container.backupPrefs.plan.value, status, clock).single()
        assertEquals(AlertKind.NEEDS_ACTION, alert.kind)
        assertTrue(drive.calls.isEmpty())
    }

    @Test
    fun aDeveloperErrorIsNotRetriedAndKeepsItsRawCode() {
        plan(local = false, drive = true)
        drive.auth = DriveAuthResult.Failed(BackupFailure.DEVELOPER_ERROR, "api 10")
        assertEquals(ListenableWorker.Result.failure(), run("DRIVE", attempt = 0))
        val status = container.backupPrefs.status.value.drive
        assertEquals(BackupFailure.DEVELOPER_ERROR, status.lastFailure)
        assertEquals("api 10", status.lastDetail)
    }

    @Test
    fun theBackupTheWorkerStoresRestoresTheSameData() {
        plan()
        assertEquals(ListenableWorker.Result.success(), run("LOCAL"))
        val json = String(local.files.values.single().second, Charsets.UTF_8)
        val parsed = BackupCodec.parse(json) as BackupParseResult.Valid
        assertEquals(runBlocking { container.backups.export() }.products.size, parsed.data.products.size)
        assertNotNull(parsed.document.createdAt)
        assertTrue(Build.VERSION.SDK_INT >= 24)
    }
}
