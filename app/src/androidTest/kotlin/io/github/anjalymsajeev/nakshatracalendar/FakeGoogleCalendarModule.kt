package io.github.anjalymsajeev.nakshatracalendar

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalendarEventDraft
import io.github.anjalymsajeev.nakshatracalendar.domain.model.GoogleCalendarInfo
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.AuthorizationState
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.CalendarGateway
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.GoogleAuthRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SyncScheduler
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.CalendarSyncEngine
import io.github.anjalymsajeev.nakshatracalendar.integration.googlecalendar.GoogleCalendarModule
import java.util.Collections
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * TEST ONLY: replaces the real Google integration (OAuth + Calendar REST + WorkManager) in instrumented tests, so
 * the sync UI can be exercised on an emulator without a Google account. The production app never uses this.
 */
object TestGoogleCalendar {
    val calendar = GoogleCalendarInfo("family-test@group.calendar.google.com", "Family (test)")
    val upserts: MutableList<CalendarEventDraft> = Collections.synchronizedList(mutableListOf())
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [GoogleCalendarModule::class])
object FakeGoogleCalendarModule {
    @Provides
    @Singleton
    fun gateway(): CalendarGateway = object : CalendarGateway {
        override suspend fun writableCalendars(accessToken: String) = AppResult.Success(listOf(TestGoogleCalendar.calendar))

        override suspend fun upsertEvent(accessToken: String, draft: CalendarEventDraft): AppResult<Unit> {
            TestGoogleCalendar.upserts += draft
            return AppResult.Success(Unit)
        }

        override suspend fun deleteEvent(accessToken: String, calendarId: String, eventId: String) = AppResult.Success(Unit)

        override suspend fun revoke(accessToken: String) = AppResult.Success(Unit)
    }

    @Provides
    @Singleton
    fun auth(): GoogleAuthRepository = object : GoogleAuthRepository {
        override suspend fun authorize(): AppResult<AuthorizationState> = AppResult.Success(AuthorizationState.Authorized("test-token"))

        override suspend fun accessToken() = AppResult.Success("test-token")

        override fun completeConsent(resultData: Any?) = AppResult.Success("test-token")

        override suspend fun invalidate(accessToken: String) = Unit
    }

    /** Runs the real sync engine immediately instead of through WorkManager. */
    @Provides
    @Singleton
    fun scheduler(engine: Provider<CalendarSyncEngine>): SyncScheduler = object : SyncScheduler {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        override fun schedule(syncId: Long) {
            scope.launch { engine.get().run(syncId, finalAttempt = true) }
        }

        override fun scheduleRemoval(syncId: Long) {
            scope.launch { engine.get().remove(syncId, finalAttempt = true) }
        }
    }
}
