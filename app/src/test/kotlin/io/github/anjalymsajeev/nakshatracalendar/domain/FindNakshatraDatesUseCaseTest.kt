package io.github.anjalymsajeev.nakshatracalendar.domain

import io.github.anjalymsajeev.kollavarsham.core.KollavarshamCalendar
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalculationLocation
import io.github.anjalymsajeev.nakshatracalendar.domain.model.DateRange
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.model.StarDayConvention
import io.github.anjalymsajeev.nakshatracalendar.domain.model.UserSettings
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SettingsRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.FindNakshatraDatesUseCase
import io.github.anjalymsajeev.nakshatracalendar.integration.malayalamcalendar.KollavarshamDateProvider
import io.github.anjalymsajeev.nakshatracalendar.integration.nakshatra.PanchangamNakshatraEngine
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real library engine and calendar: results are real astronomy, not fixtures. */
class FindNakshatraDatesUseCaseTest {
    private val tvm = CalculationLocation.Thiruvananthapuram
    private val dubai = CalculationLocation("Dubai", 25.2048, 55.2708, ZoneId.of("Asia/Dubai"))
    private val year2026 = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))

    private fun useCase(convention: StarDayConvention = StarDayConvention.SUNRISE) = FindNakshatraDatesUseCase(
        PanchangamNakshatraEngine(),
        KollavarshamDateProvider(KollavarshamCalendar.create()),
        mockk<SettingsRepository> { every { settings } returns flowOf(UserSettings(starDayConvention = convention)) },
        Dispatchers.Unconfined
    )

    private suspend fun find(person: Person, range: DateRange = year2026) = (useCase()(person, range) as AppResult.Success).value

    @Test
    fun thiruvonamIn2026IncludesOnamWithMalayalamDate() = runTest {
        val result = find(Person(1, "Anvidh", 22, calculationLocation = tvm))
        val onam = result.occurrences.single { it.observanceDate == LocalDate.of(2026, 8, 26) }
        assertEquals("Thiruvonam", result.nakshatra.nameEnglish)
        assertEquals("Chingam", onam.malayalam?.monthNameEnglish)
        assertEquals(1202, onam.malayalam?.kollavarshamYear)
        assertTrue(onam.start < onam.end)
    }

    @Test
    fun aYearHoldsThirteenOrFourteenOccurrencesWithUnevenGaps() = runTest {
        val occurrences = find(Person(1, "Anu", 13, calculationLocation = tvm)).occurrences
        assertTrue(occurrences.size in 13..14)
        assertTrue(occurrences.all { it.observanceDate!! in year2026.start..year2026.endInclusive })
        // The Moon's speed varies, so the gap between occurrences is not a fixed 27 days.
        val gaps = occurrences.zipWithNext { a, b -> Duration.between(a.start, b.start).toMinutes() }
        assertTrue(gaps.distinct().size > 1)
        assertTrue(gaps.all { it in Duration.ofDays(26).toMinutes()..Duration.ofDays(29).toMinutes() })
    }

    @Test
    fun timesFollowThePersonsZoneAndMidnightSpansAreFlagged() = runTest {
        val occurrences = find(Person(1, "Dev", 5, calculationLocation = dubai)).occurrences
        assertTrue(occurrences.all { it.zone == dubai.zone })
        // A star lasts about a day, so most occurrences cross local midnight.
        assertTrue(occurrences.any { it.spansTwoDays })
        occurrences.filter { it.spansTwoDays }.forEach { assertEquals(it.startDate.plusDays(1), it.endDate) }
    }

    @Test
    fun resultsKeepThePersonsOrderAndAShortRangeCanBeEmpty() = runTest {
        val persons = listOf(Person(1, "A", 1, calculationLocation = tvm), Person(2, "B", 27, calculationLocation = tvm))
        val all = (useCase()(persons, year2026) as AppResult.Success).value
        assertEquals(listOf(1L, 2L), all.map { it.person.id })
        // Atham (13) lasts about a day and recurs about every 27 days, so a single day usually has none of another star.
        val oneDay = DateRange(LocalDate.of(2026, 8, 26), LocalDate.of(2026, 8, 26))
        assertTrue(find(Person(3, "C", 13, calculationLocation = tvm), oneDay).occurrences.isEmpty())
    }
}
