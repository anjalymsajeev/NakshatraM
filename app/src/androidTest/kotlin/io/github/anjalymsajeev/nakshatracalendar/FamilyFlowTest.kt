package io.github.anjalymsajeev.nakshatracalendar

import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Add, edit and delete a family member through the real UI (real Room database, real library).
 * Each test uses a unique name, so tests do not depend on what other tests left behind.
 */
@RunWith(AndroidJUnit4::class)
class FamilyFlowTest {
    private val unique = "T${System.nanoTime() % 1_000_000}"

    /** Espresso does not wait for background coroutines; retry an assertion for up to [timeoutMs]. */
    private fun eventually(timeoutMs: Long = 10_000, block: () -> Unit) {
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

    private fun openFamilyAddScreen() {
        onView(withId(R.id.familyFragment)).perform(click())
        eventually {
            try {
                onView(allOf(withId(R.id.add_person), isDisplayed())).perform(click())
            } catch (e: Throwable) {
                onView(allOf(withId(R.id.empty_add_person), isDisplayed())).perform(click())
            }
        }
    }

    @Test
    fun addEditAndDeleteAFamilyMember() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Add
            openFamilyAddScreen()
            eventually { onView(withId(R.id.name_input)).check(matches(isDisplayed())) }
            onView(withId(R.id.name_input)).perform(replaceText(unique), closeSoftKeyboard())
            onView(withId(R.id.nakshatra_input)).perform(click())
            onView(withId(R.id.search)).perform(replaceText("Atham"), closeSoftKeyboard())
            // The search box also shows "Atham"; click the list row, not the EditText.
            eventually { onView(allOf(withText("Atham"), not(isAssignableFrom(EditText::class.java)))).perform(click()) }
            onView(withId(R.id.save)).perform(click())
            eventually { onView(withText(unique)).check(matches(isDisplayed())) }

            // Edit: rename through the card
            onView(withText(unique)).perform(click())
            eventually { onView(withId(R.id.name_input)).check(matches(withText(unique))) }
            onView(withId(R.id.name_input)).perform(replaceText("${unique}x"), closeSoftKeyboard())
            onView(withId(R.id.save)).perform(click())
            eventually { onView(withText("${unique}x")).check(matches(isDisplayed())) }

            // Delete through the card's menu, with confirmation
            onView(allOf(withId(R.id.more), withParent(hasDescendant(withText("${unique}x"))))).perform(click())
            onView(withText(R.string.action_delete)).inRoot(isPlatformPopup()).perform(click())
            onView(allOf(withText(R.string.action_delete), withId(android.R.id.button1))).perform(click())
            // The delete ran when its Snackbar appears; then no card for the member may be shown. (When the last member
            // goes, the list is hidden and a GONE RecyclerView keeps its old child views until its next layout, so check
            // what is displayed rather than what exists in the view tree.)
            val deletedMessage = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.family_deleted, "${unique}x")
            eventually { onView(withText(deletedMessage)).check(matches(isDisplayed())) }
            eventually { onView(allOf(withId(R.id.name), withText("${unique}x"), isDisplayed())).check(doesNotExist()) }
        }
    }

    @Test
    fun saveWithoutNameOrStarShowsErrors() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openFamilyAddScreen()
            eventually { onView(withId(R.id.save)).check(matches(isDisplayed())) }
            onView(withId(R.id.save)).perform(click())
            // Errors may sit below the fold of the scrolling form, so check visibility rather than isDisplayed().
            eventually { onView(withText(R.string.error_name_required)).check(matches(withEffectiveVisibility(Visibility.VISIBLE))) }
            onView(withText(R.string.error_nakshatra_required)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        }
    }
}
