package com.dosius.smart.domain.engine.physiology

import com.dosius.smart.domain.model.CarbConfidence
import com.dosius.smart.domain.model.DeviationPoint
import com.dosius.smart.domain.model.GlucoseReading
import com.dosius.smart.domain.model.GlucoseTrend
import com.dosius.smart.domain.model.InsulinType
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseForecasterTest {

    private val forecaster = GlucoseForecaster(IOBCalculator(), COBCalculator())
    private val params = TherapyParameters.defaults() // ISF=50, ICR=10
    private val now = LocalDateTime(2024, 1, 1, 12, 0, 0)

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun minutesFromNow(offset: Long) =
        now.toInstant(TimeZone.currentSystemDefault())
            .plus(offset, DateTimeUnit.MINUTE)
            .toLocalDateTime(TimeZone.currentSystemDefault())

    private fun reading(offsetMinutes: Long, glucose: Int) = GlucoseReading(
        id = "r$offsetMinutes",
        timestamp = minutesFromNow(offsetMinutes),
        glucoseValue = glucose,
        trend = GlucoseTrend.STABLE,
        trendRate = 0f,
        source = "test"
    )

    private fun deviation(dev: Float) = DeviationPoint(
        id = "d",
        timestamp = now,
        predictedDelta = 0f,
        observedDelta = dev,
        deviation = dev,
        bgi = 0f,
        fastingWindow = true,
        postprandialWindow = false,
        carbConfidence = CarbConfidence.HIGH,
        recentExercise = false,
        outlierFlag = false,
        timeSlot = 12,
        dayOfWeek = 1,
        iobPresent = false,
        cobPresent = false
    )

    // ── Scenario 1: flat ──────────────────────────────────────────────────────

    @Test
    fun `flat scenario - no IOB COB slope or deviations - all points stay near bgNow`() {
        val points = forecaster.forecast(
            bgNow = 120,
            now = now,
            doses = emptyList(),
            meals = emptyList(),
            cgmHistory = emptyList(),
            recentDeviations = emptyList(),
            params = params
        )

        assertEquals(48, points.size)
        points.forEach { point ->
            assertEquals(
                "Expected ~120 at deltaMinutes=${point.deltaMinutes}",
                120f,
                point.predictedBg,
                1f
            )
        }
    }

    // ── Scenario 2: IOB-only drop ─────────────────────────────────────────────

    @Test
    fun `IOB-only - 2U bolus now - last point drops significantly below bgNow`() {
        val dose = InsulinDose(
            units = 2f,
            timestamp = now,
            type = InsulinType.BOLUS
        )

        val points = forecaster.forecast(
            bgNow = 180,
            now = now,
            doses = listOf(dose),
            meals = emptyList(),
            cgmHistory = emptyList(),
            recentDeviations = emptyList(),
            params = params
        )

        // At 240 min, most of the 2U × ISF=50 = 100 mg/dL drop has occurred
        val lastPoint = points.last()
        assertEquals(240, lastPoint.deltaMinutes)
        assertTrue(
            "Expected last BG < 120 (dropped significantly), got ${lastPoint.predictedBg}",
            lastPoint.predictedBg < 120f
        )
        assertTrue(
            "Expected last BG > 40 (not clamped), got ${lastPoint.predictedBg}",
            lastPoint.predictedBg > 40f
        )
    }

    @Test
    fun `IOB-only - BG decreases monotonically across forecast`() {
        val dose = InsulinDose(units = 2f, timestamp = now, type = InsulinType.BOLUS)

        val points = forecaster.forecast(
            bgNow = 180,
            now = now,
            doses = listOf(dose),
            meals = emptyList(),
            cgmHistory = emptyList(),
            recentDeviations = emptyList(),
            params = params
        )

        for (i in 0 until points.size - 1) {
            assertTrue(
                "Expected BG to decrease at step ${i + 1}: ${points[i].predictedBg} → ${points[i + 1].predictedBg}",
                points[i + 1].predictedBg <= points[i].predictedBg + 0.01f
            )
        }
    }

    // ── Scenario 3: slope-only momentum ──────────────────────────────────────

    @Test
    fun `positive slope - first 4 steps rise above bgNow, then momentum stabilises`() {
        // 3 readings trending up at 1 mg/dL per minute
        // newest first: now=120, -5min=115, -10min=110
        // slope function finds reading closest to 15min ago → index 2 (now-10min)
        // elapsed = 10min, slope = (120 - 110) / 10 = 1.0 mg/dL/min
        val history = listOf(
            reading(0, 120),
            reading(-5, 115),
            reading(-10, 110)
        )

        val points = forecaster.forecast(
            bgNow = 120,
            now = now,
            doses = emptyList(),
            meals = emptyList(),
            cgmHistory = history,
            recentDeviations = emptyList(),
            params = params
        )

        // First 4 steps (t=5..20) should be above bgNow due to momentum
        for (i in 0..3) {
            assertTrue(
                "Expected points[$i] (t=${points[i].deltaMinutes}) > 120, got ${points[i].predictedBg}",
                points[i].predictedBg > 120f
            )
        }

        // After t=20 min momentum adds nothing — point 5 onward should equal point 4
        assertEquals(
            "Momentum should stabilise after t=20min",
            points[3].predictedBg,
            points[7].predictedBg,
            0.01f
        )
    }

    // ── Scenario 4: retrospective correction ─────────────────────────────────

    @Test
    fun `negative mean deviation - retro correction pulls BG down over first 60 min`() {
        val deviations = List(6) { deviation(-3f) }

        val points = forecaster.forecast(
            bgNow = 150,
            now = now,
            doses = emptyList(),
            meals = emptyList(),
            cgmHistory = emptyList(),
            recentDeviations = deviations,
            params = params
        )

        // First point should already be below bgNow
        assertTrue(
            "Expected first point < 150, got ${points[0].predictedBg}",
            points[0].predictedBg < 150f
        )

        // BG at t=55min (step 11) should be lower than at t=5min (step 1)
        assertTrue(
            "Expected retro correction to accumulate: points[10]=${points[10].predictedBg} should be < points[0]=${points[0].predictedBg}",
            points[10].predictedBg < points[0].predictedBg
        )

        // After t=60min retro stops adding — point 13 should equal point 12
        assertEquals(
            "Retro correction should stabilise after t=60min",
            points[11].predictedBg,
            points[13].predictedBg,
            0.01f
        )
    }
}
