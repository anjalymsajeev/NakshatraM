package io.github.anjalymsajeev.nakshatracalendar.feature

import androidx.lifecycle.SavedStateHandle
import io.github.anjalymsajeev.kollavarsham.core.KollavarshamCalendar
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalculationLocation
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.model.UserSettings
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PersonRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SettingsRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.FindNakshatraDatesUseCase
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.ValidateDateRangeUseCase
import io.github.anjalymsajeev.nakshatracalendar.feature.finddates.FindDatesViewModel
import io.github.anjalymsajeev.nakshatracalendar.feature.finddates.ResultRowUi
import io.github.anjalymsajeev.nakshatracalendar.feature.finddates.ResultsState
import io.github.anjalymsajeev.nakshatracalendar.integration.malayalamcalendar.KollavarshamDateProvider
import io.github.anjalymsajeev.nakshatracalendar.integration.nakshatra.PanchangamNakshatraEngine
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FindDatesViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val today = LocalDate.of(2026, 8, 1)
    private val clock = Clock.fixed(today.atStartOfDay(ZoneId.systemDefault()).plusHours(12).toInstant(), ZoneId.systemDefault())
    private val tvm = CalculationLocation.Thiruvananthapuram
    private val family = MutableStateFlow(
        listOf(Person(1, "Anvidh", 22, calculationLocation = tvm), Person(2, "Meera", 13, calculationLocation = tvm))
    )
    private val persons = mockk<PersonRepository> { every { observeAll() } returns family }
    private val dates = KollavarshamDateProvider(KollavarshamCalendar.create())
    private val settings = mockk<SettingsRepository> { every { settings } returns flowOf(UserSettings()) }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(personId: Long = FindDatesViewModel.NO_PERSON) = FindDatesViewModel(
        SavedStateHandle(mapOf(FindDatesViewModel.ARG_PERSON_ID to personId)),
        persons,
        dates,
        ValidateDateRangeUseCase(dates),
        FindNakshatraDatesUseCase(PanchangamNakshatraEngine(), dates, settings, dispatcher),
        clock
    )

    @Test
    fun defaultsToEveryoneForOneYearAndGroupsResultsByPerson() = runTest(dispatcher) {
        val state = viewModel().uiState.value
        assertEquals(today, state.start)
        assertEquals(today.plusYears(1).minusDays(1), state.end)
        assertTrue(state.members.all { it.selected })
        val content = state.results as ResultsState.Content
        val headers = content.rows.filterIsInstance<ResultRowUi.Header>()
        assertEquals(listOf("Anvidh", "Meera"), headers.map { it.name })
        assertEquals(content.total, content.rows.count { it is ResultRowUi.Occurrence })
        assertTrue(content.rows.filterIsInstance<ResultRowUi.Occurrence>().any { it.observanceDate == "Wed, 26 Aug 2026" })
    }

    @Test
    fun navigationArgumentPreselectsOneMember() = runTest(dispatcher) {
        val state = viewModel(personId = 2).uiState.value
        assertEquals(listOf(false, true), state.members.map { it.selected })
        val headers = (state.results as ResultsState.Content).rows.filterIsInstance<ResultRowUi.Header>()
        assertEquals(listOf("Meera"), headers.map { it.name })
    }

    @Test
    fun invalidRangesAndEmptySelectionAreExplained() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setEnd(today.minusDays(1))
        assertEquals(R.string.find_error_end_before_start, (vm.uiState.value.results as ResultsState.InvalidRange).message)
        vm.setEnd(today.plusYears(5).plusDays(1))
        assertEquals(R.string.find_error_too_long, (vm.uiState.value.results as ResultsState.InvalidRange).message)

        vm.setEnd(today.plusYears(5))
        val fiveYears = vm.uiState.value.results as ResultsState.Content
        assertTrue(fiveYears.total in 2 * 66..2 * 70)

        vm.toggleMember(1)
        vm.toggleMember(2)
        assertEquals(ResultsState.NoSelection, vm.uiState.value.results)
    }

    @Test
    fun noFamilyMembersShowsAPrompt() = runTest(dispatcher) {
        family.value = emptyList()
        assertEquals(ResultsState.NoMembers, viewModel().uiState.value.results)
    }
}
