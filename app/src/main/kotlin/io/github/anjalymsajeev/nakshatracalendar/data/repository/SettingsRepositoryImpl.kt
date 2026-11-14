package io.github.anjalymsajeev.nakshatracalendar.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.anjalymsajeev.nakshatracalendar.domain.model.StarDayConvention
import io.github.anjalymsajeev.nakshatracalendar.domain.model.ThemeMode
import io.github.anjalymsajeev.nakshatracalendar.domain.model.UserSettings
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Preferences in Jetpack DataStore. Unknown stored values fall back to defaults instead of crashing. */
class SettingsRepositoryImpl @Inject constructor(private val dataStore: DataStore<Preferences>) : SettingsRepository {
    override val settings: Flow<UserSettings> =
        dataStore.data.map { prefs ->
            UserSettings(
                themeMode = enumOrDefault(prefs[THEME_MODE], ThemeMode.SYSTEM),
                starDayConvention = enumOrDefault(prefs[STAR_DAY], StarDayConvention.SUNRISE)
            )
        }.distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    override suspend fun setStarDayConvention(convention: StarDayConvention) {
        dataStore.edit { it[STAR_DAY] = convention.name }
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(value: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == value } ?: default

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val STAR_DAY = stringPreferencesKey("star_day_convention")
    }
}
