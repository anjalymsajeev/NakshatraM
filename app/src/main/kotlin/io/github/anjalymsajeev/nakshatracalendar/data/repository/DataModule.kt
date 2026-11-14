package io.github.anjalymsajeev.nakshatracalendar.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PersonRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SettingsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun settingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    abstract fun personRepository(impl: PersonRepositoryImpl): PersonRepository

    companion object {
        @Provides
        @Singleton
        fun preferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
    }
}
