package io.github.anjalymsajeev.nakshatracalendar.feature

import androidx.lifecycle.SavedStateHandle
import io.github.anjalymsajeev.kollavarsham.core.KollavarshamCalendar
import io.github.anjalymsajeev.nakshatracalendar.domain.model.UserSettings
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PersonRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SettingsRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.ObserveCalendarDaysUseCase
import io.github.anjalymsajeev.nakshatracalendar.feature.calendar.CalendarMode
import io.github.anjalymsajeev.nakshatracalendar.feature.calendar.CalendarViewModel
import io.github.anjalymsajeev.nakshatracalendar.integration.malayalamcalendar.KollavarshamDateProvider
import io.github.anjalymsajeev.nakshatracalendar.integration.nakshatra.PanchangamNakshatraEngine
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val today = LocalDate.of(2026, 8, 26)
    private val clock = Clock.fixed(today.atStartOfDay(ZoneId.systemDefault()).plusHours(12).toInstant(), ZoneId.systemDefault())
    private val dates = KollavarshamDateProvider(KollavarshamCalendar.create())
    private val engine = PanchangamNakshatraEngine()
    private val persons = mockk<PersonRepository> { every { observeAll() } returns flowOf(emptyList()) }
    private val settings = mockk<SettingsRepository> { every { settings } returns flowOf(UserSettings()) }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(state: SavedStateHandle = SavedStateHandle()) =
        CalendarViewModel(state, dates, ObserveCalendarDaysUseCase(persons, settings, engine, dates), clock, dispatcher)

    @Test
    fun opensOnTodaysGregorianMonthWithSundayFirstBlanks() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        val state = vm.uiState.value
        assertEquals(YearMonth.of(2026, 8), state.period?.gregorianMonth)
        // 1 August 2026 is a Saturday: six blank cells before it.
        assertEquals(6, state.cells.takeWhile { it.date == null }.size)
        assertEquals(31, state.cells.count { it.date != null })
        val todayCell = state.cells.single { it.isToday }
        assertEquals("26", todayCell.primary)
        assertEquals("10", todayCell.secondary) // Chingam 10
        assertEquals("Thiruvonam", todayCell.nakshatraEn)
        // Subtitle months: August 2026 overlaps Karkidakam 1201 and Chingam 1202.
        assertEquals(listOf("Karkidakam", "Chingam"), state.period?.overlappedMalayalam?.map { it.nameEnglish })
    }

    @Test
    fun malayalamModeShowsTheMonthContainingTodayAndNavigates() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.setMode(CalendarMode.MALAYALAM)
        var period = vm.uiState.value.period!!
        assertEquals("Chingam", period.malayalamMonth?.nameEnglish)
        assertEquals(LocalDate.of(2026, 8, 17), period.first)
        assertEquals("1", vm.uiState.value.cells.first { it.date != null }.primary)

        vm.next()
        period = vm.uiState.value.period!!
        assertEquals("Kanni", period.malayalamMonth?.nameEnglish)
        assertEquals(period.first, LocalDate.of(2026, 9, 17))

        vm.previous()
        vm.previous()
        assertEquals("Karkidakam", vm.uiState.value.period?.malayalamMonth?.nameEnglish)
        assertEquals(1201, vm.uiState.value.period?.malayalamMonth?.kollavarshamYear)

        vm.goToToday()
        assertEquals("Chingam", vm.uiState.value.period?.malayalamMonth?.nameEnglish)
    }

    @Test
    fun modeAndMonthSurviveRecreationAndStopAtTheSupportedRange() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        backgroundScope.launch { first.uiState.collect {} }
        first.setMode(CalendarMode.MALAYALAM)
        first.next()

        val restored = viewModel(saved)
        backgroundScope.launch { restored.uiState.collect {} }
        assertEquals(CalendarMode.MALAYALAM, restored.uiState.value.period?.mode)
        assertEquals("Kanni", restored.uiState.value.period?.malayalamMonth?.nameEnglish)

        val edge = viewModel(SavedStateHandle(mapOf("anchor" to LocalDate.of(1900, 1, 15).toEpochDay())))
        backgroundScope.launch { edge.uiState.collect {} }
        assertFalse(edge.uiState.value.canGoBack)
        assertTrue(edge.uiState.value.canGoForward)
        assertNull(edge.uiState.value.error)
    }
}
