package com.dosius.smart.data.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseAlarmCheckerTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun transition(
        glucoseValue: Int,
        hypoEnabled: Boolean = true,
        hyperEnabled: Boolean = true,
        hypoThreshold: Int = 70,
        hyperThreshold: Int = 180,
        hypoState: String = "NORMAL",
        hyperState: String = "NORMAL"
    ) = computeAlarmTransition(
        glucoseValue, hypoEnabled, hyperEnabled,
        hypoThreshold, hyperThreshold, hypoState, hyperState
    )

    // ── Hypo: NORMAL → ALARMED ────────────────────────────────────────────────

    @Test
    fun `first hypo reading fires and sets hypo state to ALARMED`() {
        val t = transition(glucoseValue = 55, hypoState = "NORMAL")
        assertTrue(t.shouldFireHypo)
        assertEquals("ALARMED", t.newHypoState)
        assertFalse(t.shouldCancel)
    }

    @Test
    fun `second consecutive hypo reading does not re-fire`() {
        val t = transition(glucoseValue = 55, hypoState = "ALARMED")
        assertFalse(t.shouldFireHypo)
        assertEquals("ALARMED", t.newHypoState)
    }

    // ── Recovery: ALARMED → NORMAL ────────────────────────────────────────────

    @Test
    fun `BG recovering above hypo threshold resets state to NORMAL`() {
        val t = transition(glucoseValue = 90, hypoState = "ALARMED", hyperState = "NORMAL")
        assertEquals("NORMAL", t.newHypoState)
        assertFalse(t.shouldFireHypo)
        assertTrue(t.shouldCancel)
    }

    // ── Hypo → Hyper transition ────────────────────────────────────────────────

    @Test
    fun `BG rising from hypo to hyper resets hypo and arms hyper`() {
        val t = transition(glucoseValue = 220, hypoState = "ALARMED", hyperState = "NORMAL")
        assertEquals("NORMAL", t.newHypoState)
        assertEquals("ALARMED", t.newHyperState)
        assertFalse(t.shouldFireHypo)
        assertTrue(t.shouldFireHyper)
    }

    @Test
    fun `second consecutive hyper reading does not re-fire`() {
        val t = transition(glucoseValue = 220, hyperState = "ALARMED")
        assertFalse(t.shouldFireHyper)
        assertEquals("ALARMED", t.newHyperState)
    }

    // ── Disabled alarms ────────────────────────────────────────────────────────

    @Test
    fun `hypo alarm disabled prevents firing even when BG is below threshold`() {
        val t = transition(glucoseValue = 55, hypoEnabled = false, hypoState = "NORMAL")
        assertFalse(t.shouldFireHypo)
        // BG is below hypo threshold but disabled, and it's not above hyper threshold → else branch
        assertTrue(t.shouldCancel)
    }

    @Test
    fun `hyper alarm disabled prevents firing even when BG is above threshold`() {
        val t = transition(glucoseValue = 220, hyperEnabled = false, hyperState = "NORMAL")
        assertFalse(t.shouldFireHyper)
        assertTrue(t.shouldCancel)
    }

    // ── Both disabled ─────────────────────────────────────────────────────────

    @Test
    fun `both alarms disabled produces cancel transition regardless of BG`() {
        val t = transition(glucoseValue = 45, hypoEnabled = false, hyperEnabled = false)
        assertFalse(t.shouldFireHypo)
        assertFalse(t.shouldFireHyper)
        assertTrue(t.shouldCancel)
    }

    // ── In range ──────────────────────────────────────────────────────────────

    @Test
    fun `BG in range with both states NORMAL produces cancel-only transition`() {
        val t = transition(glucoseValue = 120)
        assertFalse(t.shouldFireHypo)
        assertFalse(t.shouldFireHyper)
        assertTrue(t.shouldCancel)
        assertEquals("NORMAL", t.newHypoState)
        assertEquals("NORMAL", t.newHyperState)
    }

    // ── Cross-alarm reset ─────────────────────────────────────────────────────

    @Test
    fun `hypo triggered while hyper was ALARMED resets hyper`() {
        val t = transition(glucoseValue = 55, hypoState = "NORMAL", hyperState = "ALARMED")
        assertEquals("ALARMED", t.newHypoState)
        assertEquals("NORMAL", t.newHyperState)
        assertTrue(t.shouldFireHypo)
        assertFalse(t.shouldFireHyper)
    }

    @Test
    fun `hyper triggered while hypo was ALARMED resets hypo`() {
        val t = transition(glucoseValue = 220, hypoState = "ALARMED", hyperState = "NORMAL")
        assertEquals("NORMAL", t.newHypoState)
        assertEquals("ALARMED", t.newHyperState)
        assertFalse(t.shouldFireHypo)
        assertTrue(t.shouldFireHyper)
    }

    // ── initializeState pre-arming logic ──────────────────────────────────────

    @Test
    fun `initializeState logic sets ALARMED when BG already below hypo threshold`() {
        // Simulating the logic in initializeState: if BG < hypoThreshold at save time,
        // the next check() call should NOT fire because state is pre-armed.
        // We model this by running a check() with hypoState = "ALARMED".
        val t = transition(glucoseValue = 55, hypoState = "ALARMED")
        assertFalse("Pre-armed state must not fire again", t.shouldFireHypo)
    }

    @Test
    fun `initializeState logic sets ALARMED when BG already above hyper threshold`() {
        val t = transition(glucoseValue = 210, hyperState = "ALARMED")
        assertFalse("Pre-armed state must not fire again", t.shouldFireHyper)
    }
}
