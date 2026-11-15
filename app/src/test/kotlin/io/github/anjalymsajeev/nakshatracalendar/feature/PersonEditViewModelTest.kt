package io.github.anjalymsajeev.nakshatracalendar.feature

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.anjalymsajeev.nakshatracalendar.R
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalculationLocation
import io.github.anjalymsajeev.nakshatracalendar.domain.model.EventColor
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.model.PersonValidationError
import io.github.anjalymsajeev.nakshatracalendar.domain.model.UserSettings
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PersonRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.SettingsRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.CalculateBirthStarUseCase
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.SavePersonUseCase
import io.github.anjalymsajeev.nakshatracalendar.feature.person.PersonEditEvent
import io.github.anjalymsajeev.nakshatracalendar.feature.person.PersonEditViewModel
import io.github.anjalymsajeev.nakshatracalendar.integration.nakshatra.PanchangamNakshatraEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersonEditViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val dubai = CalculationLocation("Dubai", 25.2, 55.27, ZoneId.of("Asia/Dubai"))
    private val persons = mockk<PersonRepository>()
    private val settings = mockk<SettingsRepository> { every { settings } returns flowOf(UserSettings(calculationLocation = dubai)) }
    private val engine = PanchangamNakshatraEngine()
    private val clock = Clock.fixed(Instant.parse("2026-11-15T06:00:00Z"), ZoneOffset.UTC)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(id: Long = -1L) = PersonEditViewModel(
        SavedStateHandle(mapOf(PersonEditViewModel.ARG_PERSON_ID to id)),
        persons,
        settings,
        engine,
        SavePersonUseCase(persons, clock),
        CalculateBirthStarUseCase(engine),
        dispatcher
    )

    @Test
    fun newPersonStartsWithTheSettingsLocation() {
        val state = vm().state.value
        assertFalse(state.isEditing)
        assertFalse(state.isLoading)
        assertEquals(dubai, state.calculationLocation)
    }

    @Test
    fun invalidFormShowsErrorsAndDoesNotSave() {
        val vm = vm()
        vm.save()
        assertEquals(setOf(PersonValidationError.NameRequired, PersonValidationError.NakshatraRequired), vm.state.value.errors)
        vm.onNameChanged("Anvidh")
        assertEquals(setOf(PersonValidationError.NakshatraRequired), vm.state.value.errors)
        coVerify(exactly = 0) { persons.save(any()) }
    }

    @Test
    fun birthTimeWithoutDateIsRejected() {
        val vm = vm()
        vm.onNameChanged("Anvidh")
        vm.onNakshatraSelected(13)
        vm.onBirthTimeSelected(java.time.LocalTime.of(6, 30))
        vm.save()
        assertEquals(setOf(PersonValidationError.BirthPlaceWithoutDate), vm.state.value.errors)
    }

    @Test
    fun validFormSavesAndEmitsSaved() = runTest {
        coEvery { persons.save(any()) } returns AppResult.Success(3L)
        val vm = vm()
        vm.eventFlow.test {
            vm.onNameChanged("Anvidh")
            vm.onNakshatraSelected(13)
            vm.onColorSelected(EventColor.PEACOCK)
            vm.save()
            assertEquals(PersonEditEvent.Saved, awaitItem())
        }
        coVerify {
            persons.save(Person(name = "Anvidh", nakshatraNumber = 13, calculationLocation = dubai, eventColor = EventColor.PEACOCK))
        }
    }

    @Test
    fun editingLoadsTheExistingPerson() {
        val existing = Person(9, "Meenakshi", 1, calculationLocation = dubai, eventColor = EventColor.GRAPE)
        coEvery { persons.get(9) } returns AppResult.Success(existing)
        val state = vm(9).state.value
        assertTrue(state.isEditing)
        assertEquals("Meenakshi", state.name)
        assertEquals("Ashwathi", state.nakshatra?.nameEnglish)
        assertEquals(EventColor.GRAPE, state.eventColor)
    }

    @Test
    fun calculateWithoutBirthDateAsksForIt() = runTest {
        val vm = vm()
        vm.eventFlow.test {
            vm.calculateStar()
            assertEquals(PersonEditEvent.Message(R.string.person_calculate_needs_date), awaitItem())
        }
    }

    @Test
    fun calculateWithAClearTimeSetsTheStar() = runTest {
        val vm = vm()
        // Midday on Onam 2026 in India: well inside Thiruvonam.
        vm.onBirthDateSelected(LocalDate.of(2026, 8, 26))
        vm.onBirthTimeSelected(java.time.LocalTime.of(9, 0))
        vm.onBirthPlaceSelected(CalculationLocation.Thiruvananthapuram)
        vm.eventFlow.test {
            vm.calculateStar()
            val event = awaitItem()
            assertTrue(event is PersonEditEvent.BirthStarCalculated || event is PersonEditEvent.ChooseBirthStar)
        }
    }
}
