package com.dosius.smart.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey val type: String,
    val expectedGlucoseDropPerHour: Float?,
    val confidencePercentage: Int?,
    val dropVariance: Float? = null,
    val nObservations: Int = 0


)