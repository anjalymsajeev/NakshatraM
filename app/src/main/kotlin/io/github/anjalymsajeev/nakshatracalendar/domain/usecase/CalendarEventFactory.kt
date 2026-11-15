package io.github.anjalymsajeev.nakshatracalendar.domain.usecase

import io.github.anjalymsajeev.nakshatracalendar.domain.model.CalendarEventDraft
import io.github.anjalymsajeev.nakshatracalendar.domain.model.ReminderSetting
import io.github.anjalymsajeev.nakshatracalendar.domain.model.StoredOccurrence
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds Google Calendar events in the brief's format. Pure functions: no I/O, fully unit-tested.
 * Event text is English with Malayalam names alongside, so it reads the same on every device.
 */
object CalendarEventFactory {
    private val gregorianFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy (EEEE)", Locale.ENGLISH)
    private val timeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm a", Locale.ENGLISH)

    /**
     * Deterministic Google event id: base32hex (Google's allowed alphabet, a-v and 0-9) of a SHA-1 over the
     * person, nakshatra, observance day and calendar. The same occurrence always maps to the same id, so a
     * repeated insert is answered with "already exists" instead of creating a duplicate.
     */
    fun eventId(personId: Long, nakshatraNumber: Int, date: LocalDate, calendarId: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("nakshatram|$personId|$nakshatraNumber|$date|$calendarId".toByteArray())
        return "nk" + base32Hex(digest)
    }

    fun title(personName: String, nakshatraEnglish: String) = "$personName - $nakshatraEnglish Nakshatra"

    fun draft(
        occurrence: StoredOccurrence,
        eventId: String,
        personName: String,
        colorId: Int,
        calendarId: String,
        reminder: ReminderSetting
    ): CalendarEventDraft {
        val zone = ZoneId.of(occurrence.zoneId)
        val description = listOf(
            "Person: $personName",
            "Nakshatra: ${occurrence.nakshatra.nameEnglish} (${occurrence.nakshatra.nameMalayalam})",
            "Gregorian Date: ${occurrence.observanceDate.format(gregorianFormatter)}",
            "Malayalam Date: ${occurrence.malayalamDateEn} (${occurrence.malayalamDateMl})",
            "Malayalam Month: ${occurrence.malayalamMonthEn} (${occurrence.malayalamMonthMl})",
            "Kollavarsham: ${occurrence.kollavarshamYear}",
            "Nakshatra Start: ${occurrence.start.atZone(zone).format(timeFormatter)} (${zone.id})",
            "Nakshatra End: ${occurrence.end.atZone(zone).format(timeFormatter)} (${zone.id})",
            "Location: ${occurrence.locationName}",
            "Reminder: ${reminderText(reminder)}",
            "",
            "Created by Nakshatra Calendar"
        ).joinToString("\n")
        return CalendarEventDraft(
            eventId = eventId,
            calendarId = calendarId,
            title = title(personName, occurrence.nakshatra.nameEnglish),
            description = description,
            location = occurrence.locationName,
            date = occurrence.observanceDate,
            colorId = colorId,
            reminderMinutes = (reminder as? ReminderSetting.Before)?.minutes,
            privateProperties = mapOf(
                PROPERTY_APP to APP_MARKER,
                "personId" to (occurrence.personId?.toString() ?: ""),
                "nakshatra" to occurrence.nakshatra.number.toString(),
                "observanceDate" to occurrence.observanceDate.toString()
            )
        )
    }

    /** Fingerprint of everything sent; an unchanged hash means the event needs no update. */
    fun contentHash(draft: CalendarEventDraft): String {
        val text = listOf(
            draft.eventId, draft.calendarId, draft.title, draft.description, draft.location, draft.date.toString(),
            draft.colorId.toString(), draft.reminderMinutes?.toString().orEmpty(),
            draft.privateProperties.toSortedMap().toString()
        ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun reminderText(reminder: ReminderSetting): String = when (reminder) {
        ReminderSetting.None -> "None"
        is ReminderSetting.Before -> when {
            reminder.minutes == 0 -> "At the start of the day"
            reminder.minutes % 1_440 == 0 -> plural(reminder.minutes / 1_440, "day") + " before"
            reminder.minutes % 60 == 0 -> plural(reminder.minutes / 60, "hour") + " before"
            else -> plural(reminder.minutes, "minute") + " before"
        }
    }

    private fun plural(n: Int, unit: String) = if (n == 1) "1 $unit" else "$n ${unit}s"

    private fun base32Hex(bytes: ByteArray): String {
        val alphabet = "0123456789abcdefghijklmnopqrstuv"
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                out.append(alphabet[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) out.append(alphabet[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }

    const val PROPERTY_APP = "app"
    const val APP_MARKER = "nakshatram"
}
