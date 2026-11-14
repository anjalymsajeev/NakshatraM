package io.github.anjalymsajeev.nakshatracalendar.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.anjalymsajeev.nakshatracalendar.data.repository.SettingsRepositoryImpl
import io.github.anjalymsajeev.nakshatracalendar.domain.model.StarDayConvention
import io.github.anjalymsajeev.nakshatracalendar.domain.model.ThemeMode
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Uses a real DataStore in a temporary file (DataStore runs on the plain JVM). */
class SettingsRepositoryImplTest {
    private fun newStore(scope: kotlinx.coroutines.CoroutineScope) = PreferenceDataStoreFactory.create(scope = scope) {
        File(createTempDirectory("settings").toFile(), "test.preferences_pb")
    }

    @Test
    fun defaultsThenStoredValues() = runTest {
        val store = newStore(backgroundScope)
        val repo = SettingsRepositoryImpl(store)
        assertEquals(ThemeMode.SYSTEM, repo.settings.first().themeMode)
        assertEquals(StarDayConvention.SUNRISE, repo.settings.first().starDayConvention)
        repo.setThemeMode(ThemeMode.DARK)
        repo.setStarDayConvention(StarDayConvention.SIX_NAZHIKA)
        assertEquals(ThemeMode.DARK, repo.settings.first().themeMode)
        assertEquals(StarDayConvention.SIX_NAZHIKA, repo.settings.first().starDayConvention)
    }

    @Test
    fun unknownStoredValuesFallBackToDefaults() = runTest {
        val store = newStore(backgroundScope)
        store.edit { it[stringPreferencesKey("theme_mode")] = "PURPLE" }
        assertEquals(ThemeMode.SYSTEM, SettingsRepositoryImpl(store).settings.first().themeMode)
    }
}
