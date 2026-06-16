package com.dosius.smart.domain.model

import androidx.room.Entity
import kotlinx.datetime.LocalDateTime

@Entity(
    tableName = "therapy_parameters",
    primaryKeys = ["slotIndex", "parameterType"]
)
data class TherapyParameter(
    val slotIndex: Int,
    val parameterType: String,
    val mean: Float,
    val variance: Float,
    val nObservations: Int,
    val lastUpdated: LocalDateTime,
    val currentValue: Float
)