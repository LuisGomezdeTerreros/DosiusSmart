package com.dosius.smart.data.repository

import com.dosius.smart.data.local.dao.DeviationPointDao
import com.dosius.smart.data.local.dao.EntryDao
import com.dosius.smart.data.local.dao.GlucoseReadingDao
import com.dosius.smart.data.local.dao.TherapyParameterDao
import com.dosius.smart.domain.engine.physiology.DeviationCalculator
import com.dosius.smart.domain.engine.physiology.InsulinDose
import com.dosius.smart.domain.engine.physiology.TherapyParameters
import com.dosius.smart.domain.engine.physiology.buildMealEvents
import com.dosius.smart.domain.model.InsulinType
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Singleton
class DeviationRepository @Inject constructor(
    private val glucoseReadingDao: GlucoseReadingDao,
    private val entryDao: EntryDao,
    private val therapyParameterDao: TherapyParameterDao,
    private val deviationCalculator: DeviationCalculator,
    private val deviationPointDao: DeviationPointDao,
) {

    suspend fun computeAndStore(now: LocalDateTime) {
        val params = TherapyParameters.fromDb(therapyParameterDao.getAllSuspend())
        val diaMinutes = params.diaMinutes
        val nowInstant = Clock.System.now()
        val entries =
            entryDao.getRecentEntriesSince(nowInstant.minus(5.hours).epochSeconds)

        val doses =
            entries.filter { it.insulinUnits != null && it.insulinType == InsulinType.BOLUS }
                .map { it ->
                    InsulinDose(
                        units = it.insulinUnits!!, timestamp = it.timestamp, type = it.insulinType!!
                    )
                }
        val meals = buildMealEvents(entries)
        val cobDeviations =
            deviationPointDao.getAfterTimestamp(nowInstant.minus(6.hours).epochSeconds)
        val readings = glucoseReadingDao.getReadingsAfterTimestamp(
            nowInstant.minus(10.minutes).epochSeconds
        )
        if (readings.size < 2) return
        val bgNow = readings[0].glucoseValue
        val bgPrev = readings[1].glucoseValue

        val deviationPoint = deviationCalculator.compute(
            bgNow, bgPrev, doses, meals, cobDeviations, entries, params, now
        )
        deviationPointDao.insert(deviationPoint)
        deviationPointDao.deleteOlderThan(nowInstant.minus(7.days).epochSeconds)
    }
}