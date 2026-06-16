package com.dosius.smart.presentation.database


sealed class FoodDatabaseUiState() {

    object Loading : FoodDatabaseUiState()
    data class Success(
        val foodData: List<FoodCategoryGroup>,
        val search: String,
        val totalNumberFood: Int
    ) : FoodDatabaseUiState()

    data class Error(val message: String) : FoodDatabaseUiState()
}