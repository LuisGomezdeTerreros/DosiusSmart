package com.dosius.smart.data.repository

import com.dosius.smart.data.local.dao.EntryDao
import com.dosius.smart.data.local.dao.FoodCaseDao
import com.dosius.smart.data.local.dao.FoodReadingDao
import com.dosius.smart.domain.engine.learning.BayesianParameterFitter
import com.dosius.smart.domain.model.CarbConfidence
import com.dosius.smart.domain.model.Entry
import com.dosius.smart.domain.model.Food
import com.dosius.smart.domain.model.FoodCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepository @Inject constructor(
    private val foodDao: FoodReadingDao,
    private val entryDao: EntryDao,
    private val foodCaseDao: FoodCaseDao,
    private val fitter: BayesianParameterFitter
) {
    suspend fun getFoodCount(): Int = foodDao.getCount()
    suspend fun getEntryCount(): Int = entryDao.getCount()

    fun getAllFoods() = foodDao.getAllFoods()

    fun getFoodById(id: String) = foodDao.getFoodById(id)

    suspend fun upsertFood(food: Food) = foodDao.upsertFood(food)

    suspend fun deleteFood(food: Food) = foodDao.deleteFood(food)

    fun getEntriesByFoodId(foodId: String) = entryDao.getEntriesByFoodId(foodId)

    fun getAllEntries() = entryDao.getAllEntries()

    suspend fun upsertEntry(entry: Entry) = entryDao.upsertEntry(entry)

    suspend fun deleteEntry(entry: Entry) = entryDao.deleteEntry(entry)

    fun getEntriesByExerciseType(type: String) = entryDao.getEntriesByExerciseType(type)

    fun getDistinctCategories() = foodDao.getDistinctCategories()
    fun getPendingReviews() = entryDao.getPendingReviews()

    suspend fun getEntryById(id: String): Entry? = entryDao.getEntryByIdSuspend(id)

    suspend fun searchFoods(query: String): List<Food> = foodDao.searchFoods(query)

    suspend fun getFoodCases(foodId: String): List<FoodCase> = foodCaseDao.getByFoodId(foodId)

    suspend fun getAllFoodCases(): List<FoodCase> = foodCaseDao.getAll()

    suspend fun getRecentEntriesSince(cutoffEpoch: Long): List<Entry> =
        entryDao.getRecentEntriesSince(cutoffEpoch)

    suspend fun getEntriesWithIsfLearning(): List<Entry> =
        entryDao.getEntriesWithIsfLearning()

    suspend fun getSleepEntries(): List<Entry> =
        entryDao.getSleepEntries()

    suspend fun acceptMealCarbObservation(entry: Entry, useInferredForPortion: Boolean = false) {
        val quantityG = entry.quantity?.takeIf { it > 0f } ?: return
        val inferredTotal = entry.inferredCarbsPostEvent ?: return
        val enteredTotal = entry.totalCarbs ?: return
        val foodId = entry.foodId ?: return
        val food = foodDao.getFoodById(foodId).first() ?: return

        val confidence = entry.carbConfidence ?: CarbConfidence.LOW
        val enteredPer100g = enteredTotal / (quantityG / 100f)
        val inferredPer100g = inferredTotal / (quantityG / 100f)
        val alpha = when (confidence) {
            CarbConfidence.CERTAIN, CarbConfidence.HIGH -> 0f
            CarbConfidence.MEDIUM -> 0.5f
            CarbConfidence.LOW -> 0.9f
        }
        val observationPer100g = alpha * inferredPer100g + (1 - alpha) * enteredPer100g

        val portionTotal = if (useInferredForPortion) inferredTotal else enteredTotal
        val n = food.entries
        val newCarbsRecommended = if (food.carbsRecommended == null || n == 0) {
            portionTotal
        } else {
            (food.carbsRecommended * n + portionTotal) / (n + 1)
        }

        val updatedFood = if (confidence == CarbConfidence.MEDIUM || confidence == CarbConfidence.LOW) {
            val priorMean = food.posteriorMean ?: food.carbsPer100g ?: observationPer100g
            val priorVariance = food.posteriorVariance ?: BayesianParameterFitter.DEFAULT_PRIOR_VARIANCE
            val posterior = fitter.learnFoodCarbs(priorMean, priorVariance, observationPer100g)
            food.copy(
                posteriorMean = posterior.mean,
                posteriorVariance = posterior.variance,
                nObservations = food.nObservations + 1,
                carbsRecommended = newCarbsRecommended
            )
        } else {
            food.copy(carbsRecommended = newCarbsRecommended)
        }

        foodDao.upsertFood(updatedFood)
    }

    suspend fun incrementFoodEntries(foodId: String) {
        val food = foodDao.getFoodById(foodId).first() ?: return
        foodDao.upsertFood(food.copy(entries = food.entries + 1))
    }

    suspend fun updatePortionSize(entry: Entry) {
        val enteredTotal = entry.totalCarbs?.takeIf { it > 0f } ?: return
        val foodId = entry.foodId ?: return
        val food = foodDao.getFoodById(foodId).first() ?: return

        val n = food.entries
        val newCarbsRecommended = if (food.carbsRecommended == null || n == 0) {
            enteredTotal
        } else {
            (food.carbsRecommended * n + enteredTotal) / (n + 1)
        }

        foodDao.upsertFood(food.copy(carbsRecommended = newCarbsRecommended))
    }
}