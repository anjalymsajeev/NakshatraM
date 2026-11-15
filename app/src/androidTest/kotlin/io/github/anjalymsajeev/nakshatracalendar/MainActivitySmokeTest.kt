package io.github.anjalymsajeev.nakshatracalendar

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launches the real app (real DI, real library) and walks the bottom navigation. Runs on an emulator in CI. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Test
    fun launchesAndNavigatesBetweenTabs() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.bottom_navigation)).check(matches(isDisplayed()))
            onView(withId(R.id.settingsFragment)).perform(click())
            onView(withId(R.id.star_day_group)).check(matches(isDisplayed()))
            onView(withId(R.id.familyFragment)).perform(click())
            onView(withId(R.id.calendarFragment)).perform(click())
            onView(withId(R.id.homeFragment)).perform(click())
        }
    }
}
