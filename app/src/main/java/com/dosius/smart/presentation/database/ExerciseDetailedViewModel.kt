package com.dosius.smart.presentation.database

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosius.smart.data.repository.EntryRepository
import com.dosius.smart.data.repository.ExerciseRepository
import com.dosius.smart.domain.model.Exercise
import com.dosius.smart.domain.model.ExerciseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExerciseDetailedViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val entryRepository: EntryRepository,
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {

    private val type: String = savedStateHandle["type"] ?: ""

    private val _uiState = MutableStateFlow<ExerciseDetailUiState>(ExerciseDetailUiState.Loading)
    val uiState: StateFlow<ExerciseDetailUiState> = _uiState.asStateFlow()

    private val _expectedGlucoseDropPerHour = MutableStateFlow("")
    val expectedGlucoseDropPerHour: StateFlow<String> = _expectedGlucoseDropPerHour.asStateFlow()

    private val _expectedGlucoseDropPerHourError = MutableStateFlow<String?>(null)
    val expectedGlucoseDropPerHourError: StateFlow<String?> = _expectedGlucoseDropPerHourError.asStateFlow()

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private val _cases = MutableStateFlow<List<ExerciseCase>>(emptyList())

    init {
        viewModelScope.launch {
            _cases.value = exerciseRepository.getCasesForType(type)
        }
        viewModelScope.launch {
            combine(
                exerciseRepository.getExerciseById(type),
                entryRepository.getEntriesByExerciseType(type),
                _cases
            ) { exercise, entries, cases ->
                if (exercise == null) ExerciseDetailUiState.Error("Exercise not found")
                else ExerciseDetailUiState.Success(
                    exercise = exercise,
                    entries = entries,
                    cases = cases,
                    stats = buildStats(exercise, cases)
                )
            }.collect { result ->
                _uiState.value = result
            }
        }
    }

    private fun buildStats(exercise: Exercise, cases: List<ExerciseCase>): ExerciseStats {
        val avgObservedDropPerHour = cases.takeIf { it.isNotEmpty() }
            ?.map { it.actualDrop.toFloat() / (it.durationMinutes / 60f) }
            ?.average()?.toFloat()
        val avgDuration = cases.takeIf { it.isNotEmpty() }
            ?.map { it.durationMinutes }
            ?.average()?.toFloat()
        return ExerciseStats(
            currentDropPerHour = exercise.expectedGlucoseDropPerHour,
            averageObservedDropPerHour = avgObservedDropPerHour,
            averageDurationMinutes = avgDuration,
            nCases = cases.size,
            recentCases = cases.take(10)
        )
    }

    fun saveChanges() {
        val current = (_uiState.value as? ExerciseDetailUiState.Success)?.exercise ?: return
        val updated = current.copy(
            expectedGlucoseDropPerHour = _expectedGlucoseDropPerHour.value.toFloatOrNull()
        )
        viewModelScope.launch {
            exerciseRepository.upsertExercise(updated)
            _isEditing.value = false
        }
    }

    fun onExpectedGlucoseDropPerHourChange(input: String) {
        _expectedGlucoseDropPerHour.value = input
        _expectedGlucoseDropPerHourError.value = when {
            input.isBlank() -> null
            input.toFloatOrNull() == null -> "Must be a number"
            input.toFloat() < 0f -> "Must be ≥ 0"
            else -> null
        }
    }

    fun onEditToggle() {
        if (!_isEditing.value) {
            val exercise = (_uiState.value as? ExerciseDetailUiState.Success)?.exercise ?: return
            _expectedGlucoseDropPerHour.value = exercise.expectedGlucoseDropPerHour?.toString() ?: ""
            _expectedGlucoseDropPerHourError.value = null
        }
        _isEditing.value = !_isEditing.value
    }
}
