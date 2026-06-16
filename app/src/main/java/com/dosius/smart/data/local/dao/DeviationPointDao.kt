package com.dosius.smart.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dosius.smart.domain.model.DeviationPoint
import kotlinx.coroutines.flow.Flow


@Dao
interface DeviationPointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: DeviationPoint)

    @Query("SELECT * FROM deviation_points WHERE timestamp >= :sinceEpoch ORDER BY timestamp DESC")
    suspend fun getAfterTimestamp(sinceEpoch: Long): List<DeviationPoint>

    @Query("SELECT * FROM deviation_points WHERE timestamp >= :sinceEpoch ORDER BY timestamp DESC")
    fun observeAfterTimestamp(sinceEpoch: Long): Flow<List<DeviationPoint>>

    @Query("DELETE FROM deviation_points WHERE timestamp < :cutoffEpoch ")
    suspend fun deleteOlderThan(cutoffEpoch: Long)
}