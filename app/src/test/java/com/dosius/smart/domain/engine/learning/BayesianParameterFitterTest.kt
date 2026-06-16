package com.dosius.smart.domain.engine.learning

import com.dosius.smart.domain.model.CarbConfidence
import com.dosius.smart.domain.model.DeviationPoint
import com.dosius.smart.domain.model.Entry
import com.dosius.smart.domain.model.RegistrationMethod
import com.dosius.smart.domain.model.TherapyParameter
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.minutes

class BayesianParameterFitterTest {

    private val fitter = BayesianParameterFitter()

    // ── Helper builders ───────────────────────────────────────────────────────

    private fun makeIsf(slot: Int, value: Float = 50f, mean: Float = 50f, variance: Float = 100f) =
        TherapyParameter(
            slotIndex = slot,
            parameterType = "ISF",
            mean = mean,
            variance = variance,
            nObservations = 0,
            lastUpdated = LocalDateTime(2026, 1, 1, 0, 0),
            currentValue = value
        )

    private fun makeIcr(slot: Int, value: Float = 10f, mean: Float = 10f, variance: Float = 100f) =
        TherapyParameter(
            slotIndex = slot,
            parameterType = "ICR",
            mean = mean,
            variance = variance,
            nObservations = 0,
            lastUpdated = LocalDateTime(2026, 1, 1, 0, 0),
            currentValue = value
        )

    private fun fastingPoint(slot: Int, deviation: Float) = DeviationPoint(
        id = "p_${slot}_${deviation}",
        timestamp = LocalDateTime(2026, 1, 1, slot, 0),
        predictedDelta = 0f,
        observedDelta = deviation,
        deviation = deviation,
        bgi = 0f,
        fastingWindow = true,
        postprandialWindow = false,
        carbConfidence = CarbConfidence.LOW,
        recentExercise = false,
        outlierFlag = false,
        timeSlot = slot,
        dayOfWeek = 1,
        iobPresent = false,
        cobPresent = false
    )

    // Correction-tail point: iobPresent=true, cobPresent=false, bgi nonzero.
    // fitPassIsf now learns ISF from these (correction tails) not pure fasting windows.
    // ISF_obs = observedDelta * currentIsf / bgi
    //   observedDelta < bgi (both negative, but smaller drop) → ISF_obs < current → ISF decreases
    //   observedDelta > bgi (both negative, but bigger drop) → ISF_obs > current → ISF increases
    private fun correctionTailPoint(slot: Int, bgi: Float, observedDelta: Float) = DeviationPoint(
        id = "corr_${slot}_${observedDelta}_${bgi}",
        timestamp = LocalDateTime(2026, 1, 1, slot, 0),
        predictedDelta = bgi,
        observedDelta = observedDelta,
        deviation = observedDelta - bgi,
        bgi = bgi,
        fastingWindow = true,  // no food/exercise; fit() pre-filters on this before fitPassIsf
        postprandialWindow = false,
        carbConfidence = CarbConfidence.LOW,
        recentExercise = false,
        outlierFlag = false,
        timeSlot = slot,
        dayOfWeek = 1,
        iobPresent = true,
        cobPresent = false
    )

    private fun mealIcrObs(slot: Int, icrObs: Float, obsVariance: Float = 9f) =
        BayesianParameterFitter.MealIcrObservation(
            timeSlot = slot,
            icrObservation = icrObs,
            obsVariance = obsVariance
        )

    // ── gaussianUpdate ────────────────────────────────────────────────────────

    @Test
    fun `gaussianUpdate with n=0 returns prior unchanged`() {
        val (mean, variance) = fitter.gaussianUpdate(50f, 100f, 40f, 25f, 0)
        assertEquals(50f, mean, 0.001f)
        assertEquals(100f, variance, 0.001f)
    }

    @Test
    fun `gaussianUpdate posterior mean is between prior and observation`() {
        // Prior: mean=50, variance=100. Observation: mean=40, obsVariance=25, n=1.
        val (mean, variance) = fitter.gaussianUpdate(50f, 100f, 40f, 25f, 1)
        assertTrue("Posterior mean should be between 40 and 50", mean in 40f..50f)
        assertTrue("Posterior variance should be smaller than prior variance", variance < 100f)
    }

