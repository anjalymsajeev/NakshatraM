package io.github.anjalymsajeev.nakshatracalendar.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.CalendarPreferences
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PersonRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.ReminderRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SettingsRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SyncRepository
import javax.inject.Singleton

/**
 * One DataStore per process for "settings" (same file as before: files/datastore/settings.preferences_pb). The
 * delegate guarantees a single instance even when a new Hilt component is created (as in instrumented tests).
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun settingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    abstract fun personRepository(impl: PersonRepositoryImpl): PersonRepository

    @Binds
    abstract fun syncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds
    abstract fun reminderRepository(impl: ReminderRepositoryImpl): ReminderRepository

    @Binds
    abstract fun calendarPreferences(impl: CalendarPreferencesImpl): CalendarPreferences

    companion object {
        @Provides
        @Singleton
        fun preferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.settingsDataStore
    }
}
