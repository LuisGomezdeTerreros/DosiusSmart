package com.dosius.smart.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dosius.smart.domain.model.ContaminationWindow
import kotlinx.coroutines.flow.Flow

@Dao
interface ContaminationWindowDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contaminationWindow: ContaminationWindow)

    @Delete
    suspend fun delete(contaminationWindow: ContaminationWindow)

    @Query("SELECT * FROM contamination_windows ORDER BY startTime Desc")
    fun getAll(): Flow<List<ContaminationWindow>>

    @Query("SELECT * FROM contamination_windows WHERE startTime <= :timestampEpoch AND endTime >= :timestampEpoch ORDER BY startTime Desc")
    suspend fun getWindowsContaining(timestampEpoch: Long): List<ContaminationWindow>

    @Query("SELECT * FROM contamination_windows WHERE startTime >= :sinceEpoch AND reason = 'RULE_UAM'  ORDER BY startTime Desc")
    suspend fun getUAM(sinceEpoch: Long): List<ContaminationWindow>

    @Query("SELECT * FROM contamination_windows WHERE sourceEventId = :sourceEventId LIMIT 1")
    suspend fun getBySourceEventId(sourceEventId: String): ContaminationWindow?


}

