package io.github.anjalymsajeev.nakshatracalendar.feature.sync

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppError
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.DateRange
import io.github.anjalymsajeev.nakshatracalendar.domain.model.GoogleCalendarInfo
import io.github.anjalymsajeev.nakshatracalendar.domain.model.NakshatraOccurrenceResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncMode
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.AuthorizationState
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.CalendarPreferences
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.ConsentRequest
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.GoogleAuthRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PersonRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SyncRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.FindNakshatraDatesUseCase
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.StartSyncUseCase
import io.github.anjalymsajeev.nakshatracalendar.feature.common.messageRes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One date in the preview. */
data class PreviewRowUi(
    val date: LocalDate?,
    val gregorian: String,
    val malayalamMl: String?,
    val malayalamEn: String?,
    val starTimes: String,
    val selected: Boolean,
    /** Already in the selected calendar: re-syncing updates it, never duplicates it. */
    val alreadySynced: Boolean,
    /** No observance day under the convention, so no all-day event can be made. */
    val selectable: Boolean
)

sealed interface PreviewUiState {
    data object Loading : PreviewUiState

    data class Error(@StringRes val message: Int) : PreviewUiState

    data class Content(
        val personName: String,
        val nakshatraMl: String,
        val nakshatraEn: String,
        val calendarName: String?,
        val rows: List<PreviewRowUi>,
        val selectedCount: Int,
        val eligibleCount: Int,
        val busy: Boolean = false
    ) : PreviewUiState
}

sealed interface PreviewEvent {
    /** No calendar chosen yet: open the Google account screen to connect and pick one. */
    data object ChooseCalendar : PreviewEvent

    data class Confirm(val mode: SyncMode, val count: Int, val calendarName: String) : PreviewEvent

    data class LaunchConsent(val request: ConsentRequest) : PreviewEvent

    data class Started(val syncId: Long) : PreviewEvent

    data class Message(@StringRes val message: Int) : PreviewEvent
}

