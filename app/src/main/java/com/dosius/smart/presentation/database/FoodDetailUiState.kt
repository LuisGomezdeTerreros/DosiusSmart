package com.dosius.smart.presentation.database

import com.dosius.smart.domain.model.Entry
import com.dosius.smart.domain.model.Food
import com.dosius.smart.domain.model.FoodCase

data class FoodStats(
    val nCases: Int,
    val averageQuantityG: Float?,
    val posteriorMean: Float?,
    val posteriorStd: Float?,
    val seededDefault: Float?,
    val glycemicSuccessRate: Float?,
    val recentCases: List<FoodCase>
)

sealed class FoodDetailUiState() {

    object Loading : FoodDetailUiState()
    data class Success(
        val events: List<Entry>,
        val food: Food,
        val stats: FoodStats
    ) : FoodDetailUiState()

    data class Error(val message: String) : FoodDetailUiState()
}
