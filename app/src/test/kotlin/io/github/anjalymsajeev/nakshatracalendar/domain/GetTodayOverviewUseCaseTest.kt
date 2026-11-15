package io.github.anjalymsajeev.nakshatracalendar.domain

import app.cash.turbine.test
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppError
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.MalayalamDateInfo
import io.github.anjalymsajeev.nakshatracalendar.domain.model.NakshatraInfo
import io.github.anjalymsajeev.nakshatracalendar.domain.model.UserSettings
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.MalayalamDateProvider
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.NakshatraEngine
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PersonRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SettingsRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.GetTodayOverviewUseCase
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.GetUpcomingPirannalsUseCase
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetTodayOverviewUseCaseTest {
    // 2026-11-14 00:30 IST is still 2026-11-13 in UTC: "today" must follow the location's zone.
    private val clock = Clock.fixed(Instant.parse("2026-11-13T19:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 14)
    private val dateInfo = MalayalamDateInfo(today, 1202, 3, 28, "തുലാം", "Thulam", "ml", "en")
    private val star = NakshatraInfo(14, "ചിത്തിര", "Chithira", Instant.EPOCH, Instant.EPOCH.plusSeconds(1))

    private val dates = mockk<MalayalamDateProvider>()
    private val nakshatras = mockk<NakshatraEngine>()
    private val settings = mockk<SettingsRepository> { every { settings } returns MutableStateFlow(UserSettings()) }
    private val count = MutableStateFlow(0)
    private val persons = mockk<PersonRepository> { every { observeCount() } returns count }
    private val upcoming = mockk<GetUpcomingPirannalsUseCase> { every { this@mockk.invoke(any()) } returns flowOf(emptyList()) }
    private val useCase = GetTodayOverviewUseCase(dates, nakshatras, settings, persons, upcoming, clock)

    @Test
    fun combinesDateNakshatraAndCountUsingTheLocationsZone() = runTest {
        every { dates.malayalamDate(today) } returns AppResult.Success(dateInfo)
        every { nakshatras.nakshatraAtSunrise(today, any()) } returns AppResult.Success(star)
        useCase().test {
            val first = (awaitItem() as AppResult.Success).value
            assertEquals(dateInfo, first.date)
            assertEquals(star, first.nakshatraAtSunrise)
            assertEquals(0, first.familyMemberCount)
            count.value = 3
            assertEquals(3, (awaitItem() as AppResult.Success).value.familyMemberCount)
        }
    }

    @Test
    fun propagatesFailures() = runTest {
        every { dates.malayalamDate(any()) } returns AppResult.Failure(AppError.UnsupportedDate("x"))
        useCase().test {
            val item = awaitItem()
            assertTrue(item is AppResult.Failure && item.error is AppError.UnsupportedDate)
        }
    }
}
