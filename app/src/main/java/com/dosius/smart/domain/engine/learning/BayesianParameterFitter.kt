package com.dosius.smart.domain.engine.learning

import com.dosius.smart.domain.engine.physiology.TherapyParameters
import com.dosius.smart.domain.model.CarbConfidence
import com.dosius.smart.domain.model.DeviationPoint
import com.dosius.smart.domain.model.Entry
import com.dosius.smart.domain.model.TherapyParameter
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.minutes

class BayesianParameterFitter @Inject constructor() {

    // ── Public API ────────────────────────────────────────────────────────────

    data class FitResult(
        val updatedParams: List<TherapyParameter>,
        val clippedSlots: List<Pair<Int, String>>,
        val factorA: Float,
        val factorBIsf: Float,
        val factorBIcr: Float,
        val rmse: Float,
        val cleanPointCount: Int
    )

    /**
     * One ICR observation derived from a single CERTAIN/HIGH-confidence meal event.
     * Computed by the worker using the area-under-the-curve approach:
     * sum all postprandial deviations over the 30–240 min window and invert to an ICR estimate.
     * This is GI-robust because spike and fall largely cancel in the sum.
     */
    data class MealIcrObservation(
        val timeSlot: Int,
        val icrObservation: Float,
        val obsVariance: Float
    )

    /**
     * Runs two-pass Bayesian parameter learning on a cleaned deviation stream.
     *
     * The caller is responsible for pre-filtering: only pass points that are not
     * within a contamination window.  Outlier-flagged points are filtered here.
     *
     * Pass 1 — fasting deviations → ISF update (basal error removed first)
     * Pass 2 — high-confidence postprandial deviations → ICR update (ISF now pinned)
     *
     * Returns the full list of TherapyParameter rows, updated where sufficient
     * evidence exists, unchanged for slots with fewer than MIN_POINTS_PER_SLOT
     * clean observations.
     */
    /**
     * @param points          All clean non-contaminated deviation points (used for RMSE and ICR).
     * @param currentParams   Current TherapyParameter rows from the DB.
     * @param isfSafePoints   Subset of [points] where no meal was logged in the 4 hours before
     *                        the deviation timestamp.  Defaults to [points] (old behaviour) so
     *                        existing call sites and unit tests don't need to change.
     *
     * The separation matters because COB < 5g is an imperfect proxy for "no food interference":
     * a logged small meal or low-GI food keeps COB low while still causing a real glucose rise.
     * A logged meal timestamp is a more reliable exclusion signal than the physiological COB state.
     */
    fun fit(
        points: List<DeviationPoint>,
        currentParams: List<TherapyParameter>,
        isfSafePoints: List<DeviationPoint> = points,
        mealIcrObservations: List<MealIcrObservation> = emptyList()
    ): FitResult {
        val cleanPoints = points.filter { !it.outlierFlag }

        // With no data we have nothing to learn. Return params unchanged with zero weights.
        if (cleanPoints.isEmpty() && mealIcrObservations.isEmpty()) return noDataResult(currentParams)

        // RMSE uses ISF-safe fasting points only, not all clean points.
        // Unlogged meals produce large deviations with no contamination window, so including
        // postprandial points would inflate RMSE and unfairly punish users who don't log every
        // meal.  Fasting periods are free of meal effects whether logged or not — they are the
        // cleanest available signal for "did the model explain physiology this week?"
        // Fall back to all clean points only when no fasting data exists at all.
        val fastingPointsForRmse = isfSafePoints.filter { !it.outlierFlag && it.fastingWindow }
        val rmse = computeRmse(fastingPointsForRmse.ifEmpty { cleanPoints })
        val factorA = if (cleanPoints.isEmpty()) 0f else exp(-rmse / 20f)

        // ── Pass 1: fasting → ISF ─────────────────────────────────────────────
        // Use isfSafePoints (meal-excluded) rather than all cleanPoints.
        // This prevents small logged meals from corrupting the ISF estimate via
        // the COB < 5g loophole — even if COB is low, a logged meal in the prior
        // 4 hours is enough reason to exclude the point from ISF learning.
        val isfCleanPoints = isfSafePoints.filter { !it.outlierFlag }
        val fastingPoints = isfCleanPoints.filter { it.fastingWindow }
        val (isfFitted, isfPostVariances) = fitPassIsf(fastingPoints, currentParams)

        // factor_B_ISF: how sharp the ISF posterior is (average posterior std dev).
        // Low σ_post → exp(-small/15) ≈ 1. High σ_post (few points) → close to 0.
        val factorBIsf = if (isfPostVariances.isNotEmpty()) {
            val avgPostStd = sqrt(isfPostVariances.values.average().toFloat())
            exp(-avgPostStd / ISF_SCALE_B)
        } else 0f

        // ── Pass 2: meal AUC observations → ICR ──────────────────────────────
        // Each observation is the ICR inferred from a single CERTAIN/HIGH meal:
        //   ICR_obs = ICR_current × expectedBGRise / (expectedBGRise + Σdeviations)
        // Summing over 30–240 min cancels GI-driven spike/fall asymmetry that the
        // 5-min point-mean approach suffered from. Built by the worker, passed in.
        val (icrFitted, icrPostVariances) = fitPassIcr(mealIcrObservations, currentParams)

        val factorBIcr = if (icrPostVariances.isNotEmpty()) {
            val avgPostStd = sqrt(icrPostVariances.values.average().toFloat())
            exp(-avgPostStd / ICR_SCALE_B)
        } else 0f

        // ── Mixture rule + guardrails → final parameters ──────────────────────
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val clippedSlots = mutableListOf<Pair<Int, String>>()

        val updatedList = currentParams.map { param ->
            when (param.parameterType) {
                "ISF" -> applyUpdate(
                    param = param,
                    fittedBySlot = isfFitted,
                    postVarianceBySlot = isfPostVariances,
                    guardrail = ISF_GUARDRAIL,
                    clippedSlots = clippedSlots,
                    now = now
                )
                "ICR" -> applyUpdate(
                    param = param,
                    fittedBySlot = icrFitted,
                    postVarianceBySlot = icrPostVariances,
                    guardrail = ICR_GUARDRAIL,
                    clippedSlots = clippedSlots,
                    now = now
                )
                // BASAL, TARGET_LOW, TARGET_HIGH — not auto-updated in V1.
                else -> param
            }
        }

        return FitResult(
            updatedParams = updatedList,
            clippedSlots = clippedSlots,
            factorA = factorA,
            factorBIsf = factorBIsf,
            factorBIcr = factorBIcr,
            rmse = rmse,
            cleanPointCount = cleanPoints.size
        )
    }

