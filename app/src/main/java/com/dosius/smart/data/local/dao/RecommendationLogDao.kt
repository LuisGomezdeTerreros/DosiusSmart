package com.dosius.smart.data.local.dao

import androidx.room.*
import com.dosius.smart.domain.model.RecommendationLog
import kotlinx.coroutines.flow.Flow

@Dao
interface RecommendationLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: RecommendationLog)

    @Query("SELECT * FROM recommendation_logs ORDER BY timestamp DESC")
    fun getAll(): Flow<List<RecommendationLog>>

    @Query("SELECT * FROM recommendation_logs WHERE timestamp >= :sinceEpoch ORDER BY timestamp DESC")
    suspend fun getAfterTimestamp(sinceEpoch: Long): List<RecommendationLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateUserAction(recommendationLog : RecommendationLog)
}