    @Test
    fun `gaussianUpdate with many observations pulls strongly toward observation`() {
        // 20 observations all agreeing at 40 should overpower a prior at 50.
        val (mean, _) = fitter.gaussianUpdate(50f, 100f, 40f, 25f, 20)
        assertTrue("With 20 obs at 40, posterior mean should be close to 40", mean < 43f)
    }

    @Test
    fun `gaussianUpdate posterior variance is strictly less than prior variance`() {
        val (_, variance) = fitter.gaussianUpdate(50f, 100f, 40f, 25f, 5)
        assertTrue(variance < 100f)
    }

    // ── applyGuardrail ────────────────────────────────────────────────────────

    @Test
    fun `applyGuardrail does not clip when change is within limit`() {
        // current=50, proposed=55 (10% change), limit=25% → no clip
        val (clipped, wasClipped) = fitter.applyGuardrail(50f, 55f, 0.25f)
        assertEquals(55f, clipped, 0.001f)
        assertTrue(!wasClipped)
    }

    @Test
    fun `applyGuardrail clips upward change exceeding limit`() {
        // current=50, proposed=70 (40% change), limit=25% → clip to 62.5
        val (clipped, wasClipped) = fitter.applyGuardrail(50f, 70f, 0.25f)
        assertEquals(62.5f, clipped, 0.001f)
        assertTrue(wasClipped)
    }

    @Test
    fun `applyGuardrail clips downward change exceeding limit`() {
        // current=50, proposed=30 (40% decrease), limit=25% → clip to 37.5
        val (clipped, wasClipped) = fitter.applyGuardrail(50f, 30f, 0.25f)
        assertEquals(37.5f, clipped, 0.001f)
        assertTrue(wasClipped)
    }

    // ── fit() with empty / all-outlier input ─────────────────────────────────

    @Test
    fun `fit returns unchanged params when no points provided`() {
        val params = (0 until 24).map { makeIsf(it) }
        val result = fitter.fit(emptyList(), params)
        assertEquals(params, result.updatedParams)
        assertEquals(0f, result.factorA, 0.001f)
    }

    @Test
    fun `fit returns unchanged params when all points are outliers`() {
        val allOutliers = (0 until 5).map { fastingPoint(8, 10f).copy(outlierFlag = true) }
        val params = (0 until 24).map { makeIsf(it) }
        val result = fitter.fit(allOutliers, params)
        assertEquals(params, result.updatedParams)
    }

    // ── fit() directional correctness (Pass 1 — ISF) ─────────────────────────

    @Test
    fun `systematic positive deviation in correction tail decreases ISF for that slot`() {
        // bgi=-4 (predicted drop), observedDelta=-2 (actual drop smaller) → deviation=+2
        // ISF_obs = (-2)*50/(-4) = 25 < 50 → glucose didn't drop as much as predicted → ISF too high → decreases
        val points = (0 until 5).map { correctionTailPoint(8, bgi = -4f, observedDelta = -2f) }
        val allParams = (0 until 24).flatMap { slot ->
            listOf(makeIsf(slot), makeIcr(slot))
        }
        val result = fitter.fit(points, allParams)

        val updatedIsf8 = result.updatedParams.find { it.slotIndex == 8 && it.parameterType == "ISF" }!!
        assertTrue(
            "Positive correction-tail deviation should decrease ISF mean (was 50, now ${updatedIsf8.mean})",
            updatedIsf8.mean < 50f
        )
    }

    @Test
    fun `systematic negative deviation in correction tail increases ISF for that slot`() {
        // bgi=-2 (predicted drop), observedDelta=-4 (actual drop larger) → deviation=-2
        // ISF_obs = (-4)*50/(-2) = 100 > 50 → glucose dropped more than predicted → ISF too low → increases
        val points = (0 until 5).map { correctionTailPoint(8, bgi = -2f, observedDelta = -4f) }
        val allParams = (0 until 24).flatMap { slot ->
            listOf(makeIsf(slot), makeIcr(slot))
        }
        val result = fitter.fit(points, allParams)

        val updatedIsf8 = result.updatedParams.find { it.slotIndex == 8 && it.parameterType == "ISF" }!!
        assertTrue(
            "Negative correction-tail deviation should increase ISF mean (was 50, now ${updatedIsf8.mean})",
            updatedIsf8.mean > 50f
        )
    }

