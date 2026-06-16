package com.dosius.smart.data.local.dao

import androidx.room.*
import com.dosius.smart.domain.model.Forecast
import kotlinx.coroutines.flow.Flow

@Dao
interface ForecastDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(forecast: Forecast)

    @Query("SELECT * FROM forecasts ORDER BY timestampGenerated DESC LIMIT 1")
    fun getLatest(): Flow<Forecast?>

    @Query("SELECT * FROM forecasts ORDER BY timestampGenerated DESC LIMIT 1")
    suspend fun getLatestSuspend(): Forecast?

    @Query("DELETE FROM forecasts WHERE timestampGenerated < :cutoffEpoch")
    suspend fun deleteOlderThan(cutoffEpoch: Long)
}