package io.github.anjalymsajeev.nakshatracalendar.integration.geocoding

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PlaceSearchRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class GeocodingModule {
    @Binds abstract fun geocoderClient(impl: AndroidGeocoderClient): GeocoderClient

    @Binds abstract fun timeZoneResolver(impl: IcuTimeZoneResolver): TimeZoneResolver

    @Binds abstract fun placeSearchRepository(impl: GeocoderPlaceSearchRepository): PlaceSearchRepository
}
