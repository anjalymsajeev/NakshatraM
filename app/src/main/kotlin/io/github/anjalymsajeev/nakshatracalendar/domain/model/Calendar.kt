package io.github.anjalymsajeev.nakshatracalendar.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A user-chosen period, both ends included. */
data class DateRange(val start: LocalDate, val endInclusive: LocalDate)

/** Why a [DateRange] cannot be used. */
sealed interface DateRangeError {
    data object EndBeforeStart : DateRangeError

    data class TooLong(val maxYears: Int) : DateRangeError

    /** Outside the dates the Malayalam calendar library supports. */
    data class OutOfSupportedRange(val supported: ClosedRange<LocalDate>) : DateRangeError
}

/**
 * One occurrence of a person's birth nakshatra. Times are instants; [zone] is the person's calculation zone,
 * used to show local start/end times and to decide whether the star spans two civil days.
 */
data class NakshatraOccurrenceResult(
    val person: Person,
    val nakshatra: NakshatraName,
    val start: Instant,
    val end: Instant,
    val zone: ZoneId,
    /** The civil day that belongs to this occurrence under the Settings convention, if one qualifies. */
    val observanceDate: LocalDate?,
    /** Malayalam date of [observanceDate] (or of the start day when there is no observance date). */
    val malayalam: MalayalamDateInfo?
) {
    val startDate: LocalDate get() = start.atZone(zone).toLocalDate()
    val endDate: LocalDate get() = end.atZone(zone).toLocalDate()
    val spansTwoDays: Boolean get() = startDate != endDate
}

/** All occurrences for one person in a range. */
data class PersonOccurrences(val person: Person, val nakshatra: NakshatraName, val occurrences: List<NakshatraOccurrenceResult>)

/** A Malayalam month and the Gregorian days it covers (from the library's month boundaries). */
data class MalayalamMonthSpan(
    val kollavarshamYear: Int,
    val monthNumber: Int,
    val nameMalayalam: String,
    val nameEnglish: String,
    val firstDay: LocalDate,
    val lastDay: LocalDate
)

/** Google Calendar state of a day's family events. Sprint 4 fills this from sync history; until then NONE. */
enum class SyncState { NONE, SYNCED, PARTIAL }

/** A family member whose birth star falls on a calendar day. */
data class FamilyMark(val personId: Long, val name: String, val color: EventColor, val nakshatra: NakshatraName)

/** Everything a calendar cell and its detail sheet show. */
data class CalendarDay(
    val date: LocalDate,
    val malayalam: MalayalamDateInfo,
    /** The nakshatra at sunrise at the Settings calculation location; null if it could not be computed. */
    val nakshatraAtSunrise: NakshatraInfo?,
    val family: List<FamilyMark>,
    /** Zone of the calculation location, for showing nakshatra times. */
    val zone: ZoneId,
    val syncState: SyncState = SyncState.NONE
)
