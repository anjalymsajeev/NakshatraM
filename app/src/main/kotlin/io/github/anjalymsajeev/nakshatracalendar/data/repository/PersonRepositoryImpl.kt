package io.github.anjalymsajeev.nakshatracalendar.data.repository

import android.database.SQLException
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppError
import io.github.anjalymsajeev.nakshatracalendar.core.common.AppResult
import io.github.anjalymsajeev.nakshatracalendar.core.common.IoDispatcher
import io.github.anjalymsajeev.nakshatracalendar.data.local.PersonMapper
import io.github.anjalymsajeev.nakshatracalendar.data.local.dao.PersonProfileDao
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PersonRepository
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PersonRepositoryImpl @Inject constructor(
    private val dao: PersonProfileDao,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher
) : PersonRepository {
    override fun observeCount(): Flow<Int> = dao.observeCount()

    override fun observeAll(): Flow<List<Person>> = dao.observeAll().map { list -> list.map(PersonMapper::toDomain) }

    override suspend fun get(id: Long): AppResult<Person?> = db { dao.get(id)?.let(PersonMapper::toDomain) }

    override suspend fun save(person: Person): AppResult<Long> = db {
        val now = clock.millis()
        val createdAt = if (person.id == 0L) now else dao.get(person.id)?.createdAt ?: now
        val rowId = dao.upsert(PersonMapper.toEntity(person, createdAt, now))
        if (person.id == 0L) rowId else person.id
    }

    override suspend fun delete(id: Long): AppResult<Unit> = db {
        dao.delete(id)
        Unit
    }

    private suspend fun <T> db(block: suspend () -> T): AppResult<T> = withContext(io) {
        try {
            AppResult.Success(block())
        } catch (e: SQLException) {
            AppResult.Failure(AppError.Database(e))
        } catch (e: IllegalStateException) {
            AppResult.Failure(AppError.Database(e))
        }
    }
}
