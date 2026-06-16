package com.dosius.smart.domain.engine.learning

import com.dosius.smart.domain.engine.physiology.TherapyParameters
import com.dosius.smart.domain.model.CarbConfidence
import com.dosius.smart.domain.model.DeviationPoint
import com.dosius.smart.domain.model.Entry
import javax.inject.Inject

/**
 * Inverse carb inference for per-food learning (FoodCase observation).
 *
 * Given a meal's entered carbs and the post-meal glucose deviation, estimates the carbs the
 * body actually saw, then blends entered vs inferred according to how confident the user was
 * in their carb count. Pure domain logic, free of any persistence or framework concerns.
 */
class MealCarbInferenceEngine @Inject constructor() {

    /**
     * inferred_carbs = entered_carbs + (Σdeviation × carbFraction) × ICR / ISF.
     *
     * [carbFraction] attributes only this food's share of a multi-food meal's deviation
     * (1.0 for a single-food meal).
     */
    fun inferCarbsTotal(
        entry: Entry,
        deviations: List<DeviationPoint>,
        params: TherapyParameters,
        carbFraction: Float = 1f
    ): Float {
        val isf = params.isfAt(entry.timestamp.hour)
        val icr = params.icrAt(entry.timestamp.hour)
        val enteredCarbs = entry.totalCarbs ?: 0f
        val proportionalDeviation = deviations.sumOf { it.deviation.toDouble() }.toFloat() * carbFraction
        return enteredCarbs + proportionalDeviation * icr / isf
    }

    /**
     * Blends inferred and entered carbs-per-100g. High confidence trusts the entered value;
     * low confidence leans on the inferred (deviation-derived) value.
     */
    fun blendObservation(entry: Entry, inferredPer100g: Float, enteredPer100g: Float): Float {
        val alpha = when (entry.carbConfidence) {
            CarbConfidence.CERTAIN, CarbConfidence.HIGH -> 0f
            CarbConfidence.MEDIUM -> 0.5f
            CarbConfidence.LOW, null -> 0.9f
        }
        return alpha * inferredPer100g + (1 - alpha) * enteredPer100g
    }
}