    // ── Pass 1 internals ─────────────────────────────────────────────────────

    /**
     * Estimates ISF from correction-tail deviation points (iobPresent=true, cobPresent=false).
     *
     * WHY this window: the old code used fastingWindow (IOB < 0.5) which has almost no
     * active insulin, so bgi ≈ 0 and no ISF signal can be observed. The ISF signal
     * requires insulin to be acting: we measure the actual glucose drop vs the predicted
     * drop and infer ISF from the ratio.
     *
     * The basalError subtraction is kept but now derived from the true fasting (IOB < 0.5)
     * points: those have no insulin effect so any drift is purely basal underdose/overdose.
     *
     * Per-point ISF observation formula:
     *   bgi = -iob × ISF_current / steps   (stored in DeviationPoint)
     *   observedDelta ≈ -iob × ISF_true / steps  (at COB = 0)
     *   => ISF_obs_i = observedDelta_i × ISF_current / bgi_i
     *
     * This is dimensionally correct (bgi and observedDelta cancel units, ISF_current
     * provides the mg/dL/U scaling) and is consistent with what IsfLearningWorker does.
     */
    private fun fitPassIsf(
        allIsfSafePoints: List<DeviationPoint>,
        currentParams: List<TherapyParameter>
    ): Pair<Map<Int, Float>, Map<Int, Float>> {
        if (allIsfSafePoints.isEmpty()) return emptyMap<Int, Float>() to emptyMap()

        // True fasting points (IOB ≈ 0, COB ≈ 0): used only for basalError estimation.
        val fastingPoints = allIsfSafePoints.filter { it.fastingWindow }
        val allFastingBySlot = fastingPoints.groupBy { it.timeSlot }
        val basalError = if (allFastingBySlot.size >= MIN_SLOTS_FOR_BASAL_ESTIMATE) {
            fastingPoints.map { it.deviation }.average().toFloat()
        } else {
            0f
        }

        // ISF window: insulin is active (iobPresent) but no food absorbing (cobPresent=false).
        // These are the correction tails that actually have an observable insulin effect.
        val isfWindowPoints = allIsfSafePoints.filter {
            it.iobPresent && !it.cobPresent && !it.recentExercise && abs(it.bgi) > 0.1f
        }
        val bySlot = isfWindowPoints.groupBy { it.timeSlot }

        val isfFitted    = mutableMapOf<Int, Float>()
        val postVariances = mutableMapOf<Int, Float>()

        for ((slot, slotPoints) in bySlot) {
            if (slotPoints.size < MIN_POINTS_PER_SLOT) continue

            val priorParam    = currentParams.find { it.slotIndex == slot && it.parameterType == "ISF" }
            val priorMean     = priorParam?.mean ?: DEFAULT_ISF
            val priorVariance = priorParam?.variance?.takeIf { it > 0f } ?: DEFAULT_PRIOR_VARIANCE
            val currentIsf    = priorParam?.currentValue ?: DEFAULT_ISF

            // Per-point ISF observations, then average → one per-slot observation.
            // Subtract basalError (converted to ISF units) from each point first so
            // a day-wide basal drift doesn't skew the slot-specific ISF estimate.
            val isfObs = slotPoints.map { pt ->
                val rawIsf = pt.observedDelta * currentIsf / pt.bgi
                // basalError is in mg/dL/5min; convert to ISF units the same way:
                // a basalError deviation of X mg/dL/5min would look like X*steps/iob additional ISF,
                // but since we already have bgi we can subtract basalError from observedDelta first.
                val adjustedObs = (pt.observedDelta - basalError) * currentIsf / pt.bgi
                adjustedObs
            }
            val obsMean     = isfObs.average().toFloat().coerceIn(currentIsf * 0.3f, currentIsf * 3f)
            val obsVariance = computeSampleVariance(isfObs).coerceAtLeast(DEFAULT_OBS_VARIANCE)

            val (postMean, postVariance) = gaussianUpdate(
                priorMean, priorVariance,
                obsMean, obsVariance, slotPoints.size
            )
            isfFitted[slot]    = postMean
            postVariances[slot] = postVariance
        }

        // Smoothness regularization: blend 70/15/15 with neighbouring slots.
        val currentIsfBySlot = currentParams
            .filter { it.parameterType == "ISF" }
            .associate { it.slotIndex to it.currentValue }
        return applySmoothnessRegularization(isfFitted, currentIsfBySlot) to postVariances
    }

