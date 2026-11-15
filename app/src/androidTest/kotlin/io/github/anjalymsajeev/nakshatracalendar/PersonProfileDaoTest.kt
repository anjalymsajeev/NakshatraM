package io.github.anjalymsajeev.nakshatracalendar

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.anjalymsajeev.nakshatracalendar.core.database.AppDatabase
import io.github.anjalymsajeev.nakshatracalendar.data.local.PersonMapper
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalculationLocation
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonProfileDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    private val dao = db.personProfileDao()

    @After fun close() = db.close()

    private fun entity(name: String, id: Long = 0) =
        PersonMapper.toEntity(Person(id, name, 13, calculationLocation = CalculationLocation.Thiruvananthapuram), 1, 1)

    @Test
    fun insertUpdateObserveAndDelete() = runTest {
        val id = dao.upsert(entity("zara"))
        dao.upsert(entity("Anu"))
        assertEquals(listOf("Anu", "zara"), dao.observeAll().first().map { it.name }) // case-insensitive order
        assertEquals(2, dao.observeCount().first())
        dao.upsert(entity("Zara M", id))
        assertEquals("Zara M", dao.get(id)?.name)
        dao.delete(id)
        assertNull(dao.get(id))
        assertEquals(1, dao.observeCount().first())
    }
}
