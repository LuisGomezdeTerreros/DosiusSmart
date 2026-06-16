package com.dosius.smart.domain.engine.learning

import com.dosius.smart.domain.model.ContaminationWindow
import com.dosius.smart.domain.model.Entry
import com.dosius.smart.domain.model.GlucoseReading
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

/**
 * Per-event exercise processing: measures the observed glucose drop over the exercise window
 * and produces the contamination window that excludes it from ISF/ICR learning. Pure domain
 * logic, free of any persistence or framework concerns.
 */
class ExerciseDropEngine @Inject constructor() {

    data class Result(
        val actualDrop: Float,
        val contaminationWindow: ContaminationWindow
    )

    /**
     * Observed drop is the fall from [bgStart] to the lowest reading inside the window
     * [exerciseStart, exerciseStart + duration + 90min]. Returns null when no readings fall in
     * the window, signalling the caller there is nothing to learn or exclude.
     */
    fun process(
        entry: Entry,
        readings: List<GlucoseReading>,
        bgStart: Int,
        durationMinutes: Float
    ): Result? {
        val windowStart = entry.timestamp.toInstant(TimeZone.UTC)
        val windowEnd = windowStart + durationMinutes.toLong().minutes + 90.minutes

        val windowReadings = readings.filter { it.timestamp.toInstant(TimeZone.UTC) <= windowEnd }
        val minBg = windowReadings.minOfOrNull { it.glucoseValue } ?: return null
        val actualDrop = (bgStart - minBg).coerceAtLeast(0).toFloat()

        // Exercise always distorts the glucose signal, so the window is excluded
        // from ISF/ICR learning regardless of whether the user later accepts the drop.
        val contaminationWindow = ContaminationWindow(
            id = "${entry.id}_exercise_event",
            startTime = entry.timestamp,
            endTime = windowEnd.toLocalDateTime(TimeZone.UTC),
            reason = "RULE_EXERCISE",
            sourceEventId = entry.id,
            excludedParamTypes = "ISF,ICR"
        )
        return Result(actualDrop, contaminationWindow)
    }
}
