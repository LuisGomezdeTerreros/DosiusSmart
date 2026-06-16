package com.dosius.smart.domain.engine.contamination

import com.dosius.smart.domain.model.ContaminationWindow
import com.dosius.smart.domain.model.DeviationPoint
import com.dosius.smart.domain.model.Entry
import com.dosius.smart.domain.model.GlucoseReading
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Builds the contamination windows that exclude distorted time ranges from ISF/ICR/BASAL
 * learning. Pure domain logic (oref1-inspired): each method takes already-fetched rows and
 * returns the candidate windows. Persistence and de-duplication are the repository's concern.
 */
class ContaminationDetectionEngine @Inject constructor() {

    /** RULE_HYPO: any reading below the hypo threshold contaminates [t-1h, t+2h]. */
    fun detectHypoWindows(readings: List<GlucoseReading>): List<ContaminationWindow> =
        readings.filter { it.glucoseValue < HYPO_THRESHOLD }.map {
            ContaminationWindow(
                id = "${it.id}_hypo",
                startTime = it.timestamp.toInstant(TimeZone.UTC).minus(1.hours)
                    .toLocalDateTime(TimeZone.UTC),
                endTime = it.timestamp.toInstant(TimeZone.UTC).plus(2.hours)
                    .toLocalDateTime(TimeZone.UTC),
                reason = "RULE_HYPO",
                sourceEventId = it.id,
                excludedParamTypes = "ISF,ICR,BASAL"
            )
        }

    /** RULE_EXERCISE: any logged exercise contaminates [t, t+4h]. */
    fun detectExerciseWindows(entries: List<Entry>): List<ContaminationWindow> =
        entries.filter { it.exerciseType != null }.map {
            ContaminationWindow(
                id = "${it.id}_exercise",
                startTime = it.timestamp,
                endTime = it.timestamp.toInstant(TimeZone.UTC).plus(4.hours)
                    .toLocalDateTime(TimeZone.UTC),
                reason = "RULE_EXERCISE",
                sourceEventId = it.id,
                excludedParamTypes = "ISF,ICR,BASAL"
            )
        }

    /**
     * RULE_STATISTICAL: clusters of >= 3 outlier deviation points that all fall within
     * 30 min of the cluster's first point.
     */
    fun detectStatisticalWindows(deviations: List<DeviationPoint>): List<ContaminationWindow> {
        val outliers = deviations.filter { it.outlierFlag }.sortedBy { it.timestamp }
        val window = mutableListOf<DeviationPoint>()
        val windows = mutableListOf<List<DeviationPoint>>()
        for (deviation in outliers) {
            if (window.isEmpty()) {
                window.add(deviation)
            } else if (deviation.timestamp.toInstant(TimeZone.UTC) -
                window.first().timestamp.toInstant(TimeZone.UTC) < STATISTICAL_CLUSTER_GAP
            ) {
                window.add(deviation)
            } else if (window.size >= STATISTICAL_MIN_POINTS) {
                windows.add(window.toList())
                window.clear()
                window.add(deviation)
            } else {
                window.clear()
                window.add(deviation)
            }
        }
        if (window.size >= STATISTICAL_MIN_POINTS) {
            windows.add(window.toList())
        }
        return windows.map {
            ContaminationWindow(
                id = "${it.first().id}_statistical",
                startTime = it.first().timestamp,
                endTime = it.last().timestamp,
                reason = "RULE_STATISTICAL",
                sourceEventId = it.first().id,
                excludedParamTypes = "ISF,ICR,BASAL"
            )
        }
    }

    /**
     * RULE_UAM (unannounced meal): clusters of >= 4 positive deviation points whose rolling
     * mean exceeds the threshold, with no logged meal in the 4 h before the cluster starts.
     * Contaminates [clusterStart, clusterEnd + 90min].
     */
    fun detectUamWindows(
        deviations: List<DeviationPoint>,
        entries: List<Entry>
    ): List<ContaminationWindow> {
        val positives = deviations.filter { it.deviation > 0 }.sortedBy { it.timestamp }
        val window = mutableListOf<DeviationPoint>()
        val windows = mutableListOf<List<DeviationPoint>>()
        for (deviation in positives) {
            if (window.isEmpty()) {
                window.add(deviation)
            } else if (
                deviation.timestamp.toInstant(TimeZone.UTC) -
                window.last().timestamp.toInstant(TimeZone.UTC) < UAM_CLUSTER_GAP &&
                (window.map { it.deviation }.sum() + deviation.deviation) / (window.size + 1) > UAM_MEAN_THRESHOLD &&
                entries.none { entry ->
                    entry.totalCarbs != null &&
                    (window.first().timestamp.toInstant(TimeZone.UTC) - entry.timestamp.toInstant(TimeZone.UTC))
                        .let { gap -> gap > 0.minutes && gap < UAM_MEAL_LOOKBACK }
                }
            ) {
                window.add(deviation)
            } else if (window.size >= UAM_MIN_POINTS) {
                windows.add(window.toList())
                window.clear()
                window.add(deviation)
            } else {
                window.clear()
                window.add(deviation)
            }
        }
        if (window.size >= UAM_MIN_POINTS) {
            windows.add(window.toList())
        }
        return windows.map { w ->
            ContaminationWindow(
                id = "${w.first().id}_uam",
                startTime = w.first().timestamp,
                endTime = w.last().timestamp.toInstant(TimeZone.UTC).plus(90.minutes)
                    .toLocalDateTime(TimeZone.UTC),
                reason = "RULE_UAM",
                sourceEventId = w.first().id,
                excludedParamTypes = "ISF,ICR,BASAL"
            )
        }
    }

    companion object {
        const val HYPO_THRESHOLD = 60
        val STATISTICAL_CLUSTER_GAP = 30.minutes
        const val STATISTICAL_MIN_POINTS = 3
        val UAM_CLUSTER_GAP = 60.minutes
        val UAM_MEAL_LOOKBACK = 240.minutes
        const val UAM_MEAN_THRESHOLD = 3f
        const val UAM_MIN_POINTS = 4
    }
}
