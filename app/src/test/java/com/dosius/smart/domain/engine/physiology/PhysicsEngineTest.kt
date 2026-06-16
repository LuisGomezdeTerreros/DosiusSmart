package com.dosius.smart.domain.engine.physiology

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsEngineTest {

    private val params = TherapyParameters.defaults()  // ISF=50, ICR=10, target=100

    // ── InsulinCurve ──────────────────────────────────────────────────────────

    @Test
    fun `iobFraction at t=0 is 1`() {
        assertEquals(1.0f, InsulinCurve.iobFraction(0f), 0.001f)
    }

    @Test
    fun `iobFraction at t=DIA is 0`() {
        assertEquals(0.0f, InsulinCurve.iobFraction(300f), 0.001f)
    }

    @Test
    fun `iobFraction at t=75 is between 0_5 and 0_9`() {
        // Default peak=55; at t=75 the curve returns ~0.56 (not 0.75 from the old peak=75 linear approx)
        val fraction = InsulinCurve.iobFraction(75f)
        assertTrue("Expected iobFraction(75) to be between 0.5 and 0.9, got $fraction",
            fraction in 0.5f..0.9f)
    }

    @Test
    fun `iobFraction is monotonically decreasing`() {
        val samples = listOf(0f, 30f, 75f, 150f, 225f, 300f)
            .map { InsulinCurve.iobFraction(it) }
        for (i in 0 until samples.size - 1) {
            assert(samples[i] >= samples[i + 1]) {
                "Not monotonically decreasing at index $i: ${samples[i]} < ${samples[i + 1]}"
            }
        }
    }

    @Test
    fun `basalIobFraction at half DIA is 0_5`() {
        val halfDiaMin = 24f * 60f / 2f  // 720 minutes
        assertEquals(0.5f, InsulinCurve.basalIobFraction(halfDiaMin), 0.001f)
    }

    // ── GlucosePredictor ──────────────────────────────────────────────────────

    @Test
    fun `eventualBG with only IOB drops BG`() {
        // 2U IOB, ISF=50 → eventual = 180 - 2×50 = 80
        val result = GlucosePredictor().eventualBG(
            bgNow = 180, iob = 2.0f, cob = 0f, parameters = params, timeSlot = 12
        )
        assertEquals(80f, result, 0.5f)
    }

    @Test
    fun `eventualBG with only COB raises BG`() {
        // 30g COB, ISF=50, ICR=10 → eventual = 120 + 30×(50/10) = 270
        val result = GlucosePredictor().eventualBG(
            bgNow = 120, iob = 0f, cob = 30f, parameters = params, timeSlot = 12
        )
        assertEquals(270f, result, 0.5f)
    }

    @Test
    fun `eventualBG with balanced IOB and COB stays near baseline`() {
        // 3U IOB covers 30g at ICR=10 → effects cancel → stays at bgNow
        // 140 - 3×50 + 30×5 = 140 - 150 + 150 = 140
        val result = GlucosePredictor().eventualBG(
            bgNow = 140, iob = 3.0f, cob = 30f, parameters = params, timeSlot = 12
        )
        assertEquals(140f, result, 0.5f)
    }

    @Test
    fun `predictDelta5min with only IOB is negative`() {
        val delta = GlucosePredictor().predictDelta5min(
            iob = 2.0f, cob = 0f, parameters = params, timeSlot = 12
        )
        assert(delta < 0f) { "Expected negative delta with active IOB, got $delta" }
    }

    @Test
    fun `predictDelta5min with only COB is positive`() {
        val delta = GlucosePredictor().predictDelta5min(
            iob = 0f, cob = 30f, parameters = params, timeSlot = 12
        )
        assert(delta > 0f) { "Expected positive delta with active COB, got $delta" }
    }

    // ── CarbAbsorptionModel ───────────────────────────────────────────────────

    @Test
    fun `absorbStep applies mg_dL floor and converts to grams when deviation is zero`() {
        // MIN_5M_CARB_IMPACT_MGDL = 8 mg/dL; converted to grams via icr/isf: 8 * 10/50 = 1.6g
        val absorbed = CarbAbsorptionModel.absorbStep(
            cobRemaining = 30f, observedDeviation = 0f, icr = 10f, isf = 50f
        )
        assertEquals(1.6f, absorbed, 0.001f)
    }

    @Test
    fun `absorbStep returns dynamic rate when deviation is large`() {
        // deviation=60, ICR=10, ISF=50 → dynamic = 60×10/50 = 12g > 8g minimum
        val absorbed = CarbAbsorptionModel.absorbStep(
            cobRemaining = 30f, observedDeviation = 60f, icr = 10f, isf = 50f
        )
        assertEquals(12f, absorbed, 0.001f)
    }
}
