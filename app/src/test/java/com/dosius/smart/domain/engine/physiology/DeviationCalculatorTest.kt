package com.dosius.smart.domain.engine.physiology

import com.dosius.smart.domain.model.CarbConfidence
import com.dosius.smart.domain.model.Entry
import com.dosius.smart.domain.model.ExerciseIntensity
import com.dosius.smart.domain.model.InsulinType
import com.dosius.smart.domain.model.RegistrationMethod
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviationCalculatorTest {

    private val calculator = DeviationCalculator(IOBCalculator(), COBCalculator(), GlucosePredictor())
    private val params = TherapyParameters.defaults() // ISF=50, ICR=10, DIA=300
    private val now = LocalDateTime(2024, 6, 15, 12, 0, 0)

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun exerciseEntry(minutesAgo: Long): Entry {
        val totalMinutes = 12 * 60 - minutesAgo
        return Entry(
            id = "ex-1",
            timestamp = LocalDateTime(2024, 6, 15, (totalMinutes / 60).toInt(), (totalMinutes % 60).toInt(), 0),
            createdAt = now,
            foodId = null, quantity = null, totalCarbs = null, carbConfidence = null,
            mealType = null, registrationMethod = RegistrationMethod.MANUAL,
            insulinUnits = null, insulinType = null, recommendedUnits = null, currentGlucose = null,
            exerciseType = "Running", durationOfExercise = 30f, intensity = ExerciseIntensity.MEDIUM,
            recommendedCarbs = null
        )
    }

    // ── observedDelta ─────────────────────────────────────────────────────────

    @Test
    fun `observedDelta is bgNow minus bgPrev as float`() {
        val point = calculator.compute(
            bgNow = 130, bgPrev = 120,
            doses = emptyList(), meals = emptyList(),
            cobDeviations = emptyList(), entries = emptyList(),
            params = params, now = now
        )
        assertEquals(10f, point.observedDelta, 0.001f)
    }

    @Test
    fun `observedDelta is negative when BG fell`() {
        val point = calculator.compute(
            bgNow = 110, bgPrev = 120,
            doses = emptyList(), meals = emptyList(),
            cobDeviations = emptyList(), entries = emptyList(),
            params = params, now = now
        )
        assertEquals(-10f, point.observedDelta, 0.001f)
    }

    // ── fastingWindow ─────────────────────────────────────────────────────────

    @Test
    fun `fastingWindow is true with zero IOB, zero COB, no exercise`() {
        val point = calculator.compute(
            bgNow = 120, bgPrev = 120,
            doses = emptyList(), meals = emptyList(),
            cobDeviations = emptyList(), entries = emptyList(),
            params = params, now = now
        )
        assertTrue(point.fastingWindow)
    }

    @Test
    fun `fastingWindow is false when there is recent exercise`() {
        val point = calculator.compute(
            bgNow = 120, bgPrev = 120,
            doses = emptyList(), meals = emptyList(),
            cobDeviations = emptyList(),
            entries = listOf(exerciseEntry(minutesAgo = 60)),
            params = params, now = now
        )
        assertFalse(point.fastingWindow)
        assertTrue(point.recentExercise)
    }

    @Test
    fun `fastingWindow is false when IOB is significant`() {
        // 4U bolus 30 min ago → IOB well above 0.5U threshold
        val doses = listOf(
            InsulinDose(units = 4f, timestamp = LocalDateTime(2024, 6, 15, 11, 30, 0), type = InsulinType.BOLUS)
        )
        val point = calculator.compute(
            bgNow = 120, bgPrev = 120,
            doses = doses, meals = emptyList(),
            cobDeviations = emptyList(), entries = emptyList(),
            params = params, now = now
        )
        assertFalse(point.fastingWindow)
        assertTrue(point.iobPresent)
    }

    @Test
    fun `exercise older than 2 hours does not affect fastingWindow`() {
        val point = calculator.compute(
            bgNow = 120, bgPrev = 120,
            doses = emptyList(), meals = emptyList(),
            cobDeviations = emptyList(),
            entries = listOf(exerciseEntry(minutesAgo = 150)), // 2.5h ago
            params = params, now = now
        )
        assertTrue(point.fastingWindow)
        assertFalse(point.recentExercise)
    }

    // ── postprandialWindow ────────────────────────────────────────────────────

    @Test
    fun `postprandialWindow is true when active meal produces COB above 5g`() {
        // 30g meal logged 10 min ago, no deviations → COB ≈ 30g
        val meals = listOf(
            MealEvent(carbsG = 30f, timestamp = LocalDateTime(2024, 6, 15, 11, 50, 0), carbConfidence = CarbConfidence.HIGH)
        )
        val point = calculator.compute(
            bgNow = 120, bgPrev = 120,
            doses = emptyList(), meals = meals,
            cobDeviations = emptyList(), entries = emptyList(),
            params = params, now = now
        )
        assertTrue(point.postprandialWindow)
        assertTrue(point.cobPresent)
        assertFalse(point.fastingWindow)
    }

    // ── carbConfidence ────────────────────────────────────────────────────────

    @Test
    fun `carbConfidence is LOW when not postprandial`() {
        val point = calculator.compute(
            bgNow = 120, bgPrev = 120,
            doses = emptyList(), meals = emptyList(),
            cobDeviations = emptyList(), entries = emptyList(),
            params = params, now = now
        )
        assertEquals(CarbConfidence.LOW, point.carbConfidence)
    }

    @Test
    fun `carbConfidence propagates HIGH from meal event`() {
        val meals = listOf(
            MealEvent(carbsG = 30f, timestamp = LocalDateTime(2024, 6, 15, 11, 50, 0), carbConfidence = CarbConfidence.HIGH)
        )
        val point = calculator.compute(
            bgNow = 120, bgPrev = 120,
            doses = emptyList(), meals = meals,
            cobDeviations = emptyList(), entries = emptyList(),
            params = params, now = now
        )
        assertEquals(CarbConfidence.HIGH, point.carbConfidence)
    }

    @Test
    fun `carbConfidence propagates MEDIUM from meal event`() {
        val meals = listOf(
            MealEvent(carbsG = 30f, timestamp = LocalDateTime(2024, 6, 15, 11, 50, 0), carbConfidence = CarbConfidence.MEDIUM)
        )
        val point = calculator.compute(
            bgNow = 120, bgPrev = 120,
            doses = emptyList(), meals = meals,
            cobDeviations = emptyList(), entries = emptyList(),
            params = params, now = now
        )
        assertEquals(CarbConfidence.MEDIUM, point.carbConfidence)
    }

    @Test
    fun `carbConfidence is LOW when meal has null confidence`() {
        val meals = listOf(
            MealEvent(carbsG = 30f, timestamp = LocalDateTime(2024, 6, 15, 11, 50, 0), carbConfidence = null)
        )
        val point = calculator.compute(
            bgNow = 120, bgPrev = 120,
            doses = emptyList(), meals = meals,
            cobDeviations = emptyList(), entries = emptyList(),
            params = params, now = now
        )
        assertEquals(CarbConfidence.LOW, point.carbConfidence)
    }

    // ── outlierFlag ───────────────────────────────────────────────────────────

    @Test
    fun `outlierFlag is false for a plausible BG change`() {
        // ISF=50, DIA=300 → threshold ≈ 1.67 mg/dL per tick; 1mg/dL delta is safe
        val point = calculator.compute(
            bgNow = 121, bgPrev = 120,
            doses = emptyList(), meals = emptyList(),
            cobDeviations = emptyList(), entries = emptyList(),
            params = params, now = now
        )
        assertFalse(point.outlierFlag)
    }

    @Test
    fun `outlierFlag is true when deviation is physiologically implausible`() {
        // 40 mg/dL unexplained swing in 5 min — sensor glitch
        val point = calculator.compute(
            bgNow = 160, bgPrev = 120,
            doses = emptyList(), meals = emptyList(),
            cobDeviations = emptyList(), entries = emptyList(),
            params = params, now = now
        )
        assertTrue(point.outlierFlag)
    }

    @Test
    fun `outlierFlag can be true during fastingWindow (sensor glitch while fasting)`() {
        // No insulin, no food, no exercise — but BG jumped 40 mg/dL unexplained
        val point = calculator.compute(
            bgNow = 160, bgPrev = 120,
            doses = emptyList(), meals = emptyList(),
            cobDeviations = emptyList(), entries = emptyList(),
            params = params, now = now
        )
        assertTrue(point.fastingWindow)
        assertTrue(point.outlierFlag)
    }

    // ── deviation = 0 in ideal fasting ───────────────────────────────────────

    @Test
    fun `deviation is zero when BG is flat and no active insulin or carbs`() {
        val point = calculator.compute(
            bgNow = 120, bgPrev = 120,
            doses = emptyList(), meals = emptyList(),
            cobDeviations = emptyList(), entries = emptyList(),
            params = params, now = now
        )
        assertEquals(0f, point.deviation, 0.001f)
        assertEquals(0f, point.predictedDelta, 0.001f)
    }
}
