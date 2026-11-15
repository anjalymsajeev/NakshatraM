package io.github.anjalymsajeev.nakshatracalendar.domain.usecase

import io.github.anjalymsajeev.nakshatracalendar.core.common.AppError
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.model.PersonValidationError
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PersonRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Result of saving a person: saved, rejected by validation, or failed. */
sealed interface SavePersonResult {
    data class Saved(val id: Long) : SavePersonResult

    data class Invalid(val errors: Set<PersonValidationError>) : SavePersonResult

    data class Failed(val error: AppError) : SavePersonResult
}

class SavePersonUseCase @Inject constructor(private val repository: PersonRepository, private val clock: Clock) {
    suspend operator fun invoke(person: Person): SavePersonResult {
        val trimmed = person.copy(name = person.name.trim())
        val errors = validate(trimmed)
        if (errors.isNotEmpty()) return SavePersonResult.Invalid(errors)
        return when (val result = repository.save(trimmed)) {
            is AppResult.Success -> SavePersonResult.Saved(result.value)
            is AppResult.Failure -> SavePersonResult.Failed(result.error)
        }
    }

    /** Validation rules; also used by the form to show errors before saving. */
    fun validate(person: Person): Set<PersonValidationError> = buildSet {
        val name = person.name.trim()
        if (name.isEmpty()) add(PersonValidationError.NameRequired)
        if (name.length > MAX_NAME_LENGTH) add(PersonValidationError.NameTooLong)
        if (person.nakshatraNumber !in 1..27) add(PersonValidationError.NakshatraRequired)
        person.birth?.date?.let { date ->
            val today = LocalDate.now(clock.withZone(person.calculationLocation.zone))
            if (date.isAfter(today)) add(PersonValidationError.BirthDateInFuture)
            if (date.isBefore(EARLIEST_BIRTH_DATE)) add(PersonValidationError.BirthDateTooEarly)
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 60

        /** The Malayalam calendar library supports 1900–2100. */
        val EARLIEST_BIRTH_DATE: LocalDate = LocalDate.of(1900, 1, 1)
    }
}
