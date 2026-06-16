package com.dosius.smart.domain.engine.physiology

import com.dosius.smart.domain.model.CarbConfidence
import com.dosius.smart.domain.model.DeviationPoint
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class COBCalculatorTest {

    private val calculator = COBCalculator()
    private val params = TherapyParameters.defaults()  // ISF=50, ICR=10
    private val now = LocalDateTime(2024, 6, 15, 12, 0, 0)

    private fun timeAt(minutesAgo: Long): LocalDateTime {
        val total = 12 * 60 - minutesAgo
        return LocalDateTime(2024, 6, 15, (total / 60).toInt(), (total % 60).toInt(), 0)
    }

    private fun meal(minutesAgo: Long, carbs: Float = 30f) =
        MealEvent(carbsG = carbs, timestamp = timeAt(minutesAgo), carbConfidence = CarbConfidence.HIGH)

    private fun deviation(minutesAgo: Long, observedDelta: Float = 0f, bgi: Float = 0f) =
        DeviationPoint(
            id = "dp-$minutesAgo",
            timestamp = timeAt(minutesAgo),
            predictedDelta = 0f,
            observedDelta = observedDelta,
            deviation = 0f,
            bgi = bgi,
            fastingWindow = true,
            postprandialWindow = false,
            carbConfidence = CarbConfidence.LOW,
            recentExercise = false,
            outlierFlag = false,
            timeSlot = 12,
            dayOfWeek = 6,
            iobPresent = false,
            cobPresent = false
        )

    // ── No meals ──────────────────────────────────────────────────────────────

    @Test
    fun `no meals returns zero COB`() {
        val status = calculator.calculate(emptyList(), emptyList(), params, now)
        assertEquals(0f, status.totalCob, 0.001f)
    }

    // ── Absorption window filtering ────────────────────────────────────────────

    @Test
    fun `meal older than 300 min is excluded`() {
        val status = calculator.calculate(listOf(meal(minutesAgo = 305)), emptyList(), params, now)
        assertEquals(0f, status.totalCob, 0.001f)
    }

    @Test
    fun `future meal with negative elapsed is excluded`() {
        val futureMeal = MealEvent(
            carbsG = 30f,
            timestamp = LocalDateTime(2024, 6, 15, 13, 0, 0), // 1h in the future
            carbConfidence = CarbConfidence.HIGH
        )
        val status = calculator.calculate(listOf(futureMeal), emptyList(), params, now)
        assertEquals(0f, status.totalCob, 0.001f)
    }

    @Test
    fun `meal 30 min ago with no deviations retains full COB`() {
        val status = calculator.calculate(listOf(meal(minutesAgo = 30)), emptyList(), params, now)
        assertEquals(30f, status.totalCob, 0.001f)
    }

    // ── Minimum absorption floor ───────────────────────────────────────────────

    @Test
    fun `one deviation point with zero observedDelta applies minimum 8g per step floor`() {
        // insulinCounteraction = 0 - 0 = 0; absorbStep floor = 8; absorbed = 8 * 10/50 = 1.6g
        val dev = deviation(minutesAgo = 15)
        val status = calculator.calculate(listOf(meal(minutesAgo = 30)), listOf(dev), params, now)
        assertEquals(30f - 1.6f, status.totalCob, 0.01f)
    }

    @Test
    fun `large positive deviation absorbs more than the floor`() {
        // insulinCounteraction = 60 - 0 = 60; absorbed = max(8,60) * 10/50 = 12g
        val dev = deviation(minutesAgo = 15, observedDelta = 60f)
        val status = calculator.calculate(listOf(meal(minutesAgo = 30)), listOf(dev), params, now)
        assertEquals(30f - 12f, status.totalCob, 0.01f)
    }

    // ── COB never goes negative ────────────────────────────────────────────────

    @Test
    fun `COB is clamped to zero even when absorption exceeds remaining`() {
        // 5g meal, each step absorbs at least 1.6g; two steps with high deviation absorb more than 5g
        val smallMeal = meal(minutesAgo = 30, carbs = 5f)
        val devs = listOf(
            deviation(minutesAgo = 25, observedDelta = 100f),
            deviation(minutesAgo = 20, observedDelta = 100f)
        )
        val status = calculator.calculate(listOf(smallMeal), devs, params, now)
        assertTrue("COB should be >= 0 but was ${status.totalCob}", status.totalCob >= 0f)
    }

    // ── Multiple meals ─────────────────────────────────────────────────────────

    @Test
    fun `two active meals sum their remaining COB`() {
        // Both meals at 30g, no deviations → total = 60g
        val meals = listOf(meal(minutesAgo = 30), meal(minutesAgo = 60))
        val status = calculator.calculate(meals, emptyList(), params, now)
        assertEquals(60f, status.totalCob, 0.001f)
    }

    @Test
    fun `deviation before meal timestamp is not applied to that meal`() {
        // Deviation at 45 min ago, meal at 30 min ago → deviation predates meal, not applied
        val dev = deviation(minutesAgo = 45, observedDelta = 60f)
        val status = calculator.calculate(listOf(meal(minutesAgo = 30)), listOf(dev), params, now)
        assertEquals(30f, status.totalCob, 0.001f)
    }

    // ── minAbsorptionRatio ────────────────────────────────────────────────────

    @Test
    fun `minAbsorptionRatio is 1_0 when all steps use the floor`() {
        // insulinCounteraction = 0 → all steps use floor → ratio = 1.0
        val devs = listOf(deviation(minutesAgo = 20), deviation(minutesAgo = 15))
        val status = calculator.calculate(listOf(meal(minutesAgo = 30)), devs, params, now)
        assertEquals(1.0f, status.minAbsorptionRatio, 0.001f)
    }

    @Test
    fun `minAbsorptionRatio is 0_0 when all deviations exceed the floor`() {
        // observedDelta = 100 >> 8 → no floor used → ratio = 0.0
        val devs = listOf(
            deviation(minutesAgo = 20, observedDelta = 100f),
            deviation(minutesAgo = 15, observedDelta = 100f)
        )
        val status = calculator.calculate(listOf(meal(minutesAgo = 30)), devs, params, now)
        assertEquals(0.0f, status.minAbsorptionRatio, 0.001f)
    }
}
