package com.dosius.smart.domain.engine.physiology

import com.dosius.smart.domain.model.DeviationPoint
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.max

data class COBStatus(
    val totalCob: Float,
    val minAbsorptionRatio: Float
)

class COBCalculator @Inject constructor() {
    private fun elapsedMinutes(from: LocalDateTime, to: LocalDateTime): Float {
        return ChronoUnit.MINUTES.between(
            from.toJavaLocalDateTime(), to.toJavaLocalDateTime()
        ).toFloat()
    }

    fun calculate(
        meals: List<MealEvent>,
        recentDeviations: List<DeviationPoint>,
        params: TherapyParameters,
        now: LocalDateTime
    ): COBStatus {
        var totalCob = 0f
        var totalSteps = 0
        var minSteps = 0
        for (meal in meals) {
            val elapsed = elapsedMinutes(meal.timestamp, now)
            if (elapsed < 0f || elapsed > 300f) continue
            val isf = params.isfAt(meal.timestamp.hour)
            val icr = params.icrAt(meal.timestamp.hour)
            var cobRemaining = meal.carbsG
            var devsAfterMeal = 0
            for (deviation in recentDeviations) {
                if (meal.timestamp < deviation.timestamp && cobRemaining > 0f) {
                    val insulinCounteraction = deviation.observedDelta - deviation.bgi
                    val usedMin = insulinCounteraction < CarbAbsorptionModel.MIN_5M_CARB_IMPACT_MGDL
                    val absorbed = CarbAbsorptionModel.absorbStep(
                        cobRemaining, insulinCounteraction, icr, isf
                    )
                    cobRemaining = max(0f, cobRemaining - absorbed)
                    totalSteps++
                    if (usedMin) minSteps++
                    devsAfterMeal++
                }
            }
            // Fallback for backdated meals: if fewer deviation points exist than elapsed 5-min
            // ticks (service was not running or data is sparse), apply the minimum absorption
            // floor for each missing step so COB always decreases at least at the Loop minimum rate.
            val expectedSteps = (elapsed / 5f).toInt()
            val missingSteps = expectedSteps - devsAfterMeal
            if (missingSteps > 0 && cobRemaining > 0f) {
                val fallbackAbsorbed = missingSteps * CarbAbsorptionModel.MIN_5M_CARB_IMPACT_MGDL * icr / isf
                cobRemaining = max(0f, cobRemaining - fallbackAbsorbed)
                totalSteps += missingSteps
                minSteps += missingSteps
            }
            totalCob += cobRemaining
        }
        val ratio = if (totalSteps > 0) minSteps.toFloat() / totalSteps else 0f
        return COBStatus(totalCob, ratio)
    }
}

