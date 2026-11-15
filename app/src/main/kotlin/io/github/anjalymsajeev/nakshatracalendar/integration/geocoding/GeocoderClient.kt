package io.github.anjalymsajeev.nakshatracalendar.integration.geocoding

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** A geocoded address, free of Android types so the mapping logic is unit-testable. */
data class GeoAddress(
    val featureName: String?,
    val locality: String?,
    val adminArea: String?,
    val countryName: String?,
    val countryCode: String?,
    val latitude: Double,
    val longitude: Double
)

/** Thin wrapper over the platform geocoder. */
interface GeocoderClient {
    fun isAvailable(): Boolean

    /** @throws IOException when the service cannot be reached (offline, timeout). */
    suspend fun search(query: String, maxResults: Int): List<GeoAddress>
}

/**
 * [Geocoder] backed by Google Play services: free, no API key, no location permission. Uses the
 * asynchronous API on Android 13+ and a background thread below.
 */
class AndroidGeocoderClient @Inject constructor(@ApplicationContext private val context: Context) : GeocoderClient {
    override fun isAvailable(): Boolean = Geocoder.isPresent()

    override suspend fun search(query: String, maxResults: Int): List<GeoAddress> {
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses: List<Address> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(
                        query,
                        maxResults,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses)

                            override fun onError(errorMessage: String?) =
                                cont.resumeWithException(IOException(errorMessage ?: "Geocoder error"))
                        }
                    )
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query, maxResults).orEmpty()
                }
            }
        return addresses.filter { it.hasLatitude() && it.hasLongitude() }.map {
            GeoAddress(it.featureName, it.locality, it.adminArea, it.countryName, it.countryCode, it.latitude, it.longitude)
        }
    }
}
