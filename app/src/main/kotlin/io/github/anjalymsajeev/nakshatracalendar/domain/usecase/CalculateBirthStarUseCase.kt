package io.github.anjalymsajeev.nakshatracalendar.domain.usecase

import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.BirthDetails
import io.github.anjalymsajeev.nakshatracalendar.domain.model.NakshatraName
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.NakshatraEngine
import java.time.Duration
import java.time.ZoneId
import javax.inject.Inject

/** The calculated birth star, with alternatives when the birth moment is uncertain. */
data class BirthStarResult(
    /** The most likely star (the one in force at the birth time, or at noon if the time is unknown). */
    val star: NakshatraName,
    /** Every star the user should choose from; just [star] when the birth time is clear of any boundary. */
    val candidates: List<NakshatraName>,
    /** True when the result is uncertain (time unknown, or within the margin of a nakshatra change). */
    val uncertain: Boolean
)

/**
 * Works out the birth nakshatra from birth details using the library's astronomy. A nakshatra is
 * geocentric, so the birth place only matters for its time zone; without a place, [defaultZone] is used.
 */
class CalculateBirthStarUseCase @Inject constructor(private val engine: NakshatraEngine) {
    operator fun invoke(birth: BirthDetails, defaultZone: ZoneId): AppResult<BirthStarResult> {
        val zone = birth.place?.zone ?: defaultZone
        val names = engine.allNakshatras().associateBy { it.number }
        val time = birth.time
        if (time == null) {
            // Unknown time: every star in force at some moment of that civil day is possible.
            val start = birth.date.atStartOfDay(zone).toInstant()
            val end = birth.date.plusDays(1).atStartOfDay(zone).toInstant()
            val noon = birth.date.atTime(12, 0).atZone(zone).toInstant()
            return when (val periods = engine.nakshatrasBetween(start, end)) {
                is AppResult.Failure -> periods
                is AppResult.Success -> {
                    val candidates = periods.value.map { names.getValue(it.number) }.distinct()
                    val atNoon = periods.value.first { !noon.isBefore(it.start) && noon.isBefore(it.end) }
                    AppResult.Success(BirthStarResult(names.getValue(atNoon.number), candidates, candidates.size > 1))
                }
            }
        }
        val instant = birth.date.atTime(time).atZone(zone).toInstant()
        return when (val period = engine.nakshatraAt(instant)) {
            is AppResult.Failure -> period
            is AppResult.Success -> {
                val star = names.getValue(period.value.number)
                val candidates = buildList {
                    if (Duration.between(period.value.start, instant) < BOUNDARY_MARGIN) add(names.getValue(previous(star.number)))
                    add(star)
                    if (Duration.between(instant, period.value.end) < BOUNDARY_MARGIN) add(names.getValue(next(star.number)))
                }
                AppResult.Success(BirthStarResult(star, candidates, candidates.size > 1))
            }
        }
    }

    private fun previous(number: Int) = if (number == 1) 27 else number - 1

    private fun next(number: Int) = if (number == 27) 1 else number + 1

    companion object {
        /** Birth times are often approximate; within this margin of a change, both stars are offered. */
        val BOUNDARY_MARGIN: Duration = Duration.ofMinutes(60)
    }
}
