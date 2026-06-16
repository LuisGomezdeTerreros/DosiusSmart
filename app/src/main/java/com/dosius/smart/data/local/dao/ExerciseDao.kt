package com.dosius.smart.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dosius.smart.domain.model.Exercise
import com.dosius.smart.domain.model.Food
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises")
    fun getAllExercises(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE type = :type")
    fun getExerciseById(type: String): Flow<Exercise?>

    @Query("SELECT * FROM exercises WHERE type = :type")
    suspend fun getByType(type: String): Exercise?

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExercise(exercise: Exercise)

    @Delete
    suspend fun deleteExercise(exercise: Exercise)
}