    // ── Pass 2 internals ─────────────────────────────────────────────────────

    private fun fitPassIcr(
        mealObservations: List<MealIcrObservation>,
        currentParams: List<TherapyParameter>
    ): Pair<Map<Int, Float>, Map<Int, Float>> {
        if (mealObservations.isEmpty()) return emptyMap<Int, Float>() to emptyMap()

        val bySlot = mealObservations.groupBy { it.timeSlot }
        val icrFitted = mutableMapOf<Int, Float>()
        val postVariances = mutableMapOf<Int, Float>()

        for ((slot, slotObs) in bySlot) {
            if (slotObs.size < MIN_POINTS_PER_SLOT) continue

            val priorParam = currentParams.find { it.slotIndex == slot && it.parameterType == "ICR" }
            val priorMean = priorParam?.mean ?: DEFAULT_ICR
            val priorVariance = priorParam?.variance?.takeIf { it > 0f } ?: DEFAULT_PRIOR_VARIANCE


            val obsMean = slotObs.map { it.icrObservation }.average().toFloat()
            val obsVariance = slotObs.map { it.obsVariance }.average().toFloat()
                .coerceAtLeast(DEFAULT_OBS_VARIANCE)

            val (postMean, postVariance) = gaussianUpdate(
                priorMean, priorVariance,
                obsMean, obsVariance, slotObs.size
            )
            icrFitted[slot] = postMean
            postVariances[slot] = postVariance
        }

        val currentIcrBySlot = currentParams
            .filter { it.parameterType == "ICR" }
            .associate { it.slotIndex to it.currentValue }
        return applySmoothnessRegularization(icrFitted, currentIcrBySlot) to postVariances
    }


