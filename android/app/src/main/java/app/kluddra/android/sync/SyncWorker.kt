package app.kluddra.android.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val syncEngine: SyncEngine
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ok = syncEngine.syncNow()
        return if (ok) Result.success() else Result.retry()
    }
}

class KluddraWorkerFactory(
    private val syncEngine: SyncEngine
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            SyncWorker::class.java.name -> SyncWorker(appContext, workerParameters, syncEngine)
            else -> null
        }
    }
}

class SyncScheduler(
    private val context: Context
) {
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    fun enqueueImmediateSync(delayMs: Long = 0L) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .apply {
                if (delayMs > 0L) {
                    setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                }
            }
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_IMMEDIATE_SYNC,
            ExistingWorkPolicy.REPLACE,
            req
        )
    }

    fun ensurePeriodicSyncScheduled() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val periodic = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(UNIQUE_IMMEDIATE_SYNC)
        workManager.cancelUniqueWork(UNIQUE_PERIODIC_SYNC)
    }

    private companion object {
        const val UNIQUE_IMMEDIATE_SYNC = "kluddra_immediate_sync"
        const val UNIQUE_PERIODIC_SYNC = "kluddra_periodic_sync"
    }
}
