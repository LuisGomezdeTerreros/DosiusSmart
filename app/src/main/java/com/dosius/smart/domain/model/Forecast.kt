package com.dosius.smart.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDateTime

@Entity(tableName = "forecasts")
data class Forecast(
    @PrimaryKey val id: String,
    val timestampGenerated: LocalDateTime,
    val forecastCurveJson: String,
    val componentsJson: String
)