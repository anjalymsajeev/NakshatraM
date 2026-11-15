package io.github.anjalymsajeev.nakshatracalendar.feature.person

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.PlaceCandidate
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.SearchPlacesUseCase
import io.github.anjalymsajeev.nakshatracalendar.feature.common.messageRes
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

sealed interface PlaceSearchUiState {
    data object Idle : PlaceSearchUiState

    data object Loading : PlaceSearchUiState

    data class Results(val places: List<PlaceCandidate>) : PlaceSearchUiState

    data object NoResults : PlaceSearchUiState

    /** Search failed; [offerManualEntry] is always true so the user is never stuck. */
    data class Error(@StringRes val message: Int, val offerManualEntry: Boolean = true) : PlaceSearchUiState
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaceSearchViewModel @Inject constructor(private val searchPlaces: SearchPlacesUseCase) : ViewModel() {
    private val query = MutableStateFlow("")

    val uiState: StateFlow<PlaceSearchUiState> =
        query
            .debounce(DEBOUNCE_MS)
            .distinctUntilChanged()
            .flatMapLatest { q ->
                flow {
                    if (q.trim().length < SearchPlacesUseCase.MIN_QUERY_LENGTH) {
                        emit(PlaceSearchUiState.Idle)
                        return@flow
                    }
                    emit(PlaceSearchUiState.Loading)
                    emit(
                        when (val result = searchPlaces(q)) {
                            is AppResult.Success ->
                                if (result.value.isEmpty()) PlaceSearchUiState.NoResults else PlaceSearchUiState.Results(result.value)
                            is AppResult.Failure -> PlaceSearchUiState.Error(result.error.messageRes())
                        }
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaceSearchUiState.Idle)

    fun onQueryChanged(text: String) {
        query.value = text
    }

    private companion object {
        const val DEBOUNCE_MS = 400L
    }
}
