package io.github.anjalymsajeev.nakshatracalendar.integration

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.anjalymsajeev.kollavarsham.core.KollavarshamCalendar
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.MalayalamDateProvider
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.NakshatraEngine
import io.github.anjalymsajeev.nakshatracalendar.integration.malayalamcalendar.KollavarshamDateProvider
import io.github.anjalymsajeev.nakshatracalendar.integration.nakshatra.PanchangamNakshatraEngine
import javax.inject.Singleton

/** Binds the domain interfaces to the Malayalam calendar library adapters. */
@Module
@InstallIn(SingletonComponent::class)
abstract class IntegrationModule {
    @Binds
    abstract fun malayalamDateProvider(impl: KollavarshamDateProvider): MalayalamDateProvider

    @Binds
    abstract fun nakshatraEngine(impl: PanchangamNakshatraEngine): NakshatraEngine

    companion object {
        /** The library's shared, thread-safe default calendar (Kerala rule, Thiruvananthapuram reference). */
        @Provides
        @Singleton
        fun kollavarshamCalendar(): KollavarshamCalendar = KollavarshamCalendar.create()
    }
}
