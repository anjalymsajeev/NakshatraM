package io.github.anjalymsajeev.nakshatracalendar.domain

import io.github.anjalymsajeev.kollavarsham.core.KollavarshamCalendar
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalculationLocation
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncState
import io.github.anjalymsajeev.nakshatracalendar.domain.model.UserSettings
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PersonRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SettingsRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.ObserveCalendarDaysUseCase
import io.github.anjalymsajeev.nakshatracalendar.integration.malayalamcalendar.KollavarshamDateProvider
import io.github.anjalymsajeev.nakshatracalendar.integration.nakshatra.PanchangamNakshatraEngine
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveCalendarDaysUseCaseTest {
    private val tvm = CalculationLocation.Thiruvananthapuram
    private val persons = mockk<PersonRepository>()
    private val useCase = ObserveCalendarDaysUseCase(
        persons,
        mockk<SettingsRepository> { every { settings } returns flowOf(UserSettings()) },
        PanchangamNakshatraEngine(),
        KollavarshamDateProvider(KollavarshamCalendar.create())
    )

    @Test
    fun august2026HasMalayalamDatesSunriseStarsAndFamilyMarks() = runTest {
        every { persons.observeAll() } returns flowOf(listOf(Person(7, "Anvidh", 22, calculationLocation = tvm)))
        val days = (useCase(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)).first() as AppResult.Success).value

        assertEquals(31, days.size)
        // Chingam 1, ME 1202 began on 17 August 2026.
        val chingam1 = days.single { it.date == LocalDate.of(2026, 8, 17) }
        assertEquals(1, chingam1.malayalam.day)
        assertEquals("Chingam", chingam1.malayalam.monthNameEnglish)
        assertTrue(days.all { it.nakshatraAtSunrise != null && it.syncState == SyncState.NONE })

        val onam = days.single { it.date == LocalDate.of(2026, 8, 26) }
        assertEquals("Thiruvonam", onam.nakshatraAtSunrise?.nameEnglish)
        assertEquals(listOf("Anvidh"), onam.family.map { it.name })
        assertEquals(listOf(onam.date), days.filter { it.family.isNotEmpty() }.map { it.date })
    }
}
