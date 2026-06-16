package com.dosius.smart.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDateTime

@Entity(tableName = "alert_events")
data class AlertEvent(
    @PrimaryKey val id: String,
    val timestamp: LocalDateTime,
    val alertType: String,
    val triggeringValue: Float,
    val recommendedAction: String,
    val userResponse: String?
)