package com.dosius.smart.data.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.dosius.smart.MainActivity
import com.dosius.smart.R
import com.dosius.smart.data.preferences.AlarmPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlucoseAlarmChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AlarmPreferences
) {
    companion object {
        const val CHANNEL_ID = "glucose_alarm"
        const val NOTIFICATION_ID = 2
    }

    fun createNotificationChannel() {
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Glucose Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Hypoglycemia and hyperglycemia threshold alerts"
            setBypassDnd(true)
            enableVibration(true)
            setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, audioAttrs)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    suspend fun check(glucoseValue: Int, readingTimestamp: LocalDateTime) {
        val hypoEnabled = prefs.hypoEnabled.first()
        val hyperEnabled = prefs.hyperEnabled.first()
        val hypoThreshold = prefs.hypoThreshold.first()
        val hyperThreshold = prefs.hyperThreshold.first()
        val hypoState = prefs.hypoState.first()
        val hyperState = prefs.hyperState.first()

        val minutesAgo = (Clock.System.now() - readingTimestamp.toInstant(TimeZone.currentSystemDefault())).inWholeMinutes
        val agoLabel = if (minutesAgo <= 0) "just now" else "$minutesAgo min ago"

        val t = computeAlarmTransition(glucoseValue, hypoEnabled, hyperEnabled, hypoThreshold, hyperThreshold, hypoState, hyperState)
        if (t.shouldFireHypo) fire("Hypoglycemia Alert", "Glucose $glucoseValue mg/dL below $hypoThreshold mg/dL · $agoLabel")
        if (t.shouldFireHyper) fire("Hyperglycemia Alert", "Glucose $glucoseValue mg/dL above $hyperThreshold mg/dL · $agoLabel")
        if (t.newHypoState != hypoState) prefs.setHypoState(t.newHypoState)
        if (t.newHyperState != hyperState) prefs.setHyperState(t.newHyperState)
        if (t.shouldCancel) cancel()
    }

    /**
     * Called after alarm settings are saved so that if glucose is already out of range
     * at save time, no alarm fires until glucose recovers and goes out of range again.
     */
    suspend fun initializeState(glucoseValue: Int?) {
        if (glucoseValue == null) return
        val hypoEnabled = prefs.hypoEnabled.first()
        val hyperEnabled = prefs.hyperEnabled.first()
        val hypoThreshold = prefs.hypoThreshold.first()
        val hyperThreshold = prefs.hyperThreshold.first()

        prefs.setHypoState(if (hypoEnabled && glucoseValue < hypoThreshold) "ALARMED" else "NORMAL")
        prefs.setHyperState(if (hyperEnabled && glucoseValue > hyperThreshold) "ALARMED" else "NORMAL")
    }

    fun cancel() {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun fire(title: String, text: String) {
        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }
}

internal data class AlarmTransition(
    val newHypoState: String,
    val newHyperState: String,
    val shouldFireHypo: Boolean,
    val shouldFireHyper: Boolean,
    val shouldCancel: Boolean
)

internal fun computeAlarmTransition(
    glucoseValue: Int,
    hypoEnabled: Boolean,
    hyperEnabled: Boolean,
    hypoThreshold: Int,
    hyperThreshold: Int,
    hypoState: String,
    hyperState: String
): AlarmTransition = when {
    hypoEnabled && glucoseValue < hypoThreshold -> AlarmTransition(
        newHypoState = "ALARMED",
        newHyperState = if (hyperState == "ALARMED") "NORMAL" else hyperState,
        shouldFireHypo = hypoState == "NORMAL",
        shouldFireHyper = false,
        shouldCancel = false
    )
    hyperEnabled && glucoseValue > hyperThreshold -> AlarmTransition(
        newHypoState = if (hypoState == "ALARMED") "NORMAL" else hypoState,
        newHyperState = "ALARMED",
        shouldFireHypo = false,
        shouldFireHyper = hyperState == "NORMAL",
        shouldCancel = false
    )
    else -> AlarmTransition(
        newHypoState = if (hypoState == "ALARMED") "NORMAL" else hypoState,
        newHyperState = if (hyperState == "ALARMED") "NORMAL" else hyperState,
        shouldFireHypo = false,
        shouldFireHyper = false,
        shouldCancel = true
    )
}
