package com.dosius.smart.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "foods")
data class Food(
    @PrimaryKey val id: String,
    val name: String,
    val idCategory: String,
    val carbsPer100g: Float?,
    val carbsRecommended: Float?,
    val glycemicIndex: Float?,
    val isCustom: Boolean,
    val isIngredient: Boolean,
    val ingredients: List<Ingredient> = emptyList(),
    val entries: Int,
    val posteriorMean: Float? = null,
    val posteriorVariance: Float? = null,
    val nObservations: Int = 0,
    val typicalAbsorptionMinutes: Int? = null,
    val isCategory: Boolean = false
)
