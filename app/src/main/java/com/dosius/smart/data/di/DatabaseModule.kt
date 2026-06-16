package com.dosius.smart.data.di

import android.content.Context
import androidx.room.Room
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
import com.dosius.smart.data.local.database.DosiusDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DosiusDatabase =
        Room.databaseBuilder(
            context,
            DosiusDatabase::class.java,
            "dosius_database"
        ).fallbackToDestructiveMigration().build()

    @Provides @Singleton
    fun provideGlucoseReadingDao(database: DosiusDatabase): GlucoseReadingDao =
        database.glucoseReadingDao()

    @Provides @Singleton
    fun provideFoodReadingDao(database: DosiusDatabase): FoodReadingDao =
        database.foodReadingDao()

    @Provides @Singleton
    fun provideEntryDao(database: DosiusDatabase): EntryDao = database.entryDao()

    @Provides @Singleton
    fun provideExerciseDao(database: DosiusDatabase): ExerciseDao = database.exerciseDao()

    @Provides @Singleton
    fun provideDeviationPointDao(database: DosiusDatabase): DeviationPointDao =
        database.deviationPointDao()

    @Provides @Singleton
    fun provideTherapyParameterDao(database: DosiusDatabase): TherapyParameterDao =
        database.therapyParameterDao()

    @Provides @Singleton
    fun provideFoodCaseDao(database: DosiusDatabase): FoodCaseDao = database.foodCaseDao()

    @Provides @Singleton
    fun provideExerciseCaseDao(database: DosiusDatabase): ExerciseCaseDao =
        database.exerciseCaseDao()

    @Provides @Singleton
    fun provideContaminationWindowDao(database: DosiusDatabase): ContaminationWindowDao =
        database.contaminationWindowDao()

    @Provides @Singleton
    fun provideForecastDao(database: DosiusDatabase): ForecastDao = database.forecastDao()

    @Provides @Singleton
    fun provideAlertEventDao(database: DosiusDatabase): AlertEventDao = database.alertEventDao()

    @Provides @Singleton
    fun provideRecommendationLogDao(database: DosiusDatabase): RecommendationLogDao =
        database.recommendationLogDao()
}

