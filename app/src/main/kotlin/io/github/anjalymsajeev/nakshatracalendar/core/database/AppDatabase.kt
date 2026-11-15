package io.github.anjalymsajeev.nakshatracalendar.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.anjalymsajeev.nakshatracalendar.data.local.dao.PersonProfileDao
import io.github.anjalymsajeev.nakshatracalendar.data.local.dao.SyncDao
import io.github.anjalymsajeev.nakshatracalendar.data.local.entity.CalendarEventEntity
import io.github.anjalymsajeev.nakshatracalendar.data.local.entity.CalendarSyncEntity
import io.github.anjalymsajeev.nakshatracalendar.data.local.entity.NakshatraOccurrenceEntity
import io.github.anjalymsajeev.nakshatracalendar.data.local.entity.PersonProfileEntity
import io.github.anjalymsajeev.nakshatracalendar.data.local.entity.ReminderConfigurationEntity

/** Schemas are exported to app/schemas; every version change needs a migration and a migration test. */
@Database(
    entities = [
        PersonProfileEntity::class,
        NakshatraOccurrenceEntity::class,
        CalendarSyncEntity::class,
        CalendarEventEntity::class,
        ReminderConfigurationEntity::class
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        // v2: birth place coordinates and time zone (nullable columns).
        AutoMigration(from = 1, to = 2),
        // v3: Google Calendar sync tables (new tables only).
        AutoMigration(from = 2, to = 3)
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personProfileDao(): PersonProfileDao

    abstract fun syncDao(): SyncDao

    companion object {
        const val NAME = "nakshatra_calendar.db"
    }
}
