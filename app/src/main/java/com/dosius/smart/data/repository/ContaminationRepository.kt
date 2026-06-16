package com.dosius.smart.data.repository

import com.dosius.smart.data.local.dao.ContaminationWindowDao
import com.dosius.smart.data.local.dao.DeviationPointDao
import com.dosius.smart.data.local.dao.EntryDao
import com.dosius.smart.data.local.dao.GlucoseReadingDao
import com.dosius.smart.domain.engine.contamination.ContaminationDetectionEngine
import com.dosius.smart.domain.model.ContaminationWindow
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@Singleton
class ContaminationRepository @Inject constructor(
    private val entryDao: EntryDao,
    private val deviationPointDao: DeviationPointDao,
    private val glucoseReadingDao: GlucoseReadingDao,
    private val contaminationWindowDao: ContaminationWindowDao,
    private val detectionEngine: ContaminationDetectionEngine
) {

    suspend fun detectAndStore(now: LocalDateTime) {
        val nowInstant = now.toInstant(TimeZone.UTC)

        val exerciseEntries = entryDao.getRecentEntriesSince(nowInstant.minus(24.hours).epochSeconds)
        detectionEngine.detectExerciseWindows(exerciseEntries).forEach { insertIfNew(it) }

        val hypoReadings = glucoseReadingDao.getReadingsAfterTimestamp(nowInstant.minus(1.days).epochSeconds)
        detectionEngine.detectHypoWindows(hypoReadings).forEach { insertIfNew(it) }

        val statDeviations = deviationPointDao.getAfterTimestamp(nowInstant.minus(1.days).epochSeconds)
        detectionEngine.detectStatisticalWindows(statDeviations).forEach { contaminationWindowDao.insert(it) }

        val uamEntries = entryDao.getRecentEntriesSince(nowInstant.minus(25.hours).epochSeconds)
        val uamDeviations = deviationPointDao.getAfterTimestamp(nowInstant.minus(1.days).epochSeconds)
        detectionEngine.detectUamWindows(uamDeviations, uamEntries).forEach { insertIfNew(it) }
    }

    private suspend fun insertIfNew(window: ContaminationWindow) {
        val sourceEventId = window.sourceEventId
        if (sourceEventId == null || contaminationWindowDao.getBySourceEventId(sourceEventId) == null) {
            contaminationWindowDao.insert(window)
        }
    }

    suspend fun isContaminated(timestamp: LocalDateTime, paramType: String): Boolean {
        return contaminationWindowDao.getWindowsContaining(
            timestamp.toInstant(TimeZone.UTC).epochSeconds
        ).any { it.excludedParamTypes.split(",").contains(paramType) }
    }

    suspend fun getUAM(sinceEpoch: Long) = contaminationWindowDao.getUAM(sinceEpoch)

    suspend fun writeWindow(window: ContaminationWindow) = contaminationWindowDao.insert(window)
}
