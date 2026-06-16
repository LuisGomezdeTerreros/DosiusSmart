package com.dosius.smart.domain.engine.physiology

import com.dosius.smart.domain.model.DeviationPoint
import com.dosius.smart.domain.model.GlucoseReading
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import javax.inject.Inject
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.max


class GlucoseForecaster @Inject constructor(
    private val iobCalculator: IOBCalculator, private val cobCalculator: COBCalculator
) {
    fun forecast(
        bgNow: Int,
        now: LocalDateTime,
        doses: List<InsulinDose>,
        diaMinutes: Float = 300f,
        meals: List<MealEvent>,
        exercises: List<ExerciseEvent> = emptyList(),
        recentDeviations: List<DeviationPoint>,
        cobDeviations: List<DeviationPoint> = emptyList(),
        params: TherapyParameters,
        cgmHistory: List<GlucoseReading>
    ): List<ForecastPoint> {

        val currentIOB = iobCalculator.calculate(
            doses = doses, now, diaMinutes
        )
        val currentCOB = cobCalculator.calculate(
            meals, cobDeviations, params, now
        ).totalCob
        val slope = slopeMgDlPerMin(cgmHistory, bgNow, now)
        val meanDev = meanDeviation(recentDeviations)
        // oref0 min_5m_carbimpact (8 mg/dL / 5 min) converted to grams via ICR/ISF.
        // Scales with the user's therapy parameters, identical to oref0's forward COB projection.
        val cobAbsPerStep = CarbAbsorptionModel.MIN_5M_CARB_IMPACT_MGDL * params.icrAt(now.hour) / params.isfAt(now.hour)

        // Pre-compute each exercise's start offset in minutes from now (negative = already started)
        val exerciseOffsets = exercises.map { ev ->
            Pair(ev, ChronoUnit.MINUTES.between(
                now.toJavaLocalDateTime(), ev.startTime.toJavaLocalDateTime()
            ).toFloat())
        }

        var cumulativeRetro = 0f
        var cumulativeMomentum = 0f
        var cumulativeExercise = 0f
        return (1..48).map { t ->
            val futurePoint =
                now.toInstant(TimeZone.currentSystemDefault()).plus(t * 5L, DateTimeUnit.MINUTE)
                    .toLocalDateTime(TimeZone.currentSystemDefault())

            val futureIOB = iobCalculator.calculate(
                doses = doses, futurePoint, diaMinutes
            )
            val futureCOB = max(0f, currentCOB - t * cobAbsPerStep)

            cumulativeMomentum += slope * 5f * max(0f, 1f - t * 5 / 20f)
            cumulativeRetro += meanDev * max(0f, 1f - t * 5 / 60f)

            // Accumulate glucose drop from exercise active in this 5-min window
            val windowStart = (t - 1) * 5f
            val windowEnd = t * 5f
            exerciseOffsets.forEach { (ev, startOff) ->
                val activeMin = maxOf(0f, minOf(windowEnd, startOff + ev.durationMin) - maxOf(windowStart, startOff))
                cumulativeExercise += ev.expectedDropPerHour * activeMin / 60f
            }

            val isf = params.isfAt(futurePoint.hour)
            val icr = params.icrAt(futurePoint.hour)
            ForecastPoint(
                t * 5,
                (bgNow + (futureIOB.totalIob - currentIOB.totalIob) * isf + (currentCOB - futureCOB) * (isf / icr) + cumulativeMomentum + cumulativeRetro - cumulativeExercise).coerceIn(40f, 400f),
                futureIOB.totalIob,
                futureCOB
            )
        }
    }

    private fun slopeMgDlPerMin(
        cgmHistory: List<GlucoseReading>, bgNow: Int, now: LocalDateTime
    ): Float {
        if (cgmHistory.size < 2) return 0f
        val targetTime =
            now.toInstant(TimeZone.currentSystemDefault()).plus(-15L, DateTimeUnit.MINUTE)
                .toLocalDateTime(TimeZone.currentSystemDefault())
        val oldest = cgmHistory.minByOrNull { reading ->
            kotlin.math.abs(
                ChronoUnit.MINUTES.between(
                    reading.timestamp.toJavaLocalDateTime(), targetTime.toJavaLocalDateTime()
                )
            )
        } ?: return 0f
        val elapsedMinutes = ChronoUnit.MINUTES.between(
            oldest.timestamp.toJavaLocalDateTime(), cgmHistory[0].timestamp.toJavaLocalDateTime()
        ).toFloat()
        if (elapsedMinutes == 0f) return 0f
        return (bgNow - oldest.glucoseValue) / elapsedMinutes
    }

    private fun meanDeviation(deviations: List<DeviationPoint>): Float {
        if (deviations.isEmpty()) return 0f
        return deviations.map { it.deviation }.average().toFloat()
    }
}
