package io.github.anjalymsajeev.nakshatracalendar.domain.usecase

import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.core.common.DefaultDispatcher
import io.github.anjalymsajeev.nakshatracalendar.core.common.getOrNull
import io.github.anjalymsajeev.nakshatracalendar.domain.model.DateRange
import io.github.anjalymsajeev.nakshatracalendar.domain.model.NakshatraOccurrenceResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.model.PersonOccurrences
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.MalayalamDateProvider
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.NakshatraEngine
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Every occurrence of each person's birth nakshatra in [DateRange], using the Settings star-day convention and
 * each person's own calculation location (so days and times follow that person's time zone).
 *
 * An occurrence belongs to the range when its observance day is inside it; an occurrence without an observance
 * day (rare: the star is too short to cover any checkpoint) is kept when its span overlaps the range.
 */
class FindNakshatraDatesUseCase @Inject constructor(
    private val engine: NakshatraEngine,
    private val dates: MalayalamDateProvider,
    private val settings: SettingsRepository,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(persons: List<Person>, range: DateRange): AppResult<List<PersonOccurrences>> = withContext(dispatcher) {
        val convention = settings.settings.first().starDayConvention
        val names = engine.allNakshatras().associateBy { it.number }
        val result = mutableListOf<PersonOccurrences>()
        for (person in persons) {
            ensureActive()
            val found = engine.occurrences(person.nakshatraNumber, range.start, range.endInclusive, person.calculationLocation, convention)
            val occurrences = when (found) {
                is AppResult.Failure -> return@withContext found
                is AppResult.Success -> found.value
            }
            val nakshatra = names.getValue(person.nakshatraNumber)
            val rows = occurrences
                .filter { occurrence -> occurrence.observanceDate?.let { it >= range.start && it <= range.endInclusive } ?: true }
                .map { occurrence ->
                    val zone = person.calculationLocation.zone
                    val day = occurrence.observanceDate ?: occurrence.period.start.atZone(zone).toLocalDate()
                    NakshatraOccurrenceResult(
                        person = person,
                        nakshatra = nakshatra,
                        start = occurrence.period.start,
                        end = occurrence.period.end,
                        zone = zone,
                        observanceDate = occurrence.observanceDate,
                        malayalam = dates.malayalamDate(day).getOrNull()
                    )
                }
            result += PersonOccurrences(person, nakshatra, rows)
        }
        AppResult.Success(result)
    }

    /** Convenience for a single person. */
    suspend operator fun invoke(person: Person, range: DateRange): AppResult<PersonOccurrences> =
        when (val all = invoke(listOf(person), range)) {
            is AppResult.Failure -> all
            is AppResult.Success -> AppResult.Success(all.value.single())
        }
}