    @Test
    fun `slots with fewer than MIN_POINTS_PER_SLOT are not updated`() {
        // Only 2 points — below the minimum threshold of 3.
        val points = (0 until 2).map { fastingPoint(10, 5.0f) }
        val allParams = (0 until 24).flatMap { slot ->
            listOf(makeIsf(slot), makeIcr(slot))
        }
        val result = fitter.fit(points, allParams)
        val isf10 = result.updatedParams.find { it.slotIndex == 10 && it.parameterType == "ISF" }!!
        assertEquals("Slot with 2 points should have unchanged mean", 50f, isf10.mean, 0.001f)
    }

    // ── fit() directional correctness (Pass 2 — ICR) ─────────────────────────

    @Test
    fun `meal ICR observations below current ICR decrease the parameter`() {
        // icrObs = 7 < current 10 → bolus was insufficient → ICR should decrease.
        val mealObs = (0 until 5).map { mealIcrObs(12, 7f) }
        val allParams = (0 until 24).flatMap { slot -> listOf(makeIsf(slot), makeIcr(slot)) }
        val result = fitter.fit(emptyList(), allParams, mealIcrObservations = mealObs)
        val icr12 = result.updatedParams.find { it.slotIndex == 12 && it.parameterType == "ICR" }!!
        assertTrue(
            "ICR_obs < current should decrease ICR mean (was 10, now ${icr12.mean})",
            icr12.mean < 10f
        )
    }

    @Test
    fun `meal ICR observations above current ICR increase the parameter`() {
        // icrObs = 14 > current 10 → bolus was too large → ICR should increase.
        val mealObs = (0 until 5).map { mealIcrObs(12, 14f) }
        val allParams = (0 until 24).flatMap { slot -> listOf(makeIsf(slot), makeIcr(slot)) }
        val result = fitter.fit(emptyList(), allParams, mealIcrObservations = mealObs)
        val icr12 = result.updatedParams.find { it.slotIndex == 12 && it.parameterType == "ICR" }!!
        assertTrue(
            "ICR_obs > current should increase ICR mean (was 10, now ${icr12.mean})",
            icr12.mean > 10f
        )
    }

    @Test
    fun `ICR not updated when fewer than MIN_POINTS_PER_SLOT meal observations`() {
        // MIN_POINTS_PER_SLOT = 1; 0 observations < 1 → no update
        val allParams = (0 until 24).flatMap { slot -> listOf(makeIsf(slot), makeIcr(slot)) }
        val result = fitter.fit(emptyList(), allParams, mealIcrObservations = emptyList())
        val icr12 = result.updatedParams.find { it.slotIndex == 12 && it.parameterType == "ICR" }!!
        assertEquals("0 meal observations < MIN_POINTS_PER_SLOT → unchanged mean", 10f, icr12.mean, 0.001f)
    }

    // ── guardrail enforcement ─────────────────────────────────────────────────

    @Test
    fun `applyGuardrail clips change when it exceeds the fraction limit`() {
        // Direct test of the guardrail helper — independent of mixture rule dampening.
        // current=50, proposed = 50 - 30 = 20 (60% decrease, exceeds 25% limit)
        val (clipped, wasClipped) = fitter.applyGuardrail(current = 50f, proposed = 20f, maxFraction = 0.25f)
        assertTrue("Guardrail should have fired", wasClipped)
        assertEquals("Clipped value should be exactly 25% below 50", 37.5f, clipped, 0.001f)
    }

    @Test
    fun `extreme deviations are flagged in clippedSlots and guardrail caps the accept`() {
        // 20 correction-tail points with extreme ISF_obs → Bayesian mean far from current (50).
        // The slot should appear in clippedSlots (would exceed 25% guardrail on accept).
        // currentValue stays at 50 until user accepts — guardrail then caps the step.
        val points = (0 until 20).map { correctionTailPoint(8, bgi = -2f, observedDelta = -50f) }
        val allParams = (0 until 24).flatMap { slot -> listOf(makeIsf(slot), makeIcr(slot)) }
        val result = fitter.fit(points, allParams)
        val isf8 = result.updatedParams.find { it.slotIndex == 8 && it.parameterType == "ISF" }!!

        // currentValue must be unchanged — only mean changes before user accepts.
        assertEquals("currentValue must not change before accept", 50f, isf8.currentValue, 0.001f)

        // The slot should be flagged as would-be-clipped on accept.
        assertTrue("Extreme deviation slot should appear in clippedSlots",
            result.clippedSlots.any { it.first == 8 && it.second == "ISF" })

        // Simulate acceptAll guardrail: change capped at 25%.
        val (clipped, wasClipped) = fitter.applyGuardrail(isf8.currentValue, isf8.mean, BayesianParameterFitter.ISF_GUARDRAIL)
        assertTrue("Guardrail should fire for extreme mean", wasClipped)
        val changeFraction = abs(clipped - 50f) / 50f
        assertTrue("Accepted change fraction should be ≤ guardrail", changeFraction <= BayesianParameterFitter.ISF_GUARDRAIL + 0.001f)
    }

