package com.dosius.smart.domain.engine.physiology

import kotlin.math.exp
import kotlin.math.max

object InsulinCurve {

    /**
     * OpenAPS exponential insulin curve (oref0 0.6+, Dragan Maksimovic model).
     * Returns the fraction of insulin still on board at [elapsedMinutes] after bolus.
     *
     * @param elapsedMinutes time since dose
     * @param peak minutes until peak activity (75 for rapid-acting, 55 for ultra-rapid/Fiasp)
     * @param dia duration of insulin activity in minutes (minimum 300)
     */
    fun iobFraction(elapsedMinutes: Float, peak: Float = 55f, dia: Float = 300f): Float {
        val end = dia.coerceAtLeast(300f)
        if (elapsedMinutes <= 0f) return 1f
        if (elapsedMinutes >= end) return 0f

        val tp = peak.coerceIn(35f, 120f)
        val tau = tp * (1 - tp / end) / (1 - 2 * tp / end)
        val a = 2 * tau / end
        val s = 1.0 / (1 - a + (1 + a) * exp((-end / tau).toDouble()))

        val t = elapsedMinutes.toDouble()
        val iob = 1.0 - s * (1 - a) * (
                (t * t / (tau * end * (1 - a)) - t / tau - 1) * exp(-t / tau) + 1
                )
        return iob.toFloat().coerceIn(0f, 1f)
    }

    fun basalIobFraction(elapsedMinutes: Float, diaHours: Float = 24f): Float {
        return max(0f, 1f - elapsedMinutes / (diaHours * 60f))
    }
}
