package com.dosius.smart.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDateTime

@Entity(tableName = "food_cases")
data class FoodCase(
    @PrimaryKey val id: String,
    val foodId: String,
    val mealEventId: String,
    val timeOfDayBucket: Int,
    val dayOfWeek: Int,
    val quantityG: Float,
    val preMealBg: Int,
    val iobAtStart: Float,
    val enteredCarbsPer100g: Float,
    val inferredCarbsPer100g: Float,
    val carbConfidence: CarbConfidence,
    val observedIauc: Float,
    val peakBg: Int,
    val postprandialCurveJson: String
)
