package io.github.anjalymsajeev.nakshatracalendar.domain.usecase

import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.TodayOverview
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.MalayalamDateProvider
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.NakshatraEngine
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PersonRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SettingsRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Today's date in both calendars, today's nakshatra and the number of family members, kept up to date. */
class GetTodayOverviewUseCase @Inject constructor(
    private val dates: MalayalamDateProvider,
    private val nakshatras: NakshatraEngine,
    private val settings: SettingsRepository,
    private val persons: PersonRepository,
    private val clock: Clock
) {
    operator fun invoke(): Flow<AppResult<TodayOverview>> = combine(settings.settings, persons.observeCount()) { userSettings, count ->
        val location = userSettings.calculationLocation
        val today = LocalDate.now(clock.withZone(location.zone))
        when (val date = dates.malayalamDate(today)) {
            is AppResult.Failure -> date
            is AppResult.Success -> when (val star = nakshatras.nakshatraAtSunrise(today, location)) {
                is AppResult.Failure -> star
                is AppResult.Success -> AppResult.Success(TodayOverview(date.value, star.value, count, location.zone))
            }
        }
    }
}
