package io.github.anjalymsajeev.nakshatracalendar.feature.calendar

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.core.common.DefaultDispatcher
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalendarDay
import io.github.anjalymsajeev.nakshatracalendar.domain.model.MalayalamMonthSpan
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncState
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.MalayalamDateProvider
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.ObserveCalendarDaysUseCase
import io.github.anjalymsajeev.nakshatracalendar.feature.common.messageRes
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

enum class CalendarMode { GREGORIAN, MALAYALAM }

/** Text for a day's detail sheet, in both languages where the content differs. */
data class DayDetailsUi(
    val date: LocalDate,
    val malayalamMl: String,
    val malayalamEn: String,
    val nakshatraMl: String?,
    val nakshatraEn: String?,
    /** When the sunrise nakshatra ends, in the calculation zone. */
    val nakshatraEnd: java.time.ZonedDateTime?,
    /** "Name · Nakshatra" for each member observing their birth star that day. */
    val familyMl: List<String>,
    val familyEn: List<String>
)

/** One grid cell; [date] null for the blank cells before the 1st. */
data class CalendarCellUi(
    val key: String,
    val date: LocalDate?,
    val primary: String = "",
    val secondary: String = "",
    /** Show the Gregorian month next to [secondary] (Malayalam view, on the 1st of a Gregorian month and the first cell). */
    val secondaryMonth: Int? = null,
    val nakshatraMl: String? = null,
    val nakshatraEn: String? = null,
    val familyColors: List<Int> = emptyList(),
    val isToday: Boolean = false,
    val synced: Boolean = false,
    val details: DayDetailsUi? = null
)

/** The period shown: a Gregorian month, or a Malayalam month with the Gregorian days it covers. */
data class CalendarPeriod(
    val mode: CalendarMode,
    val first: LocalDate,
    val last: LocalDate,
    val gregorianMonth: YearMonth?,
    val malayalamMonth: MalayalamMonthSpan?,
    /** Malayalam months the Gregorian month overlaps (first and last), for the subtitle. */
    val overlappedMalayalam: List<MalayalamMonthSpan> = emptyList()
)

data class CalendarUiState(
    val period: CalendarPeriod? = null,
    val cells: List<CalendarCellUi> = emptyList(),
    val loading: Boolean = true,
    @StringRes val error: Int? = null,
    val canGoBack: Boolean = true,
    val canGoForward: Boolean = true
)

