package io.github.anjalymsajeev.nakshatracalendar.integration.geocoding

import android.icu.util.TimeZone as IcuTimeZone
import android.os.Build
import java.time.ZoneId
import javax.inject.Inject

/** Time zones in use in a country. */
interface TimeZoneResolver {
    fun zonesFor(countryCode: String): List<ZoneId>
}

/**
 * Uses the platform's ICU data (`android.icu.util.TimeZone`). Each zone appears once: on API 28+ ICU lists
 * canonical location zones only; below that, aliases are collapsed with `getCanonicalID`, so "Asia/Calcutta"
 * and "Asia/Kolkata" never appear side by side (older devices may show the older spelling).
 */
class IcuTimeZoneResolver @Inject constructor() : TimeZoneResolver {
    override fun zonesFor(countryCode: String): List<ZoneId> {
        val region = countryCode.uppercase()
        val ids = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            IcuTimeZone.getAvailableIDs(IcuTimeZone.SystemTimeZoneType.CANONICAL_LOCATION, region, null)
        } else {
            IcuTimeZone.getAvailableIDs(region)
                .filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
                .map { IcuTimeZone.getCanonicalID(it) ?: it }
        }
        return ids.distinct().sorted().mapNotNull { id -> runCatching { ZoneId.of(id) }.getOrNull() }
    }
}
