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
