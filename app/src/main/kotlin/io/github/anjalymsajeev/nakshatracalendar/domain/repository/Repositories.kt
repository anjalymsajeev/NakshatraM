package io.github.anjalymsajeev.nakshatracalendar.domain.repository

import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalculationLocation
import io.github.anjalymsajeev.nakshatracalendar.domain.model.MalayalamDateInfo
import io.github.anjalymsajeev.nakshatracalendar.domain.model.NakshatraInfo
import io.github.anjalymsajeev.nakshatracalendar.domain.model.StarDayConvention
import io.github.anjalymsajeev.nakshatracalendar.domain.model.ThemeMode
import io.github.anjalymsajeev.nakshatracalendar.domain.model.UserSettings
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Malayalam date conversion. Implemented by the Kollavarsham library adapter; no calendar logic in the app. */
interface MalayalamDateProvider {
    fun malayalamDate(date: LocalDate): AppResult<MalayalamDateInfo>
}

/**
 * Nakshatra (astronomical) calculations, kept separate from date conversion. Implemented by the
 * library's panchangam module, which is validated against independent ephemerides.
 */
interface NakshatraEngine {
    fun nakshatraAtSunrise(date: LocalDate, location: CalculationLocation): AppResult<NakshatraInfo>
}

interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setStarDayConvention(convention: StarDayConvention)
}

/** Family member profiles. Sprint 1 exposes the count; full CRUD arrives in Sprint 2. */
interface PersonRepository {
    fun observeCount(): Flow<Int>
}
