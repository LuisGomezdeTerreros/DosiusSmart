package com.dosius.smart.domain.engine.recommendation

data class RecommendationResult(
    val recommendedUnits: Float?,
    val recommendedCarbs: Int?,
    val components: RecommendationComponents,
    val warnings: List<String>,
    val label: String? = null
)
