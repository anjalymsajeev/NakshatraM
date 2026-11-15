package io.github.anjalymsajeev.nakshatracalendar.domain.usecase

import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.core.common.getOrNull
import io.github.anjalymsajeev.nakshatracalendar.domain.model.FamilyMember
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.model.StarDayConvention
import io.github.anjalymsajeev.nakshatracalendar.domain.model.UpcomingPirannal
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.MalayalamDateProvider
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.NakshatraEngine
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PersonRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SettingsRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** All family members, sorted by name, each with their next Pirannal under the Settings convention. */
class ObserveFamilyUseCase @Inject constructor(
    private val persons: PersonRepository,
    private val settings: SettingsRepository,
    private val nakshatras: NakshatraEngine,
    private val dates: MalayalamDateProvider,
    private val clock: Clock
) {
    operator fun invoke(): Flow<List<FamilyMember>> = combine(persons.observeAll(), settings.settings) { list, userSettings ->
        val names = nakshatras.allNakshatras().associateBy { it.number }
        list.sortedBy { it.name.lowercase() }.map { person ->
            FamilyMember(
                person = person,
                nakshatra = names.getValue(person.nakshatraNumber),
                next = nextPirannal(person, userSettings.starDayConvention)
            )
        }
    }

    /** The next birth-star day of [person] on or after today (in the person's calculation zone). */
    fun nextPirannal(person: Person, convention: StarDayConvention): UpcomingPirannal? {
        val today = LocalDate.now(clock.withZone(person.calculationLocation.zone))
        val occurrence = nakshatras.nextObservance(person.nakshatraNumber, today, person.calculationLocation, convention)
            .getOrNull() ?: return null
        val day = occurrence.observanceDate ?: return null
        val malayalam = (dates.malayalamDate(day) as? AppResult.Success)?.value ?: return null
        val name = nakshatras.allNakshatras().first { it.number == person.nakshatraNumber }
        return UpcomingPirannal(person, name, day, malayalam, occurrence.period)
    }
}

/** The family's next birth-star days, soonest first, for the home dashboard. */
class GetUpcomingPirannalsUseCase @Inject constructor(private val family: ObserveFamilyUseCase) {
    operator fun invoke(limit: Int = 5): Flow<List<UpcomingPirannal>> = family().map { members ->
        members.mapNotNull { it.next }.sortedWith(compareBy({ it.observanceDate }, { it.person.name })).take(limit)
    }
}
