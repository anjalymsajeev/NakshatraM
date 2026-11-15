package io.github.anjalymsajeev.nakshatracalendar.feature.finddates

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.DateRange
import io.github.anjalymsajeev.nakshatracalendar.domain.model.DateRangeError
import io.github.anjalymsajeev.nakshatracalendar.domain.model.NakshatraOccurrenceResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.model.PersonOccurrences
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.MalayalamDateProvider
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PersonRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.FindNakshatraDatesUseCase
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.ValidateDateRangeUseCase
import io.github.anjalymsajeev.nakshatracalendar.feature.common.messageRes
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A selectable family member chip. */
data class MemberChipUi(val id: Long, val name: String, val selected: Boolean)

/** One row of the results list: a person header or an occurrence. */
sealed interface ResultRowUi {
    val key: String

    data class Header(
        val personId: Long,
        val name: String,
        val nakshatraMalayalam: String,
        val nakshatraEnglish: String,
        val count: Int,
        val colorArgb: Int
    ) : ResultRowUi {
        override val key = "h$personId"
    }

    data class Occurrence(
        override val key: String,
        /** Observance day, e.g. "Wed, 26 Aug 2026"; null when no day qualifies under the convention. */
        val observanceDate: String?,
        val malayalamDateMl: String?,
        val malayalamDateEn: String?,
        val starStart: String,
        val starEnd: String,
        val spansTwoDays: Boolean,
        /** Zone of the times when it differs from the device's, e.g. "Asia/Dubai". */
        val zoneLabel: String?
    ) : ResultRowUi
}

sealed interface ResultsState {
    data object Loading : ResultsState

    /** No family members yet. */
    data object NoMembers : ResultsState

    data object NoSelection : ResultsState

    data class InvalidRange(@StringRes val message: Int, val maxYears: Int = ValidateDateRangeUseCase.MAX_YEARS) : ResultsState

    data object Empty : ResultsState

    data class Content(val rows: List<ResultRowUi>, val total: Int) : ResultsState

    data class Error(@StringRes val message: Int) : ResultsState
}

data class FindDatesUiState(
    val members: List<MemberChipUi> = emptyList(),
    val start: LocalDate,
    val end: LocalDate,
    val supportedRange: ClosedRange<LocalDate>,
    val results: ResultsState = ResultsState.Loading
)

