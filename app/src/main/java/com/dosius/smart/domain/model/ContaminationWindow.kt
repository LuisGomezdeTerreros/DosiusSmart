package com.dosius.smart.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDateTime

@Entity(tableName = "contamination_windows")
data class ContaminationWindow(
    @PrimaryKey val id: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val reason: String,
    val sourceEventId: String?,
    val excludedParamTypes: String
)