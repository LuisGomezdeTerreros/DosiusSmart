package com.dosius.smart.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dosius.smart.domain.model.Entry
import com.dosius.smart.domain.model.TherapyParameter
import kotlinx.coroutines.flow.Flow

@Dao

interface TherapyParameterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(therapyParameters: List<TherapyParameter>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(therapyParameter: TherapyParameter)

    @Query("SELECT * FROM therapy_parameters ORDER BY slotIndex ASC")
    fun getAll(): Flow<List<TherapyParameter>>

    @Query("SELECT * FROM therapy_parameters ORDER BY slotIndex ASC")
    suspend fun getAllSuspend(): List<TherapyParameter>

    @Query("SELECT * FROM therapy_parameters WHERE slotIndex = :slot AND  parameterType  = :type ORDER BY slotIndex ASC LIMIT 1")
    suspend fun getBySlotAndType(slot: Int, type: String) : TherapyParameter?

    @Query("SELECT COUNT(*) FROM therapy_parameters")
    suspend fun getCount():Int
}