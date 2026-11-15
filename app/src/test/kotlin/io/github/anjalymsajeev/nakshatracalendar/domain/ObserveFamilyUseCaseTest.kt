package io.github.anjalymsajeev.nakshatracalendar.domain

import io.github.anjalymsajeev.kollavarsham.core.KollavarshamCalendar
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalculationLocation
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.model.UserSettings
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PersonRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SettingsRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.GetUpcomingPirannalsUseCase
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.ObserveFamilyUseCase
import io.github.anjalymsajeev.nakshatracalendar.integration.malayalamcalendar.KollavarshamDateProvider
import io.github.anjalymsajeev.nakshatracalendar.integration.nakshatra.PanchangamNakshatraEngine
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Real library engine and date provider: next Pirannal dates are real, checked against Kerala dates. */
class ObserveFamilyUseCaseTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-01T06:00:00Z"), ZoneOffset.UTC)
    private val tvm = CalculationLocation.Thiruvananthapuram
    private val persons = mockk<PersonRepository>()
    private val settings = mockk<SettingsRepository> { every { settings } returns flowOf(UserSettings()) }
    private val useCase = ObserveFamilyUseCase(
        persons,
        settings,
        PanchangamNakshatraEngine(),
        KollavarshamDateProvider(KollavarshamCalendar.create()),
        clock
    )

    @Test
    fun nextThiruvonamAfter1August2026IsOnam() = runTest {
        // Onam (Thiruvonam at sunrise) 2026 was on 26 August in Kerala.
        every { persons.observeAll() } returns flowOf(listOf(Person(1, "Anvidh", 22, calculationLocation = tvm)))
        val member = useCase().first().single()
        assertEquals("Thiruvonam", member.nakshatra.nameEnglish)
        assertEquals(LocalDate.of(2026, 8, 26), member.next?.observanceDate)
        assertEquals(1202, member.next?.malayalamDate?.kollavarshamYear)
        assertEquals("Chingam", member.next?.malayalamDate?.monthNameEnglish)
    }

    @Test
    fun membersAreSortedByNameAndUpcomingBySoonest() = runTest {
        every { persons.observeAll() } returns flowOf(
            listOf(
                Person(1, "zara", 22, calculationLocation = tvm),
                Person(2, "Anu", 13, calculationLocation = tvm)
            )
        )
        assertEquals(listOf("Anu", "zara"), useCase().first().map { it.person.name })
        val upcoming = GetUpcomingPirannalsUseCase(useCase)().first()
        assertEquals(upcoming.sortedBy { it.observanceDate }, upcoming)
        assertEquals(2, upcoming.size)
    }
}
