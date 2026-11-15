package io.github.anjalymsajeev.nakshatracalendar.domain.model

import java.time.LocalDate
import java.time.LocalTime

/** A nakshatra's identity and names (from the library), independent of any moment in time. */
data class NakshatraName(
    /** 1 (Ashwathi) .. 27 (Revathi), the library's Nakshatra.number. */
    val number: Int,
    val nameMalayalam: String,
    val nameEnglish: String
)

/**
 * Event colours, matching Google Calendar's 11 event colours (`colorId` 1–11) so synced events
 * (Sprint 4) keep the colour the user picked.
 */
enum class EventColor(val colorId: Int, val argb: Long) {
    LAVENDER(1, 0xFF7986CB),
    SAGE(2, 0xFF33B679),
    GRAPE(3, 0xFF8E24AA),
    FLAMINGO(4, 0xFFE67C73),
    BANANA(5, 0xFFF6BF26),
    TANGERINE(6, 0xFFF4511E),
    PEACOCK(7, 0xFF039BE5),
    GRAPHITE(8, 0xFF616161),
    BLUEBERRY(9, 0xFF3F51B5),
    BASIL(10, 0xFF0B8043),
    TOMATO(11, 0xFFD50000)
    ;

    companion object {
        val DEFAULT = BASIL

        fun fromColorId(id: Int): EventColor = entries.firstOrNull { it.colorId == id } ?: DEFAULT
    }
}

/** Optional birth details, used to calculate the birth nakshatra. */
data class BirthDetails(val date: LocalDate, val time: LocalTime? = null, val place: CalculationLocation? = null)

/** A family member and the settings used for their Pirannal calculations. */
data class Person(
    val id: Long = 0,
    val name: String,
    val nakshatraNumber: Int,
    val birth: BirthDetails? = null,
    val calculationLocation: CalculationLocation,
    val eventColor: EventColor = EventColor.DEFAULT
)

/** One upcoming birth-star day (Pirannal) of a person. */
data class UpcomingPirannal(
    val person: Person,
    val nakshatra: NakshatraName,
    val observanceDate: LocalDate,
    val malayalamDate: MalayalamDateInfo,
    val period: NakshatraInfo
)

/** A family member with their nakshatra names and next Pirannal (null if it could not be calculated). */
data class FamilyMember(val person: Person, val nakshatra: NakshatraName, val next: UpcomingPirannal?)

/** A place found by search, with the time zones possible for its country. */
data class PlaceCandidate(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val countryCode: String?,
    /** One zone: use it. Several (e.g. US, AU): the user chooses. Empty: the user enters one. */
    val zones: List<java.time.ZoneId>
)

/** Why a person cannot be saved. */
sealed interface PersonValidationError {
    data object NameRequired : PersonValidationError

    data object NameTooLong : PersonValidationError

    data object NakshatraRequired : PersonValidationError

    data object BirthDateInFuture : PersonValidationError

    data object BirthDateTooEarly : PersonValidationError

    data object BirthPlaceWithoutDate : PersonValidationError
}
