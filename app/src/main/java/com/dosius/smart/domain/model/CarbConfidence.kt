package com.dosius.smart.domain.model

enum class CarbConfidence {
    CERTAIN,  // user weighed/measured — ground truth, no food posterior update
    HIGH,     // user is confident — ICR learning only, no food posterior update
    MEDIUM,   // rough estimate — food posterior updated, excluded from ICR
    LOW,      // guessing — food posterior updated with high deviation weight
}
