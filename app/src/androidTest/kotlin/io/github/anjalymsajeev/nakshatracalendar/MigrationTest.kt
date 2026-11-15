package io.github.anjalymsajeev.nakshatracalendar

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.anjalymsajeev.nakshatracalendar.core.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate1To2KeepsDataAndAddsBirthPlaceColumns() {
        helper.createDatabase(DB, 1).apply {
            execSQL(
                """
                INSERT INTO person_profile (id, name, nakshatra_number, birth_date, birth_time, birth_location,
                    location_name, location_latitude, location_longitude, time_zone, event_color, created_at, updated_at)
                VALUES (1, 'Anvidh', 13, '2020-03-04', '05:45', 'Kochi', 'Thiruvananthapuram', 8.5241, 76.9366,
                    'Asia/Kolkata', 10, 0, 0)
                """.trimIndent()
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 2, true)
        db.query("SELECT name, nakshatra_number, birth_latitude, birth_time_zone FROM person_profile WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Anvidh", c.getString(0))
            assertEquals(13, c.getInt(1))
            assertTrue(c.isNull(2))
            assertTrue(c.isNull(3))
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
