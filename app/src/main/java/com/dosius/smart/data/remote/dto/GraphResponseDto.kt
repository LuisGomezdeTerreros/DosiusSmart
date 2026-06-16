package com.dosius.smart.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GraphResponseDto(val status: Int, val data: GraphDataDto? = null)

@Serializable
data class GraphDataDto(
    val connection: ConnectionDetailDto? = null,
    val graphData: List<GlucoseMeasurementDto> = emptyList()
)

@Serializable
data class ConnectionDetailDto(
    val patientId: String = "", val glucoseMeasurement: GlucoseMeasurementDto? = null
)

@Serializable
data class GlucoseMeasurementDto(
    @SerialName("Timestamp") val timestamp: String = "",
    @SerialName("ValueInMgPerDl") val valueInMgPerDl: Int = 0,
    @SerialName("TrendArrow") val trendArrow: Int = 4

)
