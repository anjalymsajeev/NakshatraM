package io.github.anjalymsajeev.nakshatracalendar.sync

import io.github.anjalymsajeev.nakshatracalendar.core.common.AppError
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalendarEventDraft
import io.github.anjalymsajeev.nakshatracalendar.domain.model.DateRange
import io.github.anjalymsajeev.nakshatracalendar.domain.model.EventSyncStatus
import io.github.anjalymsajeev.nakshatracalendar.domain.model.GoogleCalendarInfo
import io.github.anjalymsajeev.nakshatracalendar.domain.model.NakshatraOccurrenceResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.model.ReminderSetting
import io.github.anjalymsajeev.nakshatracalendar.domain.model.StoredOccurrence
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncMode
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncRun
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncStatus
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncedEvent
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.AuthorizationState
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.CalendarGateway
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.GoogleAuthRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.ReminderRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SyncRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/** Records calls; [failures] scripts errors per event id (consumed in order). */
class FakeGateway : CalendarGateway {
    val upserts = mutableListOf<CalendarEventDraft>()
    val deletes = mutableListOf<String>()
    val failures = mutableMapOf<String, ArrayDeque<AppError>>()
    val tokensSeen = mutableListOf<String>()

    override suspend fun writableCalendars(accessToken: String) = AppResult.Success(listOf(GoogleCalendarInfo("primary", "Me", true)))

    override suspend fun upsertEvent(accessToken: String, draft: CalendarEventDraft): AppResult<Unit> {
        tokensSeen += accessToken
        failures[draft.eventId]?.removeFirstOrNull()?.let { return AppResult.Failure(it) }
        upserts += draft
        return AppResult.Success(Unit)
    }

    override suspend fun deleteEvent(accessToken: String, calendarId: String, eventId: String): AppResult<Unit> {
        failures[eventId]?.removeFirstOrNull()?.let { return AppResult.Failure(it) }
        deletes += eventId
        return AppResult.Success(Unit)
    }

    override suspend fun revoke(accessToken: String) = AppResult.Success(Unit)
}

class FakeAuth(var token: AppResult<String> = AppResult.Success("token-1")) : GoogleAuthRepository {
    val invalidated = mutableListOf<String>()
    var nextToken: String? = null

    override suspend fun authorize(): AppResult<AuthorizationState> = when (val t = token) {
        is AppResult.Success -> AppResult.Success(AuthorizationState.Authorized(t.value))
        is AppResult.Failure -> t
    }

    override suspend fun accessToken(): AppResult<String> = token

    override fun completeConsent(resultData: Any?): AppResult<String> = token

    override suspend fun invalidate(accessToken: String) {
        invalidated += accessToken
        nextToken?.let { token = AppResult.Success(it) }
    }
}

class FakeReminders(initial: ReminderSetting = ReminderSetting.DEFAULT) : ReminderRepository {
    override val reminder = MutableStateFlow(initial)

    override suspend fun set(reminder: ReminderSetting) {
        this.reminder.value = reminder
    }
}

/** In-memory SyncRepository holding one run's events, enough for the engine's contract. */
class FakeSyncRepository : SyncRepository {
    val runs = mutableMapOf<Long, SyncRun>()
    val events = mutableMapOf<Long, SyncedEvent>()

    fun addRun(id: Long, occurrences: List<StoredOccurrence>, calendarId: String = "primary") {
        runs[id] = SyncRun(
            id, 4, "Anvidh", SyncFixtures.atham, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), calendarId, "Me",
            SyncMode.AUTOMATIC, SyncStatus.QUEUED, occurrences.size, 0, 0, Instant.EPOCH, Instant.EPOCH
        )
        occurrences.forEach { o ->
            events[o.id] = SyncedEvent(o.id, id, o, calendarId, "nk${o.id}", EventSyncStatus.PENDING, null, null)
        }
    }

    /** What createRun does for a re-sync: the same rows (same Google id, same hash) go back to pending. */
    fun resync() = events.replaceAll { _, e -> e.copy(status = EventSyncStatus.PENDING) }

    override fun observeRuns(): Flow<List<SyncRun>> = flowOf(runs.values.toList())

    override fun observeRun(id: Long): Flow<SyncRun?> = flowOf(runs[id])

    override fun observeEvents(syncId: Long): Flow<List<SyncedEvent>> = flowOf(events.values.filter { it.syncId == syncId })

    override fun observeSyncedDates(personId: Long, calendarId: String): Flow<Set<LocalDate>> = flowOf(emptySet())

    override suspend fun createRun(
        person: Person,
        range: DateRange,
        calendar: GoogleCalendarInfo,
        mode: SyncMode,
        occurrences: List<NakshatraOccurrenceResult>
    ): AppResult<Long> = error("not used by the engine")

    override suspend fun run(id: Long) = runs[id]

    override suspend fun events(syncId: Long) = events.values.filter { it.syncId == syncId }.sortedBy { it.id }

    override suspend fun markEvent(eventId: Long, status: EventSyncStatus, contentHash: String?, error: String?) {
        events[eventId] = events.getValue(eventId).copy(status = status, contentHash = contentHash, lastError = error)
    }

    override suspend fun finishRun(syncId: Long, status: SyncStatus) {
        val list = events.values.filter { it.syncId == syncId }
        runs[syncId] = runs.getValue(syncId).copy(
            status = status,
            succeeded = list.count { it.status == EventSyncStatus.SYNCED || it.status == EventSyncStatus.DELETED },
            failed = list.count { it.status == EventSyncStatus.FAILED }
        )
    }

    override suspend fun resetFailed(syncId: Long) = events.replaceAll { _, e ->
        if (e.syncId == syncId &&
            e.status == EventSyncStatus.FAILED
        ) {
            e.copy(status = EventSyncStatus.PENDING, lastError = null)
        } else {
            e
        }
    }
}
