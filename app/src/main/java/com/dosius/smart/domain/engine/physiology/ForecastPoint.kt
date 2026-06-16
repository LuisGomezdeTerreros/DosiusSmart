package com.dosius.smart.domain.engine.physiology

import kotlinx.serialization.Serializable

@Serializable
data class ForecastPoint(
    val deltaMinutes : Int,
    val predictedBg: Float,
    val predictedIob : Float = 0f,
    val predictedCob : Float = 0f

)
