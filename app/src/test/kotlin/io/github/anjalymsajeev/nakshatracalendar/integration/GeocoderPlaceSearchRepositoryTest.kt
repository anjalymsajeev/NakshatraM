package io.github.anjalymsajeev.nakshatracalendar.integration

import io.github.anjalymsajeev.nakshatracalendar.core.common.AppError
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.integration.geocoding.GeoAddress
import io.github.anjalymsajeev.nakshatracalendar.integration.geocoding.GeocoderClient
import io.github.anjalymsajeev.nakshatracalendar.integration.geocoding.GeocoderPlaceSearchRepository
import io.github.anjalymsajeev.nakshatracalendar.integration.geocoding.TimeZoneResolver
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeocoderPlaceSearchRepositoryTest {
    private class FakeGeocoder(val available: Boolean = true, val result: () -> List<GeoAddress> = { emptyList() }) : GeocoderClient {
        override fun isAvailable() = available

        override suspend fun search(query: String, maxResults: Int) = result()
    }

    private val zones = object : TimeZoneResolver {
        override fun zonesFor(countryCode: String) = when (countryCode) {
            "IN" -> listOf(ZoneId.of("Asia/Kolkata"))
            "US" -> listOf(ZoneId.of("America/New_York"), ZoneId.of("America/Los_Angeles"))
            else -> emptyList()
        }
    }

    @Test
    fun mapsAddressesWithLabelsAndZones() = runTest {
        val repo = GeocoderPlaceSearchRepository(
            FakeGeocoder(result = {
                listOf(
                    GeoAddress("Kozhikode", "Kozhikode", "Kerala", "India", "IN", 11.2588, 75.7804),
                    GeoAddress("Springfield", "Springfield", "Illinois", "United States", "US", 39.8, -89.6),
                    GeoAddress("Kozhikode", "Kozhikode", "Kerala", "India", "IN", 11.26, 75.78)
                )
            }),
            zones
        )
        val places = (repo.search("x") as AppResult.Success).value
        assertEquals(listOf("Kozhikode, Kerala, India", "Springfield, Illinois, United States"), places.map { it.label })
        assertEquals(listOf(ZoneId.of("Asia/Kolkata")), places[0].zones)
        assertEquals(2, places[1].zones.size)
    }

    @Test
    fun offlineAndUnavailableAreReportedNotThrown() = runTest {
        val offline = GeocoderPlaceSearchRepository(FakeGeocoder(result = { throw IOException("offline") }), zones)
        assertTrue((offline.search("x") as AppResult.Failure).error is AppError.Network)
        val missing = GeocoderPlaceSearchRepository(FakeGeocoder(available = false), zones)
        assertEquals(AppError.ServiceUnavailable, (missing.search("x") as AppResult.Failure).error)
    }
}
