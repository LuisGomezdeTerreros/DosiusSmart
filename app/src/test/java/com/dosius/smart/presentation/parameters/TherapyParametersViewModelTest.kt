package com.dosius.smart.presentation.parameters

import com.dosius.smart.data.repository.TherapyParameterRepository
import com.dosius.smart.domain.model.TherapyParameter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TherapyParametersViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val baseTime = LocalDateTime(2024, 1, 1, 0, 0, 0)

    @Before fun setUp() = Dispatchers.setMain(testDispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun row(
        type: String,
        currentValue: Float,
        mean: Float,
        nObs: Int = 3,
        slotIndex: Int = 0
    ) = TherapyParameter(
        slotIndex = slotIndex, parameterType = type,
        mean = mean, variance = 1f, nObservations = nObs,
        lastUpdated = baseTime, currentValue = currentValue
    )

    private fun buildVm(rows: List<TherapyParameter>): Pair<TherapyParametersViewModel, TherapyParameterRepository> {
        val repo = mockk<TherapyParameterRepository>(relaxed = true)
        every { repo.getAll() } returns flowOf(rows)
        return TherapyParametersViewModel(repo) to repo
    }

    // ── ISF guardrail (25%) ────────────────────────────────────────────────────

    @Test
    fun `acceptAll clips ISF when delta exceeds 25 percent guardrail`() = runTest {
        val (vm, repo) = buildVm(listOf(row("ISF", currentValue = 50f, mean = 75f)))
        val captured = slot<List<TherapyParameter>>()
        coEvery { repo.upsertAll(capture(captured)) } returns Unit

        // Subscribe so SharingStarted.WhileSubscribed activates _allRows collection
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.acceptAll()
        advanceUntilIdle()

        // delta = 25, maxDelta = 50 * 0.25 = 12.5 → accepted = 50 + 12.5 = 62.5
        val updated = captured.captured.first { it.parameterType == "ISF" }
        assertEquals(62.5f, updated.currentValue, 0.01f)
    }

    @Test
    fun `acceptAll accepts ISF when delta is within 25 percent guardrail`() = runTest {
        // 20% change: 50 → 60 (delta=10, maxDelta=12.5) → accepted fully as 60
        val (vm, repo) = buildVm(listOf(row("ISF", currentValue = 50f, mean = 60f)))
        val captured = slot<List<TherapyParameter>>()
        coEvery { repo.upsertAll(capture(captured)) } returns Unit

        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.acceptAll()
        advanceUntilIdle()

        val updated = captured.captured.first { it.parameterType == "ISF" }
        assertEquals(60f, updated.currentValue, 0.01f)
    }

    // ── ICR guardrail (30%) ────────────────────────────────────────────────────

    @Test
    fun `acceptAll clips ICR when delta exceeds 30 percent guardrail`() = runTest {
        // currentValue=10, mean=15 → delta=5, maxDelta=10*0.3=3 → clipped to 10+3=13
        val (vm, repo) = buildVm(listOf(row("ICR", currentValue = 10f, mean = 15f)))
        val captured = slot<List<TherapyParameter>>()
        coEvery { repo.upsertAll(capture(captured)) } returns Unit

        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.acceptAll()
        advanceUntilIdle()

        val updated = captured.captured.first { it.parameterType == "ICR" }
        assertEquals(13f, updated.currentValue, 0.01f)
    }

    @Test
    fun `acceptAll accepts ICR when delta is within 30 percent guardrail`() = runTest {
        // 20% change: 10 → 12 → accepted fully
        val (vm, repo) = buildVm(listOf(row("ICR", currentValue = 10f, mean = 12f)))
        val captured = slot<List<TherapyParameter>>()
        coEvery { repo.upsertAll(capture(captured)) } returns Unit

        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.acceptAll()
        advanceUntilIdle()

        val updated = captured.captured.first { it.parameterType == "ICR" }
        assertEquals(12f, updated.currentValue, 0.01f)
    }

    // ── BASAL guardrail (15%) ──────────────────────────────────────────────────

    @Test
    fun `acceptAll clips BASAL when delta exceeds 15 percent guardrail`() = runTest {
        // currentValue=20, mean=25 → delta=5, maxDelta=20*0.15=3 → clipped to 20+3=23
        val (vm, repo) = buildVm(listOf(row("BASAL", currentValue = 20f, mean = 25f, nObs = 1)))
        val captured = slot<List<TherapyParameter>>()
        coEvery { repo.upsertAll(capture(captured)) } returns Unit

        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.acceptAll()
        advanceUntilIdle()

        val updated = captured.captured.first { it.parameterType == "BASAL" }
        assertEquals(23f, updated.currentValue, 0.01f)
    }

    // ── ICR 3-observation gate ─────────────────────────────────────────────────

    @Test
    fun `acceptAll skips ICR row with fewer than 3 observations`() = runTest {
        val (vm, repo) = buildVm(listOf(row("ICR", currentValue = 10f, mean = 15f, nObs = 2)))

        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.acceptAll()
        advanceUntilIdle()

        coVerify(exactly = 0) { repo.upsertAll(any()) }
    }

    // ── Downward clip ─────────────────────────────────────────────────────────

    @Test
    fun `acceptAll clips ISF downward when mean is below current value`() = runTest {
        // currentValue=50, mean=30 → delta=-20, maxDelta=50*0.25=12.5 → clipped to 50-12.5=37.5
        val (vm, repo) = buildVm(listOf(row("ISF", currentValue = 50f, mean = 30f)))
        val captured = slot<List<TherapyParameter>>()
        coEvery { repo.upsertAll(capture(captured)) } returns Unit

        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.acceptAll()
        advanceUntilIdle()

        val updated = captured.captured.first { it.parameterType == "ISF" }
        assertEquals(37.5f, updated.currentValue, 0.01f)
    }

    // ── Zero observations skipped ─────────────────────────────────────────────

    @Test
    fun `acceptAll does nothing when no rows have any observations`() = runTest {
        val (vm, repo) = buildVm(listOf(row("ISF", currentValue = 50f, mean = 75f, nObs = 0)))

        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.acceptAll()
        advanceUntilIdle()

        coVerify(exactly = 0) { repo.upsertAll(any()) }
    }
}
