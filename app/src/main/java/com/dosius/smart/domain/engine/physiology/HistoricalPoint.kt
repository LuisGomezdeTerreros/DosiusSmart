package com.dosius.smart.domain.engine.physiology

import kotlinx.datetime.LocalDateTime

data class HistoricalPoint(
    val timestamp: LocalDateTime,
    val iob: Float,
    val cob: Float,
    val cobMinAbsorptionRatio: Float = 0f
)
