package io.github.anjalymsajeev.nakshatracalendar.integration

import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalculationLocation
import io.github.anjalymsajeev.nakshatracalendar.integration.nakshatra.PanchangamNakshatraEngine
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PanchangamNakshatraEngineTest {
    private val engine = PanchangamNakshatraEngine()

    @Test
    fun thiruvonamIsTheSunriseNakshatraOnOnam2026() {
        // Independent reference: Onam (Thiruvonam) 2026 was celebrated in Kerala on 26 August.
        val result = engine.nakshatraAtSunrise(LocalDate.of(2026, 8, 26), CalculationLocation.Thiruvananthapuram)
        val star = (result as AppResult.Success).value
        assertEquals(22, star.number)
        assertEquals("Thiruvonam", star.nameEnglish)
        assertEquals("തിരുവോണം", star.nameMalayalam)
        assertTrue(star.start < star.end)
    }

    @Test
    fun reusesOnePanchangamPerLocation() {
        val a = engine.nakshatraAtSunrise(LocalDate.of(2026, 11, 14), CalculationLocation.Thiruvananthapuram)
        val b = engine.nakshatraAtSunrise(LocalDate.of(2026, 11, 14), CalculationLocation.Thiruvananthapuram)
        assertEquals(a, b)
    }
}

class PanchangamNakshatraEngineOccurrenceTest {
    private val engine = PanchangamNakshatraEngine()

    @Test
    fun listsAll27StarsInBothLanguages() {
        val all = engine.allNakshatras()
        org.junit.Assert.assertEquals((1..27).toList(), all.map { it.number })
        org.junit.Assert.assertEquals("അശ്വതി", all.first().nameMalayalam)
        org.junit.Assert.assertEquals("Revathi", all.last().nameEnglish)
    }

    @Test
    fun nextThiruvonamObservanceIsOnam2026() {
        val result = engine.nextObservance(
            22,
            java.time.LocalDate.of(2026, 8, 1),
            io.github.anjalymsajeev.nakshatracalendar.domain.model.CalculationLocation.Thiruvananthapuram,
            io.github.anjalymsajeev.nakshatracalendar.domain.model.StarDayConvention.SUNRISE
        )
        val occurrence = (result as io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult.Success).value
        org.junit.Assert.assertEquals(java.time.LocalDate.of(2026, 8, 26), occurrence?.observanceDate)
    }

    @Test
    fun nakshatrasBetweenAreContiguous() {
        val start = java.time.Instant.parse("2026-11-14T00:00:00Z")
        val periods = (
            engine.nakshatrasBetween(
                start,
                start.plusSeconds(3 * 86_400L)
            ) as io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult.Success
            ).value
        org.junit.Assert.assertTrue(periods.size in 3..5)
        periods.zipWithNext().forEach { (a, b) ->
            org.junit.Assert.assertTrue(java.time.Duration.between(a.end, b.start).abs().seconds <= 1)
        }
    }
}