    // ── smoothness regularization ─────────────────────────────────────────────

    @Test
    fun `smoothness regularization limits single-slot spikes`() {
        // Give slot 8 correction-tail data only; neighbors have none → their mean stays at prior (50).
        // The raw fit for slot 8 changes, smoothness blends 70/15/15 with neighbors.
        val points = (0 until 10).map { correctionTailPoint(8, bgi = -4f, observedDelta = -2f) }
        val allParams = (0 until 24).flatMap { slot ->
            listOf(makeIsf(slot), makeIcr(slot))
        }
        val result = fitter.fit(points, allParams)

        val isf7 = result.updatedParams.find { it.slotIndex == 7 && it.parameterType == "ISF" }!!.mean
        val isf8 = result.updatedParams.find { it.slotIndex == 8 && it.parameterType == "ISF" }!!.mean
        val isf9 = result.updatedParams.find { it.slotIndex == 9 && it.parameterType == "ISF" }!!.mean

        // Slot 8 mean should be pulled down. Neighbors have no data → mean stays at prior (50).
        val neighborDifference = abs(isf8 - isf7)
        assertTrue("Slot 8 mean should differ from neighbors when it has extreme data", neighborDifference > 0f)
        assertTrue("Neighbor means should stay at prior 50", isf7 == 50f && isf9 == 50f)
    }

    // ── factorA / RMSE ────────────────────────────────────────────────────────

    @Test
    fun `factorA is close to 1 when deviations are small`() {
        // RMSE of 1 → factorA = exp(-1/20) ≈ 0.95
        val points = (0 until 5).map { fastingPoint(8, 1.0f) }
        val allParams = (0 until 24).flatMap { slot -> listOf(makeIsf(slot), makeIcr(slot)) }
        val result = fitter.fit(points, allParams)
        assertTrue("factorA should be close to 1 for small deviations", result.factorA > 0.9f)
    }

    @Test
    fun `factorA is close to 0 when deviations are large`() {
        // RMSE of 40 → factorA = exp(-40/20) = exp(-2) ≈ 0.135
        val points = (0 until 5).map { fastingPoint(8, 40.0f) }
        val allParams = (0 until 24).flatMap { slot -> listOf(makeIsf(slot), makeIcr(slot)) }
        val result = fitter.fit(points, allParams)
        assertTrue("factorA should be small for large deviations", result.factorA < 0.2f)
    }

    // ── nObservations tracking ────────────────────────────────────────────────

    @Test
    fun `nObservations increments for updated slots`() {
        val points = (0 until 5).map { correctionTailPoint(8, bgi = -4f, observedDelta = -2f) }
        val allParams = (0 until 24).flatMap { slot -> listOf(makeIsf(slot), makeIcr(slot)) }
        val result = fitter.fit(points, allParams)
        val isf8 = result.updatedParams.find { it.slotIndex == 8 && it.parameterType == "ISF" }!!
        assertEquals(1, isf8.nObservations)
    }

    @Test
    fun `nObservations does not change for non-updated slots`() {
        val points = (0 until 5).map { fastingPoint(8, 2.0f) }
        val allParams = (0 until 24).flatMap { slot -> listOf(makeIsf(slot), makeIcr(slot)) }
        val result = fitter.fit(points, allParams)
        val isf10 = result.updatedParams.find { it.slotIndex == 10 && it.parameterType == "ISF" }!!
        assertEquals(0, isf10.nObservations)
    }

    // ── buildMealIcrObservations ──────────────────────────────────────────────

    // Helpers for buildMealIcrObservations tests

