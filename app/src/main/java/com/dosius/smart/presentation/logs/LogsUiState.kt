package com.dosius.smart.presentation.logs

import com.dosius.smart.domain.model.Entry
import com.dosius.smart.domain.model.Food
import kotlinx.datetime.LocalDate

enum class LogsFilter { ALL, FOOD, EXERCISE, BOLUS, FAST_CARBS }

data class LogsUiState(
    val isLoading: Boolean = true,
    val entriesByDay: Map<LocalDate, List<Entry>> = emptyMap(),
    val foodsById: Map<String, Food> = emptyMap(),
    val activeFilter: LogsFilter = LogsFilter.ALL
)
