package io.github.anjalymsajeev.nakshatracalendar.integration.malayalamcalendar

import io.github.anjalymsajeev.kollavarsham.core.DateOutOfSupportedRangeException
import io.github.anjalymsajeev.kollavarsham.core.KollavarshamCalendar
import io.github.anjalymsajeev.kollavarsham.locale.CalendarLocale
import io.github.anjalymsajeev.kollavarsham.locale.DateStyle
import io.github.anjalymsajeev.kollavarsham.locale.displayName
import io.github.anjalymsajeev.kollavarsham.locale.format
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppError
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.MalayalamDateInfo
import io.github.anjalymsajeev.nakshatracalendar.domain.model.MalayalamMonthSpan
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.MalayalamDateProvider
import java.time.LocalDate
import javax.inject.Inject

/**
 * Adapter over the Kollavarsham library (io.github.anjalymsajeev:kollavarsham-core/-locale).
 * All conversion is done by the library; this class only maps types and errors.
 */
class KollavarshamDateProvider @Inject constructor(private val calendar: KollavarshamCalendar) : MalayalamDateProvider {
    override val supportedRange: ClosedRange<LocalDate> get() = calendar.supportedRange

    override fun monthContaining(date: LocalDate): AppResult<MalayalamMonthSpan> = convert {
        val ml = calendar.fromGregorian(date)
        val boundary = calendar.monthBoundary(ml.year, ml.month)
        MalayalamMonthSpan(
            kollavarshamYear = ml.year,
            monthNumber = ml.month.number,
            nameMalayalam = ml.month.displayName(CalendarLocale.MALAYALAM),
            nameEnglish = ml.month.displayName(CalendarLocale.ENGLISH),
            firstDay = boundary.firstDay,
            lastDay = boundary.lastDay
        )
    }

    override fun malayalamDate(date: LocalDate): AppResult<MalayalamDateInfo> = try {
        val ml = calendar.fromGregorian(date)
        AppResult.Success(
            MalayalamDateInfo(
                gregorianDate = date,
                kollavarshamYear = ml.year,
                monthNumber = ml.month.number,
                day = ml.day,
                monthNameMalayalam = ml.month.displayName(CalendarLocale.MALAYALAM),
                monthNameEnglish = ml.month.displayName(CalendarLocale.ENGLISH),
                formattedMalayalam = ml.format(CalendarLocale.MALAYALAM, DateStyle.LONG),
                formattedEnglish = ml.format(CalendarLocale.ENGLISH, DateStyle.LONG)
            )
        )
    } catch (e: DateOutOfSupportedRangeException) {
        AppResult.Failure(AppError.UnsupportedDate(e.message.orEmpty()))
    } catch (e: IllegalArgumentException) {
        AppResult.Failure(AppError.CalculationFailed(e))
    }

    private inline fun <T> convert(block: () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (e: DateOutOfSupportedRangeException) {
        AppResult.Failure(AppError.UnsupportedDate(e.message.orEmpty()))
    } catch (e: IllegalArgumentException) {
        AppResult.Failure(AppError.CalculationFailed(e))
    }
}
