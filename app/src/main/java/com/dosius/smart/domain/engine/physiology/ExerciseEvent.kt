package com.dosius.smart.domain.engine.physiology

import kotlinx.datetime.LocalDateTime

data class ExerciseEvent(
    val startTime: LocalDateTime,
    val durationMin: Float,
    val expectedDropPerHour: Float
)
