package io.github.anjalymsajeev.nakshatracalendar.domain.usecase

import io.github.anjalymsajeev.nakshatracalendar.core.common.AppError
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.core.common.getOrNull
import io.github.anjalymsajeev.nakshatracalendar.core.common.isRetryable
import io.github.anjalymsajeev.nakshatracalendar.domain.model.EventColor
import io.github.anjalymsajeev.nakshatracalendar.domain.model.EventSyncStatus
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncStatus
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncedEvent
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.CalendarGateway
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.GoogleAuthRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PersonRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.ReminderRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SyncRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** What the caller (the WorkManager worker) should do next. */
sealed interface SyncOutcome {
    data class Finished(val status: SyncStatus) : SyncOutcome

    /** Temporary failures (offline, quota, server): try again later with backoff. */
    data object RetryLater : SyncOutcome
}

/**
 * Pushes a run's pending and failed events to Google Calendar, idempotently:
 * - each event has a deterministic id, so a repeated insert updates instead of duplicating;
 * - an event whose content hash is unchanged since its last successful sync is skipped;
 * - a renamed member, new colour or new reminder changes the hash, so the event is updated.
 */
class CalendarSyncEngine @Inject constructor(
    private val gateway: CalendarGateway,
    private val auth: GoogleAuthRepository,
    private val sync: SyncRepository,
    private val persons: PersonRepository,
    private val reminders: ReminderRepository
) {
    suspend fun run(syncId: Long, finalAttempt: Boolean = false): SyncOutcome {
        sync.run(syncId) ?: return SyncOutcome.Finished(SyncStatus.FAILED)
        sync.finishRun(syncId, SyncStatus.RUNNING)
        val session = when (val token = auth.accessToken()) {
            is AppResult.Success -> TokenSession(token.value)
            is AppResult.Failure -> return stopOn(syncId, token.error, finalAttempt)
        }
        val reminder = reminders.reminder.first()
        var retryable = false
        for (event in sync.events(syncId).filter { it.status == EventSyncStatus.PENDING || it.status == EventSyncStatus.FAILED }) {
            val person = event.occurrence.personId?.let { persons.get(it).getOrNull() }
            val draft = CalendarEventFactory.draft(
                occurrence = event.occurrence,
                eventId = event.googleEventId,
                personName = person?.name ?: event.occurrence.personName,
                colorId = (person?.eventColor ?: EventColor.DEFAULT).colorId,
                calendarId = event.calendarId,
                reminder = reminder
            )
            val hash = CalendarEventFactory.contentHash(draft)
            if (event.contentHash == hash && event.status == EventSyncStatus.PENDING && event.wasSyncedBefore()) {
                sync.markEvent(event.id, EventSyncStatus.SYNCED, hash, null)
                continue
            }
            when (val result = session.call { token -> gateway.upsertEvent(token, draft) }) {
                is AppResult.Success -> sync.markEvent(event.id, EventSyncStatus.SYNCED, hash, null)
                is AppResult.Failure -> {
                    // Keep the last good hash: the event in Google still has that content.
                    sync.markEvent(event.id, EventSyncStatus.FAILED, event.contentHash, result.error.describe())
                    if (result.error == AppError.AuthRequired) return stopOn(syncId, result.error, finalAttempt)
                    if (result.error.isRetryable) retryable = true
                }
            }
        }
        return finish(syncId, retryable, finalAttempt)
    }

    /** Deletes the run's app-created events from Google Calendar ("Remove events" in history). */
    suspend fun remove(syncId: Long, finalAttempt: Boolean = false): SyncOutcome {
        val session = when (val token = auth.accessToken()) {
            is AppResult.Success -> TokenSession(token.value)
            is AppResult.Failure -> return stopOn(syncId, token.error, finalAttempt)
        }
        var retryable = false
        var failed = false
        for (event in sync.events(syncId).filter { it.status != EventSyncStatus.DELETED }) {
            when (val result = session.call { token -> gateway.deleteEvent(token, event.calendarId, event.googleEventId) }) {
                is AppResult.Success -> sync.markEvent(event.id, EventSyncStatus.DELETED, null, null)
                is AppResult.Failure -> {
                    failed = true
                    if (result.error == AppError.AuthRequired) return stopOn(syncId, result.error, finalAttempt)
                    if (result.error.isRetryable) retryable = true
                }
            }
        }
        return when {
            retryable && !finalAttempt -> SyncOutcome.RetryLater
            failed -> SyncOutcome.Finished(SyncStatus.PARTIAL).also { sync.finishRun(syncId, it.status) }
            else -> SyncOutcome.Finished(SyncStatus.REMOVED).also { sync.finishRun(syncId, it.status) }
        }
    }

    private suspend fun finish(syncId: Long, retryable: Boolean, finalAttempt: Boolean): SyncOutcome {
        val events = sync.events(syncId)
        val failed = events.count { it.status == EventSyncStatus.FAILED }
        val succeeded = events.count { it.status == EventSyncStatus.SYNCED }
        if (failed > 0 && retryable && !finalAttempt) {
            sync.finishRun(syncId, SyncStatus.QUEUED)
            return SyncOutcome.RetryLater
        }
        val status = when {
            failed == 0 -> SyncStatus.SUCCEEDED
            succeeded == 0 -> SyncStatus.FAILED
            else -> SyncStatus.PARTIAL
        }
        sync.finishRun(syncId, status)
        return SyncOutcome.Finished(status)
    }

    private suspend fun stopOn(syncId: Long, error: AppError, finalAttempt: Boolean): SyncOutcome = when {
        error == AppError.AuthRequired -> SyncOutcome.Finished(SyncStatus.NEEDS_AUTH).also { sync.finishRun(syncId, it.status) }
        error.isRetryable && !finalAttempt -> SyncOutcome.RetryLater.also { sync.finishRun(syncId, SyncStatus.QUEUED) }
        else -> SyncOutcome.Finished(SyncStatus.FAILED).also { sync.finishRun(syncId, it.status) }
    }

    /** Holds the access token; on the first 401 it drops the token, fetches a fresh one and repeats the call once. */
    private inner class TokenSession(private var token: String) {
        private var refreshed = false

        suspend fun <T> call(request: suspend (String) -> AppResult<T>): AppResult<T> {
            val result = request(token)
            if (result !is AppResult.Failure || result.error != AppError.AuthRequired || refreshed) return result
            refreshed = true
            auth.invalidate(token)
            token = when (val fresh = auth.accessToken()) {
                is AppResult.Failure -> return fresh
                is AppResult.Success -> fresh.value
            }
            return request(token)
        }
    }

    private fun SyncedEvent.wasSyncedBefore() = contentHash != null && lastError == null

    private fun AppError.describe(): String = when (this) {
        is AppError.ApiError -> "HTTP $code: $detail"
        is AppError.ServerError -> "HTTP $code"
        is AppError.Network -> "Network: ${cause.message}"
        else -> toString()
    }
}
