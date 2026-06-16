package com.dosius.smart.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDateTime

@Entity(tableName = "glucose_readings")
data class GlucoseReading(
    @PrimaryKey val id: String,
    val timestamp: LocalDateTime,
    val glucoseValue: Int,
    val trend: GlucoseTrend,
    val trendRate: Float,
    val source: String
)