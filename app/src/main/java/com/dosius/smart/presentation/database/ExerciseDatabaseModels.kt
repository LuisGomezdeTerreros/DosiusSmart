package com.dosius.smart.presentation.database

import com.dosius.smart.domain.model.Exercise
import com.dosius.smart.domain.model.ExerciseCase

data class ExerciseDetails(
    val exercise: Exercise,
    val numberEntries: Int,
    val avgDurationMinutes: Int? = null
)

data class ExerciseStats(
    val currentDropPerHour: Float?,
    val averageObservedDropPerHour: Float?,
    val averageDurationMinutes: Float?,
    val nCases: Int,
    val recentCases: List<ExerciseCase>
)