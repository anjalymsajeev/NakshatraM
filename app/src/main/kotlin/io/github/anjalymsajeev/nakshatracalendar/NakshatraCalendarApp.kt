package io.github.anjalymsajeev.nakshatracalendar

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.HiltAndroidApp
import io.github.anjalymsajeev.nakshatracalendar.domain.model.ThemeMode
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltAndroidApp
class NakshatraCalendarApp : Application() {
    @Inject lateinit var settings: SettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // Apply the stored light/dark preference for the whole app.
        appScope.launch {
            settings.settings.map { it.themeMode }.distinctUntilChanged().collect { mode ->
                AppCompatDelegate.setDefaultNightMode(
                    when (mode) {
                        ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                        ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    }
                )
            }
        }
    }
}
