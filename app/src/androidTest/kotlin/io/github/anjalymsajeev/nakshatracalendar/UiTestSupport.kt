package io.github.anjalymsajeev.nakshatracalendar

import android.widget.EditText
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not

/** Espresso does not wait for background coroutines; retry an assertion for up to [timeoutMs]. */
fun eventually(timeoutMs: Long = 10_000, block: () -> Unit) {
    val end = System.currentTimeMillis() + timeoutMs
    while (true) {
        try {
            block()
            return
        } catch (e: Throwable) {
            if (System.currentTimeMillis() > end) throw e
            Thread.sleep(200)
        }
    }
}

/** Adds a family member through the real UI, starting from any tab; ends on the Family tab. */
fun addMemberThroughUi(name: String, starEnglish: String) {
    onView(withId(R.id.familyFragment)).perform(click())
    eventually {
        try {
            onView(allOf(withId(R.id.add_person), isDisplayed())).perform(click())
        } catch (e: Throwable) {
            onView(allOf(withId(R.id.empty_add_person), isDisplayed())).perform(click())
        }
    }
    eventually { onView(withId(R.id.name_input)).check(matches(isDisplayed())) }
    onView(withId(R.id.name_input)).perform(replaceText(name), closeSoftKeyboard())
    onView(withId(R.id.nakshatra_input)).perform(click())
    onView(withId(R.id.search)).perform(replaceText(starEnglish), closeSoftKeyboard())
    eventually { onView(allOf(withText(starEnglish), not(isAssignableFrom(EditText::class.java)))).perform(click()) }
    onView(withId(R.id.save)).perform(click())
    eventually { onView(allOf(withText(name), isDisplayed())).check(matches(isDisplayed())) }
}
