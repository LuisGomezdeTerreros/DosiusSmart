package com.dosius.smart.domain.engine.physiology

import com.dosius.smart.domain.model.InsulinType
import kotlinx.datetime.LocalDateTime

data class InsulinDose(
    val units: Float,
    val timestamp: LocalDateTime,
    val type: InsulinType
)
