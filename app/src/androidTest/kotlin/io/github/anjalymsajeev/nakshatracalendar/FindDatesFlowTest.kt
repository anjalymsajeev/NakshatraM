package io.github.anjalymsajeev.nakshatracalendar

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.allOf
import org.junit.Test
import org.junit.runner.RunWith

/** Add a member, open Find dates, see real occurrences grouped under the member (real library, real Room). */
@RunWith(AndroidJUnit4::class)
class FindDatesFlowTest {
    private val unique = "F${System.nanoTime() % 1_000_000}"

    @Test
    fun findDatesListsOccurrencesForTheSelectedMember() {
        ActivityScenario.launch(MainActivity::class.java).use {
            addMemberThroughUi(unique, "Atham")
            eventually { onView(allOf(withId(R.id.find_dates), isDisplayed())).perform(click()) }
            eventually { onView(withId(R.id.start_input)).check(matches(isDisplayed())) }

            // The default one-year range holds about 13 Atham days; the member's header and the count are shown.
            eventually {
                onView(allOf(withId(R.id.name), withText(unique), isDescendantOfA(withId(R.id.results)))).check(matches(isDisplayed()))
            }
            onView(withId(R.id.summary)).check(matches(isDisplayed()))
        }
    }
}
