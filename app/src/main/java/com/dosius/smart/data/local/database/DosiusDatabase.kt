package com.dosius.smart.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.dosius.smart.data.local.dao.AlertEventDao
import com.dosius.smart.data.local.dao.ContaminationWindowDao
import com.dosius.smart.data.local.dao.DeviationPointDao
import com.dosius.smart.data.local.dao.EntryDao
import com.dosius.smart.data.local.dao.ExerciseCaseDao
import com.dosius.smart.data.local.dao.ExerciseDao
import com.dosius.smart.data.local.dao.FoodCaseDao
import com.dosius.smart.data.local.dao.FoodReadingDao
import com.dosius.smart.data.local.dao.ForecastDao
import com.dosius.smart.data.local.dao.GlucoseReadingDao
import com.dosius.smart.data.local.dao.RecommendationLogDao
import com.dosius.smart.data.local.dao.TherapyParameterDao
import com.dosius.smart.domain.model.AlertEvent
import com.dosius.smart.domain.model.ContaminationWindow
import com.dosius.smart.domain.model.DeviationPoint
import com.dosius.smart.domain.model.Entry
import com.dosius.smart.domain.model.Exercise
import com.dosius.smart.domain.model.ExerciseCase
import com.dosius.smart.domain.model.Food
import com.dosius.smart.domain.model.FoodCase
import com.dosius.smart.domain.model.Forecast
import com.dosius.smart.domain.model.GlucoseReading
import com.dosius.smart.domain.model.RecommendationLog
import com.dosius.smart.domain.model.TherapyParameter

@Database(
    entities = [
        GlucoseReading::class,
        Food::class,
        Entry::class,
        Exercise::class,
        DeviationPoint::class,
        TherapyParameter::class,
        FoodCase::class,
        ExerciseCase::class,
        ContaminationWindow::class,
        Forecast::class,
        AlertEvent::class,
        RecommendationLog::class
    ],
    version = 26,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class DosiusDatabase : RoomDatabase() {
    abstract fun glucoseReadingDao(): GlucoseReadingDao
    abstract fun foodReadingDao(): FoodReadingDao
    abstract fun entryDao(): EntryDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun deviationPointDao(): DeviationPointDao
    abstract fun therapyParameterDao(): TherapyParameterDao
    abstract fun foodCaseDao(): FoodCaseDao
    abstract fun exerciseCaseDao(): ExerciseCaseDao
    abstract fun contaminationWindowDao(): ContaminationWindowDao
    abstract fun forecastDao(): ForecastDao
    abstract fun alertEventDao(): AlertEventDao
    abstract fun recommendationLogDao(): RecommendationLogDao
}
