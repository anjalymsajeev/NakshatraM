package io.github.anjalymsajeev.nakshatracalendar.data.local

import io.github.anjalymsajeev.nakshatracalendar.data.local.entity.PersonProfileEntity
import io.github.anjalymsajeev.nakshatracalendar.domain.model.BirthDetails
import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalculationLocation
import io.github.anjalymsajeev.nakshatracalendar.domain.model.EventColor
import io.github.anjalymsajeev.nakshatracalendar.domain.model.Person
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Maps between the Room entity and the domain model. Dates and times are stored as ISO-8601 text. */
object PersonMapper {
    fun toDomain(e: PersonProfileEntity): Person = Person(
        id = e.id,
        name = e.name,
        nakshatraNumber = e.nakshatraNumber,
        birth = e.birthDate?.let { date ->
            BirthDetails(
                date = LocalDate.parse(date),
                time = e.birthTime?.let(LocalTime::parse),
                place = if (e.birthLocation != null && e.birthLatitude != null && e.birthLongitude != null && e.birthTimeZone != null) {
                    CalculationLocation(e.birthLocation, e.birthLatitude, e.birthLongitude, ZoneId.of(e.birthTimeZone))
                } else {
                    null
                }
            )
        },
        calculationLocation = CalculationLocation(e.locationName, e.locationLatitude, e.locationLongitude, ZoneId.of(e.timeZone)),
        eventColor = EventColor.fromColorId(e.eventColor)
    )

    fun toEntity(p: Person, createdAt: Long, updatedAt: Long): PersonProfileEntity = PersonProfileEntity(
        id = p.id,
        name = p.name,
        nakshatraNumber = p.nakshatraNumber,
        birthDate = p.birth?.date?.toString(),
        birthTime = p.birth?.time?.toString(),
        birthLocation = p.birth?.place?.name,
        birthLatitude = p.birth?.place?.latitude,
        birthLongitude = p.birth?.place?.longitude,
        birthTimeZone = p.birth?.place?.zone?.id,
        locationName = p.calculationLocation.name,
        locationLatitude = p.calculationLocation.latitude,
        locationLongitude = p.calculationLocation.longitude,
        timeZone = p.calculationLocation.zone.id,
        eventColor = p.eventColor.colorId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
