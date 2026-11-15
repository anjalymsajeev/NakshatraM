package io.github.anjalymsajeev.nakshatracalendar.domain.repository

import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalendarEventDraft
import io.github.anjalymsajeev.nakshatracalendar.domain.model.DateRange
import io.github.anjalymsajeev.nakshatracalendar.domain.model.EventSyncStatus
import io.github.anjalymsajeev.nakshatracalendar.domain.model.GoogleCalendarInfo
import io.github.anjalymsajeev.nakshatracalendar.domain.model.NakshatraOccurrenceResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.model.ReminderSetting
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncMode
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncRun
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncStatus
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncedEvent
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** The Google Calendar REST API. Implemented over OkHttp; tests use MockWebServer or a fake. */
interface CalendarGateway {
    /** Calendars the user can add events to. */
    suspend fun writableCalendars(accessToken: String): AppResult<List<GoogleCalendarInfo>>

    /** Creates the event with its deterministic id, or updates it if that id already exists (also restoring a deleted one). */
    suspend fun upsertEvent(accessToken: String, draft: CalendarEventDraft): AppResult<Unit>

    /** Deletes an app-created event. An event that is already gone counts as deleted. */
    suspend fun deleteEvent(accessToken: String, calendarId: String, eventId: String): AppResult<Unit>

    /** Revokes the app's access for this token (disconnect). */
    suspend fun revoke(accessToken: String): AppResult<Unit>
}

/** Opaque request for user consent, launched by the UI (an Android PendingIntent inside the integration layer). */
interface ConsentRequest

sealed interface AuthorizationState {
    data class Authorized(val accessToken: String) : AuthorizationState

    data class NeedsConsent(val request: ConsentRequest) : AuthorizationState
}

/**
 * Google authorization (OAuth 2.0 via Google Identity Services). Access tokens are short-lived, requested when
 * needed and never stored by the app.
 */
interface GoogleAuthRepository {
    /** Silent when access was granted before; otherwise returns a consent request for the UI. */
    suspend fun authorize(): AppResult<AuthorizationState>

    /** Background use (sync worker): a token if access is granted, otherwise [io.github.anjalymsajeev.nakshatracalendar.core.common.AppError.AuthRequired]. */
    suspend fun accessToken(): AppResult<String>

    /** Completes consent from the UI's activity result data. */
    fun completeConsent(resultData: Any?): AppResult<String>

    /** Drops a token Google rejected (401), so the next request gets a fresh one. */
    suspend fun invalidate(accessToken: String)
}

/** The calendar chosen for synced events (stored in DataStore; not sensitive). */
interface CalendarPreferences {
    val selectedCalendar: Flow<GoogleCalendarInfo?>

    suspend fun select(calendar: GoogleCalendarInfo)

    suspend fun clear()
}

interface ReminderRepository {
    val reminder: Flow<ReminderSetting>

    suspend fun set(reminder: ReminderSetting)
}

/** Local record of synchronization runs, occurrences and Google event mappings (Room). */
interface SyncRepository {
    fun observeRuns(): Flow<List<SyncRun>>

    fun observeRun(id: Long): Flow<SyncRun?>

    fun observeEvents(syncId: Long): Flow<List<SyncedEvent>>

    /** Observance dates of [personId]'s nakshatra already synced to [calendarId]. */
    fun observeSyncedDates(personId: Long, calendarId: String): Flow<Set<LocalDate>>

    /**
     * Records a run for [occurrences] (all with an observance date). Occurrences already mapped to the calendar
     * reuse their row and Google id, so a re-sync updates instead of duplicating.
     */
    suspend fun createRun(
        person: Person,
        range: DateRange,
        calendar: GoogleCalendarInfo,
        mode: SyncMode,
        occurrences: List<NakshatraOccurrenceResult>
    ): AppResult<Long>

    suspend fun run(id: Long): SyncRun?

    suspend fun events(syncId: Long): List<SyncedEvent>

    suspend fun markEvent(eventId: Long, status: EventSyncStatus, contentHash: String?, error: String?)

    /** Recounts the run's events and stores [status]. */
    suspend fun finishRun(syncId: Long, status: SyncStatus)

    /** Failed events of the run go back to pending, for a retry. */
    suspend fun resetFailed(syncId: Long)
}

/** Starts background synchronization of a run (WorkManager in the app; direct execution in UI tests). */
interface SyncScheduler {
    fun schedule(syncId: Long)

    fun scheduleRemoval(syncId: Long)
}
