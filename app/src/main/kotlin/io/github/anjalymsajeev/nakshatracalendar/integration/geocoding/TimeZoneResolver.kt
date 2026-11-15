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
 * canonical location zones only; below that, aliases are collapsed with `getCanonicalID`. CLDR's legacy
 * spellings are then replaced with current IANA names, so users see "Asia/Kolkata", never "Asia/Calcutta".
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
        // Older tz data may not know a newer name (e.g. "Europe/Kyiv"); keep the original ID then.
        return ids.mapNotNull { id -> zoneOrNull(LEGACY_TO_IANA[id]) ?: zoneOrNull(id) }
            .distinct()
            .sortedBy { it.id }
    }

    private fun zoneOrNull(id: String?): ZoneId? = id?.let { runCatching { ZoneId.of(it) }.getOrNull() }

    private companion object {
        /**
         * ICU's canonical IDs follow CLDR, which keeps some pre-rename names (e.g. "Asia/Calcutta"). Show the
         * current IANA names instead. java.time accepts both, so this only affects what users see and store.
         */
        val LEGACY_TO_IANA = mapOf(
            "Asia/Calcutta" to "Asia/Kolkata",
            "Asia/Katmandu" to "Asia/Kathmandu",
            "Asia/Saigon" to "Asia/Ho_Chi_Minh",
            "Asia/Rangoon" to "Asia/Yangon",
            "Europe/Kiev" to "Europe/Kyiv",
            "Atlantic/Faeroe" to "Atlantic/Faroe",
            "Africa/Asmera" to "Africa/Asmara",
            "America/Godthab" to "America/Nuuk",
            "America/Coral_Harbour" to "America/Atikokan",
            "America/Buenos_Aires" to "America/Argentina/Buenos_Aires",
            "America/Catamarca" to "America/Argentina/Catamarca",
            "America/Cordoba" to "America/Argentina/Cordoba",
            "America/Jujuy" to "America/Argentina/Jujuy",
            "America/Mendoza" to "America/Argentina/Mendoza",
            "America/Indianapolis" to "America/Indiana/Indianapolis",
            "America/Louisville" to "America/Kentucky/Louisville",
            "Pacific/Truk" to "Pacific/Chuuk",
            "Pacific/Ponape" to "Pacific/Pohnpei",
            "Pacific/Enderbury" to "Pacific/Kanton"
        )
    }
}
