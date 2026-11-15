package io.github.anjalymsajeev.nakshatracalendar

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.anjalymsajeev.nakshatracalendar.core.database.AppDatabase
import io.github.anjalymsajeev.nakshatracalendar.data.local.PersonMapper
import io.github.anjalymsajeev.nakshatracalendar.data.local.entity.CalendarEventEntity
import io.github.anjalymsajeev.nakshatracalendar.data.local.entity.CalendarSyncEntity
import io.github.anjalymsajeev.nakshatracalendar.data.local.entity.NakshatraOccurrenceEntity
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalculationLocation
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    private val people = db.personProfileDao()
    private val dao = db.syncDao()

    @After fun close() = db.close()

    private suspend fun seed(): Triple<Long, Long, Long> {
        val personId = people.upsert(
            PersonMapper.toEntity(Person(0, "Anvidh", 13, calculationLocation = CalculationLocation.Thiruvananthapuram), 1, 1)
        )
        val runId = dao.insertRun(
            CalendarSyncEntity(0, personId, "Anvidh", 13, "2026-01-01", "2026-12-31", "primary", "Me", "MANUAL", "QUEUED", 1, 0, 0, 1, 1)
        )
        val occurrenceId = dao.insertOccurrence(
            NakshatraOccurrenceEntity(
                0, personId, "Anvidh", 13, "2026-09-02", 1, 2, "Asia/Kolkata", "Thiruvananthapuram", "ml", "en", "ml", "en", 1202
            )
        )
        return Triple(personId, runId, occurrenceId)
    }

    @Test
    fun anOccurrenceMapsToOneEventPerCalendar() = runTest {
        val (personId, runId, occurrenceId) = seed()
        dao.upsertEvent(CalendarEventEntity(0, runId, occurrenceId, "primary", "nk1", "SYNCED", "h", null, 1))
        val duplicate = runCatching { dao.insertDuplicateForTest(runId, occurrenceId) }
        assertTrue(duplicate.exceptionOrNull() is SQLiteConstraintException)
        assertEquals(listOf("2026-09-02"), dao.observeSyncedDates(personId, "primary").first())
        assertTrue(dao.observeSyncedDates(personId, "other").first().isEmpty())
    }

    @Test
    fun deletingAMemberKeepsTheirSyncRecordsForLaterRemoval() = runTest {
        val (personId, runId, occurrenceId) = seed()
        dao.upsertEvent(CalendarEventEntity(0, runId, occurrenceId, "primary", "nk1", "SYNCED", "h", null, 1))
        people.delete(personId)
        val events = dao.events(runId)
        assertEquals(1, events.size)
        assertNull(events.single().occurrence.personId)
        assertNull(dao.run(runId)?.personId)
    }

    @Test
    fun reminderConfigurationIsASingleRow() = runTest {
        assertNull(dao.observeReminder().first())
        dao.upsertReminder(
            io.github.anjalymsajeev.nakshatracalendar.data.local.entity.ReminderConfigurationEntity(kind = "BEFORE", minutes = 30)
        )
        dao.upsertReminder(
            io.github.anjalymsajeev.nakshatracalendar.data.local.entity.ReminderConfigurationEntity(kind = "NONE", minutes = null)
        )
        assertEquals("NONE", dao.observeReminder().first()?.kind)
    }

    /** A second mapping for the same occurrence and calendar must be rejected by the unique index. */
    private suspend fun io.github.anjalymsajeev.nakshatracalendar.data.local.dao.SyncDao.insertDuplicateForTest(
        runId: Long,
        occurrenceId: Long
    ) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO calendar_event (sync_id, occurrence_id, calendar_id, google_event_id, status, updated_at) VALUES (?, ?, 'primary', 'nk2', 'PENDING', 1)",
            arrayOf(runId, occurrenceId)
        )
    }
}
