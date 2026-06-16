package com.dosius.smart.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDateTime

@Entity(tableName = "deviation_points")
data class DeviationPoint(
    @PrimaryKey val id: String,
    val timestamp: LocalDateTime,
    val predictedDelta: Float,
    val observedDelta: Float,
    val deviation: Float,
    val bgi: Float,
    val fastingWindow: Boolean,
    val postprandialWindow: Boolean,
    val carbConfidence: CarbConfidence,
    val recentExercise: Boolean,
    val outlierFlag: Boolean,
    val timeSlot: Int,
    val dayOfWeek: Int,
    val iobPresent: Boolean,
    val cobPresent: Boolean
)
