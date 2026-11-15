package io.github.anjalymsajeev.nakshatracalendar.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.anjalymsajeev.nakshatracalendar.data.local.entity.PersonProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonProfileDao {
    @Query("SELECT COUNT(*) FROM person_profile")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM person_profile ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<PersonProfileEntity>>

    @Query("SELECT * FROM person_profile WHERE id = :id")
    suspend fun get(id: Long): PersonProfileEntity?

    /** Inserts when id is 0 (returns the new id) or replaces the existing row (returns -1). */
    @Upsert
    suspend fun upsert(entity: PersonProfileEntity): Long

    @Query("DELETE FROM person_profile WHERE id = :id")
    suspend fun delete(id: Long): Int
}
