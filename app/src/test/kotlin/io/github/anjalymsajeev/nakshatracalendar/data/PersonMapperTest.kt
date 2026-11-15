package io.github.anjalymsajeev.nakshatracalendar.data

import io.github.anjalymsajeev.nakshatracalendar.data.local.PersonMapper
import io.github.anjalymsajeev.nakshatracalendar.domain.model.BirthDetails
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalculationLocation
import io.github.anjalymsajeev.nakshatracalendar.domain.model.EventColor
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonMapperTest {
    private val dubai = CalculationLocation("Dubai, United Arab Emirates", 25.2048, 55.2708, ZoneId.of("Asia/Dubai"))

    @Test
    fun roundTripsAllFields() {
        val person = Person(
            id = 5,
            name = "Anvidh",
            nakshatraNumber = 13,
            birth = BirthDetails(LocalDate.of(2020, 3, 4), LocalTime.of(5, 45), dubai),
            calculationLocation = CalculationLocation.Thiruvananthapuram,
            eventColor = EventColor.PEACOCK
        )
        val entity = PersonMapper.toEntity(person, createdAt = 1, updatedAt = 2)
        assertEquals(7, entity.eventColor) // Google Calendar colorId
        assertEquals("2020-03-04", entity.birthDate)
        assertEquals(person, PersonMapper.toDomain(entity))
    }

    @Test
    fun personWithoutBirthDetails() {
        val person = Person(name = "Anu", nakshatraNumber = 1, calculationLocation = dubai)
        val back = PersonMapper.toDomain(PersonMapper.toEntity(person, 0, 0))
        assertNull(back.birth)
        assertEquals(person, back)
    }

    @Test
    fun unknownColourFallsBackToDefault() {
        val entity = PersonMapper.toEntity(Person(name = "A", nakshatraNumber = 1, calculationLocation = dubai), 0, 0).copy(eventColor = 99)
        assertEquals(EventColor.DEFAULT, PersonMapper.toDomain(entity).eventColor)
    }
}
