package io.github.anjalymsajeev.nakshatracalendar.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppError
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.core.common.DefaultDispatcher
import io.github.anjalymsajeev.nakshatracalendar.domain.model.TodayOverview
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.GetTodayOverviewUseCase
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(getTodayOverview: GetTodayOverviewUseCase, @DefaultDispatcher dispatcher: CoroutineDispatcher) :
    ViewModel() {
    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
    private val timeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")

    val uiState: StateFlow<HomeUiState> =
        getTodayOverview()
            .map { result ->
                when (result) {
                    is AppResult.Success -> result.value.toContent()
                    is AppResult.Failure -> HomeUiState.Error(result.error.toMessage())
                }
            }
            .catch { emit(HomeUiState.Error(R.string.error_generic)) }
            .flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState.Loading)

    private fun TodayOverview.toContent(): HomeUiState.Content = HomeUiState.Content(
        gregorianDate = date.gregorianDate.format(dateFormatter),
        malayalamDateMalayalam = date.formattedMalayalam,
        malayalamDateEnglish = date.formattedEnglish,
        malayalamMonthMalayalam = date.monthNameMalayalam,
        malayalamMonthEnglish = date.monthNameEnglish,
        kollavarshamYear = date.kollavarshamYear,
        nakshatraMalayalam = nakshatraAtSunrise.nameMalayalam,
        nakshatraEnglish = nakshatraAtSunrise.nameEnglish,
        nakshatraUntil = nakshatraAtSunrise.end.atZone(zone).format(timeFormatter),
        familyMemberCount = familyMemberCount
    )

    private fun AppError.toMessage(): Int = when (this) {
        is AppError.UnsupportedDate -> R.string.error_unsupported_date
        is AppError.CalculationFailed -> R.string.error_calculation
        is AppError.Database -> R.string.error_database
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
