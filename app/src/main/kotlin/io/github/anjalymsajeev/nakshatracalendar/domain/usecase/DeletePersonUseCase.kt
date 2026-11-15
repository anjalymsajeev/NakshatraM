package io.github.anjalymsajeev.nakshatracalendar.domain.usecase

import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PersonRepository
import javax.inject.Inject

/** Deletes a person and returns it, so the UI can offer "Undo" (which saves it back with the same id). */
class DeletePersonUseCase @Inject constructor(private val repository: PersonRepository) {
    suspend operator fun invoke(id: Long): AppResult<Person?> = when (val existing = repository.get(id)) {
        is AppResult.Failure -> existing
        is AppResult.Success -> when (val deleted = repository.delete(id)) {
            is AppResult.Failure -> deleted
            is AppResult.Success -> existing
        }
    }

    suspend fun undo(person: Person): AppResult<Long> = repository.save(person)
}
