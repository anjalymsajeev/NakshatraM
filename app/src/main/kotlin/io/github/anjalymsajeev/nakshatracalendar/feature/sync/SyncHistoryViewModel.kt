package io.github.anjalymsajeev.nakshatracalendar.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncRun
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SyncRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.RemoveSyncedEventsUseCase
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** null while loading. */
data class SyncHistoryUiState(val runs: List<SyncRun>? = null)

/** Synchronization history (screen 16, Feature 8). */
@HiltViewModel
class SyncHistoryViewModel @Inject constructor(
    sync: SyncRepository,
    private val authorizedRetry: AuthorizedRetry,
    private val removeEvents: RemoveSyncedEventsUseCase
) : ViewModel() {
    private val events = Channel<RetryEvent>(Channel.BUFFERED)
    val eventFlow: Flow<RetryEvent> = events.receiveAsFlow()

    val uiState: StateFlow<SyncHistoryUiState> = sync.observeRuns().map { SyncHistoryUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncHistoryUiState())

    /** Retry failed events, or reconnect and retry when authorization was lost. */
    fun retry(syncId: Long) {
        viewModelScope.launch { authorizedRetry.retry(syncId)?.let { events.send(it) } }
    }

    fun onConsentResult(data: Any?) {
        viewModelScope.launch { authorizedRetry.onConsentResult(data)?.let { events.send(it) } }
    }

    fun remove(syncId: Long) = removeEvents(syncId)
}
