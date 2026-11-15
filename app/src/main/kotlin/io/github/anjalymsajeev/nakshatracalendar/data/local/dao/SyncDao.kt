package io.github.anjalymsajeev.nakshatracalendar.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import io.github.anjalymsajeev.nakshatracalendar.data.local.entity.CalendarEventEntity
import io.github.anjalymsajeev.nakshatracalendar.data.local.entity.CalendarSyncEntity
import io.github.anjalymsajeev.nakshatracalendar.data.local.entity.EventWithOccurrence
import io.github.anjalymsajeev.nakshatracalendar.data.local.entity.NakshatraOccurrenceEntity
import io.github.anjalymsajeev.nakshatracalendar.data.local.entity.ReminderConfigurationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Query("SELECT * FROM calendar_sync ORDER BY updated_at DESC")
    fun observeRuns(): Flow<List<CalendarSyncEntity>>

    @Query("SELECT * FROM calendar_sync WHERE id = :id")
    fun observeRun(id: Long): Flow<CalendarSyncEntity?>

    @Query("SELECT * FROM calendar_sync WHERE id = :id")
    suspend fun run(id: Long): CalendarSyncEntity?

    @Insert
    suspend fun insertRun(run: CalendarSyncEntity): Long

    @Update
    suspend fun updateRun(run: CalendarSyncEntity)

    @Query("SELECT * FROM nakshatra_occurrence WHERE person_id = :personId AND nakshatra_number = :nakshatra AND observance_date = :date")
    suspend fun occurrence(personId: Long, nakshatra: Int, date: String): NakshatraOccurrenceEntity?

    @Insert
    suspend fun insertOccurrence(occurrence: NakshatraOccurrenceEntity): Long

    @Update
    suspend fun updateOccurrence(occurrence: NakshatraOccurrenceEntity)

    @Query("SELECT * FROM calendar_event WHERE occurrence_id = :occurrenceId AND calendar_id = :calendarId")
    suspend fun event(occurrenceId: Long, calendarId: String): CalendarEventEntity?

    @Upsert
    suspend fun upsertEvent(event: CalendarEventEntity): Long

    @Query("UPDATE calendar_event SET status = :status, content_hash = :hash, last_error = :error, updated_at = :now WHERE id = :id")
    suspend fun markEvent(id: Long, status: String, hash: String?, error: String?, now: Long)

    @Query(
        "UPDATE calendar_event SET status = 'PENDING', last_error = NULL, updated_at = :now WHERE sync_id = :syncId AND status = 'FAILED'"
    )
    suspend fun resetFailed(syncId: Long, now: Long)

    @Transaction
    @Query("SELECT * FROM calendar_event WHERE sync_id = :syncId ORDER BY id")
    suspend fun events(syncId: Long): List<EventWithOccurrence>

    @Transaction
    @Query("SELECT * FROM calendar_event WHERE sync_id = :syncId ORDER BY id")
    fun observeEvents(syncId: Long): Flow<List<EventWithOccurrence>>

    @Query(
        """SELECT o.observance_date FROM calendar_event e JOIN nakshatra_occurrence o ON o.id = e.occurrence_id
           WHERE o.person_id = :personId AND e.calendar_id = :calendarId AND e.status = 'SYNCED'"""
    )
    fun observeSyncedDates(personId: Long, calendarId: String): Flow<List<String>>

    @Query("SELECT * FROM reminder_configuration WHERE id = 1")
    fun observeReminder(): Flow<ReminderConfigurationEntity?>

    @Upsert
    suspend fun upsertReminder(reminder: ReminderConfigurationEntity)
}
