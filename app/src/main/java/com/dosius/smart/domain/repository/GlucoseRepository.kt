package com.dosius.smart.domain.repository

import com.dosius.smart.domain.model.GlucoseReading
import kotlinx.coroutines.flow.Flow

interface GlucoseRepository {
    suspend fun getGlucoseHistory(): List<GlucoseReading>
    suspend fun getLatestReading(): GlucoseReading?
    suspend fun getReadingClosestTo(epochSeconds: Long): GlucoseReading?

    fun getReadingsStream(): Flow<List<GlucoseReading>>


}
