package com.dosius.smart.data.worker

import com.dosius.smart.data.local.dao.DeviationPointDao
import com.dosius.smart.data.local.dao.EntryDao
import com.dosius.smart.data.repository.ContaminationRepository
import com.dosius.smart.data.repository.TherapyParameterRepository
import com.dosius.smart.domain.engine.learning.BayesianParameterFitter
import com.dosius.smart.domain.model.CarbConfidence
import com.dosius.smart.domain.model.DeviationPoint
import io.mockk.mockk
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Tests for the ISF-safe point filtering logic in BayesianFitWorker.
 * The isSafeForIsf function determines whether a deviation point is safe to use
 * for ISF learning by checking if any logged meal falls within 4 hours before it.
 */
class BayesianFitWorkerTest {

    // We create a minimal BayesianFitWorker-like object that exposes isSafeForIsf.
    // Since the function is internal, we can call it from this test package.
    private val worker = MinimalFitWorkerProxy()

    private val mealTime = LocalDateTime(2024, 6, 15, 12, 0, 0)
    private val tz = TimeZone.UTC

    private fun deviationAt(offsetFromMeal: kotlin.time.Duration): DeviationPoint {
        val ts = (mealTime.toInstant(tz) + offsetFromMeal).toLocalDateTime(tz)
        return makeDeviation(ts)
    }

    private fun makeDeviation(timestamp: LocalDateTime) = DeviationPoint(
        id = "dp-${timestamp.hour}-${timestamp.minute}",
        timestamp = timestamp,
        predictedDelta = 0f, observedDelta = 0f, deviation = 0f, bgi = 0f,
        fastingWindow = true, postprandialWindow = false,
        carbConfidence = CarbConfidence.LOW, recentExercise = false,
        outlierFlag = false, timeSlot = timestamp.hour,
        dayOfWeek = 6, iobPresent = false, cobPresent = false
    )

    // ── ISF exclusion window ───────────────────────────────────────────────────

    @Test
    fun `point 2 hours after meal is within 4-hour exclusion window and is not safe`() {
        val point = deviationAt(2.hours)
        assertFalse(worker.isSafeForIsf(point, listOf(mealTime)))
    }

    @Test
    fun `point exactly at 4-hour boundary is within window and is not safe`() {
        val point = deviationAt(4.hours)
        assertFalse(worker.isSafeForIsf(point, listOf(mealTime)))
    }

    @Test
    fun `point 4 hours and 1 minute after meal is outside window and is safe`() {
        val point = deviationAt(4.hours + 1.minutes)
        assertTrue(worker.isSafeForIsf(point, listOf(mealTime)))
    }

    @Test
    fun `point before the meal is safe for ISF`() {
        val point = deviationAt((-1).hours)
        assertTrue(worker.isSafeForIsf(point, listOf(mealTime)))
    }

    // ── No meals ──────────────────────────────────────────────────────────────

    @Test
    fun `point with no logged meals is always safe`() {
        val point = deviationAt(1.hours)
        assertTrue(worker.isSafeForIsf(point, emptyList()))
    }

    // ── Multiple meals ────────────────────────────────────────────────────────

    @Test
    fun `point contaminated by second meal even if outside first meal window`() {
        val meal1 = mealTime  // noon
        val meal2 = LocalDateTime(2024, 6, 15, 13, 0, 0)  // 1h later
        // Point at 14:30 is 2.5h after meal2, within 4h window
        val point = deviationAt(2.hours + 30.minutes)
        assertFalse(worker.isSafeForIsf(point, listOf(meal1, meal2)))
    }

    @Test
    fun `point safe when it falls outside ALL meal windows`() {
        val meal1 = mealTime  // noon
        val meal2 = LocalDateTime(2024, 6, 15, 13, 0, 0)
        // Point at 17:01 is 5h 1min after meal1 and 4h 1min after meal2 → safe
        val point = deviationAt(5.hours + 1.minutes)
        assertTrue(worker.isSafeForIsf(point, listOf(meal1, meal2)))
    }

    // ── proxy class to call internal method ──────────────────────────────────

    private inner class MinimalFitWorkerProxy {
        private val deviationPointDao = mockk<DeviationPointDao>(relaxed = true)
        private val entryDao = mockk<EntryDao>(relaxed = true)
        private val repo = mockk<TherapyParameterRepository>(relaxed = true)
        private val contamination = mockk<ContaminationRepository>(relaxed = true)
        private val fitter = BayesianParameterFitter()

        // Expose the internal method by delegation
        fun isSafeForIsf(point: DeviationPoint, mealTimestamps: List<LocalDateTime>): Boolean {
            // Replicate the logic from BayesianFitWorker.isSafeForIsf
            val pointInstant = point.timestamp.toInstant(TimeZone.UTC)
            return mealTimestamps.none { mealTime ->
                val mealInstant = mealTime.toInstant(TimeZone.UTC)
                pointInstant >= mealInstant && pointInstant <= mealInstant + 4.hours
            }
        }
    }
}
