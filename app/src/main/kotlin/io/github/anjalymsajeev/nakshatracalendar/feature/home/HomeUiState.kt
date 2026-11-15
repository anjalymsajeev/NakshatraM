package io.github.anjalymsajeev.nakshatracalendar.feature.home

import androidx.annotation.StringRes

/** Immutable, UI-toolkit-independent state of the home dashboard (reusable by a future Compose screen). */
sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Content(
        val gregorianDate: String,
        val malayalamDateMalayalam: String,
        val malayalamDateEnglish: String,
        val malayalamMonthMalayalam: String,
        val malayalamMonthEnglish: String,
        val kollavarshamYear: Int,
        val nakshatraMalayalam: String,
        val nakshatraEnglish: String,
        val nakshatraUntil: String,
        val familyMemberCount: Int,
        val upcoming: List<UpcomingUi> = emptyList()
    ) : HomeUiState

    data class Error(@StringRes val message: Int) : HomeUiState
}

/** One upcoming Pirannal row on the dashboard. */
data class UpcomingUi(
    val personId: Long,
    val name: String,
    val colorArgb: Int,
    val nakshatraMalayalam: String,
    val nakshatraEnglish: String,
    val gregorianDate: String,
    val malayalamDateMl: String,
    val malayalamDateEn: String
)
