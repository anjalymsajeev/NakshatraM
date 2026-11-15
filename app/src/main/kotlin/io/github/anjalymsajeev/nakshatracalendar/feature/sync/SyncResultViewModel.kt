package io.github.anjalymsajeev.nakshatracalendar.feature.sync

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncRun
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncedEvent
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SyncRepository
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SyncResultUiState(val run: SyncRun? = null, val events: List<SyncedEvent> = emptyList(), val loading: Boolean = true)

/** Synchronization results (screen 15): live counts and per-date status of one run, with retry. */
@HiltViewModel
class SyncResultViewModel @Inject constructor(
    savedState: SavedStateHandle,
    sync: SyncRepository,
    private val authorizedRetry: AuthorizedRetry
) : ViewModel() {
    private val syncId: Long = savedState.get<Long>(ARG_SYNC_ID) ?: -1L
    private val events = Channel<RetryEvent>(Channel.BUFFERED)
    val eventFlow: Flow<RetryEvent> = events.receiveAsFlow()

    val uiState: StateFlow<SyncResultUiState> = combine(sync.observeRun(syncId), sync.observeEvents(syncId)) { run, events ->
        SyncResultUiState(run, events, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncResultUiState())

    /** Retry failed events, or reconnect and retry when authorization was lost. */
    fun retry() {
        viewModelScope.launch { authorizedRetry.retry(syncId)?.let { events.send(it) } }
    }

    fun onConsentResult(data: Any?) {
        viewModelScope.launch { authorizedRetry.onConsentResult(data)?.let { events.send(it) } }
    }

    companion object {
        const val ARG_SYNC_ID = "syncId"
    }
}
