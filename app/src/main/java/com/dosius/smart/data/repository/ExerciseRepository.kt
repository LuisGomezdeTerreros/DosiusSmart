package com.dosius.smart.data.repository

import com.dosius.smart.data.local.dao.EntryDao
import com.dosius.smart.data.local.dao.ExerciseCaseDao
import com.dosius.smart.data.local.dao.ExerciseDao
import com.dosius.smart.data.local.dao.FoodReadingDao
import com.dosius.smart.domain.engine.learning.BayesianParameterFitter
import com.dosius.smart.domain.model.Entry
import com.dosius.smart.domain.model.Exercise
import com.dosius.smart.domain.model.ExerciseCase
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val entryDao: EntryDao,
    private val exerciseCaseDao: ExerciseCaseDao,
    private val fitter: BayesianParameterFitter
) {
    fun getAllExercises() = exerciseDao.getAllExercises()
    fun getExerciseById(type: String) = exerciseDao.getExerciseById(type)
    suspend fun getCount(): Int = exerciseDao.getCount()
    suspend fun upsertExercise(exercise: Exercise) = exerciseDao.upsertExercise(exercise)
    suspend fun deleteExercise(exercise: Exercise) = exerciseDao.deleteExercise(exercise)

    suspend fun getAllExerciseCases(): List<ExerciseCase> = exerciseCaseDao.getAll()
    suspend fun getCasesForType(type: String): List<ExerciseCase> = exerciseCaseDao.getByExerciseType(type)
    suspend fun getCaseCount(): Int = exerciseCaseDao.getCount()

    suspend fun acceptDropObservation(entry: Entry) {
        val type = entry.exerciseType ?: return
        val exercise = exerciseDao.getExerciseById(type).first() ?: return
        val actualDrop = entry.actualExerciseDrop ?: return
        val durationHours = (entry.durationOfExercise ?: 60f) / 60f
        if (durationHours <= 0f) return
        val dropPerHour = actualDrop / durationHours

        val currentMean = exercise.expectedGlucoseDropPerHour ?: dropPerHour
        val posterior = fitter.learnExerciseDrop(currentMean, exercise.dropVariance ?: BayesianParameterFitter.EXERCISE_DEFAULT_PRIOR_VARIANCE, dropPerHour)
        val newObs = exercise.nObservations + 1

        exerciseDao.upsertExercise(exercise.copy(
            expectedGlucoseDropPerHour = posterior.mean,
            dropVariance = posterior.variance,
            nObservations = newObs,
            confidencePercentage = minOf(100, 30 + newObs * 10)
        ))
        exerciseCaseDao.insert(ExerciseCase(
            id = UUID.randomUUID().toString(),
            exerciseType = type,
            timestamp = entry.timestamp,
            preExerciseBg = entry.currentGlucose ?: 100,
            iobAtStart = 0f,
            durationMinutes = entry.durationOfExercise ?: 0f,
            intensity = entry.intensity?.name ?: "MEDIUM",
            expectedDrop = exercise.expectedGlucoseDropPerHour?.toInt() ?: 0,
            actualDrop = actualDrop.toInt(),
            observedIauc = 0f
        ))
    }
}