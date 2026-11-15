package io.github.anjalymsajeev.nakshatracalendar.feature.person

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.core.common.DefaultDispatcher
import io.github.anjalymsajeev.nakshatracalendar.domain.model.BirthDetails
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalculationLocation
import io.github.anjalymsajeev.nakshatracalendar.domain.model.EventColor
import io.github.anjalymsajeev.nakshatracalendar.domain.model.NakshatraName
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.model.PersonValidationError
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.NakshatraEngine
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PersonRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SettingsRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.CalculateBirthStarUseCase
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.SavePersonResult
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.SavePersonUseCase
import io.github.anjalymsajeev.nakshatracalendar.feature.common.messageRes
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The add/edit form. Immutable; every change produces a new state. */
data class PersonFormState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = true,
    val name: String = "",
    val nakshatra: NakshatraName? = null,
    val birthDate: LocalDate? = null,
    val birthTime: LocalTime? = null,
    val birthPlace: CalculationLocation? = null,
    val calculationLocation: CalculationLocation = CalculationLocation.Thiruvananthapuram,
    val eventColor: EventColor = EventColor.DEFAULT,
    val errors: Set<PersonValidationError> = emptySet(),
    val isSaving: Boolean = false,
    val isCalculating: Boolean = false
)

sealed interface PersonEditEvent {
    data object Saved : PersonEditEvent

    /** The calculated star is uncertain: let the user choose. [suggested] is pre-selected. */
    data class ChooseBirthStar(val candidates: List<NakshatraName>, val suggested: NakshatraName) : PersonEditEvent

    data class BirthStarCalculated(val star: NakshatraName) : PersonEditEvent

    data class Message(@StringRes val message: Int) : PersonEditEvent
}

@HiltViewModel
class PersonEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val persons: PersonRepository,
    private val settings: SettingsRepository,
    private val engine: NakshatraEngine,
    private val savePerson: SavePersonUseCase,
    private val calculateBirthStar: CalculateBirthStarUseCase,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) : ViewModel() {
    private val personId: Long = savedStateHandle.get<Long>(ARG_PERSON_ID) ?: NEW_PERSON
    private val _state = MutableStateFlow(PersonFormState(isEditing = personId != NEW_PERSON))
    val state: StateFlow<PersonFormState> = _state.asStateFlow()

    private val events = Channel<PersonEditEvent>(Channel.BUFFERED)
    val eventFlow: Flow<PersonEditEvent> = events.receiveAsFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val defaultLocation = settings.settings.first().calculationLocation
        if (personId == NEW_PERSON) {
            _state.update { it.copy(isLoading = false, calculationLocation = defaultLocation) }
            return
        }
        when (val result = persons.get(personId)) {
            is AppResult.Success -> {
                val p = result.value
                if (p == null) {
                    events.send(PersonEditEvent.Message(R.string.person_not_found))
                    _state.update { it.copy(isLoading = false, isEditing = false, calculationLocation = defaultLocation) }
                } else {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            name = p.name,
                            nakshatra = engine.allNakshatras().firstOrNull { star -> star.number == p.nakshatraNumber },
                            birthDate = p.birth?.date,
                            birthTime = p.birth?.time,
                            birthPlace = p.birth?.place,
                            calculationLocation = p.calculationLocation,
                            eventColor = p.eventColor
                        )
                    }
                }
            }
            is AppResult.Failure -> {
                events.send(PersonEditEvent.Message(result.error.messageRes()))
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onNameChanged(name: String) = _state.update { it.copy(name = name, errors = it.errors - NAME_ERRORS) }

    fun onNakshatraSelected(number: Int) = _state.update {
        it.copy(
            nakshatra = engine.allNakshatras().firstOrNull { star -> star.number == number },
            errors = it.errors - PersonValidationError.NakshatraRequired
        )
    }

    fun onBirthDateSelected(date: LocalDate?) = _state.update { it.copy(birthDate = date, errors = it.errors - BIRTH_ERRORS) }

    fun onBirthTimeSelected(time: LocalTime?) = _state.update { it.copy(birthTime = time, errors = it.errors - BIRTH_ERRORS) }

    fun onBirthPlaceSelected(place: CalculationLocation?) = _state.update { it.copy(birthPlace = place, errors = it.errors - BIRTH_ERRORS) }

    fun onCalculationLocationSelected(location: CalculationLocation) = _state.update { it.copy(calculationLocation = location) }

    fun onColorSelected(color: EventColor) = _state.update { it.copy(eventColor = color) }

    fun calculateStar() {
        val form = _state.value
        val date = form.birthDate
        if (date == null) {
            events.trySend(PersonEditEvent.Message(R.string.person_calculate_needs_date))
            return
        }
        _state.update { it.copy(isCalculating = true) }
        viewModelScope.launch {
            val birth = BirthDetails(date, form.birthTime, form.birthPlace)
            val result = withContext(dispatcher) { calculateBirthStar(birth, form.calculationLocation.zone) }
            _state.update { it.copy(isCalculating = false) }
            when (result) {
                is AppResult.Failure -> events.send(PersonEditEvent.Message(result.error.messageRes()))
                is AppResult.Success -> {
                    val r = result.value
                    if (r.uncertain) {
                        events.send(PersonEditEvent.ChooseBirthStar(r.candidates, r.star))
                    } else {
                        onNakshatraSelected(r.star.number)
                        events.send(PersonEditEvent.BirthStarCalculated(r.star))
                    }
                }
            }
        }
    }

    fun save() {
        val form = _state.value
        if (form.isSaving) return
        val person = form.toPerson()
        val errors = savePerson.validate(person) + birthDetailErrors(form)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = savePerson(person)) {
                is SavePersonResult.Saved -> events.send(PersonEditEvent.Saved)
                is SavePersonResult.Invalid -> _state.update { it.copy(errors = result.errors) }
                is SavePersonResult.Failed -> events.send(PersonEditEvent.Message(result.error.messageRes()))
            }
            _state.update { it.copy(isSaving = false) }
        }
    }

    private fun PersonFormState.toPerson() = Person(
        id = if (personId == NEW_PERSON) 0 else personId,
        name = name,
        nakshatraNumber = nakshatra?.number ?: 0,
        birth = birthDate?.let { BirthDetails(it, birthTime, birthPlace) },
        calculationLocation = calculationLocation,
        eventColor = eventColor
    )

    /** Birth time or place need a birth date (otherwise they would be silently dropped). */
    private fun birthDetailErrors(form: PersonFormState): Set<PersonValidationError> =
        if (form.birthDate == null && (form.birthTime != null || form.birthPlace != null)) {
            setOf(PersonValidationError.BirthPlaceWithoutDate)
        } else {
            emptySet()
        }

    companion object {
        const val ARG_PERSON_ID = "personId"
        const val NEW_PERSON = -1L
        private val NAME_ERRORS = setOf(PersonValidationError.NameRequired, PersonValidationError.NameTooLong)
        private val BIRTH_ERRORS = setOf(
            PersonValidationError.BirthDateInFuture,
            PersonValidationError.BirthDateTooEarly,
            PersonValidationError.BirthPlaceWithoutDate
        )
    }
}
