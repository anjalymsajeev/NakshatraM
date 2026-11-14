package io.github.anjalymsajeev.nakshatracalendar.integration.nakshatra

import io.github.anjalymsajeev.kollavarsham.core.GeoLocation
import io.github.anjalymsajeev.kollavarsham.locale.CalendarLocale
import io.github.anjalymsajeev.kollavarsham.panchangam.Panchangam
import io.github.anjalymsajeev.kollavarsham.panchangam.PanchangamLocation
import io.github.anjalymsajeev.kollavarsham.panchangam.displayName
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppError
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalculationLocation
import io.github.anjalymsajeev.nakshatracalendar.domain.model.NakshatraInfo
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.NakshatraEngine
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter over the library's panchangam module (io.github.anjalymsajeev:kollavarsham-panchangam), the
 * validated astronomical engine. Kept separate from Malayalam date conversion.
 */
@Singleton
class PanchangamNakshatraEngine @Inject constructor() : NakshatraEngine {
    private val panchangams = ConcurrentHashMap<CalculationLocation, Panchangam>()

    override fun nakshatraAtSunrise(date: LocalDate, location: CalculationLocation): AppResult<NakshatraInfo> = try {
        val panchangam = panchangams.getOrPut(location) {
            Panchangam.create(PanchangamLocation(GeoLocation(location.latitude, location.longitude, location.name), location.zone))
        }
        val sunrise = panchangam.sunrise(date)
            ?: return AppResult.Failure(AppError.CalculationFailed(IllegalStateException("No sunrise on $date at ${location.name}")))
        val span = panchangam.nakshatraSpan(sunrise)
        AppResult.Success(
            NakshatraInfo(
                number = span.value.number,
                nameMalayalam = span.value.displayName(CalendarLocale.MALAYALAM),
                nameEnglish = span.value.displayName(CalendarLocale.ENGLISH),
                start = span.start,
                end = span.end
            )
        )
    } catch (e: IllegalArgumentException) {
        AppResult.Failure(AppError.CalculationFailed(e))
    } catch (e: IllegalStateException) {
        AppResult.Failure(AppError.CalculationFailed(e))
    }
}