    private fun applyUpdate(
        param: TherapyParameter,
        fittedBySlot: Map<Int, Float>,
        postVarianceBySlot: Map<Int, Float>,
        guardrail: Float,
        clippedSlots: MutableList<Pair<Int, String>>,
        now: kotlinx.datetime.LocalDateTime
    ): TherapyParameter {
        val optimalMean = fittedBySlot[param.slotIndex] ?: return param
        val postVariance = postVarianceBySlot[param.slotIndex] ?: param.variance
        val (_, wouldBeClipped) = applyGuardrail(param.currentValue, optimalMean, guardrail)
        if (wouldBeClipped) clippedSlots += (param.slotIndex to param.parameterType)
        return param.copy(
            mean = optimalMean,
            variance = postVariance,
            nObservations = param.nObservations + 1,
            lastUpdated = now
        )
    }
    internal fun buildMealIcrObservations(
        mealEntries: List<Entry>,
        cleanPoints: List<DeviationPoint>,
        currentParams: List<TherapyParameter>
    ): List<MealIcrObservation> {
        val params = TherapyParameters.fromDb(currentParams)

        return mealEntries
            .filter { it.carbConfidence == CarbConfidence.CERTAIN || it.carbConfidence == CarbConfidence.HIGH }
            .mapNotNull { entry ->
                val enteredCarbs = entry.totalCarbs?.takeIf { it > 0f } ?: return@mapNotNull null
                val mealInstant = entry.timestamp.toInstant(TimeZone.UTC)
                val windowStart = mealInstant + POSTPRANDIAL_SKIP
                val windowEnd   = mealInstant + POSTPRANDIAL_END

                val windowPoints = cleanPoints.filter { point ->
                    val pt = point.timestamp.toInstant(TimeZone.UTC)
                    !point.outlierFlag && pt >= windowStart && pt <= windowEnd
                }
                if (windowPoints.size < MIN_POINTS_PER_SLOT) return@mapNotNull null

                val slot = entry.timestamp.hour
                val isf  = params.isfAt(slot)
                val icr  = params.icrAt(slot)
                val expectedBGRise = enteredCarbs * isf / icr
                if (expectedBGRise <= 0f) return@mapNotNull null

                val totalDeviation = windowPoints.sumOf { it.deviation.toDouble() }.toFloat()
                val denominator = expectedBGRise + totalDeviation
                if (denominator <= 0f) return@mapNotNull null

                val icrObs = (icr * expectedBGRise / denominator)
                    .coerceIn(icr * 0.3f, icr * 5f)

                val deviations = windowPoints.map { it.deviation }
                val mean = deviations.average().toFloat()
                val obsVariance = if (deviations.size >= 2)
                    deviations.map { (it - mean) * (it - mean) }.average().toFloat()
                        .coerceAtLeast(DEFAULT_OBS_VARIANCE)
                else DEFAULT_OBS_VARIANCE

                MealIcrObservation(
                    timeSlot = slot,
                    icrObservation = icrObs,
                    obsVariance = obsVariance
                )
            }
    }

    // ── Pass 3: basal from sleep-window fasting deviations ─────────────────

    data class BasalFitResult(
        val updatedParam: TherapyParameter,
        val wasClipped: Boolean,
        val basalObservation: Float,
        val meanDeviation: Float,
        val cleanPointCount: Int
    )

