package io.github.anjalymsajeev.nakshatracalendar.domain.usecase

import io.github.anjalymsajeev.nakshatracalendar.domain.model.DateRange
import io.github.anjalymsajeev.nakshatracalendar.domain.model.DateRangeError
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.MalayalamDateProvider
import javax.inject.Inject

/** Checks a user-chosen range: ordered, at most [MAX_YEARS] long, inside the library's supported dates. */
class ValidateDateRangeUseCase @Inject constructor(private val dates: MalayalamDateProvider) {
    operator fun invoke(range: DateRange): DateRangeError? {
        val supported = dates.supportedRange
        return when {
            range.endInclusive.isBefore(range.start) -> DateRangeError.EndBeforeStart
            range.start !in supported || range.endInclusive !in supported -> DateRangeError.OutOfSupportedRange(supported)
            range.endInclusive.isAfter(range.start.plusYears(MAX_YEARS.toLong())) -> DateRangeError.TooLong(MAX_YEARS)
            else -> null
        }
    }

    companion object {
        /** Longest range a user may choose (owner decision D10). */
        const val MAX_YEARS = 5
    }
}
