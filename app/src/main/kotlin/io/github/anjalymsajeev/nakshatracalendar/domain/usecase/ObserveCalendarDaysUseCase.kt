package io.github.anjalymsajeev.nakshatracalendar.domain.usecase

import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.core.common.getOrNull
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalendarDay
import io.github.anjalymsajeev.nakshatracalendar.domain.model.FamilyMark
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.model.UserSettings
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.MalayalamDateProvider
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.NakshatraEngine
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PersonRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SettingsRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Calendar cells for [first]..[last]: Malayalam date, nakshatra at sunrise (Settings calculation location) and
 * the family members whose birth star is observed that day. Re-emits when family or settings change.
 */
class ObserveCalendarDaysUseCase @Inject constructor(
    private val persons: PersonRepository,
    private val settings: SettingsRepository,
    private val engine: NakshatraEngine,
    private val dates: MalayalamDateProvider
) {
    operator fun invoke(first: LocalDate, last: LocalDate): Flow<AppResult<List<CalendarDay>>> =
        combine(persons.observeAll(), settings.settings) { family, userSettings -> build(first, last, family, userSettings) }

    private fun build(first: LocalDate, last: LocalDate, family: List<Person>, userSettings: UserSettings): AppResult<List<CalendarDay>> {
        val names = engine.allNakshatras().associateBy { it.number }
        val marks = mutableMapOf<LocalDate, MutableList<FamilyMark>>()
        for (person in family.sortedBy { it.name.lowercase() }) {
            val occurrences = engine.occurrences(
                person.nakshatraNumber,
                first,
                last,
                person.calculationLocation,
                userSettings.starDayConvention
            ).getOrNull().orEmpty()
            val mark = FamilyMark(person.id, person.name, person.eventColor, names.getValue(person.nakshatraNumber))
            occurrences.mapNotNull { it.observanceDate }
                .filter { it >= first && it <= last }
                .forEach { marks.getOrPut(it) { mutableListOf() } += mark }
        }
        val days = mutableListOf<CalendarDay>()
        var date = first
        while (date <= last) {
            val malayalam = when (val converted = dates.malayalamDate(date)) {
                is AppResult.Failure -> return converted
                is AppResult.Success -> converted.value
            }
            days += CalendarDay(
                date = date,
                malayalam = malayalam,
                nakshatraAtSunrise = engine.nakshatraAtSunrise(date, userSettings.calculationLocation).getOrNull(),
                family = marks[date].orEmpty(),
                zone = userSettings.calculationLocation.zone
            )
            date = date.plusDays(1)
        }
        return AppResult.Success(days)
    }
}
