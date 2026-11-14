package io.github.anjalymsajeev.nakshatracalendar.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonProfileDao {
    @Query("SELECT COUNT(*) FROM person_profile")
    fun observeCount(): Flow<Int>
}
