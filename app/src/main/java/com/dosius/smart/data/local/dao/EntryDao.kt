package com.dosius.smart.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dosius.smart.domain.model.Entry
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Query("SELECT * FROM entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE timestamp > :sinceEpoch  ORDER BY timestamp DESC ")
    suspend fun getRecentEntriesSince(sinceEpoch : Long): List<Entry>

    @Query("SELECT * FROM entries WHERE foodId = :foodId ORDER BY timestamp DESC")
    fun getEntriesByFoodId(foodId: String): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE exerciseType = :type ORDER BY timestamp DESC")
    fun getEntriesByExerciseType(type: String): Flow<List<Entry>>

    @Query("SELECT COUNT(*) FROM entries")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: Entry)

    @Delete
    suspend fun deleteEntry(entry: Entry)

    @Query("SELECT * FROM entries WHERE id = :id LIMIT 1")
    fun getEntriesById(id: String): Entry

    @Query("SELECT * FROM entries WHERE id = :id LIMIT 1")
    suspend fun getEntryByIdSuspend(id: String): Entry?

    @Query("SELECT * FROM entries WHERE eventClockStatus IN ('PENDING', 'PENDING_EXERCISE') ")
    fun getPendingReviews(): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE mealGroupId = :mealGroupId")
    suspend fun getEntriesByMealGroupId(mealGroupId: String): List<Entry>

    @Query("SELECT * FROM entries WHERE isfLearningEnabled = 1 ORDER BY timestamp DESC")
    suspend fun getEntriesWithIsfLearning(): List<Entry>

    @Query("SELECT * FROM entries WHERE sleepWindowEnd IS NOT NULL ORDER BY timestamp DESC")
    suspend fun getSleepEntries(): List<Entry>

}
