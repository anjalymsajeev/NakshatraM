package io.github.anjalymsajeev.nakshatracalendar.integration

import io.github.anjalymsajeev.kollavarsham.core.KollavarshamCalendar
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppError
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.integration.malayalamcalendar.KollavarshamDateProvider
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs against the real published library: verifies the adapter's mapping, not the library's maths. */
class KollavarshamDateProviderTest {
    private val provider = KollavarshamDateProvider(KollavarshamCalendar.create())

    @Test
    fun mapsALibraryDateToAppModel() {
        val result = provider.malayalamDate(LocalDate.of(2026, 11, 14))
        val info = (result as AppResult.Success).value
        assertEquals(1202, info.kollavarshamYear)
        assertEquals(3, info.monthNumber)
        assertEquals(28, info.day)
        assertEquals("തുലാം", info.monthNameMalayalam)
        assertEquals("Thulam", info.monthNameEnglish)
        assertEquals("28 Thulam 1202 ME", info.formattedEnglish)
        assertEquals("കൊല്ലവർഷം 1202 തുലാം 28", info.formattedMalayalam)
    }

    @Test
    fun mapsOutOfRangeDatesToUnsupportedDate() {
        val result = provider.malayalamDate(LocalDate.of(1899, 12, 31))
        assertTrue(result is AppResult.Failure && result.error is AppError.UnsupportedDate)
    }
}
