package com.dosius.smart.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDateTime

@Entity(tableName = "exercise_cases")
data class ExerciseCase(
    @PrimaryKey val id: String,
    val exerciseType: String,
    val timestamp: LocalDateTime,
    val preExerciseBg: Int,
    val iobAtStart: Float,
    val durationMinutes: Float,
    val intensity: String,
    val expectedDrop: Int,
    val actualDrop: Int,
    val observedIauc: Float
)