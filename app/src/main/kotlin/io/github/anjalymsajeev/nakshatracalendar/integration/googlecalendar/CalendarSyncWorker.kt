package io.github.anjalymsajeev.nakshatracalendar.integration.googlecalendar

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SyncScheduler
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.CalendarSyncEngine
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.SyncOutcome
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Runs one sync (or removal) run in the background. WorkManager waits for a network connection and retries
 * temporary failures (offline, quota, 5xx) with exponential backoff; the last attempt records the final status.
 */
@HiltWorker
class CalendarSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: CalendarSyncEngine
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val syncId = inputData.getLong(KEY_SYNC_ID, -1L).takeIf { it > 0 } ?: return Result.failure()
        val finalAttempt = runAttemptCount + 1 >= MAX_ATTEMPTS
        val outcome = if (inputData.getBoolean(KEY_REMOVE, false)) engine.remove(syncId, finalAttempt) else engine.run(syncId, finalAttempt)
        return when (outcome) {
            SyncOutcome.RetryLater -> Result.retry()
            is SyncOutcome.Finished -> Result.success()
        }
    }

    companion object {
        const val KEY_SYNC_ID = "sync_id"
        const val KEY_REMOVE = "remove"
        const val MAX_ATTEMPTS = 5
    }
}

/** Enqueues [CalendarSyncWorker]; one unique job per run, so double taps never run a sync twice at once. */
class WorkManagerSyncScheduler @Inject constructor(@ApplicationContext private val context: Context) : SyncScheduler {
    override fun schedule(syncId: Long) = enqueue(syncId, remove = false)

    override fun scheduleRemoval(syncId: Long) = enqueue(syncId, remove = true)

    private fun enqueue(syncId: Long, remove: Boolean) {
        val request = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
            .setInputData(workDataOf(CalendarSyncWorker.KEY_SYNC_ID to syncId, CalendarSyncWorker.KEY_REMOVE to remove))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("calendar-sync-$syncId", ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val BACKOFF_SECONDS = 30L
    }
}
