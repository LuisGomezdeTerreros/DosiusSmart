package com.dosius.smart.data.local.database

import androidx.room.TypeConverter
import com.dosius.smart.domain.model.CarbConfidence
import com.dosius.smart.domain.model.ExerciseIntensity
import com.dosius.smart.domain.model.GlucoseTrend
import com.dosius.smart.domain.model.Ingredient
import com.dosius.smart.domain.model.InsulinType
import com.dosius.smart.domain.model.RegistrationMethod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class Converters {

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime): Long =
        value.toInstant(TimeZone.UTC).epochSeconds

    @TypeConverter
    fun toLocalDateTime(value: Long): LocalDateTime =
        Instant.fromEpochSeconds(value).toLocalDateTime(TimeZone.UTC)

    @TypeConverter
    fun fromIngredients(ingredients: List<Ingredient>): String =
        ingredients.joinToString(";") { "${it.foodId}|${it.quantity}" }

    @TypeConverter
    fun toIngredients(value: String): List<Ingredient> =
        if (value.isEmpty()) emptyList()
        else value.split(";").map {
            val parts = it.split("|")
            Ingredient(parts[0], parts[1].toFloat())
        }

    @TypeConverter
    fun fromRegistrationMethod(method: RegistrationMethod): String = method.name

    @TypeConverter
    fun toRegistrationMethod(value: String): RegistrationMethod =
        RegistrationMethod.valueOf(value)

    @TypeConverter
    fun fromGlucoseTrend(trend: GlucoseTrend): String = trend.name

    @TypeConverter
    fun toGlucoseTrend(value: String): GlucoseTrend =
        GlucoseTrend.valueOf(value)

    @TypeConverter fun fromInsulinType(v: InsulinType?): String? = v?.name
    @TypeConverter fun toInsulinType(v: String?): InsulinType? = v?.let { InsulinType.valueOf(it) }

    @TypeConverter fun fromExerciseIntensity(v: ExerciseIntensity?): String? = v?.name
    @TypeConverter fun toExerciseIntensity(v: String?): ExerciseIntensity? = v?.let { ExerciseIntensity.valueOf(it) }

    @TypeConverter fun fromCarbConfidence(v: CarbConfidence?): String? = v?.name
    @TypeConverter fun toCarbConfidence(v: String?): CarbConfidence? = v?.let { CarbConfidence.valueOf(it) }
}
