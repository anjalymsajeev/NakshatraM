package io.github.anjalymsajeev.nakshatracalendar.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Where sunrise is computed for star-day rules, and which civil time zone defines days. */
data class CalculationLocation(val name: String, val latitude: Double, val longitude: Double, val zone: ZoneId) {
    companion object {
        val Thiruvananthapuram = CalculationLocation("Thiruvananthapuram", 8.5241, 76.9366, ZoneId.of("Asia/Kolkata"))
    }
}

/** Which civil day belongs to a birth star when the star spans two days. */
enum class StarDayConvention { SUNRISE, SIX_NAZHIKA }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** User preferences. The display language is owned by AppCompat's per-app locale, not stored here. */
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val starDayConvention: StarDayConvention = StarDayConvention.SUNRISE,
    val calculationLocation: CalculationLocation = CalculationLocation.Thiruvananthapuram
)

/** A Malayalam (Kollavarsham) date with ready-to-show text in both languages. App-owned, not a library type. */
data class MalayalamDateInfo(
    val gregorianDate: LocalDate,
    val kollavarshamYear: Int,
    val monthNumber: Int,
    val day: Int,
    val monthNameMalayalam: String,
    val monthNameEnglish: String,
    val formattedMalayalam: String,
    val formattedEnglish: String
)

/** A nakshatra in force at some moment, with the period it covers. */
data class NakshatraInfo(val number: Int, val nameMalayalam: String, val nameEnglish: String, val start: Instant, val end: Instant)

/** Everything the home dashboard shows for today. */
data class TodayOverview(
    val date: MalayalamDateInfo,
    val nakshatraAtSunrise: NakshatraInfo,
    val familyMemberCount: Int,
    /** The calculation location's zone, for showing times. */
    val zone: ZoneId
)
