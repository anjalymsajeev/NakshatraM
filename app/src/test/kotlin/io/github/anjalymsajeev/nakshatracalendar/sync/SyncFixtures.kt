package io.github.anjalymsajeev.nakshatracalendar.sync

import io.github.anjalymsajeev.nakshatracalendar.domain.model.NakshatraName
import io.github.anjalymsajeev.nakshatracalendar.domain.model.StoredOccurrence
import java.time.Instant
import java.time.LocalDate

object SyncFixtures {
    val atham = NakshatraName(13, "അത്തം", "Atham")

    /** Times are illustrative fixtures for formatting tests, not astronomical results. */
    fun occurrence(id: Long = 1, date: LocalDate = LocalDate.of(2026, 9, 2), personId: Long? = 4) = StoredOccurrence(
        id = id,
        personId = personId,
        personName = "Anvidh",
        nakshatra = atham,
        observanceDate = date,
        start = Instant.parse("2026-09-01T17:45:00Z").plusSeconds((date.toEpochDay() - 20698) * 86_400),
        end = Instant.parse("2026-09-02T17:15:00Z").plusSeconds((date.toEpochDay() - 20698) * 86_400),
        zoneId = "Asia/Kolkata",
        locationName = "Thiruvananthapuram",
        malayalamDateMl = "കൊല്ലവർഷം 1202 ചിങ്ങം 17",
        malayalamDateEn = "17 Chingam 1202 ME",
        malayalamMonthMl = "ചിങ്ങം",
        malayalamMonthEn = "Chingam",
        kollavarshamYear = 1202
    )
}
