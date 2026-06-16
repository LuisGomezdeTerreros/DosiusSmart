package com.dosius.smart.data.repository

import com.dosius.smart.data.local.dao.DeviationPointDao
import com.dosius.smart.data.local.dao.EntryDao
import com.dosius.smart.data.local.dao.ExerciseDao
import com.dosius.smart.data.local.dao.ForecastDao
import com.dosius.smart.data.local.dao.GlucoseReadingDao
import com.dosius.smart.data.local.dao.TherapyParameterDao
import com.dosius.smart.domain.engine.physiology.COBCalculator
import com.dosius.smart.domain.engine.physiology.ExerciseEvent
import com.dosius.smart.domain.engine.physiology.ForecastPoint
import com.dosius.smart.domain.engine.physiology.GlucoseForecaster
import com.dosius.smart.domain.engine.physiology.HistoricalPoint
import com.dosius.smart.domain.engine.physiology.IOBCalculator
import com.dosius.smart.domain.engine.physiology.InsulinDose
import com.dosius.smart.domain.engine.physiology.buildMealEvents
import com.dosius.smart.domain.engine.physiology.TherapyParameters
import com.dosius.smart.domain.model.Forecast
import com.dosius.smart.domain.model.InsulinType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes


@Singleton
class ForecastRepository @Inject constructor(
    private val forecastDao: ForecastDao,
    private val glucoseForecaster: GlucoseForecaster,
    private val entryDao: EntryDao,
    private val glucoseReadingDao: GlucoseReadingDao,
    private val deviationPointDao: DeviationPointDao,
    private val therapyParameterDao: TherapyParameterDao,
    private val exerciseDao: ExerciseDao,
    private val iobCalculator: IOBCalculator,
    private val cobCalculator: COBCalculator
) {
    private val _historicalPoints = MutableStateFlow<List<HistoricalPoint>>(emptyList())
    val historicalPoints: StateFlow<List<HistoricalPoint>> = _historicalPoints.asStateFlow()

    suspend fun computeAndStore(now: LocalDateTime): List<ForecastPoint> {

        val params = TherapyParameters.fromDb(therapyParameterDao.getAllSuspend())
        val diaMinutes = params.diaMinutes
        val nowInstant = Clock.System.now()
        val entries =
            entryDao.getRecentEntriesSince(nowInstant.minus(5.hours).epochSeconds)

        val doses =
            entries.filter { it.insulinUnits != null && it.insulinType != InsulinType.BASAL }
                .map { it ->
                    InsulinDose(
                        units = it.insulinUnits!!, timestamp = it.timestamp,
                        type = it.insulinType ?: InsulinType.BOLUS
                    )
                }
        val meals = buildMealEvents(entries)
        val exerciseEvents = entries
            .filter { it.exerciseType != null && it.durationOfExercise != null }
            .mapNotNull { entry ->
                val drop = exerciseDao.getByType(entry.exerciseType!!)?.expectedGlucoseDropPerHour
                    ?: return@mapNotNull null
                ExerciseEvent(
                    startTime = entry.timestamp,
                    durationMin = entry.durationOfExercise!!,
                    expectedDropPerHour = drop
                )
            }
        val cgmHistory = glucoseReadingDao.getReadingsAfterTimestamp(
            nowInstant.minus(2.hours).epochSeconds
        )
        // Short window for retrospective correction (meanDev) — captures current trend noise only
        val recentDeviations =
            deviationPointDao.getAfterTimestamp(nowInstant.minus(10.minutes).epochSeconds)
        // Full absorption window for COB — needs all deviations since any active meal (max 300 min)
        val cobDeviations =
            deviationPointDao.getAfterTimestamp(nowInstant.minus(6.hours).epochSeconds)
        val bgNow = glucoseReadingDao.getLatestReading()?.glucoseValue ?: return emptyList()

        val points = glucoseForecaster.forecast(
            bgNow, now, doses, diaMinutes, meals, exerciseEvents, recentDeviations, cobDeviations, params, cgmHistory
        )
        val allDeviations = deviationPointDao.getAfterTimestamp(nowInstant.minus(12.hours).epochSeconds)


        _historicalPoints.value = cgmHistory.map { reading ->
            val deviationsForReading = allDeviations.filter {
                it.timestamp <= reading.timestamp
            }
            val cobStatus = cobCalculator.calculate(meals, deviationsForReading, params, reading.timestamp)
            HistoricalPoint(
                reading.timestamp,
                iobCalculator.calculate(doses, reading.timestamp, diaMinutes).totalIob,
                cobStatus.totalCob,
                cobStatus.minAbsorptionRatio
            )
        }
        val forecastCurveJson = Json.encodeToString(points)
        val forecast = Forecast(
            id = UUID.randomUUID().toString(), now, forecastCurveJson, componentsJson = "{}"
        )
        forecastDao.upsert(forecast)
        return points


    }

    fun observeLatest(): Flow<List<ForecastPoint>?> {
        return forecastDao.getLatest()
            .map { forecast -> forecast?.let { Json.decodeFromString<List<ForecastPoint>>(it.forecastCurveJson) } }

    }


}