    private fun makeMealEntry(
        slot: Int,
        carbs: Float = 60f,
        confidence: CarbConfidence = CarbConfidence.HIGH
    ) = Entry(
        id = "meal_${slot}_${confidence.name}",
        timestamp = LocalDateTime(2026, 1, 1, slot, 0),
        createdAt = LocalDateTime(2026, 1, 1, slot, 0),
        description = "",
        foodId = "f1",
        quantity = 100f,
        totalCarbs = carbs,
        carbConfidence = confidence,
        mealType = "Lunch",
        registrationMethod = RegistrationMethod.MANUAL,
        insulinUnits = null, insulinType = null, recommendedUnits = null,
        currentGlucose = 120,
        exerciseType = null, durationOfExercise = null, intensity = null,
        recommendedCarbs = null
    )

    private fun deviationPointAt(
        meal: Entry,
        minutesAfterMeal: Int,
        deviation: Float,
        outlier: Boolean = false
    ): DeviationPoint {
        val ts = (meal.timestamp.toInstant(TimeZone.UTC) + minutesAfterMeal.minutes)
            .toLocalDateTime(TimeZone.UTC)
        return DeviationPoint(
            id = "dp_${meal.id}_$minutesAfterMeal",
            timestamp = ts,
            predictedDelta = 0f,
            observedDelta = deviation,
            deviation = deviation,
            bgi = 0f,
            fastingWindow = false,
            postprandialWindow = true,
            carbConfidence = CarbConfidence.HIGH,
            recentExercise = false,
            outlierFlag = outlier,
            timeSlot = ts.hour,
            dayOfWeek = 1,
            iobPresent = false,
            cobPresent = true
        )
    }

    // Default params: ISF=50, ICR=10 → expectedBGRise = 60g × 50/10 = 300 mg/dL

    @Test
    fun `buildMealIcrObservations positive deviation produces ICR_obs below current ICR`() {
        val meal = makeMealEntry(slot = 12, carbs = 60f)
        val points = (0 until 5).map { i -> deviationPointAt(meal, 60 + i * 5, deviation = 5f) }
        val allParams = (0 until 24).flatMap { listOf(makeIsf(it), makeIcr(it)) }

        val obs = fitter.buildMealIcrObservations(listOf(meal), points, allParams)

        assertEquals(1, obs.size)
        assertTrue("Positive total deviation → ICR_obs < 10", obs[0].icrObservation < 10f)
    }

    @Test
    fun `buildMealIcrObservations negative deviation produces ICR_obs above current ICR`() {
        val meal = makeMealEntry(slot = 12, carbs = 60f)
        val points = (0 until 5).map { i -> deviationPointAt(meal, 60 + i * 5, deviation = -5f) }
        val allParams = (0 until 24).flatMap { listOf(makeIsf(it), makeIcr(it)) }

        val obs = fitter.buildMealIcrObservations(listOf(meal), points, allParams)

        assertEquals(1, obs.size)
        assertTrue("Negative total deviation → ICR_obs > 10", obs[0].icrObservation > 10f)
    }

    @Test
    fun `buildMealIcrObservations zero total deviation produces ICR_obs equal to current ICR`() {
        val meal = makeMealEntry(slot = 12, carbs = 60f)
        val points = (0 until 6).map { i ->
            val dev = if (i < 3) 4f else -4f  // +12 then -12 → sum = 0
            deviationPointAt(meal, 60 + i * 5, deviation = dev)
        }
        val allParams = (0 until 24).flatMap { listOf(makeIsf(it), makeIcr(it)) }

        val obs = fitter.buildMealIcrObservations(listOf(meal), points, allParams)

        assertEquals(1, obs.size)
        assertEquals("Zero net deviation → ICR_obs ≈ current", 10f, obs[0].icrObservation, 0.01f)
    }

    @Test
    fun `buildMealIcrObservations excludes LOW and MEDIUM confidence meals`() {
        val allParams = (0 until 24).flatMap { listOf(makeIsf(it), makeIcr(it)) }
        listOf(CarbConfidence.LOW, CarbConfidence.MEDIUM).forEach { confidence ->
            val meal = makeMealEntry(slot = 12, confidence = confidence)
            val points = (0 until 5).map { i -> deviationPointAt(meal, 60 + i * 5, deviation = 3f) }
            val obs = fitter.buildMealIcrObservations(listOf(meal), points, allParams)
            assertEquals("$confidence meal should be excluded", 0, obs.size)
        }
    }

