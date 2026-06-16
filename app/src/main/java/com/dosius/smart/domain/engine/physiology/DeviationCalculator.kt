package com.dosius.smart.domain.engine.physiology

import com.dosius.smart.domain.model.CarbConfidence
import com.dosius.smart.domain.model.DeviationPoint
import com.dosius.smart.domain.model.Entry
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

class DeviationCalculator @Inject constructor(
    private val iobCalculator: IOBCalculator,
    private val cobCalculator: COBCalculator,
    private val glucosePredictor: GlucosePredictor

) {
    fun compute(
        bgNow: Int,
        bgPrev: Int,
        doses: List<InsulinDose>,
        meals: List<MealEvent>,
        cobDeviations: List<DeviationPoint>,
        entries: List<Entry>,
        params: TherapyParameters,
        now: LocalDateTime
    ): DeviationPoint {
        val observedDelta = (bgNow - bgPrev).toFloat()
        val timeSlot = now.hour
        val iob = iobCalculator.calculate(doses, now, params.diaMinutes).totalIob
        val cob = cobCalculator.calculate(meals, cobDeviations, params, now).totalCob

        val bgi = glucosePredictor.bgi5min(iob, params, timeSlot)
        val predictedDelta = glucosePredictor.predictDelta5min(iob, cob, params, timeSlot)
        val deviation = observedDelta - predictedDelta
        val recentExercise = entries.any { it ->
            it.exerciseType != null && ChronoUnit.HOURS.between(
                it.timestamp.toJavaLocalDateTime(), now.toJavaLocalDateTime()
            ) < 2
        }
        val iobPresent = iob > 0.5f
        val cobPresent = cob > 5f
        val fastingWindow = !recentExercise && !iobPresent && !cobPresent

        val postPrandialWindow = cobPresent
        val dominantMeal = meals.maxByOrNull { it.timestamp }
        val carbConfidence: CarbConfidence = when {
            !postPrandialWindow -> CarbConfidence.LOW
            dominantMeal == null -> CarbConfidence.LOW
            else -> dominantMeal.carbConfidence ?: CarbConfidence.LOW
        }
        val ticksInDia = params.diaMinutes / 5f
        val outlierFlag = abs(deviation) > 2f * params.isfAt(timeSlot) / ticksInDia
        val dayOfWeek = now.toJavaLocalDateTime().dayOfWeek.value
        val id = UUID.randomUUID().toString()




        return DeviationPoint(
            id,
            now,
            predictedDelta,
            observedDelta,
            deviation,
            bgi,
            fastingWindow,
            postPrandialWindow,
            carbConfidence,
            recentExercise,
            outlierFlag,
            timeSlot,
            dayOfWeek,
            iobPresent,
            cobPresent
        )
    }

}