/**
 * Calendar tab: Gregorian or Malayalam month grid with nakshatra at sunrise and family birth-star days.
 * Mode and the displayed month survive process death via [SavedStateHandle].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val dates: MalayalamDateProvider,
    private val observeDays: ObserveCalendarDaysUseCase,
    clock: Clock,
    @DefaultDispatcher dispatcher: CoroutineDispatcher
) : ViewModel() {
    private val today = LocalDate.now(clock.withZone(ZoneId.systemDefault()))
    private val mode = MutableStateFlow(savedState.get<String>(KEY_MODE)?.let(CalendarMode::valueOf) ?: CalendarMode.GREGORIAN)
    private val anchor = MutableStateFlow(savedState.get<Long>(KEY_ANCHOR)?.let(LocalDate::ofEpochDay) ?: today)

    val uiState: StateFlow<CalendarUiState> = combine(mode, anchor) { m, a -> m to a }
        .flatMapLatest { (m, a) ->
            when (val period = periodFor(m, a)) {
                is AppResult.Failure -> flowOf(CalendarUiState(loading = false, error = period.error.messageRes()))
                is AppResult.Success -> observeDays(period.value.first, period.value.last).map { days ->
                    when (days) {
                        is AppResult.Failure -> CalendarUiState(period.value, loading = false, error = days.error.messageRes())
                        is AppResult.Success -> CalendarUiState(
                            period = period.value,
                            cells = cells(period.value, days.value),
                            loading = false,
                            canGoBack = period.value.first.minusDays(1) in dates.supportedRange,
                            canGoForward = period.value.last.plusDays(1) in dates.supportedRange
                        )
                    }
                }.onStart { emit(CalendarUiState(period.value, loading = true)) }
            }
        }
        .flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun setMode(newMode: CalendarMode) {
        if (newMode == mode.value) return
        // Keep the user near the same days: today if it is on screen, otherwise the middle of the period.
        val period = uiState.value.period
        val focus = when {
            period == null -> anchor.value
            today >= period.first && today <= period.last -> today
            else -> period.first.plusDays((period.last.toEpochDay() - period.first.toEpochDay()) / 2)
        }
        mode.value = newMode
        savedState[KEY_MODE] = newMode.name
        moveTo(focus)
    }

    fun next() {
        uiState.value.period?.let { if (uiState.value.canGoForward) moveTo(it.last.plusDays(1)) }
    }

    fun previous() {
        uiState.value.period?.let { if (uiState.value.canGoBack) moveTo(it.first.minusDays(1)) }
    }

    fun goToToday() = moveTo(today)

    private fun moveTo(date: LocalDate) {
        anchor.value = date
        savedState[KEY_ANCHOR] = date.toEpochDay()
    }

    private fun periodFor(mode: CalendarMode, anchor: LocalDate): AppResult<CalendarPeriod> = when (mode) {
        CalendarMode.GREGORIAN -> {
            val month = YearMonth.from(anchor)
            val first = month.atDay(1)
            val last = month.atEndOfMonth()
            val overlapped = listOf(first, last).mapNotNull { (dates.monthContaining(it) as? AppResult.Success)?.value }.distinct()
            AppResult.Success(CalendarPeriod(mode, first, last, month, null, overlapped))
        }
        CalendarMode.MALAYALAM -> when (val span = dates.monthContaining(anchor)) {
            is AppResult.Failure -> span
            is AppResult.Success -> AppResult.Success(CalendarPeriod(mode, span.value.firstDay, span.value.lastDay, null, span.value))
        }
    }

    private fun cells(period: CalendarPeriod, days: List<CalendarDay>): List<CalendarCellUi> {
        // Weeks start on Sunday, as in Kerala almanacs and the design.
        val leading = period.first.dayOfWeek.value % 7
        val blanks = (0 until leading).map { CalendarCellUi(key = "blank$it", date = null) }
        return blanks + days.mapIndexed { index, day -> day.toCell(period.mode, showMonth = index == 0) }
    }

    private fun CalendarDay.toCell(mode: CalendarMode, showMonth: Boolean): CalendarCellUi {
        val gregorianDay = date.dayOfMonth.toString()
        val malayalamDay = malayalam.day.toString()
        return CalendarCellUi(
            key = date.toString(),
            date = date,
            primary = if (mode == CalendarMode.GREGORIAN) gregorianDay else malayalamDay,
            secondary = if (mode == CalendarMode.GREGORIAN) malayalamDay else gregorianDay,
            secondaryMonth = if (mode == CalendarMode.MALAYALAM && (showMonth || date.dayOfMonth == 1)) date.monthValue else null,
            nakshatraMl = nakshatraAtSunrise?.nameMalayalam,
            nakshatraEn = nakshatraAtSunrise?.nameEnglish,
            familyColors = family.map { it.color.argb.toInt() },
            isToday = date == today,
            synced = syncState != SyncState.NONE,
            details = DayDetailsUi(
                date = date,
                malayalamMl = malayalam.formattedMalayalam,
                malayalamEn = malayalam.formattedEnglish,
                nakshatraMl = nakshatraAtSunrise?.nameMalayalam,
                nakshatraEn = nakshatraAtSunrise?.nameEnglish,
                nakshatraEnd = nakshatraAtSunrise?.end?.atZone(zone),
                familyMl = family.map { "${it.name} · ${it.nakshatra.nameMalayalam}" },
                familyEn = family.map { "${it.name} · ${it.nakshatra.nameEnglish}" }
            )
        )
    }

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_ANCHOR = "anchor"
    }
}
