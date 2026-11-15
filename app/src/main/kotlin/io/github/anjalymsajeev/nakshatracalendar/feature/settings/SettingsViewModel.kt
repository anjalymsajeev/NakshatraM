package io.github.anjalymsajeev.nakshatracalendar.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anjalymsajeev.nakshatracalendar.domain.model.StarDayConvention
import io.github.anjalymsajeev.nakshatracalendar.domain.model.ThemeMode
import io.github.anjalymsajeev.nakshatracalendar.domain.model.UserSettings
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings screen state. [settings] is `null` until DataStore has delivered the stored values, so the screen
 * never shows (or writes back) placeholder defaults. Setters ignore values that are already stored: writing the
 * theme makes AppCompat recreate the activity, and a redundant write would start a recreate loop.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<UserSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setThemeMode(mode: ThemeMode) {
        val current = settings.value ?: return
        if (current.themeMode == mode) return
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setStarDayConvention(convention: StarDayConvention) {
        val current = settings.value ?: return
        if (current.starDayConvention == convention) return
        viewModelScope.launch { repository.setStarDayConvention(convention) }
    }
}
