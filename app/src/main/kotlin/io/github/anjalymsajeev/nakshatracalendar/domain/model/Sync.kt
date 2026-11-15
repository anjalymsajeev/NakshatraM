package io.github.anjalymsajeev.nakshatracalendar.domain.model

import java.time.Instant
import java.time.LocalDate

/** A Google calendar the user can write to. */
data class GoogleCalendarInfo(val id: String, val name: String, val primary: Boolean = false)

/** Reminder applied to every synced event (Feature 7). All-day events count back from midnight of the day. */
sealed interface ReminderSetting {
    data object None : ReminderSetting

    data class Before(val minutes: Int) : ReminderSetting {
        init {
            require(minutes in 0..MAX_MINUTES) { "Google Calendar accepts 0..$MAX_MINUTES minutes" }
        }
    }

    companion object {
        /** Google Calendar's limit: four weeks. */
        const val MAX_MINUTES = 40_320
        val PRESETS: List<ReminderSetting> = listOf(None, Before(10), Before(30), Before(60), Before(1_440))
        val DEFAULT: ReminderSetting = Before(1_440)
    }
}

enum class SyncMode { AUTOMATIC, MANUAL }

/** State of a synchronization run, as shown in results and history. */
enum class SyncStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,

    /** Some events failed permanently (others succeeded). */
    PARTIAL,
    FAILED,

    /** Google authorization expired or was revoked; the user must reconnect. */
    NEEDS_AUTH,

    /** The run's events were removed from Google Calendar by the user. */
    REMOVED
}

enum class EventSyncStatus { PENDING, SYNCED, FAILED, DELETED }

/** One synchronization run (Feature 8). */
data class SyncRun(
    val id: Long,
    val personId: Long?,
    val personName: String,
    val nakshatra: NakshatraName,
    val rangeStart: LocalDate,
    val rangeEnd: LocalDate,
    val calendarId: String,
    val calendarName: String,
    val mode: SyncMode,
    val status: SyncStatus,
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    val pending: Int get() = total - succeeded - failed
}

/** A run's event as stored locally: the occurrence it came from and its Google state. */
data class SyncedEvent(
    val id: Long,
    val syncId: Long,
    val occurrence: StoredOccurrence,
    val calendarId: String,
    val googleEventId: String,
    val status: EventSyncStatus,
    val contentHash: String?,
    val lastError: String?
)

/** A nakshatra occurrence saved for synchronization (a snapshot, so retries need no recalculation). */
data class StoredOccurrence(
    val id: Long,
    val personId: Long?,
    val personName: String,
    val nakshatra: NakshatraName,
    val observanceDate: LocalDate,
    val start: Instant,
    val end: Instant,
    val zoneId: String,
    val locationName: String,
    val malayalamDateMl: String,
    val malayalamDateEn: String,
    val malayalamMonthMl: String,
    val malayalamMonthEn: String,
    val kollavarshamYear: Int
)

/** Everything sent to Google Calendar for one all-day event. */
data class CalendarEventDraft(
    /** Deterministic id (base32hex), so repeated inserts can never create duplicates. */
    val eventId: String,
    val calendarId: String,
    val title: String,
    val description: String,
    val location: String,
    val date: LocalDate,
    val colorId: Int,
    val reminderMinutes: Int?,
    val privateProperties: Map<String, String>
)
