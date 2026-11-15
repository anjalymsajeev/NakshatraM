package io.github.anjalymsajeev.nakshatracalendar.feature.sync

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.GoogleCalendarInfo
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.AuthorizationState
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.CalendarGateway
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.CalendarPreferences
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.ConsentRequest
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.GoogleAuthRepository
import io.github.anjalymsajeev.nakshatracalendar.feature.common.messageRes
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CalendarsState {
    /** Not loaded: the user has not connected in this session yet. */
    data object Idle : CalendarsState

    data object Loading : CalendarsState

    data class Loaded(val calendars: List<GoogleCalendarInfo>) : CalendarsState

    data class Error(@StringRes val message: Int) : CalendarsState
}

data class GoogleAccountUiState(val selected: GoogleCalendarInfo? = null, val calendars: CalendarsState = CalendarsState.Idle)

sealed interface GoogleAccountEvent {
    data class LaunchConsent(val request: ConsentRequest) : GoogleAccountEvent

    /** A calendar was chosen; screens opened to pick one can close. */
    data object CalendarChosen : GoogleAccountEvent

    data class Message(@StringRes val message: Int) : GoogleAccountEvent
}

/** Google account connection (screen 12) and calendar selection (screen 13). */
@HiltViewModel
class GoogleAccountViewModel @Inject constructor(
    private val auth: GoogleAuthRepository,
    private val gateway: CalendarGateway,
    private val preferences: CalendarPreferences
) : ViewModel() {
    private val calendars = MutableStateFlow<CalendarsState>(CalendarsState.Idle)
    private val events = Channel<GoogleAccountEvent>(Channel.BUFFERED)
    val eventFlow: Flow<GoogleAccountEvent> = events.receiveAsFlow()

    val uiState: StateFlow<GoogleAccountUiState> = combine(preferences.selectedCalendar, calendars) { selected, list ->
        GoogleAccountUiState(selected, list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoogleAccountUiState())

    /** Connect (or refresh): asks for consent if needed, then lists the writable calendars. */
    fun connect() {
        viewModelScope.launch {
            calendars.value = CalendarsState.Loading
            when (val result = auth.authorize()) {
                is AppResult.Failure -> calendars.value = CalendarsState.Error(result.error.messageRes())
                is AppResult.Success -> when (val state = result.value) {
                    is AuthorizationState.Authorized -> load(state.accessToken)
                    is AuthorizationState.NeedsConsent -> {
                        calendars.value = CalendarsState.Idle
                        events.send(GoogleAccountEvent.LaunchConsent(state.request))
                    }
                }
            }
        }
    }

    fun onConsentResult(data: Any?) {
        when (val token = auth.completeConsent(data)) {
            is AppResult.Failure -> calendars.value = CalendarsState.Error(token.error.messageRes())
            is AppResult.Success -> viewModelScope.launch { load(token.value) }
        }
    }

    fun choose(calendar: GoogleCalendarInfo) {
        viewModelScope.launch {
            preferences.select(calendar)
            events.send(GoogleAccountEvent.CalendarChosen)
        }
    }

    /** Revokes the app's Google access and forgets the chosen calendar. Local sync history is kept. */
    fun disconnect() {
        viewModelScope.launch {
            (auth.accessToken() as? AppResult.Success)?.let { token ->
                gateway.revoke(token.value)
                auth.invalidate(token.value)
            }
            preferences.clear()
            calendars.value = CalendarsState.Idle
            events.send(GoogleAccountEvent.Message(R.string.account_disconnected))
        }
    }

    private suspend fun load(token: String) {
        calendars.value = CalendarsState.Loading
        calendars.value = when (val result = gateway.writableCalendars(token)) {
            is AppResult.Failure -> CalendarsState.Error(result.error.messageRes())
            is AppResult.Success ->
                if (result.value.isEmpty()) CalendarsState.Error(R.string.account_no_calendars) else CalendarsState.Loaded(result.value)
        }
    }
}