    fun fitBasalFromSleep(
        sleepDeviations: List<DeviationPoint>,
        currentParams: List<TherapyParameter>
    ): BasalFitResult? {
        val cleanPoints = sleepDeviations.filter { !it.outlierFlag && it.fastingWindow }
        if (cleanPoints.size < MIN_SLEEP_POINTS) return null

        val basalParam = currentParams.find { it.slotIndex == 0 && it.parameterType == "BASAL" }
            ?: return null
        val currentBasal = basalParam.currentValue
        if (currentBasal <= 0f) return null

        val params = TherapyParameters.fromDb(currentParams)
        val sleepHours = cleanPoints.map { it.timeSlot }.distinct()
        val avgIsf = sleepHours.map { params.isfAt(it) }.average().toFloat()
        if (avgIsf <= 0f) return null

        val meanDev = cleanPoints.map { it.deviation }.average().toFloat()
        val basalObs = (currentBasal + meanDev * TICKS_PER_DAY / avgIsf)
            .coerceIn(currentBasal * 0.5f, currentBasal * 2f)

        val basalObservations = cleanPoints.map { pt ->
            currentBasal + pt.deviation * TICKS_PER_DAY / avgIsf
        }
        val obsVariance = computeSampleVariance(basalObservations)
            .coerceAtLeast(DEFAULT_OBS_VARIANCE)

        val priorMean = basalParam.mean
        val priorVariance = basalParam.variance.takeIf { it > 0f } ?: DEFAULT_PRIOR_VARIANCE

        val (postMean, postVariance) = gaussianUpdate(
            priorMean, priorVariance,
            basalObs, obsVariance, cleanPoints.size
        )

        val (_, wouldBeClipped) = applyGuardrail(currentBasal, postMean, BASAL_GUARDRAIL)

        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val updatedParam = basalParam.copy(
            mean = postMean,
            variance = postVariance,
            nObservations = basalParam.nObservations + 1,
            lastUpdated = now
        )

        return BasalFitResult(
            updatedParam = updatedParam,
            wasClipped = wouldBeClipped,
            basalObservation = basalObs,
            meanDeviation = meanDev,
            cleanPointCount = cleanPoints.size
        )
    }

    // ── Per-event ISF / ICR learning ──────────────────────────────────────────

    /** Posterior produced by a single per-event learning step, ready to persist. */
    data class EventPosterior(
        val mean: Float,
        val variance: Float
    )

    /**
     * Learns ISF from a single correction tail (one bolus event = one observation, n=1).
     *
     * The caller passes [windowPoints] already restricted to the correction-tail time window
     * and with contamination windows excluded (those are data-layer concerns). This method
     * applies the physiological validity predicate and the conjugate Gaussian math.
     *
     * Per-point observation: ISF_obs_i = observedDelta_i × ISF_current / bgi_i.
     * The points in one correction tail are correlated, so their mean is a single n=1 observation.
     *
     * @return the smoothed posterior, or null if too few valid points to learn.
     */
    fun learnIsfFromCorrection(
        windowPoints: List<DeviationPoint>,
        currentIsf: Float,
        priorMean: Float,
        priorVariance: Float,
        neighborMeanPrev: Float?,
        neighborMeanNext: Float?
    ): EventPosterior? {
        val cleanPoints = windowPoints.filter {
            it.iobPresent && !it.cobPresent && !it.recentExercise &&
                !it.outlierFlag && abs(it.bgi) > 0.1f
        }
        if (cleanPoints.size < MIN_POINTS_PER_SLOT) return null

        val isfObservations = cleanPoints.map { it.observedDelta * currentIsf / it.bgi }
        val obsMean = isfObservations.average().toFloat()
            .coerceIn(currentIsf * 0.3f, currentIsf * 3f)

        val sampleVariance = computeSampleVariance(isfObservations).coerceAtLeast(DEFAULT_OBS_VARIANCE)
        val obsVariance = sampleVariance.coerceAtLeast(currentIsf * currentIsf * ISF_MIN_RELATIVE_VAR)

        val priorVar = priorVariance.takeIf { it > 0f } ?: DEFAULT_PRIOR_VARIANCE
        val (postMean, postVariance) = gaussianUpdate(priorMean, priorVar, obsMean, obsVariance, 1)

        val prev = neighborMeanPrev ?: postMean
        val next = neighborMeanNext ?: postMean
        val smoothedMean = 0.7f * postMean + 0.15f * prev + 0.15f * next

        return EventPosterior(smoothedMean, postVariance)
    }