/**
 * Find dates (Feature 3 + 4): pick members and a range; occurrences are recalculated automatically whenever the
 * selection or range changes (the previous calculation is cancelled).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FindDatesViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    persons: PersonRepository,
    private val dates: MalayalamDateProvider,
    private val validateRange: ValidateDateRangeUseCase,
    private val findDates: FindNakshatraDatesUseCase,
    clock: Clock
) : ViewModel() {
    private val deviceZone: ZoneId = ZoneId.systemDefault()
    private val today = LocalDate.now(clock.withZone(deviceZone))
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

    private val range = MutableStateFlow(
        DateRange(
            savedState.get<Long>(KEY_START)?.let(LocalDate::ofEpochDay) ?: today,
            savedState.get<Long>(KEY_END)?.let(LocalDate::ofEpochDay) ?: today.plusYears(1).minusDays(1)
        )
    )

    /** null = not chosen yet: everyone (or the member passed in the navigation argument) is selected. */
    private val selection = MutableStateFlow(savedState.get<LongArray>(KEY_SELECTION)?.toSet())

    private val _uiState = MutableStateFlow(
        FindDatesUiState(start = range.value.start, end = range.value.endInclusive, supportedRange = dates.supportedRange)
    )
    val uiState: StateFlow<FindDatesUiState> = _uiState.asStateFlow()

    private val family = persons.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            combine(family, selection, range) { all, chosen, period -> Triple(all, chosen, period) }
                .distinctUntilChanged()
                .collectLatest { (all, chosen, period) ->
                    if (all == null) return@collectLatest
                    val selectedIds = chosen ?: defaultSelection(all)
                    _uiState.update { state ->
                        state.copy(
                            members = all.map { MemberChipUi(it.id, it.name, it.id in selectedIds) },
                            start = period.start,
                            end = period.endInclusive
                        )
                    }
                    _uiState.update { it.copy(results = ResultsState.Loading) }
                    _uiState.update { it.copy(results = calculate(all, selectedIds, period)) }
                }
        }
    }

    fun toggleMember(id: Long) {
        val current = selection.value ?: _uiState.value.members.filter { it.selected }.map { it.id }.toSet()
        val next = if (id in current) current - id else current + id
        selection.value = next
        savedState[KEY_SELECTION] = next.toLongArray()
    }

    fun setStart(date: LocalDate) = setRange(date, range.value.endInclusive)

    fun setEnd(date: LocalDate) = setRange(range.value.start, date)

    private fun setRange(start: LocalDate, end: LocalDate) {
        range.value = DateRange(start, end)
        savedState[KEY_START] = start.toEpochDay()
        savedState[KEY_END] = end.toEpochDay()
    }

    private fun defaultSelection(all: List<Person>): Set<Long> {
        val requested = savedState.get<Long>(ARG_PERSON_ID) ?: NO_PERSON
        return if (requested != NO_PERSON && all.any { it.id == requested }) setOf(requested) else all.map { it.id }.toSet()
    }

    private suspend fun calculate(all: List<Person>, selectedIds: Set<Long>, period: DateRange): ResultsState {
        if (all.isEmpty()) return ResultsState.NoMembers
        val selected = all.filter { it.id in selectedIds }
        if (selected.isEmpty()) return ResultsState.NoSelection
        validateRange(period)?.let { error ->
            return ResultsState.InvalidRange(
                when (error) {
                    DateRangeError.EndBeforeStart -> R.string.find_error_end_before_start
                    is DateRangeError.TooLong -> R.string.find_error_too_long
                    is DateRangeError.OutOfSupportedRange -> R.string.error_unsupported_date
                }
            )
        }
        return when (val result = findDates(selected, period)) {
            is AppResult.Failure -> ResultsState.Error(result.error.messageRes())
            is AppResult.Success -> {
                val total = result.value.sumOf { it.occurrences.size }
                if (total == 0) ResultsState.Empty else ResultsState.Content(result.value.flatMap { it.toRows() }, total)
            }
        }
    }

    private fun PersonOccurrences.toRows(): List<ResultRowUi> = buildList {
        add(
            ResultRowUi.Header(
                personId = person.id,
                name = person.name,
                nakshatraMalayalam = nakshatra.nameMalayalam,
                nakshatraEnglish = nakshatra.nameEnglish,
                count = occurrences.size,
                colorArgb = person.eventColor.argb.toInt()
            )
        )
        occurrences.forEach { add(it.toRow()) }
    }

    private fun NakshatraOccurrenceResult.toRow(): ResultRowUi.Occurrence {
        val zoneFormatter = DateTimeFormatter.ofPattern("EEE d MMM, ").withZone(zone)
        val clockFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone)
        return ResultRowUi.Occurrence(
            key = "${person.id}-${start.epochSecond}",
            observanceDate = observanceDate?.format(dateFormatter),
            malayalamDateMl = malayalam?.formattedMalayalam,
            malayalamDateEn = malayalam?.formattedEnglish,
            starStart = zoneFormatter.format(start) + clockFormatter.format(start),
            starEnd = zoneFormatter.format(end) + clockFormatter.format(end),
            spansTwoDays = spansTwoDays,
            zoneLabel = zone.id.takeIf { zone.rules != deviceZone.rules }
        )
    }

    companion object {
        /** Navigation argument: preselect this member (-1 = everyone). */
        const val ARG_PERSON_ID = "personId"
        const val NO_PERSON = -1L
        private const val KEY_START = "start"
        private const val KEY_END = "end"
        private const val KEY_SELECTION = "selection"
    }
}
