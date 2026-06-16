package com.dosius.smart.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dosius.smart.data.repository.DeviationRepository
import com.dosius.smart.data.repository.ForecastRepository
import com.dosius.smart.data.repository.LibreLinkUpRepository
import com.dosius.smart.data.repository.ContaminationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@HiltWorker
class GlucoseRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: LibreLinkUpRepository,
    private val forecastRepository: ForecastRepository,
    private val deviationRepository: DeviationRepository,
    private val contaminationRepository: ContaminationRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            repository.refresh()
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            deviationRepository.computeAndStore(now)
            contaminationRepository.detectAndStore(now)
            forecastRepository.computeAndStore(now)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "glucose_refresh"
    }
}
