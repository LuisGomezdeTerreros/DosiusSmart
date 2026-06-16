package com.dosius.smart.domain.model

enum class GlucoseTrend(val displayArrow: String, val displayLabel: String, val angleDeg: Float) {
    RISING_FAST("↑↑", "Rising Fast", -90f),
    RISING("↑", "Rising", -65f),
    RISING_SLIGHTLY("↗", "Rising Slightly", -35f),
    STABLE("→", "Stable", 0f),
    FALLING_SLIGHTLY("↘", "Falling Slightly", 35f),
    FALLING("↓", "Falling", 65f),
    FALLING_FAST("↓↓", "Falling Fast", 90f);

    companion object {
        fun fromRate(rate: Float): GlucoseTrend = when {
            rate > 2.0f  -> RISING_FAST
            rate > 1.0f  -> RISING
            rate > 0.5f  -> RISING_SLIGHTLY
            rate > -0.5f -> STABLE
            rate > -1.0f -> FALLING_SLIGHTLY
            rate > -2.0f -> FALLING
            else         -> FALLING_FAST
        }
        fun fromTrendArrow(arrow: Int): GlucoseTrend = when (arrow) {
            1    -> FALLING
            2    -> FALLING_SLIGHTLY
            3    -> STABLE
            4    -> RISING_SLIGHTLY
            5    -> RISING
            else -> STABLE
        }
    }
}
