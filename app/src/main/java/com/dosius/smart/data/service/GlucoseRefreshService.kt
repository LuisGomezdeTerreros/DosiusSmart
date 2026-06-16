package com.dosius.smart.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dosius.smart.MainActivity
import com.dosius.smart.R
import com.dosius.smart.data.alarm.GlucoseAlarmChecker
import com.dosius.smart.data.repository.ContaminationRepository
import com.dosius.smart.data.repository.DeviationRepository
import com.dosius.smart.data.repository.ForecastRepository
import com.dosius.smart.data.repository.LibreLinkUpRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

@AndroidEntryPoint
class GlucoseRefreshService : Service() {

    @Inject lateinit var repository: LibreLinkUpRepository
    @Inject lateinit var alarmChecker: GlucoseAlarmChecker
    @Inject lateinit var deviationRepository: DeviationRepository
    @Inject lateinit var contaminationRepository: ContaminationRepository
    @Inject lateinit var forecastRepository: ForecastRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "glucose_monitor"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        alarmChecker.createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Monitoring glucose..."))

        serviceScope.launch {
            while (true) {
                try {
                    repository.refresh()
                    val reading = repository.getLatestReading()
                    val text = if (reading != null) {
                        val minutesAgo = (Clock.System.now() - reading.timestamp.toInstant(TimeZone.currentSystemDefault())).inWholeMinutes
                        val agoLabel = if (minutesAgo <= 0) "just now" else "$minutesAgo min ago"
                        "${reading.glucoseValue} mg/dL ${reading.trend.displayArrow} · $agoLabel"
                    } else {
                        "Waiting for data..."
                    }
                    updateNotification(text)
                    if (reading != null) alarmChecker.check(reading.glucoseValue, reading.timestamp)
                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    runCatching { deviationRepository.computeAndStore(now) }
                    runCatching { contaminationRepository.detectAndStore(now) }
                    runCatching { forecastRepository.computeAndStore(now) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // network failure — keep looping
                }
                delay(5.minutes)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Glucose Monitor",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dosius Smart")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        startForeground(NOTIFICATION_ID, buildNotification(text))
    }
}