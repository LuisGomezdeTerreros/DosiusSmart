package com.dosius.smart.domain.model


import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDateTime

@Entity(
    tableName = "recommendation_logs",
    foreignKeys = [ForeignKey(
        entity = Entry::class,
        parentColumns = ["id"],
        childColumns = ["entryId"],
        onDelete = ForeignKey.SET_NULL
    )]
)data class RecommendationLog(
    @PrimaryKey val id: String,
    val timestamp: LocalDateTime,
    val type: String,
    val inputsJson: String,
    val outputJson: String,
    val confidence: Int,
    val userAction: String?, //REJECTED, ACCEPTED . for now
    val actualOutcome: String?,
    val rejectionDirection: String?,
    val entryId: String?
)