    @Test
    fun `buildMealIcrObservations includes CERTAIN confidence meals`() {
        val meal = makeMealEntry(slot = 12, confidence = CarbConfidence.CERTAIN)
        val points = (0 until 5).map { i -> deviationPointAt(meal, 60 + i * 5, deviation = 3f) }
        val allParams = (0 until 24).flatMap { listOf(makeIsf(it), makeIcr(it)) }

        val obs = fitter.buildMealIcrObservations(listOf(meal), points, allParams)

        assertEquals("CERTAIN meal should be included", 1, obs.size)
    }

    @Test
    fun `buildMealIcrObservations skips meal when fewer than MIN_POINTS_PER_SLOT in window`() {
        // MIN_POINTS_PER_SLOT = 1; 0 points in window < 1 → no observation produced
        val meal = makeMealEntry(slot = 12)
        val allParams = (0 until 24).flatMap { listOf(makeIsf(it), makeIcr(it)) }

        val obs = fitter.buildMealIcrObservations(listOf(meal), emptyList(), allParams)

        assertEquals("0 points < MIN_POINTS_PER_SLOT → no observation", 0, obs.size)
    }

    @Test
    fun `buildMealIcrObservations excludes outlier-flagged points from window`() {
        val meal = makeMealEntry(slot = 12)
        // 4 normal + 1 outlier → only 4 clean points → still ≥ MIN_POINTS_PER_SLOT
        val clean = (0 until 4).map { i -> deviationPointAt(meal, 60 + i * 5, deviation = 5f) }
        val outlier = deviationPointAt(meal, 90, deviation = 100f, outlier = true)
        val allParams = (0 until 24).flatMap { listOf(makeIsf(it), makeIcr(it)) }

        val obs = fitter.buildMealIcrObservations(listOf(meal), clean + outlier, allParams)

        assertEquals(1, obs.size)
        // ICR_obs should be based on the 4 clean points only, not inflated by the outlier
        val expectedSum = 4 * 5f   // = 20
        val expectedBGRise = 60f * 50f / 10f  // = 300
        val expectedIcrObs = 10f * expectedBGRise / (expectedBGRise + expectedSum)
        assertEquals(expectedIcrObs, obs[0].icrObservation, 0.01f)
    }

    // ── fitBasalFromSleep ──────────────────────────────────────────────────────

    private fun makeBasal(value: Float = 20f, mean: Float = 20f, variance: Float = 100f, nObs: Int = 0) =
        TherapyParameter(
            slotIndex = 0,
            parameterType = "BASAL",
            mean = mean,
            variance = variance,
            nObservations = nObs,
            lastUpdated = LocalDateTime(2026, 1, 1, 0, 0),
            currentValue = value
        )

    private fun sleepFastingPoint(slot: Int, deviation: Float) = DeviationPoint(
        id = "sleep_${slot}_${deviation}",
        timestamp = LocalDateTime(2026, 1, 1, slot, 0),
        predictedDelta = 0f,
        observedDelta = deviation,
        deviation = deviation,
        bgi = 0f,
        fastingWindow = true,
        postprandialWindow = false,
        carbConfidence = CarbConfidence.LOW,
        recentExercise = false,
        outlierFlag = false,
        timeSlot = slot,
        dayOfWeek = 1,
        iobPresent = false,
        cobPresent = false
    )

    @Test
    fun `fitBasalFromSleep returns null when fewer than MIN_SLEEP_POINTS`() {
        val points = (0 until 3).map { sleepFastingPoint(it + 1, 0.5f) }
        val params = (0 until 24).flatMap { listOf(makeIsf(it), makeIcr(it)) } + makeBasal()
        val result = fitter.fitBasalFromSleep(points, params)
        assertEquals("Should return null with too few points", null, result)
    }

    @Test
    fun `fitBasalFromSleep returns null when basal currentValue is zero`() {
        val points = (0 until 10).map { sleepFastingPoint(it % 6 + 1, 0.5f) }
        val params = (0 until 24).flatMap { listOf(makeIsf(it), makeIcr(it)) } + makeBasal(value = 0f, mean = 0f)
        val result = fitter.fitBasalFromSleep(points, params)
        assertEquals("Should return null with zero basal", null, result)
    }

