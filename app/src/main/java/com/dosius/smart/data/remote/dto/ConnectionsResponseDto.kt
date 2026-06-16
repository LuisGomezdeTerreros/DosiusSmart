package com.dosius.smart.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionsResponseDto(val status: Int, val data: List<ConnectionDto> = emptyList())

@Serializable
data class ConnectionDto( val patientId: String, val glucoseMeasurement: GlucoseMeasurementDto? = null)
