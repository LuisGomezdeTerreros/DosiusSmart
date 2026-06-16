package com.dosius.smart.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDateTime

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey val id: String,
    val timestamp: LocalDateTime, // User-selected time
    val createdAt: LocalDateTime, // Actual creation time
    val description: String = "",

    val foodId: String? ,
    val quantity: Float?,
    val totalCarbs: Float?,
    val carbConfidence: CarbConfidence?,
    val mealType: String?,
    val registrationMethod: RegistrationMethod,

    val insulinUnits: Float?,
    val insulinType: InsulinType?,
    val recommendedUnits: Float?,
    val currentGlucose: Int?,

    val exerciseType: String? ,
    val durationOfExercise: Float?,
    val intensity: ExerciseIntensity?,

    val recommendedCarbs: Int?,

    val inferredCarbsPostEvent: Float? = null,
    val eventClockStatus: String? = null,
    val dismissalReason: String? = null,

    val actualExerciseDrop: Float? = null,
    val mealGroupId: String? = null,
    val isfLearningEnabled: Boolean = false,
    val icrLearningEnabled: Boolean = false,
    val sleepWindowEnd: LocalDateTime? = null
)

enum class InsulinType{
    BASAL, BOLUS
}

enum class ExerciseIntensity{
    LOW,MEDIUM, HIGH
}

enum class RegistrationMethod {
    MANUAL, CHAT
}