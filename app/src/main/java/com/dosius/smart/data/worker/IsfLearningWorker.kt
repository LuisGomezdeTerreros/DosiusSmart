package com.dosius.smart.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dosius.smart.data.local.dao.DeviationPointDao
import com.dosius.smart.data.local.dao.EntryDao
import com.dosius.smart.data.local.dao.TherapyParameterDao
import com.dosius.smart.data.repository.ContaminationRepository
import com.dosius.smart.domain.engine.learning.BayesianParameterFitter
import com.dosius.smart.domain.engine.physiology.TherapyParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

/**
 * Fired 4 hours after a bolus entry that the user opted in for ISF learning.
 *
 * Orchestration only: collects the correction-tail deviation points, excludes contaminated
 * ones, then hands the clean window to [BayesianParameterFitter.learnIsfFromCorrection] for the
 * observation math and conjugate Gaussian update, and persists the posterior.
 */
@HiltWorker
class IsfLearningWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val entryDao: EntryDao,
    private val deviationPointDao: DeviationPointDao,
    private val therapyParameterDao: TherapyParameterDao,
    private val contaminationRepository: ContaminationRepository,
    private val fitter: BayesianParameterFitter
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val entryId = inputData.getString(KEY_ENTRY_ID) ?: return Result.failure()
            val entry = entryDao.getEntryByIdSuspend(entryId) ?: return Result.success()

            if (!entry.isfLearningEnabled) return Result.success()

            val therapyParams = TherapyParameters.fromDb(therapyParameterDao.getAllSuspend())
            val timeSlot = entry.timestamp.hour
            val currentIsf = therapyParams.isfAt(timeSlot)

            val bolusTsInstant = entry.timestamp.toInstant(TimeZone.UTC)
            val windowStart = bolusTsInstant + ISF_WINDOW_SKIP
            val windowEnd   = bolusTsInstant + ISF_WINDOW_END

            val windowPoints = deviationPointDao
                .getAfterTimestamp(windowStart.epochSeconds)
                .filter { point ->
                    val ptInstant = point.timestamp.toInstant(TimeZone.UTC)
                    ptInstant <= windowEnd &&
                        !contaminationRepository.isContaminated(point.timestamp, "ISF")
                }

            val priorParam = therapyParameterDao.getBySlotAndType(timeSlot, "ISF")
                ?: return Result.success()
            val prevSlot = (timeSlot + 23) % 24
            val nextSlot = (timeSlot + 1) % 24
            val prevMean = therapyParameterDao.getBySlotAndType(prevSlot, "ISF")?.mean
            val nextMean = therapyParameterDao.getBySlotAndType(nextSlot, "ISF")?.mean

            val posterior = fitter.learnIsfFromCorrection(
                windowPoints = windowPoints,
                currentIsf = currentIsf,
                priorMean = priorParam.mean,
                priorVariance = priorParam.variance,
                neighborMeanPrev = prevMean,
                neighborMeanNext = nextMean
            ) ?: return Result.success()

            therapyParameterDao.upsert(
                priorParam.copy(
                    mean          = posterior.mean,
                    variance      = posterior.variance,
                    nObservations = priorParam.nObservations + 1,
                    lastUpdated   = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                )
            )

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_ENTRY_ID      = "entry_id"
        private val ISF_WINDOW_SKIP = 60.minutes
        private val ISF_WINDOW_END  = 240.minutes
    }
}
