package io.github.anjalymsajeev.nakshatracalendar

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarFlowTest {
    private fun monthTitle(month: YearMonth, locale: Locale) = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))

    @Test
    fun navigateMonthsSwitchToMalayalamAndOpenADay() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var locale = Locale.ENGLISH
            scenario.onActivity { locale = it.resources.configuration.locales[0] }
            onView(withId(R.id.calendarFragment)).perform(click())
            val now = YearMonth.now()
            eventually { onView(withId(R.id.title)).check(matches(withText(monthTitle(now, locale)))) }

            onView(withId(R.id.next)).perform(click())
            eventually { onView(withId(R.id.title)).check(matches(withText(monthTitle(now.plusMonths(1), locale)))) }
            onView(withId(R.id.today)).perform(click())
            eventually { onView(withId(R.id.title)).check(matches(withText(monthTitle(now, locale)))) }

            onView(withId(R.id.mode_malayalam)).perform(click())
            eventually { onView(withId(R.id.title)).check(matches(not(withText(monthTitle(now, locale))))) }

            // Tap a day (position 10 is always a real day: at most 6 blanks precede the 1st).
            onView(withId(R.id.grid)).perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(10, click()))
            eventually { onView(withText(R.string.calendar_nakshatra_at_sunrise)).check(matches(isDisplayed())) }
        }
    }
}
