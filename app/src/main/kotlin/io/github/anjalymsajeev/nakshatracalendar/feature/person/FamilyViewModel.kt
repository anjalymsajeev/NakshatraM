package io.github.anjalymsajeev.nakshatracalendar.feature.person

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.core.common.DefaultDispatcher
import io.github.anjalymsajeev.nakshatracalendar.domain.model.FamilyMember
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.DeletePersonUseCase
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.ObserveFamilyUseCase
import io.github.anjalymsajeev.nakshatracalendar.feature.common.messageRes
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One card in the family list; both languages are provided and the screen picks one. */
data class FamilyItemUi(
    val id: Long,
    val name: String,
    val colorArgb: Int,
    val nakshatraMalayalam: String,
    val nakshatraEnglish: String,
    val nextGregorian: String?,
    val nextMalayalamMl: String?,
    val nextMalayalamEn: String?
)

sealed interface FamilyUiState {
    data object Loading : FamilyUiState

    data object Empty : FamilyUiState

    data class Content(val items: List<FamilyItemUi>) : FamilyUiState

    data class Error(@StringRes val message: Int) : FamilyUiState
}

sealed interface FamilyEvent {
    /** A person was deleted; offer to undo. */
    data class Deleted(val person: Person) : FamilyEvent

    data class Message(@StringRes val message: Int) : FamilyEvent
}

@HiltViewModel
class FamilyViewModel @Inject constructor(
    observeFamily: ObserveFamilyUseCase,
    private val deletePerson: DeletePersonUseCase,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : ViewModel() {
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
    private val events = Channel<FamilyEvent>(Channel.BUFFERED)
    val eventFlow: Flow<FamilyEvent> = events.receiveAsFlow()

    val uiState: StateFlow<FamilyUiState> =
        observeFamily()
            .map { members -> if (members.isEmpty()) FamilyUiState.Empty else FamilyUiState.Content(members.map { it.toUi() }) }
            .catch { emit(FamilyUiState.Error(R.string.error_database)) }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FamilyUiState.Loading)

    fun delete(id: Long) {
        viewModelScope.launch {
            when (val result = deletePerson(id)) {
                is AppResult.Success -> result.value?.let { events.send(FamilyEvent.Deleted(it)) }
                is AppResult.Failure -> events.send(FamilyEvent.Message(result.error.messageRes()))
            }
        }
    }

    fun undoDelete(person: Person) {
        viewModelScope.launch {
            val result = deletePerson.undo(person)
            if (result is AppResult.Failure) events.send(FamilyEvent.Message(result.error.messageRes()))
        }
    }

    private fun FamilyMember.toUi() = FamilyItemUi(
        id = person.id,
        name = person.name,
        colorArgb = person.eventColor.argb.toInt(),
        nakshatraMalayalam = nakshatra.nameMalayalam,
        nakshatraEnglish = nakshatra.nameEnglish,
        nextGregorian = next?.observanceDate?.format(dateFormatter),
        nextMalayalamMl = next?.malayalamDate?.formattedMalayalam,
        nextMalayalamEn = next?.malayalamDate?.formattedEnglish
    )
}
