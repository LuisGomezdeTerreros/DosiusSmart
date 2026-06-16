package com.dosius.smart.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dosius.smart.domain.model.GlucoseReading
import kotlinx.coroutines.flow.Flow

@Dao
interface GlucoseReadingDao {
    @Query("SELECT * FROM glucose_readings ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestReading(): GlucoseReading?

    @Query("SELECT * FROM glucose_readings ORDER BY ABS(timestamp - :epochSeconds) LIMIT 1")
    suspend fun getReadingClosestTo(epochSeconds: Long): GlucoseReading?

    @Query("SELECT * FROM glucose_readings ORDER BY timestamp DESC")
    fun getAllReadings(): Flow<List<GlucoseReading>>

    @Query("SELECT * FROM glucose_readings WHERE timestamp >= :sinceEpoch ORDER BY timestamp DESC")
    suspend fun getReadingsAfterTimestamp(sinceEpoch: Long): List<GlucoseReading>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(readings: List<GlucoseReading>)

    @Query("DELETE FROM glucose_readings WHERE timestamp < :cutoffEpoch")
    suspend fun deleteOlderThan(cutoffEpoch: Long)
}
