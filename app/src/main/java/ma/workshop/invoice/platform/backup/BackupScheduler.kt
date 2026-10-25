package ma.workshop.invoice.platform.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ma.workshop.invoice.InvoiceApp
import ma.workshop.invoice.domain.backup.BackupDestination
import ma.workshop.invoice.domain.backup.BackupFrequency
import ma.workshop.invoice.domain.backup.BackupHealth
import ma.workshop.invoice.domain.backup.BackupPlan
import ma.workshop.invoice.domain.backup.BackupStatus
import java.util.concurrent.TimeUnit

/**
 * Background backups with WorkManager: one unique periodic job per destination, so the phone-only job runs
 * with no network at all and the Drive job waits for a connection. WorkManager keeps the jobs across
 * reboots and app updates; [apply] is safe to call on every app start.
 */
class BackupScheduler(private val context: Context) {

    private val work get() = WorkManager.getInstance(context)

    fun apply(plan: BackupPlan) {
        for (destination in BackupDestination.entries) {
            val name = periodicName(destination)
            if (!plan.isEnabled(destination)) {
                work.cancelUniqueWork(name)
                continue
            }
            val interval = plan.frequency.intervalMillis
            val flex = if (plan.frequency == BackupFrequency.DAILY) TimeUnit.HOURS.toMillis(6) else TimeUnit.DAYS.toMillis(1)
            val request = PeriodicWorkRequestBuilder<BackupWorker>(interval, TimeUnit.MILLISECONDS, flex, TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putString(BackupWorker.KEY_DESTINATION, destination.name).build())
                .setConstraints(constraints(destination))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            work.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }

    /**
     * Runs a backup soon for every destination that is overdue by a full interval (the phone was off, or Android
     * postponed the periodic job). Periodic work waits most of a period before its first run; the backup screen
     * takes the first backup itself the moment a schedule is switched on.
     */
    fun catchUp(plan: BackupPlan, status: BackupStatus, now: Long) {
        for (destination in BackupDestination.entries) {
            if (!BackupHealth.isDue(plan, destination, status, now)) continue
            val request = OneTimeWorkRequestBuilder<BackupWorker>()
                .setInputData(Data.Builder().putString(BackupWorker.KEY_DESTINATION, destination.name).build())
                .setConstraints(constraints(destination))
                .build()
            work.enqueueUniqueWork("auto-backup-${destination.name.lowercase()}-catchup", ExistingWorkPolicy.KEEP, request)
        }
    }

    private fun constraints(destination: BackupDestination) = Constraints.Builder()
        .setRequiredNetworkType(if (destination == BackupDestination.DRIVE) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
        .build()

    companion object {
        fun periodicName(destination: BackupDestination) = "auto-backup-${destination.name.lowercase()}"
    }
}

/** Takes one scheduled backup. Retries a few times for temporary problems, then gives up until the next period. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val destination = inputData.getString(KEY_DESTINATION)?.let { name -> BackupDestination.entries.firstOrNull { it.name == name } }
            ?: return Result.failure()
        val container = (applicationContext as InvoiceApp).container
        // The owner may have switched it off while this job was waiting.
        if (!container.backupPrefs.plan.value.isEnabled(destination)) return Result.success()

        val outcome = when (destination) {
            BackupDestination.LOCAL -> container.backupService.backUpLocal()
            BackupDestination.DRIVE -> container.backupService.backUpDriveSilently()
        }
        return when (outcome) {
            is BackupOutcome.Done -> Result.success()
            is BackupOutcome.Failed ->
                if (outcome.failure.retryable && runAttemptCount < MAX_ATTEMPTS - 1) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_DESTINATION = "destination"
        const val MAX_ATTEMPTS = 3
    }
}
