package com.dosius.smart.presentation.database

import com.dosius.smart.domain.model.Entry
import com.dosius.smart.domain.model.Exercise
import com.dosius.smart.domain.model.ExerciseCase

sealed class ExerciseDetailUiState {

    object Loading : ExerciseDetailUiState()

    data class Success(
        val entries: List<Entry>,
        val exercise: Exercise,
        val cases: List<ExerciseCase>,
        val stats: ExerciseStats
    ) : ExerciseDetailUiState()

    data class Error(val message: String) : ExerciseDetailUiState()
}