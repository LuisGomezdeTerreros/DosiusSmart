package com.dosius.smart.data.local.dao

import androidx.room.*
import com.dosius.smart.domain.model.AlertEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AlertEvent)

    @Query("SELECT * FROM alert_events ORDER BY timestamp DESC")
    fun getAll(): Flow<List<AlertEvent>>
}