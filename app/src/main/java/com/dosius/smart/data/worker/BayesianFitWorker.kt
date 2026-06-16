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
import com.dosius.smart.data.repository.ContaminationRepository
import com.dosius.smart.data.repository.TherapyParameterRepository
import com.dosius.smart.domain.engine.learning.BayesianParameterFitter
import com.dosius.smart.domain.model.DeviationPoint
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@HiltWorker
class BayesianFitWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val deviationPointDao: DeviationPointDao,
    private val entryDao: EntryDao,
    private val therapyParameterRepository: TherapyParameterRepository,
    private val contaminationRepository: ContaminationRepository,
    private val fitter: BayesianParameterFitter
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val now = Clock.System.now()

            val sevenDaysAgo = now.minus(7.days).epochSeconds
            val rawPoints = deviationPointDao.getAfterTimestamp(sevenDaysAgo)

            val cleanPoints = rawPoints.filter { point ->
                !contaminationRepository.isContaminated(point.timestamp, "ISF")
            }

            val rawMealEntries = entryDao.getRecentEntriesSince(sevenDaysAgo)
                .filter { it.foodId != null }
            val isfSafePoints = cleanPoints.filter { point ->
                isSafeForIsf(point, rawMealEntries.map { it.timestamp })
            }

            val currentParams = therapyParameterRepository.getAllSuspend()
            if (currentParams.isEmpty()) return Result.success()

            val result = fitter.fit(cleanPoints, currentParams, isfSafePoints)

            therapyParameterRepository.upsertAll(result.updatedParams)

            notifyUser(result)

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    internal fun isSafeForIsf(
        point: DeviationPoint,
        mealTimestamps: List<LocalDateTime>
    ): Boolean {
        val pointInstant = point.timestamp.toInstant(TimeZone.UTC)
        return mealTimestamps.none { mealTime ->
            val mealInstant = mealTime.toInstant(TimeZone.UTC)

            pointInstant >= mealInstant && pointInstant <= mealInstant + MEAL_ISF_EXCLUSION_HOURS
        }
    }

    private fun notifyUser(result: BayesianParameterFitter.FitResult) {
        if (result.cleanPointCount == 0) return

        val channelId = CHANNEL_ID
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Parameter Learning",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Weekly therapy parameter updates" }
        notificationManager.createNotificationChannel(channel)

        val guardrailNote = if (result.clippedSlots.isNotEmpty())
            " (${result.clippedSlots.size} slot(s) guardrail-clipped)"
        else ""

        val body = buildString {
            append("ISF confidence: ${(result.factorBIsf * 100).toInt()}%  ")
            append("Model quality: ${(result.factorA * 100).toInt()}%")
            append(guardrailNote)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Weekly parameter update available")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val WORK_NAME = "bayesian_weekly_fit"
        private const val CHANNEL_ID = "param_learning"
        private const val NOTIFICATION_ID = 3001
        private val MEAL_ISF_EXCLUSION_HOURS = 4.hours
    }
}
