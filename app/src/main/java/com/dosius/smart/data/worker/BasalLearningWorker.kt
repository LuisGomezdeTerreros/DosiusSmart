package com.dosius.smart.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dosius.smart.data.local.dao.DeviationPointDao
import com.dosius.smart.data.local.dao.EntryDao
import com.dosius.smart.data.local.dao.TherapyParameterDao
import com.dosius.smart.data.repository.ContaminationRepository
import com.dosius.smart.domain.engine.learning.BayesianParameterFitter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.hours

@HiltWorker
class BasalLearningWorker @AssistedInject constructor(
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
            val sleepEnd = entry.sleepWindowEnd ?: return Result.success()

            val sleepStartInstant = entry.timestamp.toInstant(TimeZone.UTC)
            val sleepEndInstant = sleepEnd.toInstant(TimeZone.UTC)

            // Pre-sleep contamination: reject if meal/bolus in last 4h or exercise in last 3h
            val mealCutoff = (sleepStartInstant - PRE_SLEEP_MEAL_WINDOW).epochSeconds
            val exerciseCutoff = (sleepStartInstant - PRE_SLEEP_EXERCISE_WINDOW).epochSeconds
            val preSleepEntries = entryDao.getRecentEntriesSince(mealCutoff)
                .filter { it.id != entryId }

            val hasMealOrBolus = preSleepEntries.any { e ->
                val eInstant = e.timestamp.toInstant(TimeZone.UTC)
                eInstant < sleepStartInstant && (
                    (e.totalCarbs ?: 0f) > 0f ||
                    (e.insulinUnits ?: 0f) > 0f
                )
            }
            val hasExercise = preSleepEntries.any { e ->
                val eInstant = e.timestamp.toInstant(TimeZone.UTC)
                eInstant >= (sleepStartInstant - PRE_SLEEP_EXERCISE_WINDOW) &&
                    eInstant < sleepStartInstant &&
                    e.exerciseType != null
            }
            if (hasMealOrBolus || hasExercise) return Result.success()

            // Collect deviation points during the sleep window
            val rawPoints = deviationPointDao.getAfterTimestamp(sleepStartInstant.epochSeconds)
            val sleepPoints = rawPoints.filter { pt ->
                val ptInstant = pt.timestamp.toInstant(TimeZone.UTC)
                ptInstant >= sleepStartInstant &&
                    ptInstant <= sleepEndInstant &&
                    !contaminationRepository.isContaminated(pt.timestamp, "BASAL")
            }

            val currentParams = therapyParameterDao.getAllSuspend()
            if (currentParams.isEmpty()) return Result.success()

            val result = fitter.fitBasalFromSleep(sleepPoints, currentParams)
                ?: return Result.success()

            therapyParameterDao.upsert(result.updatedParam)
            notifyUser(result)

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private fun notifyUser(result: BayesianParameterFitter.BasalFitResult) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID, "Basal Learning", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Basal dose learning from sleep windows" }
        notificationManager.createNotificationChannel(channel)

        val direction = if (result.meanDeviation > 0) "rising" else "falling"
        val body = "Overnight glucose was $direction. " +
            "New basal suggestion available (${result.cleanPointCount} data points). " +
            "Review on the Parameters screen."

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Basal learning complete")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_ENTRY_ID = "entry_id"
        private const val CHANNEL_ID = "basal_learning"
        private const val NOTIFICATION_ID = 3002
        private val PRE_SLEEP_MEAL_WINDOW = 4.hours
        private val PRE_SLEEP_EXERCISE_WINDOW = 3.hours
    }
}