/**
 * Event preview (Feature 6 A + B): the member's dates in the range with checkboxes. "Create all" is the automatic
 * mode, "Create selected" the manual one. Both go through an explicit confirmation and Google consent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EventPreviewViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val persons: PersonRepository,
    private val findDates: FindNakshatraDatesUseCase,
    private val calendars: CalendarPreferences,
    private val sync: SyncRepository,
    private val auth: GoogleAuthRepository,
    private val startSync: StartSyncUseCase
) : ViewModel() {
    private val personId: Long = savedState.get<Long>(ARG_PERSON_ID) ?: -1L
    private val range = DateRange(
        LocalDate.ofEpochDay(savedState.get<Long>(ARG_START) ?: 0L),
        LocalDate.ofEpochDay(savedState.get<Long>(ARG_END) ?: 0L)
    )
    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
    private val timeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")

    private val events = Channel<PreviewEvent>(Channel.BUFFERED)
    val eventFlow: Flow<PreviewEvent> = events.receiveAsFlow()

    private var person: Person? = null
    private val occurrences = MutableStateFlow<AppResult<List<NakshatraOccurrenceResult>>?>(null)

    /** null = untouched: every date not yet in the calendar is selected. */
    private val selection = MutableStateFlow(savedState.get<LongArray>(KEY_SELECTION)?.map(LocalDate::ofEpochDay)?.toSet())
    private val busy = MutableStateFlow(false)
    private var pendingMode: SyncMode? = null

    private val synced: Flow<Set<LocalDate>> = calendars.selectedCalendar.flatMapLatest { calendar ->
        if (calendar == null) flowOf(emptySet()) else sync.observeSyncedDates(personId, calendar.id)
    }

    val uiState: StateFlow<PreviewUiState> =
        combine(occurrences, calendars.selectedCalendar, synced, selection, busy) { found, calendar, inCalendar, chosen, working ->
            when (found) {
                null -> PreviewUiState.Loading
                is AppResult.Failure -> PreviewUiState.Error(found.error.messageRes())
                is AppResult.Success -> content(found.value, calendar, inCalendar, chosen, working)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreviewUiState.Loading)

    init {
        viewModelScope.launch {
            val loaded = (persons.get(personId) as? AppResult.Success)?.value
            person = loaded
            occurrences.value = if (loaded == null) {
                AppResult.Failure(AppError.Database(IllegalStateException("Member not found")))
            } else {
                when (val result = findDates(loaded, range)) {
                    is AppResult.Success -> AppResult.Success(result.value.occurrences)
                    is AppResult.Failure -> result
                }
            }
        }
    }

    fun toggle(date: LocalDate) = updateSelection { if (date in it) it - date else it + date }

    fun selectAll() = updateSelection { eligibleDates().toSet() }

    fun deselectAll() = updateSelection { emptySet() }

    /** Automatic mode: every date. Manual mode: the checked dates. */
    fun requestCreate(mode: SyncMode) {
        val state = uiState.value as? PreviewUiState.Content ?: return
        val count = if (mode == SyncMode.AUTOMATIC) state.eligibleCount else state.selectedCount
        if (count == 0) {
            events.trySend(PreviewEvent.Message(R.string.preview_nothing_selected))
            return
        }
        val calendarName = state.calendarName ?: run {
            events.trySend(PreviewEvent.ChooseCalendar)
            return
        }
        events.trySend(PreviewEvent.Confirm(mode, count, calendarName))
    }

    /** The user confirmed: make sure Google access is granted (consent in the foreground), then start. */
    fun confirm(mode: SyncMode) {
        pendingMode = mode
        viewModelScope.launch {
            busy.value = true
            when (val result = auth.authorize()) {
                is AppResult.Failure -> fail(result.error.messageRes())
                is AppResult.Success -> when (val state = result.value) {
                    is AuthorizationState.Authorized -> start(mode)
                    is AuthorizationState.NeedsConsent -> {
                        busy.value = false
                        events.send(PreviewEvent.LaunchConsent(state.request))
                    }
                }
            }
        }
    }

    fun onConsentResult(data: Any?) {
        val mode = pendingMode ?: return
        when (val token = auth.completeConsent(data)) {
            is AppResult.Failure -> events.trySend(PreviewEvent.Message(token.error.messageRes()))
            is AppResult.Success -> viewModelScope.launch {
                busy.value = true
                start(mode)
            }
        }
    }

    private suspend fun start(mode: SyncMode) {
        val member = person ?: return fail(R.string.error_database)
        val calendar: GoogleCalendarInfo = calendars.selectedCalendar.first() ?: return fail(R.string.preview_choose_calendar)
        val all = (occurrences.value as? AppResult.Success)?.value.orEmpty().filter { it.observanceDate != null }
        val chosen = if (mode == SyncMode.AUTOMATIC) all else all.filter { it.observanceDate in currentSelection() }
        when (val result = startSync(member, range, calendar, mode, chosen)) {
            is AppResult.Success -> events.send(PreviewEvent.Started(result.value))
            is AppResult.Failure -> events.send(PreviewEvent.Message(result.error.messageRes()))
        }
        busy.value = false
    }

    private suspend fun fail(@StringRes message: Int) {
        busy.value = false
        events.send(PreviewEvent.Message(message))
    }

    private fun eligibleDates(): List<LocalDate> =
        (occurrences.value as? AppResult.Success)?.value.orEmpty().mapNotNull { it.observanceDate }

    private fun currentSelection(): Set<LocalDate> =
        (uiState.value as? PreviewUiState.Content)?.rows?.filter { it.selected }?.mapNotNull { it.date }?.toSet().orEmpty()

    private fun updateSelection(change: (Set<LocalDate>) -> Set<LocalDate>) {
        val next = change(currentSelection())
        selection.value = next
        savedState[KEY_SELECTION] = next.map { it.toEpochDay() }.toLongArray()
    }

    private fun content(
        found: List<NakshatraOccurrenceResult>,
        calendar: GoogleCalendarInfo?,
        inCalendar: Set<LocalDate>,
        chosen: Set<LocalDate>?,
        working: Boolean
    ): PreviewUiState.Content {
        val member = person
        val selected = chosen ?: found.mapNotNull { it.observanceDate }.filter { it !in inCalendar }.toSet()
        val rows = found.map { occurrence ->
            val date = occurrence.observanceDate
            PreviewRowUi(
                date = date,
                gregorian = date?.format(dateFormatter) ?: occurrence.startDate.format(dateFormatter),
                malayalamMl = occurrence.malayalam?.formattedMalayalam,
                malayalamEn = occurrence.malayalam?.formattedEnglish,
                starTimes = "${occurrence.start.atZone(
                    occurrence.zone
                ).format(timeFormatter)} – ${occurrence.end.atZone(occurrence.zone).format(timeFormatter)}",
                selected = date != null && date in selected,
                alreadySynced = date != null && date in inCalendar,
                selectable = date != null
            )
        }
        return PreviewUiState.Content(
            personName = member?.name.orEmpty(),
            nakshatraMl = found.firstOrNull()?.nakshatra?.nameMalayalam.orEmpty(),
            nakshatraEn = found.firstOrNull()?.nakshatra?.nameEnglish.orEmpty(),
            calendarName = calendar?.name,
            rows = rows,
            selectedCount = rows.count { it.selected },
            eligibleCount = rows.count { it.selectable },
            busy = working
        )
    }

    companion object {
        const val ARG_PERSON_ID = "personId"
        const val ARG_START = "startEpochDay"
        const val ARG_END = "endEpochDay"
        private const val KEY_SELECTION = "selection"
    }
}
