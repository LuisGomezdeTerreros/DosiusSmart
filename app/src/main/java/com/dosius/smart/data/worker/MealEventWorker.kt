package com.dosius.smart.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dosius.smart.data.local.dao.DeviationPointDao
import com.dosius.smart.data.local.dao.EntryDao
import com.dosius.smart.data.local.dao.FoodCaseDao
import com.dosius.smart.data.local.dao.GlucoseReadingDao
import com.dosius.smart.data.local.dao.TherapyParameterDao
import com.dosius.smart.data.repository.ContaminationRepository
import com.dosius.smart.data.repository.EntryRepository
import com.dosius.smart.domain.engine.learning.BayesianParameterFitter
import com.dosius.smart.domain.engine.learning.MealCarbInferenceEngine
import com.dosius.smart.domain.engine.physiology.DeviationCalculator
import com.dosius.smart.domain.engine.physiology.IOBCalculator
import com.dosius.smart.domain.engine.physiology.InsulinDose
import com.dosius.smart.domain.engine.physiology.MealEvent
import com.dosius.smart.domain.engine.physiology.TherapyParameters
import com.dosius.smart.domain.engine.physiology.buildMealEvents
import com.dosius.smart.domain.model.CarbConfidence
import com.dosius.smart.domain.model.DeviationPoint
import com.dosius.smart.domain.model.Entry
import com.dosius.smart.domain.model.FoodCase
import com.dosius.smart.domain.model.InsulinType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@HiltWorker
class MealEventWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val entryDao: EntryDao,
    private val glucoseReadingDao: GlucoseReadingDao,
    private val deviationPointDao: DeviationPointDao,
    private val foodCaseDao: FoodCaseDao,
    private val therapyParameterDao: TherapyParameterDao,
    private val entryRepository: EntryRepository,
    private val contaminationRepository: ContaminationRepository,
    private val iobCalculator: IOBCalculator,
    private val deviationCalculator: DeviationCalculator,
    private val fitter: BayesianParameterFitter,
    private val carbInference: MealCarbInferenceEngine
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val entryId = inputData.getString(KEY_ENTRY_ID) ?: return Result.failure()
            val entry = entryDao.getEntriesById(entryId) ?: return Result.success()

            val params = TherapyParameters.fromDb(therapyParameterDao.getAllSuspend())

            // Food-case writing requires quantity to compute per-100g values.
            // ICR learning only needs totalCarbs, so it runs regardless of quantity.
            val quantityG = entry.quantity?.takeIf { it > 0f }
            if (quantityG != null) {
                // For multi-food meals, each food is responsible for its proportional share
                // of the post-meal glucose deviation, not the full observed response.
                val carbFraction: Float = entry.mealGroupId?.let { groupId ->
                    val siblings = entryDao.getEntriesByMealGroupId(groupId)
                    val totalGroupCarbs = siblings.sumOf { (it.totalCarbs ?: 0f).toDouble() }.toFloat()
                    if (totalGroupCarbs > 0f) (entry.totalCarbs ?: 0f) / totalGroupCarbs else 1f
                } ?: 1f

                val deviations = fetchRecomputedDeviations(entry, params)
                val enteredPer100g = (entry.totalCarbs ?: 0f) / (quantityG / 100f)
                val inferredCarbsTotal = carbInference.inferCarbsTotal(entry, deviations, params, carbFraction)
                val inferredPer100g = inferredCarbsTotal / (quantityG / 100f)
                val observationPer100g = carbInference.blendObservation(entry, inferredPer100g, enteredPer100g)

                val highConfidence = entry.carbConfidence == CarbConfidence.CERTAIN ||
                    entry.carbConfidence == CarbConfidence.HIGH
                val updatedEntry = entry.copy(
                    eventClockStatus = if (highConfidence) "CONFIRMED" else "PENDING",
                    inferredCarbsPostEvent = inferredCarbsTotal
                )
                entryRepository.upsertEntry(updatedEntry)
                if (highConfidence) entryRepository.acceptMealCarbObservation(updatedEntry)
                writeFoodCase(entry, inferredPer100g, enteredPer100g, observationPer100g, quantityG, params)
            }

            if (entry.icrLearningEnabled) {
                val shouldProcess: Boolean
                val totalCarbs: Float
                if (entry.mealGroupId != null) {
                    val siblings = entryDao.getEntriesByMealGroupId(entry.mealGroupId)
                    // Only the representative sibling (min ID) does the ICR update
                    // so multi-food meals don't double-count
                    shouldProcess = siblings.minByOrNull { it.id }?.id == entry.id
                    totalCarbs = siblings.sumOf { (it.totalCarbs ?: 0f).toDouble() }.toFloat()
                } else {
                    shouldProcess = true
                    totalCarbs = entry.totalCarbs ?: 0f
                }
                if (shouldProcess && totalCarbs > 0f) {
                    updateIcrParameter(entry, totalCarbs, params)
                }
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun fetchRecomputedDeviations(entry: Entry, params: TherapyParameters): List<DeviationPoint> {
        val windowStart = entry.timestamp.toInstant(TimeZone.UTC)
        val windowEnd = windowStart + 180.minutes

        // Fetch one reading before the window so the first pair has a valid bgPrev
        val readings = glucoseReadingDao.getReadingsAfterTimestamp((windowStart - 10.minutes).epochSeconds)
            .filter { it.timestamp.toInstant(TimeZone.UTC) <= windowEnd }
            .sortedBy { it.timestamp.toInstant(TimeZone.UTC) }

        if (readings.size < 2) return emptyList()

        // Bulk-fetch entries for IOB/COB context (5h lookback from window start)
        val contextEntries = entryDao.getRecentEntriesSince((windowStart - 5.hours).epochSeconds)

        val result = mutableListOf<DeviationPoint>()
        for (i in 1 until readings.size) {
            val prev = readings[i - 1]
            val now = readings[i]
            val nowInstant = now.timestamp.toInstant(TimeZone.UTC)

            if (nowInstant < windowStart) continue

            val doses = contextEntries
                .filter { it.insulinUnits != null && it.insulinType == InsulinType.BOLUS }
                .map { InsulinDose(units = it.insulinUnits!!, timestamp = it.timestamp, type = it.insulinType!!) }

            val point = deviationCalculator.compute(
                bgNow = now.glucoseValue,
                bgPrev = prev.glucoseValue,
                doses = doses,
                meals = buildMealEvents(contextEntries),
                cobDeviations = emptyList(),
                entries = contextEntries,
                params = params,
                now = now.timestamp
            )

            if (!contaminationRepository.isContaminated(point.timestamp, "FOOD_CARBS")) {
                result.add(point)
            }
        }
        return result
    }

    private suspend fun writeFoodCase(
        entry: Entry, inferredPer100g: Float, enteredPer100g: Float,
        observationPer100g: Float, quantityG: Float, params: TherapyParameters
    ) {
        val nowInstant = entry.timestamp.toInstant(TimeZone.UTC)
        val recentEntries = entryDao.getRecentEntriesSince(nowInstant.minus(5.hours).epochSeconds)
        val doses = recentEntries
            .filter { it.insulinUnits != null && it.insulinType == InsulinType.BOLUS }
            .map { InsulinDose(units = it.insulinUnits!!, timestamp = it.timestamp, type = it.insulinType!!) }
        val iobAtStart = iobCalculator.calculate(doses, entry.timestamp, params.diaMinutes).totalIob

        foodCaseDao.insert(FoodCase(
            id                  = UUID.randomUUID().toString(),
            foodId              = entry.foodId!!,
            mealEventId         = entry.id,
            timeOfDayBucket     = entry.timestamp.hour,
            dayOfWeek           = entry.timestamp.dayOfWeek.ordinal,
            quantityG           = quantityG,
            preMealBg           = entry.currentGlucose ?: 100,
            iobAtStart          = iobAtStart,
            enteredCarbsPer100g = enteredPer100g,
            inferredCarbsPer100g = inferredPer100g,
            carbConfidence      = entry.carbConfidence ?: CarbConfidence.MEDIUM,
            observedIauc        = 0f,
            peakBg              = 0,
            postprandialCurveJson = "[]"
        ))
    }

    private suspend fun updateIcrParameter(entry: Entry, totalCarbs: Float, params: TherapyParameters) {
        val slot = entry.timestamp.hour
        val mealInstant = entry.timestamp.toInstant(TimeZone.UTC)
        val windowStart = mealInstant + ICR_WINDOW_SKIP
        val windowEnd   = mealInstant + ICR_WINDOW_END

        val windowPoints = deviationPointDao.getAfterTimestamp(windowStart.epochSeconds)
            .filter { point ->
                val ptInstant = point.timestamp.toInstant(TimeZone.UTC)
                ptInstant <= windowEnd &&
                    !contaminationRepository.isContaminated(point.timestamp, "ICR")
            }

        val priorParam = therapyParameterDao.getBySlotAndType(slot, "ICR") ?: return
        val prevSlot = (slot + 23) % 24
        val nextSlot = (slot + 1) % 24
        val prevMean = therapyParameterDao.getBySlotAndType(prevSlot, "ICR")?.mean
        val nextMean = therapyParameterDao.getBySlotAndType(nextSlot, "ICR")?.mean

        val posterior = fitter.learnIcrFromMeal(
            windowPoints = windowPoints,
            totalCarbs = totalCarbs,
            currentIsf = params.isfAt(slot),
            currentIcr = params.icrAt(slot),
            priorMean = priorParam.mean,
            priorVariance = priorParam.variance,
            neighborMeanPrev = prevMean,
            neighborMeanNext = nextMean
        ) ?: return

        therapyParameterDao.upsert(
            priorParam.copy(
                mean          = posterior.mean,
                variance      = posterior.variance,
                nObservations = priorParam.nObservations + 1,
                lastUpdated   = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            )
        )
    }

    companion object {
        const val KEY_ENTRY_ID = "entry_id"
        private val ICR_WINDOW_SKIP = 30.minutes
        private val ICR_WINDOW_END  = 240.minutes
    }
}
