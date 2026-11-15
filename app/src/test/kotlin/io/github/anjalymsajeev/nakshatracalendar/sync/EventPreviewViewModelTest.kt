package io.github.anjalymsajeev.nakshatracalendar.sync

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.anjalymsajeev.kollavarsham.core.KollavarshamCalendar
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppError
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalculationLocation
import io.github.anjalymsajeev.nakshatracalendar.domain.model.GoogleCalendarInfo
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.model.SyncMode
import io.github.anjalymsajeev.nakshatracalendar.domain.model.UserSettings
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.AuthorizationState
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.CalendarPreferences
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.ConsentRequest
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.GoogleAuthRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PersonRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SettingsRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SyncRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.FindNakshatraDatesUseCase
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.StartSyncUseCase
import io.github.anjalymsajeev.nakshatracalendar.feature.sync.EventPreviewViewModel
import io.github.anjalymsajeev.nakshatracalendar.feature.sync.PreviewEvent
import io.github.anjalymsajeev.nakshatracalendar.feature.sync.PreviewUiState
import io.github.anjalymsajeev.nakshatracalendar.integration.malayalamcalendar.KollavarshamDateProvider
import io.github.anjalymsajeev.nakshatracalendar.integration.nakshatra.PanchangamNakshatraEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Real occurrences (library) for Onam 2026's star; Google and storage are fakes/mocks. */
@OptIn(ExperimentalCoroutinesApi::class)
class EventPreviewViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val person = Person(4, "Anvidh", 22, calculationLocation = CalculationLocation.Thiruvananthapuram)
    private val onam = LocalDate.of(2026, 8, 26)
    private val calendar = MutableStateFlow<GoogleCalendarInfo?>(null)
    private val synced = MutableStateFlow(setOf(onam))
    private val auth = mockk<GoogleAuthRepository>()
    private val startSync = mockk<StartSyncUseCase>()
    private val dates = KollavarshamDateProvider(KollavarshamCalendar.create())

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = EventPreviewViewModel(
        SavedStateHandle(
            mapOf(
                "personId" to 4L,
                "startEpochDay" to LocalDate.of(2026, 8, 1).toEpochDay(),
                "endEpochDay" to LocalDate.of(2026, 10, 31).toEpochDay()
            )
        ),
        mockk<PersonRepository> { coEvery { get(4) } returns AppResult.Success(person) },
        FindNakshatraDatesUseCase(
            PanchangamNakshatraEngine(),
            dates,
            mockk<SettingsRepository> { every { settings } returns flowOf(UserSettings()) },
            dispatcher
        ),
        mockk<CalendarPreferences> { every { selectedCalendar } returns calendar },
        mockk<SyncRepository> { every { observeSyncedDates(4, any()) } returns synced },
        auth,
        startSync
    )

    @Test
    fun datesAlreadyInTheCalendarAreMarkedAndNotPreselected() = runTest(dispatcher) {
        calendar.value = GoogleCalendarInfo("primary", "Me")
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        val state = vm.uiState.value as PreviewUiState.Content
        // August–October 2026 holds three Thiruvonam days; Onam is already synced.
        assertEquals(3, state.eligibleCount)
        assertTrue(state.rows.single { it.date == onam }.alreadySynced)
        assertEquals(2, state.selectedCount)

        vm.deselectAll()
        assertEquals(0, (vm.uiState.value as PreviewUiState.Content).selectedCount)
        vm.toggle(onam)
        assertEquals(1, (vm.uiState.value as PreviewUiState.Content).selectedCount)
    }

    @Test
    fun withoutACalendarTheUserIsSentToChooseOne() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.eventFlow.test {
            vm.requestCreate(SyncMode.AUTOMATIC)
            assertEquals(PreviewEvent.ChooseCalendar, awaitItem())
        }
    }

    @Test
    fun confirmingAsksForConsentWhenNeededThenStartsTheRun() = runTest(dispatcher) {
        calendar.value = GoogleCalendarInfo("primary", "Me")
        val consent = object : ConsentRequest {}
        coEvery { auth.authorize() } returns AppResult.Success(AuthorizationState.NeedsConsent(consent))
        coEvery { auth.completeConsent(any()) } returns AppResult.Success("token")
        coEvery { startSync(any(), any(), any(), any(), any()) } returns AppResult.Success(42L)
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        vm.eventFlow.test {
            vm.requestCreate(SyncMode.MANUAL)
            assertEquals(PreviewEvent.Confirm(SyncMode.MANUAL, 2, "Me"), awaitItem())
            vm.confirm(SyncMode.MANUAL)
            assertEquals(PreviewEvent.LaunchConsent(consent), awaitItem())
            vm.onConsentResult(null)
            assertEquals(PreviewEvent.Started(42L), awaitItem())
        }
        coVerify {
            startSync(
                person,
                any(),
                GoogleCalendarInfo("primary", "Me"),
                SyncMode.MANUAL,
                match {
                    it.size == 2 &&
                        it.none { o -> o.observanceDate == onam }
                }
            )
        }
    }

    @Test
    fun aFailedAuthorizationIsReported() = runTest(dispatcher) {
        calendar.value = GoogleCalendarInfo("primary", "Me")
        coEvery { auth.authorize() } returns AppResult.Failure(AppError.AuthRequired)
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.eventFlow.test {
            vm.confirm(SyncMode.AUTOMATIC)
            assertTrue(awaitItem() is PreviewEvent.Message)
        }
    }
}
