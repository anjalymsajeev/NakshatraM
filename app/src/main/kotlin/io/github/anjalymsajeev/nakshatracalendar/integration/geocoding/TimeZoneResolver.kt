package io.github.anjalymsajeev.nakshatracalendar.integration.geocoding

import java.time.ZoneId
import javax.inject.Inject

/** Time zones in use in a country. */
interface TimeZoneResolver {
    fun zonesFor(countryCode: String): List<ZoneId>
}

/**
 * Uses the platform's ICU data (`android.icu.util.TimeZone`, API 24+). Only canonical region zones
 * (e.g. "Asia/Kolkata") that java.time also knows are returned.
 */
class IcuTimeZoneResolver @Inject constructor() : TimeZoneResolver {
    override fun zonesFor(countryCode: String): List<ZoneId> = android.icu.util.TimeZone.getAvailableIDs(countryCode.uppercase())
        .filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
        .mapNotNull { id -> runCatching { ZoneId.of(id) }.getOrNull() }
        .distinct()
}
