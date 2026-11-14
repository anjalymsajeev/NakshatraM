package io.github.anjalymsajeev.nakshatracalendar.data.repository

import io.github.anjalymsajeev.nakshatracalendar.data.local.dao.PersonProfileDao
import io.github.anjalymsajeev.nakshatracalendar.domain.repository.PersonRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class PersonRepositoryImpl @Inject constructor(private val dao: PersonProfileDao) : PersonRepository {
    override fun observeCount(): Flow<Int> = dao.observeCount()
}
