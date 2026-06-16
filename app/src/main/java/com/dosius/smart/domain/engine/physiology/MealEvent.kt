package com.dosius.smart.domain.engine.physiology

import com.dosius.smart.domain.model.CarbConfidence
import com.dosius.smart.domain.model.Entry
import kotlinx.datetime.LocalDateTime

data class MealEvent(
    val carbsG: Float,
    val timestamp: LocalDateTime,
    val carbConfidence: CarbConfidence?
)

fun buildMealEvents(entries: List<Entry>): List<MealEvent> {
    val foodEntries = entries.filter { it.totalCarbs != null && it.totalCarbs != 0f }
    val (grouped, solo) = foodEntries.partition { it.mealGroupId != null }
    val merged = grouped.groupBy { it.mealGroupId!! }.values.map { siblings ->
        MealEvent(
            carbsG = siblings.sumOf { (it.totalCarbs ?: 0f).toDouble() }.toFloat(),
            timestamp = siblings.first().timestamp,
            carbConfidence = siblings.mapNotNull { it.carbConfidence }.maxByOrNull { it.ordinal }
        )
    }
    return solo.map { MealEvent(it.totalCarbs!!, it.timestamp, it.carbConfidence) } + merged
}
