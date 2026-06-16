package com.dosius.smart.domain.engine.recommendation

import com.dosius.smart.domain.engine.physiology.ForecastPoint
import com.dosius.smart.domain.engine.physiology.IOBCalculator
import com.dosius.smart.domain.engine.physiology.InsulinDose
import com.dosius.smart.domain.engine.physiology.TherapyParameters
import kotlinx.datetime.LocalDateTime
import javax.inject.Inject
import kotlin.math.max

class RecommendationEngine @Inject constructor(
    private val iobCalculator: IOBCalculator
) {

    companion object {
        private const val TREND_FACTOR = 0.1F
        private const val SAFETY_BUFFER_G = 3F
        private const val HYPO_THRESHOLD = 70
    }

    fun computePrandialBolus(
        carbs: Float,
        mealTime: LocalDateTime,
        currentBg: Int,
        trendRateMgPerMin: Float,
        params: TherapyParameters,
        doses: List<InsulinDose>
    ): RecommendationResult {
        val hour = mealTime.hour
        val dia = params.diaMinutes
        val isf = params.isfAt(hour)
        val icr = params.icrAt(hour)
        val target = params.targetBg
        val iobResult = iobCalculator.calculate(doses, mealTime, dia)
        val trendAdjustment = (trendRateMgPerMin * TREND_FACTOR).coerceIn(-1f, 1f)
        // COB is intentionally excluded. Manual bolus calculators (Medtronic, Tandem, Omnipod)
        // use this exact formula: previous meals are assumed pre-bolused, so their unabsorbed
        // carbs already have matching IOB. Adding COB here would double-dose on pre-bolused meals
        // because insulin and carb curves peak at different times and never perfectly cancel.
        // The "ate without injecting" gap is handled by M4's GlucoseForecaster (COB drives the
        // rising forecast) → the user sees the predicted rise and requests a correction bolus.
        val rawBolus =
            carbs / icr + (currentBg - target) / isf - iobResult.totalIob + trendAdjustment
        val bolus = rawBolus.coerceAtLeast(0f)
        val recommendationComponents = RecommendationComponents(
            carbDose = carbs / icr,
            correctionDose = (currentBg - target) / isf,
            iobOffset = -iobResult.totalIob,
            trendAdjustment
        )
        val warnings = buildList {
            if (rawBolus < 0f) add("Your current IOB already covers this meal and correction")
        }
        return RecommendationResult(
            recommendedUnits = bolus,
            recommendedCarbs = null,
            components = recommendationComponents,
            warnings
        )
    }


    fun computeCorrectionBolus(
        currentBg: Int,
        forecastPoints: List<ForecastPoint>,
        params: TherapyParameters,
        doses: List<InsulinDose>,
        now: LocalDateTime,
        trendRateMgPerMin: Float = 0f,
        hasLoggedData: Boolean = false
    ): RecommendationResult {
        val hour = now.hour
        val isf = params.isfAt(hour)
        val icr = params.icrAt(hour)
        val target = params.targetBg
        val iobResult = iobCalculator.calculate(doses, now, params.diaMinutes)
        val warnings = mutableListOf<String>()

        // Case 1: already in hypo — fixed 15-15 protocol, no formula needed
        if (currentBg < HYPO_THRESHOLD) {
            warnings.add("Treat hypoglycemia immediately. Wait 15 min before re-testing.")
            return RecommendationResult(
                recommendedUnits = null,
                recommendedCarbs = 15,
                components = RecommendationComponents(15f, 0f, -iobResult.totalIob, 0f),
                warnings = warnings,
                label = "Hypoglycemia protocol"
            )
        }

        // Case 2: heading toward hypo — carbs from forecast nadir depth, urgency from time-to-nadir
        val nadirPoint = forecastPoints.minByOrNull { it.predictedBg }
        val nadirBg = nadirPoint?.predictedBg ?: currentBg.toFloat()
        if (nadirBg < HYPO_THRESHOLD) {
            val timeToNadir = nadirPoint?.deltaMinutes ?: 0
            val carbs = max(0f, (HYPO_THRESHOLD - nadirBg) * icr / isf + SAFETY_BUFFER_G)
            if (timeToNadir < 30) {
                warnings.add("Hypo predicted in ~$timeToNadir min — use fast-acting carbs now")
            } else {
                warnings.add("Hypo predicted in ~$timeToNadir min — a snack now will prevent it")
            }
            return RecommendationResult(
                recommendedUnits = null,
                recommendedCarbs = carbs.toInt(),
                components = RecommendationComponents(carbs, 0f, -iobResult.totalIob, 0f),
                warnings = warnings,
                label = "Predicted hypo"
            )
        }

        val terminalBg = forecastPoints.lastOrNull()?.predictedBg ?: currentBg.toFloat()

        // Case 3: heading hyper with logged data — forecast terminal already has IOB/COB baked in
        if (terminalBg > target && hasLoggedData) {
            val correction = max(0f, (terminalBg - target) / isf)
            if (correction == 0f) warnings.add("IOB already covers the expected rise — consider waiting")
            else warnings.add("Conservative estimate — if you ate more than logged, BG may take longer to reach target")
            return RecommendationResult(
                recommendedUnits = correction,
                recommendedCarbs = null,
                components = RecommendationComponents(0f, correction, -iobResult.totalIob, 0f),
                warnings = warnings,
                label = "Correction bolus"
            )
        }

        // Case 4: heading hyper, nothing logged — standard correction formula (no IOB to deduct)
        if (terminalBg > target || currentBg > target) {
            val correction = max(0f, (currentBg - target) / isf)
            warnings.add("No meal or insulin logged. If you injected recently without logging, consider waiting before correcting.")
            return RecommendationResult(
                recommendedUnits = correction,
                recommendedCarbs = null,
                components = RecommendationComponents(0f, correction, 0f, 0f),
                warnings = warnings,
                label = "Correction bolus"
            )
        }

        // Case 5: BG and forecast both in range — no action needed
        warnings.add("Glucose is on track — no correction needed")
        return RecommendationResult(
            recommendedUnits = 0f,
            recommendedCarbs = null,
            components = RecommendationComponents(0f, 0f, -iobResult.totalIob, 0f),
            warnings = warnings,
            label = "BG on track"
        )
    }


    fun computePreExercise(
        expectedDropPerHour: Float,
        durationMin: Float,
        currentBg: Int,
        params: TherapyParameters,
        doses: List<InsulinDose>,
        now: LocalDateTime,
        trendRateMgPerMin: Float = 0f,
    ): RecommendationResult {
        val hour = now.hour
        val dia = params.diaMinutes
        val isf = params.isfAt(hour)
        val icr = params.icrAt(hour)
        val iobResult = iobCalculator.calculate(doses, now, dia)
        val trendAdjustment = (trendRateMgPerMin * TREND_FACTOR).coerceIn(-1f, 1f)

        val expectedDrop = expectedDropPerHour * (durationMin / 60f)
        val predictedPostBg = currentBg - expectedDrop - (iobResult.totalIob * isf)
        var recommendedCarbs = 0f
        if (predictedPostBg < HYPO_THRESHOLD) {
            recommendedCarbs = (HYPO_THRESHOLD - predictedPostBg) * icr / isf + SAFETY_BUFFER_G
        }
        val recommendationComponents = RecommendationComponents(
            carbDose = recommendedCarbs,
            correctionDose = 0f,
            iobOffset = -iobResult.totalIob,
            trendAdjustment
        )
        val warnings = buildList {
            if (predictedPostBg < HYPO_THRESHOLD) add("Pre-exercise hypoglycemia risk detected. Eat fast-acting carbs before starting.")
        }

        return RecommendationResult(
            recommendedUnits = null,
            recommendedCarbs = recommendedCarbs.toInt(),
            components = recommendationComponents,
            warnings
        )
    }

}
