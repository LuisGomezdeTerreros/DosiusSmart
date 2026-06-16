package com.dosius.smart.domain.engine.physiology

import com.dosius.smart.domain.model.TherapyParameter

data class TherapyParameters(
    val isfBySlot: FloatArray,
    val icrBySlot: FloatArray,
    val targetBgLow: Int = 70,
    val targetBgHigh: Int = 180,
    val basalUnits: Float,
    val diaMinutes: Float = 300f
) {
    val targetBg: Int get() = (targetBgLow + targetBgHigh) / 2

    fun isfAt(hour: Int): Float = isfBySlot[hour.coerceIn(0, 23)]
    fun icrAt(hour: Int): Float = icrBySlot[hour.coerceIn(0, 23)]

    companion object {
        fun defaults(): TherapyParameters = TherapyParameters(
            isfBySlot = FloatArray(24) { 50f },
            icrBySlot = FloatArray(24) { 10f },
            targetBgLow = 70,
            targetBgHigh = 180,
            basalUnits = 0f
        )

        fun fromDb(rows: List<TherapyParameter>): TherapyParameters {
            val isf = FloatArray(24) { 50f }
            val icr = FloatArray(24) { 10f }
            var basalUnits = 0f
            var targetBgLow = 70
            var targetBgHigh = 180
            for (row in rows) {
                when (row.parameterType) {
                    "ISF"         -> isf[row.slotIndex] = row.currentValue
                    "ICR"         -> icr[row.slotIndex] = row.currentValue
                    "BASAL"       -> basalUnits = row.currentValue
                    "TARGET_LOW"  -> targetBgLow = row.currentValue.toInt()
                    "TARGET_HIGH" -> targetBgHigh = row.currentValue.toInt()
                }
            }
            return TherapyParameters(
                isfBySlot = isf,
                icrBySlot = icr,
                targetBgLow = targetBgLow,
                targetBgHigh = targetBgHigh,
                basalUnits = basalUnits
            )
        }
    }
}