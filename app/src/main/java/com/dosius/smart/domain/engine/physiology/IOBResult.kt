package com.dosius.smart.domain.engine.physiology

data class IOBResult (
    val totalIob: Float,
    val contributionByDose: List<Float>
)