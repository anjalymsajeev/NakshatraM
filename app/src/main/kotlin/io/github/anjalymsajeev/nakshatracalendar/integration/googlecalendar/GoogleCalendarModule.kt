package io.github.anjalymsajeev.nakshatracalendar.integration.googlecalendar

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.CalendarGateway
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.GoogleAuthRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SyncScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class GoogleCalendarModule {
    @Binds
    abstract fun calendarGateway(impl: GoogleCalendarApi): CalendarGateway

    @Binds
    abstract fun googleAuthRepository(impl: GoogleAuthRepositoryImpl): GoogleAuthRepository

    @Binds
    abstract fun syncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler

    companion object {
        @Provides
        @Singleton
        fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        @Provides
        @Named(GoogleCalendarApi.BASE_URL)
        fun baseUrl(): HttpUrl = GoogleCalendarApi.DEFAULT_BASE_URL

        @Provides
        @Named(GoogleCalendarApi.REVOKE_URL)
        fun revokeUrl(): HttpUrl = GoogleCalendarApi.DEFAULT_REVOKE_URL
    }
}
