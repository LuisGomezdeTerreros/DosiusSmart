package com.dosius.smart.presentation.database

import com.dosius.smart.domain.model.Food


data class FoodWithStats (
    val food: Food,
    val numberEvents: Int,
    val avgQuantityGrams: Int? = null
)



data class FoodCategoryGroup(
    val categoryId: String,
    val foods: List<FoodWithStats> = emptyList(),
    val numberEvents: Int
)