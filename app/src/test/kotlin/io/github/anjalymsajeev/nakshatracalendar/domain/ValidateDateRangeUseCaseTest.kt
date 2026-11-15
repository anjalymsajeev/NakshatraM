package io.github.anjalymsajeev.nakshatracalendar.domain

import io.github.anjalymsajeev.kollavarsham.core.KollavarshamCalendar
import io.github.anjalymsajeev.nakshatracalendar.domain.model.DateRange
import io.github.anjalymsajeev.nakshatracalendar.domain.model.DateRangeError
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.ValidateDateRangeUseCase
import io.github.anjalymsajeev.nakshatracalendar.integration.malayalamcalendar.KollavarshamDateProvider
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateDateRangeUseCaseTest {
    private val validate = ValidateDateRangeUseCase(KollavarshamDateProvider(KollavarshamCalendar.create()))
    private val start = LocalDate.of(2026, 1, 1)

    @Test
    fun acceptsOneDayAndExactlyFiveYears() {
        assertNull(validate(DateRange(start, start)))
        assertNull(validate(DateRange(start, start.plusYears(5))))
    }

    @Test
    fun rejectsReversedTooLongAndUnsupportedRanges() {
        assertEquals(DateRangeError.EndBeforeStart, validate(DateRange(start, start.minusDays(1))))
        assertEquals(DateRangeError.TooLong(5), validate(DateRange(start, start.plusYears(5).plusDays(1))))
        assertTrue(validate(DateRange(LocalDate.of(1899, 12, 31), start)) is DateRangeError.OutOfSupportedRange)
        assertTrue(validate(DateRange(start, LocalDate.of(2101, 1, 1))) is DateRangeError.OutOfSupportedRange)
    }
}
