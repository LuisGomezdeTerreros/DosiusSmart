package com.dosius.smart.data.remote.mapper

import com.dosius.smart.data.remote.dto.GlucoseMeasurementDto
import com.dosius.smart.domain.model.GlucoseReading
import com.dosius.smart.domain.model.GlucoseTrend
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FORMATTER = DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.US)

fun GlucoseMeasurementDto.toDomain(): GlucoseReading {
    val javaDateTime = java.time.LocalDateTime.parse(timestamp, FORMATTER)
    return GlucoseReading(
        id = "LibreLinkUp_${timestamp.hashCode()}",
        timestamp = javaDateTime.toKotlinLocalDateTime(),
        glucoseValue = valueInMgPerDl,
        trend = GlucoseTrend.fromTrendArrow(trendArrow),
        trendRate = 0f,
        source = "LibreLinkUp"
    )
}
