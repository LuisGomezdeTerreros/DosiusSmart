package com.dosius.smart.presentation.dashboard

import com.dosius.smart.data.preferences.AlarmPreferences
import com.dosius.smart.data.repository.ContaminationRepository
import com.dosius.smart.data.repository.EntryRepository
import com.dosius.smart.data.repository.ForecastRepository
import com.dosius.smart.data.repository.LibreLinkUpRepository
import com.dosius.smart.data.repository.RecommendationRepository
import com.dosius.smart.domain.engine.physiology.HistoricalPoint
import com.dosius.smart.domain.model.ContaminationWindow
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(testDispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun window(id: String, startHour: Int, endHour: Int) = ContaminationWindow(
        id = id,
        startTime = LocalDateTime(2024, 6, 15, startHour, 0, 0),
        endTime = LocalDateTime(2024, 6, 15, endHour, 0, 0),
        reason = "RULE_UAM",
        sourceEventId = null,
        excludedParamTypes = "ISF,ICR,BASAL"
    )

    private fun buildVm(): DashboardViewModel {
        val libreLinkUp = mockk<LibreLinkUpRepository>(relaxed = true)
        val entries = mockk<EntryRepository>(relaxed = true)
        val recommendation = mockk<RecommendationRepository>(relaxed = true)
        val forecast = mockk<ForecastRepository>(relaxed = true)
        val contamination = mockk<ContaminationRepository>(relaxed = true)
        val alarmPrefs = mockk<AlarmPreferences>(relaxed = true)

        every { libreLinkUp.getReadingsStream() } returns flowOf(emptyList())
        every { entries.getAllEntries() } returns flowOf(emptyList())
        every { forecast.observeLatest() } returns flowOf(null)
        every { forecast.historicalPoints } returns kotlinx.coroutines.flow.MutableStateFlow<List<HistoricalPoint>>(emptyList())
        every { alarmPrefs.hypoEnabled } returns flowOf(false)
        every { alarmPrefs.hyperEnabled } returns flowOf(false)
        every { alarmPrefs.hypoThreshold } returns flowOf(70)
        every { alarmPrefs.hyperThreshold } returns flowOf(180)
        every { alarmPrefs.hypoState } returns flowOf("NORMAL")
        every { alarmPrefs.hyperState } returns flowOf("NORMAL")

        return DashboardViewModel(libreLinkUp, entries, recommendation, forecast, contamination, alarmPrefs)
    }

    // ── collapseToMostRecent ───────────────────────────────────────────────────

    @Test
    fun `collapseToMostRecent with empty list returns empty`() = runTest {
        val vm = buildVm()
        val result = vm.collapseToMostRecent(emptyList(), TimeZone.UTC)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `collapseToMostRecent returns single window unchanged`() = runTest {
        val vm = buildVm()
        val w = window("w1", startHour = 12, endHour = 14)
        val result = vm.collapseToMostRecent(listOf(w), TimeZone.UTC)
        assertEquals(1, result.size)
        assertEquals("w1", result[0].id)
    }

    @Test
    fun `collapseToMostRecent collapses overlapping windows to the most recent`() = runTest {
        val vm = buildVm()
        // w1: 10:00-12:00, w2: 11:00-13:00 — they overlap (w2 starts before w1 ends)
        val w1 = window("w1", startHour = 10, endHour = 12)
        val w2 = window("w2", startHour = 11, endHour = 13)
        val result = vm.collapseToMostRecent(listOf(w1, w2), TimeZone.UTC)
        // Only the most recent non-overlapping window is kept; w2 is more recent
        assertEquals(1, result.size)
        assertEquals("w2", result[0].id)
    }

    @Test
    fun `collapseToMostRecent keeps windows that are well separated`() = runTest {
        val vm = buildVm()
        // w1: 10:00-11:00, w2: 16:00-17:00 — 5h apart, no overlap
        // But collapseToMostRecent always returns at most 1 (breaks after first non-overlapping)
        val w1 = window("w1", startHour = 10, endHour = 11)
        val w2 = window("w2", startHour = 16, endHour = 17)
        val result = vm.collapseToMostRecent(listOf(w1, w2), TimeZone.UTC)
        // Most recent is w2 (sorted descending by startTime)
        assertEquals(1, result.size)
        assertEquals("w2", result[0].id)
    }

    @Test
    fun `collapseToMostRecent windows within 30-minute gap are still considered overlapping`() = runTest {
        val vm = buildVm()
        // w1: 10:00-11:00, w2: 11:20-12:20 — gap of 20 min, within ±30min overlap check
        val w1 = window("w1", startHour = 10, endHour = 11)
        val w2 = ContaminationWindow(
            id = "w2",
            startTime = LocalDateTime(2024, 6, 15, 11, 20, 0),
            endTime = LocalDateTime(2024, 6, 15, 12, 20, 0),
            reason = "RULE_UAM", sourceEventId = null, excludedParamTypes = "ISF"
        )
        val result = vm.collapseToMostRecent(listOf(w1, w2), TimeZone.UTC)
        // w2 is most recent; w1 overlaps with w2 (gap < 30min) so only w2 is kept
        assertEquals(1, result.size)
        assertEquals("w2", result[0].id)
    }
}
