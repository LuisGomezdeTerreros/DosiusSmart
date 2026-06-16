package com.dosius.smart.data.repository

import com.dosius.smart.domain.model.GlucoseReading
import com.dosius.smart.domain.model.GlucoseTrend
import com.dosius.smart.domain.repository.GlucoseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
@Singleton
class MockGlucoseRepository @Inject constructor() : GlucoseRepository {

    private val readings by lazy {
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        List(30) { i ->
            val instant = now.minus(5.minutes * i)
            GlucoseReading(
                id = "mock-$i",
                timestamp = instant.toLocalDateTime(tz),
                glucoseValue = (90..180).random(),
                trend = GlucoseTrend.entries.random(),
                trendRate = kotlin.random.Random.nextFloat() * 5f - 2.5f,
                source = "Mock"
            )
        }
    }

    override suspend fun getGlucoseHistory(): List<GlucoseReading> = readings
    override suspend fun getLatestReading(): GlucoseReading? = readings.firstOrNull()
    override suspend fun getReadingClosestTo(epochSeconds: Long): GlucoseReading? =
        readings.minByOrNull { abs(it.timestamp.toInstant(TimeZone.currentSystemDefault()).epochSeconds - epochSeconds) }

    override fun getReadingsStream(): Flow<List<GlucoseReading>> = flowOf(readings)

}
