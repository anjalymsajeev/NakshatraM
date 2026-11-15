package io.github.anjalymsajeev.nakshatracalendar.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A nakshatra occurrence chosen for synchronization: a snapshot of the calculated values, so retries and updates
 * need no recalculation. The person link is cleared (not cascaded) when a member is deleted, so their Google
 * events can still be removed later.
 */
@Entity(
    tableName = "nakshatra_occurrence",
    foreignKeys = [ForeignKey(PersonProfileEntity::class, ["id"], ["person_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["person_id", "nakshatra_number", "observance_date"], unique = true)]
)
data class NakshatraOccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "person_id") val personId: Long?,
    @ColumnInfo(name = "person_name") val personName: String,
    @ColumnInfo(name = "nakshatra_number") val nakshatraNumber: Int,
    /** ISO-8601 date. */
    @ColumnInfo(name = "observance_date") val observanceDate: String,
    @ColumnInfo(name = "start_epoch_ms") val startEpochMs: Long,
    @ColumnInfo(name = "end_epoch_ms") val endEpochMs: Long,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "location_name") val locationName: String,
    @ColumnInfo(name = "malayalam_date_ml") val malayalamDateMl: String,
    @ColumnInfo(name = "malayalam_date_en") val malayalamDateEn: String,
    @ColumnInfo(name = "malayalam_month_ml") val malayalamMonthMl: String,
    @ColumnInfo(name = "malayalam_month_en") val malayalamMonthEn: String,
    @ColumnInfo(name = "kollavarsham_year") val kollavarshamYear: Int
)

/** One synchronization run, with its counts (Feature 8). */
@Entity(
    tableName = "calendar_sync",
    foreignKeys = [ForeignKey(PersonProfileEntity::class, ["id"], ["person_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("person_id"), Index("created_at")]
)
data class CalendarSyncEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "person_id") val personId: Long?,
    @ColumnInfo(name = "person_name") val personName: String,
    @ColumnInfo(name = "nakshatra_number") val nakshatraNumber: Int,
    @ColumnInfo(name = "range_start") val rangeStart: String,
    @ColumnInfo(name = "range_end") val rangeEnd: String,
    @ColumnInfo(name = "calendar_id") val calendarId: String,
    @ColumnInfo(name = "calendar_name") val calendarName: String,
    /** SyncMode name. */
    val mode: String,
    /** SyncStatus name. */
    val status: String,
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/**
 * The mapping from an occurrence to its Google Calendar event. Unique per (occurrence, calendar), so an
 * occurrence can never be created twice in the same calendar.
 */
@Entity(
    tableName = "calendar_event",
    foreignKeys = [
        ForeignKey(CalendarSyncEntity::class, ["id"], ["sync_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(NakshatraOccurrenceEntity::class, ["id"], ["occurrence_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["occurrence_id", "calendar_id"], unique = true),
        Index(value = ["calendar_id", "google_event_id"], unique = true),
        Index("sync_id")
    ]
)
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "sync_id") val syncId: Long,
    @ColumnInfo(name = "occurrence_id") val occurrenceId: Long,
    @ColumnInfo(name = "calendar_id") val calendarId: String,
    @ColumnInfo(name = "google_event_id") val googleEventId: String,
    /** EventSyncStatus name. */
    val status: String,
    @ColumnInfo(name = "content_hash") val contentHash: String?,
    @ColumnInfo(name = "last_error") val lastError: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/** The reminder applied to synced events. A single row (id = 1); kind NONE or BEFORE. */
@Entity(tableName = "reminder_configuration")
data class ReminderConfigurationEntity(@PrimaryKey val id: Int = SINGLE_ROW, val kind: String, val minutes: Int?) {
    companion object {
        const val SINGLE_ROW = 1
    }
}

/** A calendar event row joined with its occurrence. */
data class EventWithOccurrence(
    @androidx.room.Embedded val event: CalendarEventEntity,
    @androidx.room.Relation(parentColumn = "occurrence_id", entityColumn = "id") val occurrence: NakshatraOccurrenceEntity
)
