package com.dosius.smart.data.repository

import com.dosius.smart.data.local.dao.ContaminationWindowDao
import com.dosius.smart.data.local.dao.DeviationPointDao
import com.dosius.smart.data.local.dao.EntryDao
import com.dosius.smart.data.local.dao.GlucoseReadingDao
import com.dosius.smart.domain.engine.contamination.ContaminationDetectionEngine
import com.dosius.smart.domain.model.CarbConfidence
import com.dosius.smart.domain.model.ContaminationWindow
import com.dosius.smart.domain.model.DeviationPoint
import com.dosius.smart.domain.model.Entry
import com.dosius.smart.domain.model.ExerciseIntensity
import com.dosius.smart.domain.model.GlucoseReading
import com.dosius.smart.domain.model.GlucoseTrend
import com.dosius.smart.domain.model.RegistrationMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class ContaminationRepositoryTest {

    private val now = LocalDateTime(2024, 6, 15, 12, 0, 0)
    private val nowInstant = now.toInstant(TimeZone.UTC)

    private val glucoseStorage = mutableListOf<GlucoseReading>()
    private val entryStorage = mutableListOf<Entry>()
    private val deviationStorage = mutableListOf<DeviationPoint>()
    private val windowStorage = mutableListOf<ContaminationWindow>()

    private val fakeGlucoseDao = object : GlucoseReadingDao {
        override suspend fun getLatestReading() = glucoseStorage.firstOrNull()
        override fun getAllReadings(): Flow<List<GlucoseReading>> = flowOf(glucoseStorage)
        override suspend fun getReadingsAfterTimestamp(sinceEpoch: Long) =
            glucoseStorage.filter { it.timestamp.toInstant(TimeZone.UTC).toEpochMilliseconds() >= sinceEpoch }
        override suspend fun insertAll(readings: List<GlucoseReading>) {}
        override suspend fun deleteOlderThan(cutoffEpoch: Long) {}
    }

    private val fakeEntryDao = object : EntryDao {
        override fun getAllEntries(): Flow<List<Entry>> = flowOf(entryStorage)
        override suspend fun getRecentEntriesSince(sinceEpoch: Long) =
            entryStorage.filter { it.timestamp.toInstant(TimeZone.UTC).toEpochMilliseconds() > sinceEpoch }
        override fun getEntriesByFoodId(foodId: String): Flow<List<Entry>> = flowOf(emptyList())
        override fun getEntriesByExerciseType(type: String): Flow<List<Entry>> = flowOf(emptyList())
        override suspend fun getCount() = entryStorage.size
        override suspend fun upsertEntry(entry: Entry) { entryStorage.add(entry) }
        override suspend fun deleteEntry(entry: Entry) { entryStorage.remove(entry) }
        override fun getEntriesById(id: String): Entry =
            entryStorage.first { it.id == id }
        override suspend fun getEntryByIdSuspend(id: String): Entry? =
            entryStorage.firstOrNull { it.id == id }
        override fun getPendingReviews(): Flow<List<Entry>> = flowOf(emptyList())
        override suspend fun getEntriesByMealGroupId(mealGroupId: String): List<Entry> =
            entryStorage.filter { it.mealGroupId == mealGroupId }
        override suspend fun getEntriesWithIsfLearning(): List<Entry> =
            entryStorage.filter { it.isfLearningEnabled }
        override suspend fun getSleepEntries(): List<Entry> =
            entryStorage.filter { it.sleepWindowEnd != null }
    }

    private val fakeDeviationDao = object : DeviationPointDao {
        override suspend fun insert(point: DeviationPoint) {}
        override suspend fun getAfterTimestamp(sinceEpoch: Long) =
            deviationStorage
                .filter { it.timestamp.toInstant(TimeZone.UTC).toEpochMilliseconds() >= sinceEpoch }
                .sortedBy { it.timestamp }
        override fun observeAfterTimestamp(sinceEpoch: Long): Flow<List<DeviationPoint>> = flowOf(emptyList())
        override suspend fun deleteOlderThan(cutoffEpoch: Long) {}
    }

    private val fakeContaminationDao = object : ContaminationWindowDao {
        override suspend fun insert(contaminationWindow: ContaminationWindow) {
            windowStorage.add(contaminationWindow)
        }
        override suspend fun delete(contaminationWindow: ContaminationWindow) {
            windowStorage.remove(contaminationWindow)
        }
        override fun getAll(): Flow<List<ContaminationWindow>> = flowOf(windowStorage)
        override suspend fun getWindowsContaining(timestampEpoch: Long) =
            windowStorage.filter { w ->
                w.startTime.toInstant(TimeZone.UTC).epochSeconds <= timestampEpoch &&
                w.endTime.toInstant(TimeZone.UTC).epochSeconds >= timestampEpoch
            }
        override suspend fun getUAM(sinceEpoch: Long) =
            windowStorage.filter { it.reason == "RULE_UAM" &&
                it.startTime.toInstant(TimeZone.UTC).toEpochMilliseconds() >= sinceEpoch }
        override suspend fun getBySourceEventId(sourceEventId: String) =
            windowStorage.firstOrNull { it.sourceEventId == sourceEventId }
    }

    private lateinit var repo: ContaminationRepository

    @Before
    fun setup() {
        glucoseStorage.clear()
        entryStorage.clear()
        deviationStorage.clear()
        windowStorage.clear()
        repo = ContaminationRepository(
            fakeEntryDao, fakeDeviationDao, fakeGlucoseDao,
            fakeContaminationDao, ContaminationDetectionEngine()
        )
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun reading(id: String, bg: Int, minutesAgo: Long) = GlucoseReading(
        id = id,
        timestamp = nowInstant.minus(minutesAgo.minutes).toLocalDateTime(TimeZone.UTC),
        glucoseValue = bg,
        trend = GlucoseTrend.STABLE,
        trendRate = 0f,
        source = "test"
    )

    private fun exerciseEntry(id: String, minutesAgo: Long) = Entry(
        id = id,
        timestamp = nowInstant.minus(minutesAgo.minutes).toLocalDateTime(TimeZone.UTC),
        createdAt = now,
        foodId = null, quantity = null, totalCarbs = null, carbConfidence = null,
        mealType = null, registrationMethod = RegistrationMethod.MANUAL,
        insulinUnits = null, insulinType = null, recommendedUnits = null, currentGlucose = null,
        exerciseType = "Swimming", durationOfExercise = 45f, intensity = ExerciseIntensity.MEDIUM,
        recommendedCarbs = null
    )

    private fun mealEntry(id: String, minutesAgo: Long) = Entry(
        id = id,
        timestamp = nowInstant.minus(minutesAgo.minutes).toLocalDateTime(TimeZone.UTC),
        createdAt = now,
        foodId = "f1", quantity = 200f, totalCarbs = 60f, carbConfidence = CarbConfidence.HIGH,
        mealType = "Lunch", registrationMethod = RegistrationMethod.MANUAL,
        insulinUnits = null, insulinType = null, recommendedUnits = null, currentGlucose = null,
        exerciseType = null, durationOfExercise = null, intensity = null,
        recommendedCarbs = null
    )

    private fun outlierPoint(id: String, minutesAgo: Long) = DeviationPoint(
        id = id,
        timestamp = nowInstant.minus(minutesAgo.minutes).toLocalDateTime(TimeZone.UTC),
        predictedDelta = 0f, observedDelta = 40f, deviation = 40f, bgi = 0f,
        fastingWindow = true, postprandialWindow = false, carbConfidence = CarbConfidence.LOW,
        recentExercise = false, outlierFlag = true, timeSlot = 12, dayOfWeek = 6,
        iobPresent = false, cobPresent = false
    )

    private fun positivePoint(id: String, minutesAgo: Long, deviation: Float = 2f) = DeviationPoint(
        id = id,
        timestamp = nowInstant.minus(minutesAgo.minutes).toLocalDateTime(TimeZone.UTC),
        predictedDelta = 0f, observedDelta = deviation, deviation = deviation, bgi = 0f,
        fastingWindow = true, postprandialWindow = false, carbConfidence = CarbConfidence.LOW,
        recentExercise = false, outlierFlag = false, timeSlot = 12, dayOfWeek = 6,
        iobPresent = false, cobPresent = false
    )

    // ── RULE_HYPO ─────────────────────────────────────────────────────────────

    @Test
    fun `RULE_HYPO creates window when BG is below 54`() = runBlocking {
        glucoseStorage.add(reading("r1", bg = 45, minutesAgo = 30))
        repo.detectAndStore(now)
        val window = windowStorage.firstOrNull { it.reason == "RULE_HYPO" }
        assertNotNull(window)
        assertEquals("r1_hypo", window!!.id)
        assertTrue(window.excludedParamTypes.split(",").containsAll(listOf("ISF", "ICR", "BASAL")))
    }

    @Test
    fun `RULE_HYPO does not create duplicate on second run`() = runBlocking {
        glucoseStorage.add(reading("r1", bg = 45, minutesAgo = 30))
        repo.detectAndStore(now)
        repo.detectAndStore(now)
        assertEquals(1, windowStorage.count { it.reason == "RULE_HYPO" })
    }

    @Test
    fun `RULE_HYPO does not trigger for normal BG`() = runBlocking {
        glucoseStorage.add(reading("r1", bg = 90, minutesAgo = 30))
        repo.detectAndStore(now)
        assertTrue(windowStorage.none { it.reason == "RULE_HYPO" })
    }

    // ── RULE_EXERCISE ─────────────────────────────────────────────────────────

    @Test
    fun `RULE_EXERCISE creates window for any logged exercise`() = runBlocking {
        entryStorage.add(exerciseEntry("e1", minutesAgo = 60))
        repo.detectAndStore(now)
        val window = windowStorage.firstOrNull { it.reason == "RULE_EXERCISE" }
        assertNotNull(window)
        assertEquals("e1_exercise", window!!.id)
        assertTrue(window.excludedParamTypes.split(",").containsAll(listOf("ISF", "ICR")))
        assertTrue(window.excludedParamTypes.split(",").contains("BASAL"))
    }

    @Test
    fun `RULE_EXERCISE does not trigger when no exercise logged`() = runBlocking {
        repo.detectAndStore(now)
        assertTrue(windowStorage.none { it.reason == "RULE_EXERCISE" })
    }

    // ── RULE_STATISTICAL ──────────────────────────────────────────────────────

    @Test
    fun `RULE_STATISTICAL fires for 3 consecutive outliers within 30 min`() = runBlocking {
        deviationStorage.addAll(listOf(
            outlierPoint("d1", minutesAgo = 20),
            outlierPoint("d2", minutesAgo = 15),
            outlierPoint("d3", minutesAgo = 10)
        ))
        repo.detectAndStore(now)
        assertNotNull(windowStorage.firstOrNull { it.reason == "RULE_STATISTICAL" })
    }

    @Test
    fun `RULE_STATISTICAL does not fire for only 2 outliers`() = runBlocking {
        deviationStorage.addAll(listOf(
            outlierPoint("d1", minutesAgo = 20),
            outlierPoint("d2", minutesAgo = 15)
        ))
        repo.detectAndStore(now)
        assertTrue(windowStorage.none { it.reason == "RULE_STATISTICAL" })
    }

    @Test
    fun `RULE_STATISTICAL does not fire when outliers span more than 30 min`() = runBlocking {
        // d1 is 35 min before d3 — span exceeds threshold, no valid cluster
        deviationStorage.addAll(listOf(
            outlierPoint("d1", minutesAgo = 50),
            outlierPoint("d2", minutesAgo = 15),
            outlierPoint("d3", minutesAgo = 10)
        ))
        repo.detectAndStore(now)
        assertTrue(windowStorage.none { it.reason == "RULE_STATISTICAL" })
    }

    // ── RULE_UAM ──────────────────────────────────────────────────────────────

    @Test
    fun `RULE_UAM fires for 4 consecutive positive deviations above threshold with no logged meal`() = runBlocking {
        // implementation threshold = rolling average > 3f; deviation = 4f exceeds it
        deviationStorage.addAll(listOf(
            positivePoint("u1", minutesAgo = 16, deviation = 4f),
            positivePoint("u2", minutesAgo = 13, deviation = 4f),
            positivePoint("u3", minutesAgo = 10, deviation = 4f),
            positivePoint("u4", minutesAgo = 7, deviation = 4f)
        ))
        repo.detectAndStore(now)
        assertNotNull(windowStorage.firstOrNull { it.reason == "RULE_UAM" })
    }

    @Test
    fun `RULE_UAM does not fire when a meal was logged within 60 min before the rise`() = runBlocking {
        // Use deviation=4f so UAM would fire without a meal; the meal entry should suppress it.
        deviationStorage.addAll(listOf(
            positivePoint("u1", minutesAgo = 16, deviation = 4f),
            positivePoint("u2", minutesAgo = 13, deviation = 4f),
            positivePoint("u3", minutesAgo = 10, deviation = 4f),
            positivePoint("u4", minutesAgo = 7, deviation = 4f)
        ))
        entryStorage.add(mealEntry("m1", minutesAgo = 50)) // 34 min before first deviation point
        repo.detectAndStore(now)
        assertTrue(windowStorage.none { it.reason == "RULE_UAM" })
    }

    @Test
    fun `RULE_UAM does not fire for fewer than 4 positive deviation points`() = runBlocking {
        deviationStorage.addAll(listOf(
            positivePoint("u1", minutesAgo = 16),
            positivePoint("u2", minutesAgo = 13),
            positivePoint("u3", minutesAgo = 10)
        ))
        repo.detectAndStore(now)
        assertTrue(windowStorage.none { it.reason == "RULE_UAM" })
    }

    // ── isContaminated ────────────────────────────────────────────────────────

    @Test
    fun `isContaminated returns true for timestamp inside window with matching paramType`() = runBlocking {
        windowStorage.add(ContaminationWindow(
            id = "w1",
            startTime = nowInstant.minus(30.minutes).toLocalDateTime(TimeZone.UTC),
            endTime = nowInstant.plus(30.minutes).toLocalDateTime(TimeZone.UTC),
            reason = "RULE_HYPO", sourceEventId = "r1",
            excludedParamTypes = "ISF,ICR,BASAL"
        ))
        assertTrue(repo.isContaminated(now, "ISF"))
    }

    @Test
    fun `isContaminated returns false for paramType not excluded by window`() = runBlocking {
        windowStorage.add(ContaminationWindow(
            id = "w1",
            startTime = nowInstant.minus(30.minutes).toLocalDateTime(TimeZone.UTC),
            endTime = nowInstant.plus(30.minutes).toLocalDateTime(TimeZone.UTC),
            reason = "RULE_EXERCISE", sourceEventId = "e1",
            excludedParamTypes = "ISF,ICR"
        ))
        assertFalse(repo.isContaminated(now, "BASAL"))
    }

    @Test
    fun `isContaminated returns false for timestamp outside all windows`() = runBlocking {
        windowStorage.add(ContaminationWindow(
            id = "w1",
            startTime = nowInstant.minus(120.minutes).toLocalDateTime(TimeZone.UTC),
            endTime = nowInstant.minus(60.minutes).toLocalDateTime(TimeZone.UTC),
            reason = "RULE_HYPO", sourceEventId = "r1",
            excludedParamTypes = "ISF,ICR,BASAL"
        ))
        assertFalse(repo.isContaminated(now, "ISF"))
    }
}
