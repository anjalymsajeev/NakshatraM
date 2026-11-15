package io.github.anjalymsajeev.nakshatracalendar.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.anjalymsajeev.nakshatracalendar.domain.model.GoogleCalendarInfo
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.CalendarPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The selected Google calendar's id and name. Not sensitive; no tokens are stored anywhere. */
class CalendarPreferencesImpl @Inject constructor(private val dataStore: DataStore<Preferences>) : CalendarPreferences {
    override val selectedCalendar: Flow<GoogleCalendarInfo?> = dataStore.data.map { prefs ->
        val id = prefs[CALENDAR_ID] ?: return@map null
        GoogleCalendarInfo(id, prefs[CALENDAR_NAME] ?: id)
    }.distinctUntilChanged()

    override suspend fun select(calendar: GoogleCalendarInfo) {
        dataStore.edit {
            it[CALENDAR_ID] = calendar.id
            it[CALENDAR_NAME] = calendar.name
        }
    }

    override suspend fun clear() {
        dataStore.edit {
            it.remove(CALENDAR_ID)
            it.remove(CALENDAR_NAME)
        }
    }

    private companion object {
        val CALENDAR_ID = stringPreferencesKey("google_calendar_id")
        val CALENDAR_NAME = stringPreferencesKey("google_calendar_name")
    }
}
