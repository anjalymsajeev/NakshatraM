package io.github.anjalymsajeev.nakshatracalendar.data.repository

import android.database.SQLException
import androidx.room.withTransaction
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppError
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.core.common.IoDispatcher
import io.github.anjalymsajeev.nakshatracalendar.core.database.AppDatabase
import io.github.anjalymsajeev.nakshatracalendar.data.local.dao.SyncDao
import io.github.anjalymsajeev.nakshatracalendar.data.local.entity.CalendarEventEntity
import io.github.anjalymsajeev.nakshatracalendar.data.local.entity.CalendarSyncEntity
import io.github.anjalymsajeev.nakshatracalendar.data.local.entity.EventWithOccurrence
import io.github.anjalymsajeev.nakshatracalendar.data.local.entity.NakshatraOccurrenceEntity
import io.github.anjalymsajeev.nakshatracalendar.domain.model.DateRange
import io.github.anjalymsajeev.nakshatracalendar.domain.model.EventSyncStatus
import io.github.anjalymsajeev.nakshatracalendar.domain.model.GoogleCalendarInfo
import io.github.anjalymsajeev.nakshatracalendar.domain.model.NakshatraName
import io.github.anjalymsajeev.nakshatracalendar.domain.model.NakshatraOccurrenceResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.model.StoredOccurrence
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncMode
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncRun
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncStatus
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncedEvent
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.NakshatraEngine
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SyncRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.CalendarEventFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SyncRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val dao: SyncDao,
    private val engine: NakshatraEngine,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher
) : SyncRepository {
    private val names: Map<Int, NakshatraName> by lazy { engine.allNakshatras().associateBy { it.number } }

    override fun observeRuns(): Flow<List<SyncRun>> = dao.observeRuns().map { runs -> runs.map { it.toDomain() } }

    override fun observeRun(id: Long): Flow<SyncRun?> = dao.observeRun(id).map { it?.toDomain() }

    override fun observeEvents(syncId: Long): Flow<List<SyncedEvent>> = dao.observeEvents(syncId).map { list -> list.map { it.toDomain() } }

    override fun observeSyncedDates(personId: Long, calendarId: String): Flow<Set<LocalDate>> =
        dao.observeSyncedDates(personId, calendarId).map { dates -> dates.map(LocalDate::parse).toSet() }

    override suspend fun createRun(
        person: Person,
        range: DateRange,
        calendar: GoogleCalendarInfo,
        mode: SyncMode,
        occurrences: List<NakshatraOccurrenceResult>
    ): AppResult<Long> = guarded {
        require(occurrences.all { it.observanceDate != null }) { "Only occurrences with an observance day can be synced" }
        db.withTransaction {
            val now = clock.millis()
            val syncId = dao.insertRun(
                CalendarSyncEntity(
                    personId = person.id,
                    personName = person.name,
                    nakshatraNumber = person.nakshatraNumber,
                    rangeStart = range.start.toString(),
                    rangeEnd = range.endInclusive.toString(),
                    calendarId = calendar.id,
                    calendarName = calendar.name,
                    mode = mode.name,
                    status = SyncStatus.QUEUED.name,
                    total = occurrences.size,
                    succeeded = 0,
                    failed = 0,
                    createdAt = now,
                    updatedAt = now
                )
            )
            for (occurrence in occurrences) {
                val date = occurrence.observanceDate!!
                val snapshot = occurrence.toEntity(person)
                val existing = dao.occurrence(person.id, person.nakshatraNumber, date.toString())
                val occurrenceId = if (existing == null) {
                    dao.insertOccurrence(snapshot)
                } else {
                    dao.updateOccurrence(snapshot.copy(id = existing.id))
                    existing.id
                }
                // Reuse the existing mapping (same Google id, last content hash), so a re-sync updates or skips.
                val mapped = dao.event(occurrenceId, calendar.id)
                dao.upsertEvent(
                    (
                        mapped ?: CalendarEventEntity(
                            syncId = syncId,
                            occurrenceId = occurrenceId,
                            calendarId = calendar.id,
                            googleEventId = CalendarEventFactory.eventId(person.id, person.nakshatraNumber, date, calendar.id),
                            status = EventSyncStatus.PENDING.name,
                            contentHash = null,
                            lastError = null,
                            updatedAt = now
                        )
                        ).copy(syncId = syncId, status = EventSyncStatus.PENDING.name, updatedAt = now)
                )
            }
            syncId
        }
    }

    override suspend fun run(id: Long): SyncRun? = withContext(io) { dao.run(id)?.toDomain() }

    override suspend fun events(syncId: Long): List<SyncedEvent> = withContext(io) { dao.events(syncId).map { it.toDomain() } }

    override suspend fun markEvent(eventId: Long, status: EventSyncStatus, contentHash: String?, error: String?) = withContext(io) {
        dao.markEvent(eventId, status.name, contentHash, error, clock.millis())
    }

    override suspend fun finishRun(syncId: Long, status: SyncStatus) = withContext(io) {
        db.withTransaction {
            val run = dao.run(syncId) ?: return@withTransaction
            val events = dao.events(syncId).map { it.event.status }
            dao.updateRun(
                run.copy(
                    status = status.name,
                    // A run whose events all moved on to a later re-sync keeps its recorded total.
                    total = if (events.isEmpty()) run.total else events.size,
                    succeeded = events.count { it == EventSyncStatus.SYNCED.name || it == EventSyncStatus.DELETED.name },
                    failed = events.count { it == EventSyncStatus.FAILED.name },
                    updatedAt = clock.millis()
                )
            )
        }
    }

    override suspend fun resetFailed(syncId: Long) = withContext(io) { dao.resetFailed(syncId, clock.millis()) }

    private suspend fun <T> guarded(block: suspend () -> T): AppResult<T> = withContext(io) {
        try {
            AppResult.Success(block())
        } catch (e: SQLException) {
            AppResult.Failure(AppError.Database(e))
        } catch (e: IllegalStateException) {
            AppResult.Failure(AppError.Database(e))
        }
    }

    private fun NakshatraOccurrenceResult.toEntity(person: Person) = NakshatraOccurrenceEntity(
        personId = person.id,
        personName = person.name,
        nakshatraNumber = nakshatra.number,
        observanceDate = observanceDate.toString(),
        startEpochMs = start.toEpochMilli(),
        endEpochMs = end.toEpochMilli(),
        zoneId = zone.id,
        locationName = person.calculationLocation.name,
        malayalamDateMl = malayalam?.formattedMalayalam.orEmpty(),
        malayalamDateEn = malayalam?.formattedEnglish.orEmpty(),
        malayalamMonthMl = malayalam?.monthNameMalayalam.orEmpty(),
        malayalamMonthEn = malayalam?.monthNameEnglish.orEmpty(),
        kollavarshamYear = malayalam?.kollavarshamYear ?: 0
    )

    private fun CalendarSyncEntity.toDomain() = SyncRun(
        id = id,
        personId = personId,
        personName = personName,
        nakshatra = names.getValue(nakshatraNumber),
        rangeStart = LocalDate.parse(rangeStart),
        rangeEnd = LocalDate.parse(rangeEnd),
        calendarId = calendarId,
        calendarName = calendarName,
        mode = SyncMode.valueOf(mode),
        status = SyncStatus.valueOf(status),
        total = total,
        succeeded = succeeded,
        failed = failed,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt)
    )

    private fun EventWithOccurrence.toDomain() = SyncedEvent(
        id = event.id,
        syncId = event.syncId,
        occurrence = StoredOccurrence(
            id = occurrence.id,
            personId = occurrence.personId,
            personName = occurrence.personName,
            nakshatra = names.getValue(occurrence.nakshatraNumber),
            observanceDate = LocalDate.parse(occurrence.observanceDate),
            start = Instant.ofEpochMilli(occurrence.startEpochMs),
            end = Instant.ofEpochMilli(occurrence.endEpochMs),
            zoneId = occurrence.zoneId,
            locationName = occurrence.locationName,
            malayalamDateMl = occurrence.malayalamDateMl,
            malayalamDateEn = occurrence.malayalamDateEn,
            malayalamMonthMl = occurrence.malayalamMonthMl,
            malayalamMonthEn = occurrence.malayalamMonthEn,
            kollavarshamYear = occurrence.kollavarshamYear
        ),
        calendarId = event.calendarId,
        googleEventId = event.googleEventId,
        status = EventSyncStatus.valueOf(event.status),
        contentHash = event.contentHash,
        lastError = event.lastError
    )
}
