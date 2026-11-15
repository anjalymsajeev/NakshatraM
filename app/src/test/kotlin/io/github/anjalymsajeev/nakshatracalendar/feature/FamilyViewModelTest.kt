package io.github.anjalymsajeev.nakshatracalendar.feature

import app.cash.turbine.test
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalculationLocation
import io.github.anjalymsajeev.nakshatracalendar.domain.model.FamilyMember
import io.github.anjalymsajeev.nakshatracalendar.domain.model.NakshatraName
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.DeletePersonUseCase
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.ObserveFamilyUseCase
import io.github.anjalymsajeev.nakshatracalendar.feature.person.FamilyEvent
import io.github.anjalymsajeev.nakshatracalendar.feature.person.FamilyUiState
import io.github.anjalymsajeev.nakshatracalendar.feature.person.FamilyViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FamilyViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val person = Person(4, "Anvidh", 13, calculationLocation = CalculationLocation.Thiruvananthapuram)
    private val member = FamilyMember(person, NakshatraName(13, "അത്തം", "Atham"), next = null)
    private val deletePerson = mockk<DeletePersonUseCase>()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(members: List<FamilyMember>) =
        FamilyViewModel(mockk<ObserveFamilyUseCase> { every { this@mockk.invoke() } returns flowOf(members) }, deletePerson, dispatcher)

    @Test
    fun emptyAndContentStates() = runTest {
        vm(emptyList()).uiState.test { assertEquals(FamilyUiState.Empty, awaitItem()) }
        vm(listOf(member)).uiState.test {
            val item = (awaitItem() as FamilyUiState.Content).items.single()
            assertEquals("Anvidh", item.name)
            assertEquals("അത്തം", item.nakshatraMalayalam)
            assertNull(item.nextGregorian)
        }
    }

    @Test
    fun deleteOffersUndo() = runTest {
        coEvery { deletePerson(4) } returns AppResult.Success(person)
        coEvery { deletePerson.undo(person) } returns AppResult.Success(4L)
        val vm = vm(listOf(member))
        vm.eventFlow.test {
            vm.delete(4)
            assertEquals(FamilyEvent.Deleted(person), awaitItem())
        }
        vm.undoDelete(person)
        coVerify { deletePerson.undo(person) }
    }
}
