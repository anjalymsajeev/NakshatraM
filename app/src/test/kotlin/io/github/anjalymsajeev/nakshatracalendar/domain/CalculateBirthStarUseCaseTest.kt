package io.github.anjalymsajeev.nakshatracalendar.domain

import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.BirthDetails
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalculationLocation
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.BirthStarResult
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.CalculateBirthStarUseCase
import io.github.anjalymsajeev.nakshatracalendar.integration.nakshatra.PanchangamNakshatraEngine
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses the real library engine, so the stars are real astronomy, not fakes. */
class CalculateBirthStarUseCaseTest {
    private val engine = PanchangamNakshatraEngine()
    private val useCase = CalculateBirthStarUseCase(engine)
    private val ist = ZoneId.of("Asia/Kolkata")

    private fun calc(birth: BirthDetails) = (useCase(birth, ist) as AppResult.Success<BirthStarResult>).value

    @Test
    fun clearTimeGivesOneStar() {
        // The middle of a nakshatra period, far from both boundaries.
        val period = (engine.nakshatraAt(LocalDate.of(2026, 8, 26).atTime(12, 0).atZone(ist).toInstant()) as AppResult.Success).value
        val middle = period.start.plus(Duration.between(period.start, period.end).dividedBy(2)).atZone(ist)
        val result = calc(BirthDetails(middle.toLocalDate(), middle.toLocalTime()))
        assertEquals(period.number, result.star.number)
        assertEquals(listOf(result.star), result.candidates)
        assertFalse(result.uncertain)
    }

    @Test
    fun birthNearABoundaryOffersBothStars() {
        val period = (engine.nakshatraAt(LocalDate.of(2026, 8, 26).atTime(12, 0).atZone(ist).toInstant()) as AppResult.Success).value
        val justAfterStart = period.start.plus(Duration.ofMinutes(20)).atZone(ist)
        val result = calc(BirthDetails(justAfterStart.toLocalDate(), justAfterStart.toLocalTime()))
        assertEquals(period.number, result.star.number)
        assertEquals(listOf(period.number - 1, period.number), result.candidates.map { it.number })
        assertTrue(result.uncertain)
    }

    @Test
    fun unknownTimeOffersEveryStarOfTheDay() {
        val result = calc(BirthDetails(LocalDate.of(2026, 8, 26)))
        assertTrue(result.candidates.size in 1..3)
        assertTrue(result.star in result.candidates)
    }

    @Test
    fun birthPlaceZoneIsUsed() {
        // The same clock time in New York is 9.5 h later than in India.
        val period = (engine.nakshatraAt(LocalDate.of(2026, 8, 26).atTime(12, 0).atZone(ist).toInstant()) as AppResult.Success).value
        val ny = CalculationLocation("New York", 40.71, -74.0, ZoneId.of("America/New_York"))
        val local = period.start.minus(Duration.ofHours(3)).atZone(ny.zone)
        val result = calc(BirthDetails(local.toLocalDate(), local.toLocalTime(), ny))
        assertEquals(if (period.number == 1) 27 else period.number - 1, result.star.number)
    }
}
