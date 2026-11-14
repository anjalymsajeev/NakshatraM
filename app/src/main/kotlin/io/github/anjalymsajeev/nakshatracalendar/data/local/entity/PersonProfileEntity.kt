package io.github.anjalymsajeev.nakshatracalendar.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A family member. Schema v1; screens and the full DAO arrive in Sprint 2. */
@Entity(tableName = "person_profile", indices = [Index("name")])
data class PersonProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 1..27, matching the library's Nakshatra.number. */
    @ColumnInfo(name = "nakshatra_number") val nakshatraNumber: Int,
    @ColumnInfo(name = "birth_date") val birthDate: String? = null,
    @ColumnInfo(name = "birth_time") val birthTime: String? = null,
    @ColumnInfo(name = "birth_location") val birthLocation: String? = null,
    @ColumnInfo(name = "location_name") val locationName: String,
    @ColumnInfo(name = "location_latitude") val locationLatitude: Double,
    @ColumnInfo(name = "location_longitude") val locationLongitude: Double,
    @ColumnInfo(name = "time_zone") val timeZone: String,
    @ColumnInfo(name = "event_color") val eventColor: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
