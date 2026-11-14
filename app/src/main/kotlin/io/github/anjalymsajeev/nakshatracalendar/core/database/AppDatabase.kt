package io.github.anjalymsajeev.nakshatracalendar.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.anjalymsajeev.nakshatracalendar.data.local.dao.PersonProfileDao
import io.github.anjalymsajeev.nakshatracalendar.data.local.entity.PersonProfileEntity

/** Schemas are exported to app/schemas; every version change needs a migration and a migration test. */
@Database(entities = [PersonProfileEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personProfileDao(): PersonProfileDao

    companion object {
        const val NAME = "nakshatra_calendar.db"
    }
}
