package com.dosius.smart.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dosius.smart.domain.model.ExerciseCase
import com.dosius.smart.domain.model.FoodCase

@Dao
interface ExerciseCaseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exerciseCase: ExerciseCase)

    @Query("SELECT * FROM exercise_cases WHERE exerciseType = :type ORDER BY id DESC")
    suspend fun getByExerciseType(type: String) : List<ExerciseCase>

    @Query("SELECT * FROM exercise_cases")
    suspend fun getAll(): List<ExerciseCase>

    @Query("SELECT COUNT(*) FROM exercise_cases")
    suspend fun getCount(): Int
}