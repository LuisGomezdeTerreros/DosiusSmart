package com.dosius.smart.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dosius.smart.data.local.dao.ContaminationWindowDao
import com.dosius.smart.data.local.dao.EntryDao
import com.dosius.smart.data.local.dao.GlucoseReadingDao
import com.dosius.smart.data.repository.EntryRepository
import com.dosius.smart.domain.engine.learning.ExerciseDropEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

@HiltWorker
class ExerciseEventWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val entryDao: EntryDao,
    private val glucoseReadingDao: GlucoseReadingDao,
    private val contaminationWindowDao: ContaminationWindowDao,
    private val entryRepository: EntryRepository,
    private val exerciseDropEngine: ExerciseDropEngine
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val entryId = inputData.getString(KEY_ENTRY_ID) ?: return Result.failure()
            val entry = entryDao.getEntriesById(entryId) ?: return Result.success()

            val bgStart = entry.currentGlucose ?: return Result.success()
            val durationMinutes = entry.durationOfExercise ?: 60f

            val windowStart = entry.timestamp.toInstant(TimeZone.UTC)
            val readings = glucoseReadingDao.getReadingsAfterTimestamp(windowStart.epochSeconds)

            val result = exerciseDropEngine.process(entry, readings, bgStart, durationMinutes)
                ?: return Result.success()

            contaminationWindowDao.insert(result.contaminationWindow)

            // The posterior is never updated silently: the user must accept the
            // observed drop from the review card in Logs before it is learned.
            entryRepository.upsertEntry(entry.copy(
                eventClockStatus = "PENDING_EXERCISE",
                actualExerciseDrop = result.actualDrop
            ))

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_ENTRY_ID = "entry_id"
    }
}
