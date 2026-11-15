package io.github.anjalymsajeev.nakshatracalendar.domain.repository

import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalculationLocation
import io.github.anjalymsajeev.nakshatracalendar.domain.model.MalayalamDateInfo
import io.github.anjalymsajeev.nakshatracalendar.domain.model.MalayalamMonthSpan
import io.github.anjalymsajeev.nakshatracalendar.domain.model.NakshatraInfo
import io.github.anjalymsajeev.nakshatracalendar.domain.model.NakshatraName
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.model.PlaceCandidate
import io.github.anjalymsajeev.nakshatracalendar.domain.model.StarDayConvention
import io.github.anjalymsajeev.nakshatracalendar.domain.model.ThemeMode
import io.github.anjalymsajeev.nakshatracalendar.domain.model.UserSettings
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Malayalam date conversion. Implemented by the Kollavarsham library adapter; no calendar logic in the app. */
interface MalayalamDateProvider {
    /** Dates the library can convert (1900-01-01 to 2100-12-31 at the time of writing). */
    val supportedRange: ClosedRange<LocalDate>

    fun malayalamDate(date: LocalDate): AppResult<MalayalamDateInfo>

    /** The Malayalam month containing [date], with its first and last Gregorian days. */
    fun monthContaining(date: LocalDate): AppResult<MalayalamMonthSpan>
}

/** A nakshatra occurrence and the civil day that belongs to it under a star-day convention. */
data class StarOccurrence(val period: NakshatraInfo, val observanceDate: LocalDate?)

/**
 * Nakshatra (astronomical) calculations, kept separate from date conversion. Implemented by the
 * library's panchangam module, which is validated against independent ephemerides.
 */
interface NakshatraEngine {
    /** The 27 nakshatras in order, with Malayalam and English names. */
    fun allNakshatras(): List<NakshatraName>

    fun nakshatraAtSunrise(date: LocalDate, location: CalculationLocation): AppResult<NakshatraInfo>

    /** The nakshatra in force at [instant], with the period it covers. */
    fun nakshatraAt(instant: Instant): AppResult<NakshatraInfo>

    /** Every nakshatra period overlapping [start, end), in order. */
    fun nakshatrasBetween(start: Instant, end: Instant): AppResult<List<NakshatraInfo>>

    /**
     * Every occurrence of nakshatra [number] overlapping [from]..[to] (days in [location]'s zone), found by walking
     * the real nakshatra spans (never a fixed 27-day cycle), each with its observance day under [convention].
     */
    fun occurrences(
        number: Int,
        from: LocalDate,
        to: LocalDate,
        location: CalculationLocation,
        convention: StarDayConvention
    ): AppResult<List<StarOccurrence>>

    /** The first occurrence of nakshatra [number] whose observance day is on or after [from]. */
    fun nextObservance(
        number: Int,
        from: LocalDate,
        location: CalculationLocation,
        convention: StarDayConvention
    ): AppResult<StarOccurrence?>
}

interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setStarDayConvention(convention: StarDayConvention)
}

/** Family member profiles stored on the device. */
interface PersonRepository {
    fun observeCount(): Flow<Int>

    fun observeAll(): Flow<List<Person>>

    suspend fun get(id: Long): AppResult<Person?>

    /** Inserts (id 0) or updates the person; returns its id. */
    suspend fun save(person: Person): AppResult<Long>

    suspend fun delete(id: Long): AppResult<Unit>
}

/** Online place search (city, town or village) with possible time zones. */
interface PlaceSearchRepository {
    suspend fun search(query: String): AppResult<List<PlaceCandidate>>
}