    /**
     * Learns ICR from a single meal's postprandial AUC (one meal = one observation, n=1).
     *
     * The caller passes [windowPoints] already restricted to the postprandial window and with
     * contamination windows excluded. This method applies the outlier filter and the math.
     *
     * ICR_obs = ICR_current × expectedBGRise / (expectedBGRise + Σdeviations), GI-robust because
     * summing over the window cancels spike/fall asymmetry. Observation variance is propagated
     * from deviation noise via the delta method.
     *
     * @return the smoothed posterior, or null if the meal can't yield a valid observation.
     */
    fun learnIcrFromMeal(
        windowPoints: List<DeviationPoint>,
        totalCarbs: Float,
        currentIsf: Float,
        currentIcr: Float,
        priorMean: Float,
        priorVariance: Float,
        neighborMeanPrev: Float?,
        neighborMeanNext: Float?
    ): EventPosterior? {
        val expectedBGRise = totalCarbs * currentIsf / currentIcr
        if (expectedBGRise <= 0f) return null

        val cleanPoints = windowPoints.filter { !it.outlierFlag }
        if (cleanPoints.size < MIN_POINTS_PER_SLOT) return null

        val totalDeviation = cleanPoints.sumOf { it.deviation.toDouble() }.toFloat()
        val denominator = expectedBGRise + totalDeviation
        if (denominator <= 0f) return null

        val icrObs = (currentIcr * expectedBGRise / denominator)
            .coerceIn(currentIcr * 0.3f, currentIcr * 5f)

        // icrObs = icr × E / (E + D), so dIcr/dD = -icr × E / (E+D)^2.
        // Var(icrObs) ≈ (dIcr/dD)^2 × nPoints × sigma^2_per_point (delta method).
        val devVariancePerPoint = computeSampleVariance(cleanPoints.map { it.deviation })
            .coerceAtLeast(DEFAULT_OBS_VARIANCE)
        val dIcrDdev = currentIcr * expectedBGRise / (denominator * denominator)
        val propagatedVariance = dIcrDdev * dIcrDdev * devVariancePerPoint * cleanPoints.size
        val obsVariance = propagatedVariance.coerceAtLeast(currentIcr * currentIcr * ICR_MIN_RELATIVE_VAR)

        val priorVar = priorVariance.takeIf { it > 0f } ?: DEFAULT_PRIOR_VARIANCE
        val (postMean, postVariance) = gaussianUpdate(priorMean, priorVar, icrObs, obsVariance, 1)

        val prev = neighborMeanPrev ?: postMean
        val next = neighborMeanNext ?: postMean
        val smoothedMean = 0.7f * postMean + 0.15f * prev + 0.15f * next

        return EventPosterior(smoothedMean, postVariance)
    }

    /**
     * Learns exercise glucose drop per hour from a single accepted session.
     * The observation is a direct measurement: actualDrop / durationHours.
     * obsVariance is fixed at 100f — reflects high session-to-session variability.
     * No smoothness regularization: exercise parameters are not 24-slot.
     */
    fun learnExerciseDrop(
        currentMean: Float,
        currentVariance: Float,
        dropPerHour: Float
    ): EventPosterior {
        val priorVar = currentVariance.takeIf { it > 0f } ?: EXERCISE_DEFAULT_PRIOR_VARIANCE
        val (postMean, postVariance) = gaussianUpdate(currentMean, priorVar, dropPerHour, EXERCISE_OBS_VARIANCE, 1)
        return EventPosterior(postMean, postVariance)
    }

    /**
     * Learns food carbs per 100g from a single accepted inferred observation.
     * Only called for MEDIUM/LOW confidence entries; CERTAIN/HIGH skip the Bayesian step.
     * obsVariance is fixed at 25f — calibrated for carbsPer100g space.
     */
    fun learnFoodCarbs(
        priorMean: Float,
        priorVariance: Float,
        observationPer100g: Float
    ): EventPosterior {
        val priorVar = priorVariance.takeIf { it > 0f } ?: DEFAULT_PRIOR_VARIANCE
        val (postMean, postVariance) = gaussianUpdate(priorMean, priorVar, observationPer100g, FOOD_OBS_VARIANCE, 1)
        return EventPosterior(postMean, postVariance)
    }

    // ── Math helpers (internal for unit-testability) ──────────────────────────

