package io.github.anjalymsajeev.nakshatracalendar.integration.nakshatra

import io.github.anjalymsajeev.kollavarsham.core.GeoLocation
import io.github.anjalymsajeev.kollavarsham.locale.CalendarLocale
import io.github.anjalymsajeev.kollavarsham.panchangam.Nakshatra
import io.github.anjalymsajeev.kollavarsham.panchangam.Panchangam
import io.github.anjalymsajeev.kollavarsham.panchangam.PanchangamLocation
import io.github.anjalymsajeev.kollavarsham.panchangam.Span
import io.github.anjalymsajeev.kollavarsham.panchangam.StarDayConvention as LibraryConvention
import io.github.anjalymsajeev.kollavarsham.panchangam.displayName
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppError
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalculationLocation
import io.github.anjalymsajeev.nakshatracalendar.domain.model.NakshatraInfo
import io.github.anjalymsajeev.nakshatracalendar.domain.model.NakshatraName
import io.github.anjalymsajeev.nakshatracalendar.domain.model.StarDayConvention
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.NakshatraEngine
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.StarOccurrence
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapter over the library's panchangam module (io.github.anjalymsajeev:kollavarsham-panchangam), the
 * validated astronomical engine. Kept separate from Malayalam date conversion. Maps types and errors only.
 */
@Singleton
class PanchangamNakshatraEngine @Inject constructor() : NakshatraEngine {
    private val panchangams = ConcurrentHashMap<CalculationLocation, Panchangam>()
    private val names: List<NakshatraName> = Nakshatra.entries.map { it.toName() }

    override fun allNakshatras(): List<NakshatraName> = names

    override fun nakshatraAtSunrise(date: LocalDate, location: CalculationLocation): AppResult<NakshatraInfo> = calculate {
        val panchangam = panchangamFor(location)
        val sunrise = panchangam.sunrise(date) ?: throw IllegalStateException("No sunrise on $date at ${location.name}")
        panchangam.nakshatraSpan(sunrise).toInfo()
    }

    override fun nakshatraAt(instant: Instant): AppResult<NakshatraInfo> = calculate { Panchangam.create().nakshatraSpan(instant).toInfo() }

    override fun nakshatrasBetween(start: Instant, end: Instant): AppResult<List<NakshatraInfo>> = calculate {
        require(start.isBefore(end)) { "start must be before end" }
        val panchangam = Panchangam.create()
        buildList {
            var span = panchangam.nakshatraSpan(start)
            add(span.toInfo())
            while (span.end.isBefore(end)) {
                // Probe just past the end so millisecond rounding cannot return the same span.
                span = panchangam.nakshatraSpan(span.end.plusSeconds(1))
                add(span.toInfo())
            }
        }
    }

    override fun nextObservance(
        number: Int,
        from: LocalDate,
        location: CalculationLocation,
        convention: StarDayConvention
    ): AppResult<StarOccurrence?> = calculate {
        val libraryConvention = when (convention) {
            StarDayConvention.SUNRISE -> LibraryConvention.SUNRISE
            StarDayConvention.SIX_NAZHIKA -> LibraryConvention.SIX_NAZHIKA
        }
        // A nakshatra recurs about every 27.3 days, so a 60-day window always holds the next one.
        panchangamFor(location)
            .nakshatraOccurrences(Nakshatra.of(number), from.minusDays(1), from.plusDays(WINDOW_DAYS), libraryConvention)
            .firstOrNull { occurrence -> occurrence.observanceDate?.let { !it.isBefore(from) } == true }
            ?.let { StarOccurrence(it.span.toInfo(), it.observanceDate) }
    }

    private fun panchangamFor(location: CalculationLocation): Panchangam = panchangams.getOrPut(location) {
        Panchangam.create(PanchangamLocation(GeoLocation(location.latitude, location.longitude, location.name), location.zone))
    }

    private fun Nakshatra.toName() = NakshatraName(number, displayName(CalendarLocale.MALAYALAM), displayName(CalendarLocale.ENGLISH))

    private fun Span<Nakshatra>.toInfo() = NakshatraInfo(
        number = value.number,
        nameMalayalam = value.displayName(CalendarLocale.MALAYALAM),
        nameEnglish = value.displayName(CalendarLocale.ENGLISH),
        start = start,
        end = end
    )

    /** The library signals invalid input and out-of-range dates with these exception types. */
    private inline fun <T> calculate(block: () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (e: IllegalArgumentException) {
        AppResult.Failure(AppError.CalculationFailed(e))
    } catch (e: IllegalStateException) {
        AppResult.Failure(AppError.CalculationFailed(e))
    }

    private companion object {
        const val WINDOW_DAYS = 60L
    }
}
