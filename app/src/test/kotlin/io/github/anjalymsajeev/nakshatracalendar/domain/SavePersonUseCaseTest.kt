package io.github.anjalymsajeev.nakshatracalendar.domain

import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.BirthDetails
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalculationLocation
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.model.PersonValidationError
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PersonRepository
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.SavePersonResult
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.SavePersonUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SavePersonUseCaseTest {
    private val repository = mockk<PersonRepository>()
    private val clock = Clock.fixed(Instant.parse("2026-11-15T06:00:00Z"), ZoneOffset.UTC)
    private val useCase = SavePersonUseCase(repository, clock)
    private val valid = Person(name = "Anvidh", nakshatraNumber = 13, calculationLocation = CalculationLocation.Thiruvananthapuram)

    @Test
    fun savesTrimmedValidPerson() = runTest {
        coEvery { repository.save(any()) } returns AppResult.Success(7L)
        assertEquals(SavePersonResult.Saved(7L), useCase(valid.copy(name = "  Anvidh  ")))
        coVerify { repository.save(valid) }
    }

    @Test
    fun rejectsMissingNameAndStar() = runTest {
        val result = useCase(valid.copy(name = "  ", nakshatraNumber = 0))
        assertEquals(
            SavePersonResult.Invalid(setOf(PersonValidationError.NameRequired, PersonValidationError.NakshatraRequired)),
            result
        )
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun rejectsLongNamesAndImpossibleBirthDates() {
        assertEquals(setOf(PersonValidationError.NameTooLong), useCase.validate(valid.copy(name = "x".repeat(61))))
        assertEquals(
            setOf(PersonValidationError.BirthDateInFuture),
            useCase.validate(valid.copy(birth = BirthDetails(LocalDate.of(2026, 11, 16))))
        )
        assertEquals(
            setOf(PersonValidationError.BirthDateTooEarly),
            useCase.validate(valid.copy(birth = BirthDetails(LocalDate.of(1899, 12, 31))))
        )
        assertEquals(emptySet<PersonValidationError>(), useCase.validate(valid.copy(birth = BirthDetails(LocalDate.of(2026, 11, 15)))))
    }
}