    internal fun gaussianUpdate(
        priorMean: Float, priorVariance: Float,
        obsMean: Float, obsVariance: Float, n: Int
    ): Pair<Float, Float> {
        if (n == 0) return priorMean to priorVariance
        // Conjugate Gaussian: precision (1/σ²) is additive.
        val postVariance = 1f / (1f / priorVariance + n.toFloat() / obsVariance)
        val postMean = postVariance * (priorMean / priorVariance + n.toFloat() * obsMean / obsVariance)
        return postMean to postVariance
    }

    internal fun applyGuardrail(current: Float, proposed: Float, maxFraction: Float): Pair<Float, Boolean> {
        val delta = proposed - current
        val maxDelta = abs(current) * maxFraction
        return if (abs(delta) > maxDelta) {
            val clipped = current + sign(delta) * maxDelta
            clipped to true
        } else {
            proposed to false
        }
    }

    private fun applySmoothnessRegularization(
        fitted: Map<Int, Float>,
        currentBySlot: Map<Int, Float>
    ): Map<Int, Float> = fitted.mapValues { (slot, fittedVal) ->
        // Wrap around at midnight: slot 0 looks at slot 23, slot 23 looks at slot 0.
        val prev = fitted[(slot + 23) % 24] ?: currentBySlot[(slot + 23) % 24] ?: fittedVal
        val next = fitted[(slot + 1) % 24] ?: currentBySlot[(slot + 1) % 24] ?: fittedVal
        0.7f * fittedVal + 0.15f * prev + 0.15f * next
    }

    private fun computeRmse(points: List<DeviationPoint>): Float {
        if (points.isEmpty()) return 0f
        return sqrt(points.map { it.deviation * it.deviation }.average().toFloat())
    }

    internal fun computeSampleVariance(values: List<Float>): Float {
        if (values.size < 2) return DEFAULT_OBS_VARIANCE
        val mean = values.average().toFloat()
        return values.map { (it - mean) * (it - mean) }.average().toFloat()
    }

    private fun noDataResult(currentParams: List<TherapyParameter>) = FitResult(
        updatedParams = currentParams,
        clippedSlots = emptyList(),
        factorA = 0f, factorBIsf = 0f, factorBIcr = 0f,
        rmse = 0f, cleanPointCount = 0
    )

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        internal const val MIN_POINTS_PER_SLOT = 3
        private const val MIN_SLOTS_FOR_BASAL_ESTIMATE = 3  // need multi-slot data to separate basal from ISF
        internal const val DEFAULT_OBS_VARIANCE = 9f       // ~3 mg/dL/5min noise floor
        internal const val DEFAULT_PRIOR_VARIANCE = 100f   // wide prior — seeded defaults are educated guesses
        internal const val DEFAULT_ISF = 50f
        internal const val DEFAULT_ICR = 10f
        internal const val ISF_GUARDRAIL = 0.25f           // max 25% change per weekly fit
        internal const val ICR_GUARDRAIL = 0.30f           // max 30% change per weekly fit
        internal const val ISF_MIN_RELATIVE_VAR = 0.09f    // 30% relative uncertainty floor on ISF obs
        internal const val ICR_MIN_RELATIVE_VAR = 0.09f    // 30% relative uncertainty floor on ICR obs
        internal const val ISF_SCALE_B = 15f               // factor_B tuning: ISF posterior std dev scale
        internal const val ICR_SCALE_B = 2f                // factor_B tuning: ICR posterior std dev scale
        internal val POSTPRANDIAL_SKIP = 30.minutes
        internal val POSTPRANDIAL_END  = 240.minutes
        internal const val BASAL_GUARDRAIL = 0.15f    // max 15% change per sleep learning event
        internal const val MIN_SLEEP_POINTS = 6       // ~30 min of clean fasting data
        private const val TICKS_PER_DAY = 288f        // 24h × 12 ticks/h (5-min intervals)
        internal const val EXERCISE_OBS_VARIANCE = 100f
        internal const val EXERCISE_DEFAULT_PRIOR_VARIANCE = 400f
        internal const val FOOD_OBS_VARIANCE = 25f
    }
}
