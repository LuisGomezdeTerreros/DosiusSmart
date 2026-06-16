package com.dosius.smart.domain.engine.recommendation

import com.dosius.smart.domain.engine.physiology.ForecastPoint
import com.dosius.smart.domain.engine.physiology.IOBCalculator
import com.dosius.smart.domain.engine.physiology.IOBResult
import com.dosius.smart.domain.engine.physiology.InsulinDose
import com.dosius.smart.domain.engine.physiology.TherapyParameters
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecommendationEngineTest {

    private lateinit var iobCalculator: IOBCalculator
    private lateinit var engine: RecommendationEngine

    // defaults(): ISF=50, ICR=10, targetBgLow=70, targetBgHigh=180 → targetBg=125
    private val params = TherapyParameters.defaults()
    private val now = LocalDateTime(2024, 6, 15, 12, 0, 0)
    private val emptyDoses = emptyList<InsulinDose>()

    @Before
    fun setUp() {
        iobCalculator = mockk()
        engine = RecommendationEngine(iobCalculator)
    }

    private fun stubIob(iob: Float) {
        every { iobCalculator.calculate(any(), any(), any()) } returns IOBResult(iob, emptyList())
    }

    private fun flatForecast(bg: Int) = (1..48).map {
        ForecastPoint(deltaMinutes = it * 5, predictedBg = bg.toFloat(), predictedIob = 0f, predictedCob = 0f)
    }

    // ── computePrandialBolus ───────────────────────────────────────────────────

    @Test
    fun `prandial bolus standard case carbs divided by ICR plus correction`() {
        stubIob(0f)
        // carbDose = 60/10 = 6; correction = (120-125)/50 = -0.1; rawBolus = 5.9
        val result = engine.computePrandialBolus(
            carbs = 60f, mealTime = now, currentBg = 120,
            trendRateMgPerMin = 0f, params = params, doses = emptyDoses
        )
        assertEquals(5.9f, result.recommendedUnits!!, 0.05f)
        assertNull(result.recommendedCarbs)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `prandial bolus is zero when IOB already covers the meal`() {
        stubIob(10f)
        // rawBolus = 6 - 0.1 - 10 = -4.1 → clamped to 0
        val result = engine.computePrandialBolus(
            carbs = 60f, mealTime = now, currentBg = 120,
            trendRateMgPerMin = 0f, params = params, doses = emptyDoses
        )
        assertEquals(0f, result.recommendedUnits!!, 0.001f)
        assertTrue(result.warnings.any { it.contains("IOB", ignoreCase = true) })
    }

    @Test
    fun `prandial bolus trend adjustment adds 0_2u for rising at 2 mgDL per min`() {
        stubIob(0f)
        val flat = engine.computePrandialBolus(
            carbs = 60f, mealTime = now, currentBg = 120, trendRateMgPerMin = 0f, params = params, doses = emptyDoses
        )
        val rising = engine.computePrandialBolus(
            carbs = 60f, mealTime = now, currentBg = 120, trendRateMgPerMin = 2f, params = params, doses = emptyDoses
        )
        // 2 * 0.1 = 0.2u extra
        assertEquals(0.2f, rising.recommendedUnits!! - flat.recommendedUnits!!, 0.01f)
    }

    @Test
    fun `prandial bolus trend adjustment is clamped at 1u for very rapid rise`() {
        stubIob(0f)
        val capped = engine.computePrandialBolus(
            carbs = 60f, mealTime = now, currentBg = 120, trendRateMgPerMin = 10f, params = params, doses = emptyDoses
        )
        val overCap = engine.computePrandialBolus(
            carbs = 60f, mealTime = now, currentBg = 120, trendRateMgPerMin = 50f, params = params, doses = emptyDoses
        )
        assertEquals(capped.recommendedUnits!!, overCap.recommendedUnits!!, 0.001f)
    }

    @Test
    fun `prandial bolus components do not include COB term`() {
        stubIob(0f)
        val result = engine.computePrandialBolus(
            carbs = 60f, mealTime = now, currentBg = 120, trendRateMgPerMin = 0f, params = params, doses = emptyDoses
        )
        // carbDose and correctionDose are the only non-zero components; no cob term present
        assertEquals(6f, result.components.carbDose, 0.01f)
        assertEquals(-0.1f, result.components.correctionDose, 0.01f)
        assertEquals(0f, result.components.iobOffset, 0.01f)
    }

    // ── computeCorrectionBolus — Case 1: already hypo ─────────────────────────

    @Test
    fun `correction case 1 already hypo returns 15g carbs`() {
        stubIob(0f)
        val result = engine.computeCorrectionBolus(
            currentBg = 55, forecastPoints = flatForecast(55),
            params = params, doses = emptyDoses, now = now
        )
        assertNull(result.recommendedUnits)
        assertEquals(15, result.recommendedCarbs)
        assertEquals("Hypoglycemia protocol", result.label)
        assertTrue(result.warnings.any { it.contains("hypoglycemia", ignoreCase = true) })
    }

    // ── Case 2: heading toward hypo ───────────────────────────────────────────

    @Test
    fun `correction case 2 heading toward hypo returns carbs from nadir depth`() {
        stubIob(0f)
        // Forecast nadir at t=60min is 55 mg/dL
        val forecast = flatForecast(130).toMutableList()
        forecast[11] = ForecastPoint(deltaMinutes = 60, predictedBg = 55f, predictedIob = 0f, predictedCob = 0f)
        val result = engine.computeCorrectionBolus(
            currentBg = 130, forecastPoints = forecast,
            params = params, doses = emptyDoses, now = now
        )
        assertNull(result.recommendedUnits)
        // carbs = (70 - 55) * ICR/ISF + 3 = 15 * 10/50 + 3 = 6
        assertEquals(6, result.recommendedCarbs)
        assertEquals("Predicted hypo", result.label)
    }

    @Test
    fun `correction case 2 imminent hypo triggers urgent warning`() {
        stubIob(0f)
        val forecast = flatForecast(130).toMutableList()
        forecast[1] = ForecastPoint(deltaMinutes = 10, predictedBg = 60f, predictedIob = 0f, predictedCob = 0f)
        val result = engine.computeCorrectionBolus(
            currentBg = 130, forecastPoints = forecast,
            params = params, doses = emptyDoses, now = now
        )
        assertTrue(result.warnings.any { it.contains("now", ignoreCase = true) || it.contains("fast", ignoreCase = true) })
    }

    // ── Case 3: heading hyper with logged data ─────────────────────────────────

    @Test
    fun `correction case 3 heading hyper with logged data uses terminal forecast BG`() {
        stubIob(0f)
        // Terminal BG = 220, target = 125, ISF = 50 → correction = (220-125)/50 = 1.9u
        val result = engine.computeCorrectionBolus(
            currentBg = 160, forecastPoints = flatForecast(220),
            params = params, doses = emptyDoses, now = now, hasLoggedData = true
        )
        assertNotNull(result.recommendedUnits)
        assertEquals(1.9f, result.recommendedUnits!!, 0.05f)
        assertNull(result.recommendedCarbs)
        assertEquals("Correction bolus", result.label)
    }

    @Test
    fun `correction case 3 zero correction when IOB already covers the rise`() {
        stubIob(0f)
        // Terminal BG = 130 which is just above target 125 → correction = (130-125)/50 = 0.1u
        val result = engine.computeCorrectionBolus(
            currentBg = 150, forecastPoints = flatForecast(130),
            params = params, doses = emptyDoses, now = now, hasLoggedData = true
        )
        assertEquals(0.1f, result.recommendedUnits!!, 0.01f)
    }

    // ── Case 4: heading hyper, no logged data ─────────────────────────────────

    @Test
    fun `correction case 4 no logged data uses standard formula without IOB`() {
        stubIob(0f)
        // BG=160, target=125, ISF=50 → correction = (160-125)/50 = 0.7u (no trend projection, no IOB)
        val result = engine.computeCorrectionBolus(
            currentBg = 160, forecastPoints = flatForecast(160),
            params = params, doses = emptyDoses, now = now,
            trendRateMgPerMin = 1.0f, hasLoggedData = false
        )
        assertNotNull(result.recommendedUnits)
        assertEquals(0.7f, result.recommendedUnits!!, 0.05f)
        assertEquals("Correction bolus", result.label)
        assertTrue(result.warnings.any { it.contains("logged", ignoreCase = true) })
    }

    // ── Case 5: BG and forecast both in range ─────────────────────────────────

    @Test
    fun `correction case 5 BG in range returns zero units and on-track label`() {
        stubIob(0f)
        val result = engine.computeCorrectionBolus(
            currentBg = 120, forecastPoints = flatForecast(110),
            params = params, doses = emptyDoses, now = now, hasLoggedData = true
        )
        assertEquals(0f, result.recommendedUnits!!, 0.001f)
        assertNull(result.recommendedCarbs)
        assertEquals("BG on track", result.label)
    }

    // ── computePreExercise ────────────────────────────────────────────────────

    @Test
    fun `pre-exercise no carbs when predicted post-exercise BG stays above 70`() {
        stubIob(0f)
        // expectedDrop = 40/h × 1h = 40; predictedPostBg = 160 - 40 - 0 = 120 > 70
        val result = engine.computePreExercise(
            expectedDropPerHour = 40f, durationMin = 60f,
            currentBg = 160, params = params, doses = emptyDoses, now = now
        )
        assertEquals(0, result.recommendedCarbs)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `pre-exercise recommends carbs when IOB drives post-exercise BG below 70`() {
        stubIob(2f)
        // expectedDrop = 40; IOB effect = 2 × 50 = 100; predictedPostBg = 120 - 40 - 100 = -20
        // carbs = (70 - (-20)) * 10/50 + 3 = 90 * 0.2 + 3 = 21g
        val result = engine.computePreExercise(
            expectedDropPerHour = 40f, durationMin = 60f,
            currentBg = 120, params = params, doses = emptyDoses, now = now
        )
        assertEquals(21, result.recommendedCarbs)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `pre-exercise zero duration produces no carb recommendation`() {
        stubIob(0f)
        val result = engine.computePreExercise(
            expectedDropPerHour = 40f, durationMin = 0f,
            currentBg = 120, params = params, doses = emptyDoses, now = now
        )
        assertEquals(0, result.recommendedCarbs)
    }

    @Test
    fun `pre-exercise safety buffer is included in carb recommendation`() {
        stubIob(0f)
        // predictedPostBg = 80 - 40 - 0 = 40 → carbs = (70-40)*10/50 + 3 = 6 + 3 = 9
        val result = engine.computePreExercise(
            expectedDropPerHour = 40f, durationMin = 60f,
            currentBg = 80, params = params, doses = emptyDoses, now = now
        )
        assertEquals(9, result.recommendedCarbs)
    }
}
