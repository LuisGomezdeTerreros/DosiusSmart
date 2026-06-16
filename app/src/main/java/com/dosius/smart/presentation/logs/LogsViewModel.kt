package com.dosius.smart.presentation.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosius.smart.data.repository.ContaminationRepository
import com.dosius.smart.data.repository.EntryRepository
import com.dosius.smart.data.repository.ExerciseRepository
import com.dosius.smart.domain.model.ContaminationWindow
import com.dosius.smart.domain.model.Entry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val entriesRepository: EntryRepository,
    private val exerciseRepository: ExerciseRepository,
    private val contaminationRepository: ContaminationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                entriesRepository.getAllEntries(),
                entriesRepository.getAllFoods()
            ) { entries, foods ->
                _uiState.value.copy(
                    isLoading = false,
                    entriesByDay = entries.groupBy { it.timestamp.date },
                    foodsById = foods.associateBy { it.id }
                )
            }.collect { _uiState.value = it }
        }
    }

    fun setFilter(filter: LogsFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    fun deleteEntry(entry: Entry) {
        viewModelScope.launch {
            entriesRepository.deleteEntry(entry)
        }
    }

    fun acceptMealReview(entry: Entry) {
        viewModelScope.launch {
            entriesRepository.acceptMealCarbObservation(entry, useInferredForPortion = true)
            entriesRepository.upsertEntry(entry.copy(eventClockStatus = "CONFIRMED"))
        }
    }

    fun dismissMealReview(entry: Entry) {
        viewModelScope.launch {
            val windowEnd = entry.timestamp.toInstant(TimeZone.UTC) + 180.minutes
            contaminationRepository.writeWindow(ContaminationWindow(
                id = "${entry.id}_user_dismiss",
                startTime = entry.timestamp,
                endTime = windowEnd.toLocalDateTime(TimeZone.UTC),
                reason = "USER",
                sourceEventId = entry.id,
                excludedParamTypes = "FOOD_CARBS"
            ))
            entriesRepository.updatePortionSize(entry)
            entriesRepository.upsertEntry(entry.copy(eventClockStatus = "DISMISSED"))
        }
    }

    fun acceptExerciseReview(entry: Entry) {
        viewModelScope.launch {
            exerciseRepository.acceptDropObservation(entry)
            entriesRepository.upsertEntry(entry.copy(eventClockStatus = "CONFIRMED"))
        }
    }

    fun dismissExerciseReview(entry: Entry) {
        viewModelScope.launch {
            // The contamination window is already written by ExerciseEventWorker for
            // every exercise event, so dismissing only needs to mark the entry.
            entriesRepository.upsertEntry(entry.copy(eventClockStatus = "DISMISSED"))
        }
    }
}
