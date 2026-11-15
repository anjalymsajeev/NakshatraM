package io.github.anjalymsajeev.nakshatracalendar.domain.usecase

import io.github.anjalymsajeev.nakshatracalendar.core.common.AppError
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.DateRange
import io.github.anjalymsajeev.nakshatracalendar.domain.model.GoogleCalendarInfo
import io.github.anjalymsajeev.nakshatracalendar.domain.model.NakshatraOccurrenceResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncMode
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SyncRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SyncScheduler
import javax.inject.Inject

/**
 * Records a run for the occurrences the user confirmed and starts it in the background. Called only after the
 * user's explicit confirmation; nothing is ever created without it.
 */
class StartSyncUseCase @Inject constructor(private val sync: SyncRepository, private val scheduler: SyncScheduler) {
    suspend operator fun invoke(
        person: Person,
        range: DateRange,
        calendar: GoogleCalendarInfo,
        mode: SyncMode,
        occurrences: List<NakshatraOccurrenceResult>
    ): AppResult<Long> {
        val eligible = occurrences.filter { it.observanceDate != null }.distinctBy { it.observanceDate }
        if (eligible.isEmpty()) return AppResult.Failure(AppError.CalculationFailed(IllegalArgumentException("No dates selected")))
        return when (val created = sync.createRun(person, range, calendar, mode, eligible)) {
            is AppResult.Failure -> created
            is AppResult.Success -> created.also { scheduler.schedule(it.value) }
        }
    }
}

/** Retries a run: its failed events go back to pending and the worker runs again. */
class RetrySyncUseCase @Inject constructor(private val sync: SyncRepository, private val scheduler: SyncScheduler) {
    suspend operator fun invoke(syncId: Long) {
        sync.resetFailed(syncId)
        scheduler.schedule(syncId)
    }
}

/** Removes a run's app-created events from Google Calendar (only events this app created are touched). */
class RemoveSyncedEventsUseCase @Inject constructor(private val scheduler: SyncScheduler) {
    operator fun invoke(syncId: Long) = scheduler.scheduleRemoval(syncId)
}
