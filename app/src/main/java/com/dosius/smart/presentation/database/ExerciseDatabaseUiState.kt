package com.dosius.smart.presentation.database

import com.dosius.smart.domain.model.Exercise

sealed class ExerciseDatabaseUiState() {

    object Loading : ExerciseDatabaseUiState()
    data class Success(
        val exerciseData: List<ExerciseDetails>,
        val search: String,
        val totalNumberExercise: Int
    ) : ExerciseDatabaseUiState()

    data class Error(val message: String) : ExerciseDatabaseUiState()
}
