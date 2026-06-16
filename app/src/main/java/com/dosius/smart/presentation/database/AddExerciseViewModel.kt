package com.dosius.smart.presentation.database

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosius.smart.data.repository.ExerciseRepository
import com.dosius.smart.domain.model.Exercise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddExerciseViewModel @Inject constructor(
    private val repository: ExerciseRepository
) : ViewModel() {

    private val _type = MutableStateFlow<String>("")
    val type: StateFlow<String> = _type.asStateFlow()

    private val _expectedGlucoseDropPerHour = MutableStateFlow<String>("")
    val expectedGlucoseDropPerHour: StateFlow<String> = _expectedGlucoseDropPerHour.asStateFlow()

    private val _expectedGlucoseDropPerHourError = MutableStateFlow<String?>(null)
    val expectedGlucoseDropPerHourError: StateFlow<String?> = _expectedGlucoseDropPerHourError.asStateFlow()

    private val _confidencePercentage = MutableStateFlow<String>("")
    val confidencePercentage: StateFlow<String> = _confidencePercentage.asStateFlow()

    private val _confidencePercentageError = MutableStateFlow<String?>(null)
    val confidencePercentageError: StateFlow<String?> = _confidencePercentageError.asStateFlow()

    private val _isSaved = MutableStateFlow<Boolean>(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    fun saveExercise(){
        val exercise = Exercise(
            type = _type.value,
            expectedGlucoseDropPerHour = _expectedGlucoseDropPerHour.value.toFloatOrNull(),
            confidencePercentage = _confidencePercentage.value.toIntOrNull()
        )
        viewModelScope.launch {
            repository.upsertExercise(exercise)
            _isSaved.value = true
        }
    }

    fun onTypeChange(input: String){
        _type.value = input
    }

    fun onExpectedGlucoseDropPerHourChange(input: String){
        _expectedGlucoseDropPerHour.value = input
        _expectedGlucoseDropPerHourError.value = when {
            input.isBlank() -> null
            input.toFloatOrNull() == null -> "Must be a number"
            input.toFloat() < 0f ->
                "Must be ≥ 0"
            else -> null
        }
    }

    fun onConfidencePercentageChange(input: String){
        _confidencePercentage.value = input
        _confidencePercentageError.value = when {
            input.isBlank() -> null
            input.toIntOrNull() == null -> "Must be a whole number"
            input.toInt() !in 0..100 -> "Must be 0–100"
            else -> null
        }
    }
}
