package io.github.anjalymsajeev.nakshatracalendar

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Manual sync end to end on an emulator: real library, Room, engine and UI; Google is replaced by the test-only
 * FakeGoogleCalendarModule. Select two dates, connect, pick a calendar, confirm, see the results.
 */
@HiltAndroidTest
class SyncFlowTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    private val unique = "S${System.nanoTime() % 1_000_000}"

    @Test
    fun selectTwoDatesConfirmAndSeeThemInGoogleCalendar() {
        ActivityScenario.launch(MainActivity::class.java).use {
            addMemberThroughUi(unique, "Revathi")
            eventually { onView(allOf(withId(R.id.find_dates), isDisplayed())).perform(click()) }
            // Find dates lists every member (other tests leave theirs); use this member's button.
            val addLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(
                R.string.action_add_to_google_calendar_for,
                unique
            )
            eventually { onView(allOf(withId(R.id.add_to_calendar), withContentDescription(addLabel))).perform(click()) }

            // Manual mode: choose exactly two dates.
            eventually { onView(withId(R.id.deselect_all)).check(matches(isDisplayed())) }
            onView(withId(R.id.deselect_all)).perform(click())
            onView(withId(R.id.dates)).perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))
            onView(withId(R.id.dates)).perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(1, click()))

            // Connect and pick a calendar if none is chosen yet (the choice persists between tests).
            onView(withId(R.id.change_calendar)).perform(click())
            eventually { onView(withId(R.id.connect)).perform(click()) }
            eventually { onView(withText(TestGoogleCalendar.calendar.name)).perform(click()) }

            eventually { onView(withId(R.id.create_selected)).check(matches(withText(containsCount(2)))) }
            onView(withId(R.id.create_selected)).perform(click())
            onView(withText(R.string.preview_confirm_action)).perform(click())

            eventually { onView(withId(R.id.status)).check(matches(withText(R.string.sync_status_succeeded))) }
            eventually { onView(withId(R.id.counts)).check(matches(withText(org.hamcrest.Matchers.startsWith("2 ")))) }
            val mine =
                eventuallyValue { TestGoogleCalendar.upserts.toList().filter { it.title.startsWith(unique) }.takeIf { it.size == 2 } }
            assertEquals("events sent: ${TestGoogleCalendar.upserts.map { it.title + " @" + it.date }}", 2, mine?.size)
            assertTrue(mine!!.all { it.calendarId == TestGoogleCalendar.calendar.id && it.title == "$unique - Revathi Nakshatra" })
        }
    }

    private fun <T> eventuallyValue(timeoutMs: Long = 10_000, read: () -> T?): T? {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            read()?.let { return it }
            Thread.sleep(200)
        }
        return read()
    }

    private fun containsCount(count: Int) = org.hamcrest.Matchers.containsString("($count)")
}
