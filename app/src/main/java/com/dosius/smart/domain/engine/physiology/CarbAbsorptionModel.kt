package com.dosius.smart.domain.engine.physiology

import kotlin.math.max

object CarbAbsorptionModel {
    const val MIN_5M_CARB_IMPACT_MGDL = 8f
    fun absorbStep(cobRemaining: Float, observedDeviation: Float, icr: Float, isf: Float): Float {
        return max(MIN_5M_CARB_IMPACT_MGDL, observedDeviation) * icr / isf
    }
}