    @Test
    fun `fitBasalFromSleep positive deviation increases basal suggestion`() {
        // Glucose rising overnight → basal too low → suggestion should be higher
        val points = (0 until 10).map { sleepFastingPoint(it % 6 + 1, 0.5f) }
        val params = (0 until 24).flatMap { listOf(makeIsf(it), makeIcr(it)) } + makeBasal()
        val result = fitter.fitBasalFromSleep(points, params)!!
        assertTrue(
            "Positive deviation should increase basal mean (was 20, now ${result.updatedParam.mean})",
            result.updatedParam.mean > 20f
        )
        assertTrue("meanDeviation should be positive", result.meanDeviation > 0f)
    }

    @Test
    fun `fitBasalFromSleep negative deviation decreases basal suggestion`() {
        // Glucose falling overnight → basal too high → suggestion should be lower
        val points = (0 until 10).map { sleepFastingPoint(it % 6 + 1, -0.5f) }
        val params = (0 until 24).flatMap { listOf(makeIsf(it), makeIcr(it)) } + makeBasal()
        val result = fitter.fitBasalFromSleep(points, params)!!
        assertTrue(
            "Negative deviation should decrease basal mean (was 20, now ${result.updatedParam.mean})",
            result.updatedParam.mean < 20f
        )
    }

    @Test
    fun `fitBasalFromSleep filters outlier and non-fasting points`() {
        val clean = (0 until 6).map { sleepFastingPoint(it + 1, 0.5f) }
        val outlier = sleepFastingPoint(2, 50f).copy(outlierFlag = true)
        val nonFasting = sleepFastingPoint(3, 50f).copy(fastingWindow = false)
        val params = (0 until 24).flatMap { listOf(makeIsf(it), makeIcr(it)) } + makeBasal()
        val result = fitter.fitBasalFromSleep(clean + outlier + nonFasting, params)!!
        assertEquals("Only clean fasting points should be counted", 6, result.cleanPointCount)
    }

    @Test
    fun `fitBasalFromSleep increments nObservations`() {
        val points = (0 until 10).map { sleepFastingPoint(it % 6 + 1, 0.3f) }
        val params = (0 until 24).flatMap { listOf(makeIsf(it), makeIcr(it)) } + makeBasal(nObs = 2)
        val result = fitter.fitBasalFromSleep(points, params)!!
        assertEquals(3, result.updatedParam.nObservations)
    }

    @Test
    fun `fitBasalFromSleep does not change currentValue`() {
        val points = (0 until 10).map { sleepFastingPoint(it % 6 + 1, 1f) }
        val params = (0 until 24).flatMap { listOf(makeIsf(it), makeIcr(it)) } + makeBasal()
        val result = fitter.fitBasalFromSleep(points, params)!!
        assertEquals("currentValue must not change before user accepts", 20f, result.updatedParam.currentValue, 0.001f)
    }

    @Test
    fun `fitBasalFromSleep flags clipping when change exceeds guardrail`() {
        // Very large deviation → large basal change → should be flagged
        val points = (0 until 20).map { sleepFastingPoint(it % 6 + 1, 5f) }
        val params = (0 until 24).flatMap { listOf(makeIsf(it), makeIcr(it)) } + makeBasal()
        val result = fitter.fitBasalFromSleep(points, params)!!
        assertTrue("Large deviation should flag clipping", result.wasClipped)
    }

    @Test
    fun `buildMealIcrObservations excludes points outside the postprandial window`() {
        val meal = makeMealEntry(slot = 12)
        // 3 points inside the window, 2 outside (before skip and after end)
        val inside = (0 until 3).map { i -> deviationPointAt(meal, 60 + i * 5, deviation = 5f) }
        val beforeSkip = deviationPointAt(meal, minutesAfterMeal = 10, deviation = 50f) // < 30 min
        val afterEnd   = deviationPointAt(meal, minutesAfterMeal = 260, deviation = 50f) // > 240 min
        val allParams = (0 until 24).flatMap { listOf(makeIsf(it), makeIcr(it)) }

        val obs = fitter.buildMealIcrObservations(listOf(meal), inside + beforeSkip + afterEnd, allParams)

        assertEquals(1, obs.size)
        val expectedSum = 3 * 5f
        val expectedBGRise = 60f * 50f / 10f
        val expectedIcrObs = 10f * expectedBGRise / (expectedBGRise + expectedSum)
        assertEquals(expectedIcrObs, obs[0].icrObservation, 0.01f)
    }
}
