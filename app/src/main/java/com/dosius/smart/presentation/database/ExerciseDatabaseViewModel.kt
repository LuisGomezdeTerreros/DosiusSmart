package com.dosius.smart.presentation.database


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosius.smart.data.repository.DatabaseSeeder
import com.dosius.smart.data.repository.EntryRepository
import com.dosius.smart.data.repository.ExerciseRepository
import com.dosius.smart.domain.model.Entry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExerciseDatabaseViewModel @Inject constructor(
    private val entryRepository: EntryRepository,
    private val seeder: DatabaseSeeder,
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<ExerciseDatabaseUiState>(ExerciseDatabaseUiState.Loading)
    val uiState: StateFlow<ExerciseDatabaseUiState> = _uiState.asStateFlow()

    private val _search = MutableStateFlow("")

    init {
        viewModelScope.launch {
            seeder.seedIfEmpty()
            val casesByType = exerciseRepository.getAllExerciseCases().groupBy { it.exerciseType }
            combine(
                _search, exerciseRepository.getAllExercises(), entryRepository.getAllEntries()
            ) { search, exercises, entries ->
                var exerciseSearch = exercises
                if (search != "") {
                    exerciseSearch = exercises.filter {
                        it.type.contains(
                            search, ignoreCase = true
                        )
                    }
                }

                val entriesByExerciseType = entries.groupBy { it.exerciseType }

                val exerciseData = exerciseSearch.map { it ->
                    val exerciseEntries =
                        entriesByExerciseType.getOrDefault(it.type, emptyList<Entry>())
                    val cases = casesByType[it.type]
                    val avgDuration = cases?.takeIf { it.isNotEmpty() }?.map { it.durationMinutes }?.average()?.toInt()
                    ExerciseDetails(
                        exercise = it, numberEntries = exerciseEntries.size, avgDurationMinutes = avgDuration
                    )
                }


                ExerciseDatabaseUiState.Success(
                    exerciseData = exerciseData,
                    search = search,
                    totalNumberExercise = exerciseSearch.size
                )

            }.collect { result ->
                _uiState.value = result
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _search.value = query
    }
}