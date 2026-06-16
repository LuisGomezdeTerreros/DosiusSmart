package com.dosius.smart.data.repository

import java.util.UUID
import com.dosius.smart.data.local.dao.EntryDao
import com.dosius.smart.data.local.dao.ExerciseDao
import com.dosius.smart.data.local.dao.GlucoseReadingDao
import com.dosius.smart.data.local.dao.RecommendationLogDao
import com.dosius.smart.domain.engine.physiology.InsulinDose
import com.dosius.smart.domain.engine.physiology.TherapyParameters
import com.dosius.smart.domain.engine.physiology.ForecastPoint
import com.dosius.smart.domain.engine.recommendation.CorrectionType
import com.dosius.smart.domain.engine.recommendation.RecommendationEngine
import com.dosius.smart.domain.engine.recommendation.RecommendationResult
import com.dosius.smart.domain.engine.recommendation.RecommendationType
import com.dosius.smart.domain.model.InsulinType
import com.dosius.smart.domain.model.RecommendationLog
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours

@Singleton
class RecommendationRepository @Inject constructor(
    private val entryDao: EntryDao,
    private val glucoseReadingDao: GlucoseReadingDao,
    private val therapyParameterRepository: TherapyParameterRepository,
    private val exerciseDao: ExerciseDao,
    private val recommendationLogDao: RecommendationLogDao,
    private val recommendationEngine: RecommendationEngine
) {
    private data class EngineInputs(
        val params: TherapyParameters,
        val doses: List<InsulinDose>,
        val trendRate: Float,
        val hasLoggedData: Boolean
    )

    private suspend fun buildEngineInputs(): EngineInputs {
        val rows = therapyParameterRepository.getAllSuspend()
        val therapyParameters = TherapyParameters.fromDb(rows)
        val lastReadings = glucoseReadingDao.getReadingsAfterTimestamp(Clock.System.now().minus(2.hours).epochSeconds)
        val trendRate = if (lastReadings.size >= 2) {
            (lastReadings[0].glucoseValue - lastReadings[1].glucoseValue).toFloat() / 5f
        } else 0f
        val recentEntries = entryDao.getRecentEntriesSince(Clock.System.now().minus(5.hours).epochSeconds)
        val doses = recentEntries
            .filter { it.insulinUnits != null && it.insulinType == InsulinType.BOLUS }
            .map { InsulinDose(units = it.insulinUnits!!, timestamp = it.timestamp, type = it.insulinType!!) }
        val hasLoggedData = recentEntries.any { it.totalCarbs != null || it.insulinUnits != null }

        return EngineInputs(therapyParameters, doses, trendRate, hasLoggedData)
    }

    suspend fun computePrandial(carbs: Float, mealTime: LocalDateTime): RecommendationResult {
        val engineInputs = buildEngineInputs()
        val lastReading = glucoseReadingDao.getLatestReading()
        return recommendationEngine.computePrandialBolus(
            carbs,
            mealTime,
            lastReading?.glucoseValue ?: 120,
            lastReading?.trendRate ?: 0f,
            engineInputs.params,
            engineInputs.doses
        )
    }

    suspend fun computeSmartCorrection(forecastPoints: List<ForecastPoint>): RecommendationResult {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val engineInputs = buildEngineInputs()
        val lastReading = glucoseReadingDao.getLatestReading()
        return recommendationEngine.computeCorrectionBolus(
            currentBg = lastReading?.glucoseValue ?: 120,
            forecastPoints = forecastPoints,
            params = engineInputs.params,
            doses = engineInputs.doses,
            now = now,
            trendRateMgPerMin = engineInputs.trendRate,
            hasLoggedData = engineInputs.hasLoggedData
        )
    }

    suspend fun computePreExercise(exerciseType: String, durationMin: Float, exerciseTime: LocalDateTime): RecommendationResult {
        val engineInputs = buildEngineInputs()
        val lastReading = glucoseReadingDao.getLatestReading()
        val expectedDropPerHour = exerciseDao.getExerciseById(exerciseType).first()?.expectedGlucoseDropPerHour ?: 40f
        return recommendationEngine.computePreExercise(
            expectedDropPerHour,
            durationMin,
            lastReading?.glucoseValue ?: 120,
            engineInputs.params,
            engineInputs.doses,
            exerciseTime,
            lastReading?.trendRate ?: 0f
        )
    }

    suspend fun writeLog(type: RecommendationType, result: RecommendationResult, entryId : String , userAction : String) : String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val id = UUID.randomUUID().toString()
        val recommendationLog = RecommendationLog(
            id,
            now,
            type.name,
            inputsJson = "units=${result.recommendedUnits},carbs=${result.recommendedCarbs}",
            outputJson = "warnings=${result.warnings}",
            confidence = 0,
            userAction ,
            actualOutcome = null ,
            rejectionDirection = null,
            entryId
        )
        recommendationLogDao.insert(recommendationLog)
        return id
    }

}