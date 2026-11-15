package io.github.anjalymsajeev.nakshatracalendar.sync

import io.github.anjalymsajeev.nakshatracalendar.domain.model.ReminderSetting
import io.github.anjalymsajeev.nakshatracalendar.domain.usecase.CalendarEventFactory
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarEventFactoryTest {
    private val occurrence = SyncFixtures.occurrence()

    @Test
    fun eventIdsAreDeterministicAndUseGooglesAlphabet() {
        val id = CalendarEventFactory.eventId(4, 13, LocalDate.of(2026, 9, 2), "family@group.calendar.google.com")
        assertEquals(id, CalendarEventFactory.eventId(4, 13, LocalDate.of(2026, 9, 2), "family@group.calendar.google.com"))
        // Google: 5–1024 characters from a–v and 0–9.
        assertTrue(id.length in 5..1024 && id.all { it in 'a'..'v' || it in '0'..'9' })
        assertNotEquals(id, CalendarEventFactory.eventId(4, 13, LocalDate.of(2026, 9, 3), "family@group.calendar.google.com"))
        assertNotEquals(id, CalendarEventFactory.eventId(5, 13, LocalDate.of(2026, 9, 2), "family@group.calendar.google.com"))
        assertNotEquals(id, CalendarEventFactory.eventId(4, 13, LocalDate.of(2026, 9, 2), "primary"))
    }

    @Test
    fun draftFollowsTheBriefsTitleAndDescriptionFormat() {
        val draft = CalendarEventFactory.draft(occurrence, "nkabc", "Anvidh", 10, "primary", ReminderSetting.Before(1_440))
        assertEquals("Anvidh - Atham Nakshatra", draft.title)
        assertEquals(
            listOf(
                "Person: Anvidh",
                "Nakshatra: Atham (അത്തം)",
                "Gregorian Date: 2 September 2026 (Wednesday)",
                "Malayalam Date: 17 Chingam 1202 ME (കൊല്ലവർഷം 1202 ചിങ്ങം 17)",
                "Malayalam Month: Chingam (ചിങ്ങം)",
                "Kollavarsham: 1202",
                "Nakshatra Start: Tue 1 Sep 2026, 11:15 PM (Asia/Kolkata)",
                "Nakshatra End: Wed 2 Sep 2026, 10:45 PM (Asia/Kolkata)",
                "Location: Thiruvananthapuram",
                "Reminder: 1 day before",
                "",
                "Created by Nakshatra Calendar"
            ),
            draft.description.lines()
        )
        assertEquals(LocalDate.of(2026, 9, 2), draft.date)
        assertEquals(1_440, draft.reminderMinutes)
        assertEquals("nakshatram", draft.privateProperties["app"])
        assertEquals("2026-09-02", draft.privateProperties["observanceDate"])
    }

    @Test
    fun contentHashChangesWithAnythingSentButNotOtherwise() {
        fun hash(name: String = "Anvidh", color: Int = 10, reminder: ReminderSetting = ReminderSetting.None) =
            CalendarEventFactory.contentHash(CalendarEventFactory.draft(occurrence, "nkabc", name, color, "primary", reminder))
        assertEquals(hash(), hash())
        assertNotEquals(hash(), hash(name = "Anvidh K"))
        assertNotEquals(hash(), hash(color = 3))
        assertNotEquals(hash(), hash(reminder = ReminderSetting.Before(10)))
    }

    @Test
    fun reminderTextAndLimits() {
        assertEquals("None", CalendarEventFactory.reminderText(ReminderSetting.None))
        assertEquals("10 minutes before", CalendarEventFactory.reminderText(ReminderSetting.Before(10)))
        assertEquals("1 hour before", CalendarEventFactory.reminderText(ReminderSetting.Before(60)))
        assertEquals("2 days before", CalendarEventFactory.reminderText(ReminderSetting.Before(2_880)))
        assertTrue(runCatching { ReminderSetting.Before(ReminderSetting.MAX_MINUTES + 1) }.isFailure)
    }
}
