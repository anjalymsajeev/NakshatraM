package io.github.anjalymsajeev.nakshatracalendar.integration.geocoding

import io.github.anjalymsajeev.nakshatracalendar.core.common.AppError
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.PlaceCandidate
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PlaceSearchRepository
import java.io.IOException
import javax.inject.Inject

/** Online place search via the platform geocoder, with time zones from the country. */
class GeocoderPlaceSearchRepository @Inject constructor(private val geocoder: GeocoderClient, private val zones: TimeZoneResolver) :
    PlaceSearchRepository {
    override suspend fun search(query: String): AppResult<List<PlaceCandidate>> {
        if (!geocoder.isAvailable()) return AppResult.Failure(AppError.ServiceUnavailable)
        return try {
            val candidates = geocoder.search(query, MAX_RESULTS).map { address ->
                PlaceCandidate(
                    label = label(address),
                    latitude = address.latitude,
                    longitude = address.longitude,
                    countryCode = address.countryCode,
                    zones = address.countryCode?.let(zones::zonesFor).orEmpty()
                )
            }.distinctBy { it.label }
            AppResult.Success(candidates)
        } catch (e: IOException) {
            AppResult.Failure(AppError.Network(e))
        } catch (e: IllegalArgumentException) {
            AppResult.Failure(AppError.Network(e))
        }
    }

    /** "Kozhikode, Kerala, India": the most specific name first, without repeats. */
    internal fun label(a: GeoAddress): String = listOfNotNull(a.locality ?: a.featureName, a.adminArea, a.countryName)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(", ")
        .ifEmpty { "%.4f, %.4f".format(java.util.Locale.ROOT, a.latitude, a.longitude) }

    private companion object {
        const val MAX_RESULTS = 8
    }
}
