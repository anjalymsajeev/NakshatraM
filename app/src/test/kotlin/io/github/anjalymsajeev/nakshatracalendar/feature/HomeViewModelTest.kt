package io.github.anjalymsajeev.nakshatracalendar.feature

import app.cash.turbine.test
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppError
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.MalayalamDateInfo
import io.github.anjalymsajeev.nakshatracalendar.domain.model.NakshatraInfo
import io.github.anjalymsajeev.nakshatracalendar.domain.model.TodayOverview
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.GetTodayOverviewUseCase
import io.github.anjalymsajeev.nakshatracalendar.feature.home.HomeUiState
import io.github.anjalymsajeev.nakshatracalendar.feature.home.HomeViewModel
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private val overview = TodayOverview(
        date = MalayalamDateInfo(
            LocalDate.of(2026, 11, 14),
            1202,
            3,
            28,
            "തുലാം",
            "Thulam",
            "കൊല്ലവർഷം 1202 തുലാം 28",
            "28 Thulam 1202 ME"
        ),
        nakshatraAtSunrise = NakshatraInfo(
            14,
            "ചിത്തിര",
            "Chithira",
            Instant.parse("2026-11-14T01:00:00Z"),
            Instant.parse("2026-11-15T03:42:00Z")
        ),
        familyMemberCount = 2,
        zone = ZoneId.of("Asia/Kolkata")
    )

    @Test
    fun mapsOverviewToContent() = runTest {
        val useCase = mockk<GetTodayOverviewUseCase> { every { this@mockk.invoke() } returns flowOf(AppResult.Success(overview)) }
        val vm = HomeViewModel(useCase, dispatcher)
        vm.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            assertEquals("കൊല്ലവർഷം 1202 തുലാം 28", content.malayalamDateMalayalam)
            assertEquals("28 Thulam 1202 ME", content.malayalamDateEnglish)
            assertEquals(1202, content.kollavarshamYear)
            assertEquals("Chithira", content.nakshatraEnglish)
            assertEquals("15 Nov, 09:12", content.nakshatraUntil) // 03:42 UTC shown in IST
            assertEquals(2, content.familyMemberCount)
        }
    }

    @Test
    fun mapsFailuresToMessages() = runTest {
        val useCase = mockk<GetTodayOverviewUseCase> {
            every { this@mockk.invoke() } returns flowOf(AppResult.Failure(AppError.UnsupportedDate("x")))
        }
        val vm = HomeViewModel(useCase, dispatcher)
        vm.uiState.test { assertEquals(HomeUiState.Error(R.string.error_unsupported_date), awaitItem()) }
    }
}
