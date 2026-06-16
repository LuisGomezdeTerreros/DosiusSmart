package com.dosius.smart.presentation.entry

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.dosius.smart.data.repository.EntryRepository
import com.dosius.smart.data.worker.BasalLearningWorker
import com.dosius.smart.data.worker.IsfLearningWorker
import com.dosius.smart.data.repository.ExerciseRepository
import com.dosius.smart.data.repository.RecommendationRepository
import com.dosius.smart.domain.repository.GlucoseRepository
import com.dosius.smart.data.worker.ExerciseEventWorker
import com.dosius.smart.data.worker.MealEventWorker
import com.dosius.smart.domain.engine.recommendation.RecommendationResult
import com.dosius.smart.domain.engine.recommendation.RecommendationType
import com.dosius.smart.domain.model.CarbConfidence
import com.dosius.smart.domain.model.Entry
import com.dosius.smart.domain.model.Exercise
import com.dosius.smart.domain.model.ExerciseIntensity
import com.dosius.smart.domain.model.Food
import com.dosius.smart.domain.model.InsulinType
import com.dosius.smart.domain.model.RegistrationMethod
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.roundToInt

data class QuantitySuggestion(val grams: Int, val fromNEvents: Int)

data class BasketItem(
    val tempId: String = UUID.randomUUID().toString(),
    val foodId: String?,
    val foodName: String,
    val quantity: Float?,
    val totalCarbs: Float,
    val carbConfidence: CarbConfidence
)

@OptIn(FlowPreview::class)
@HiltViewModel
class AddEntryViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val exerciseRepository: ExerciseRepository,
    private val glucoseRepository: GlucoseRepository,
    private val recommendationRepository: RecommendationRepository,
    @ApplicationContext private val context: Context

) : ViewModel() {


    private val _prandialRecommendation = MutableStateFlow<RecommendationResult?>(null)
    val prandialRecommendation: StateFlow<RecommendationResult?> =
        _prandialRecommendation.asStateFlow()

    private val _preExerciseRecommendation = MutableStateFlow<RecommendationResult?>(null)
    val preExerciseRecommendation: StateFlow<RecommendationResult?> =
        _preExerciseRecommendation.asStateFlow()

    private val _foodSearchQuery = MutableStateFlow("")
    val foodSearchQuery: StateFlow<String> = _foodSearchQuery.asStateFlow()

    private val _foodSearchResults = MutableStateFlow<List<Food>>(emptyList())
    val foodSearchResults: StateFlow<List<Food>> = _foodSearchResults.asStateFlow()

    private val _quantitySuggestion = MutableStateFlow<QuantitySuggestion?>(null)
    val quantitySuggestion: StateFlow<QuantitySuggestion?> = _quantitySuggestion.asStateFlow()

    private val _carbsRecommended = MutableStateFlow<Float?>(null)
    val carbsRecommended: StateFlow<Float?> = _carbsRecommended.asStateFlow()

    private val _basketItems = MutableStateFlow<List<BasketItem>>(emptyList())
    val basketItems: StateFlow<List<BasketItem>> = _basketItems.asStateFlow()

    val exercises: StateFlow<List<Exercise>> = exerciseRepository.getAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _exerciseName = MutableStateFlow<String>("")
    val exerciseName: StateFlow<String> = _exerciseName.asStateFlow()

    private val _selectedExercise = MutableStateFlow<Exercise?>(null)
    val selectedExercise: StateFlow<Exercise?> = _selectedExercise.asStateFlow()

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    // Date/Time selection
    private val _selectedDateTime =
        MutableStateFlow(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()))
    val selectedDateTime: StateFlow<LocalDateTime> = _selectedDateTime.asStateFlow()

    // Food State
    private val _foodName = MutableStateFlow<String>("")
    val foodName: StateFlow<String> = _foodName.asStateFlow()

    private val _selectedFood = MutableStateFlow<Food?>(null)
    val selectedFood: StateFlow<Food?> = _selectedFood.asStateFlow()


    private val _quantity = MutableStateFlow("")
    val quantity: StateFlow<String> = _quantity.asStateFlow()

    private val _quantityError = MutableStateFlow<String?>(null)
    val quantityError: StateFlow<String?> = _quantityError.asStateFlow()

    private val _totalCarbs = MutableStateFlow("")
    val totalCarbs: StateFlow<String> = _totalCarbs.asStateFlow()

    private val _totalCarbsError = MutableStateFlow<String?>(null)
    val totalCarbsError: StateFlow<String?> = _totalCarbsError.asStateFlow()

    private val _carbConfidence = MutableStateFlow(CarbConfidence.HIGH)
    val carbConfidence: StateFlow<CarbConfidence> = _carbConfidence.asStateFlow()

    private val _mealType = MutableStateFlow<String?>(null)
    val mealType: StateFlow<String?> = _mealType.asStateFlow()

    // Insulin State
    private val _insulinUnits = MutableStateFlow("")
    val insulinUnits: StateFlow<String> = _insulinUnits.asStateFlow()

    private val _insulinUnitsError = MutableStateFlow<String?>(null)
    val insulinUnitsError: StateFlow<String?> = _insulinUnitsError.asStateFlow()

    private val _insulinType = MutableStateFlow<InsulinType?>(null)
    val insulinType: StateFlow<InsulinType?> = _insulinType.asStateFlow()

    // Exercise State
    private val _durationOfExercise = MutableStateFlow("")
    val durationOfExercise: StateFlow<String> = _durationOfExercise.asStateFlow()

    private val _durationOfExerciseError = MutableStateFlow<String?>(null)
    val durationOfExerciseError: StateFlow<String?> = _durationOfExerciseError.asStateFlow()

    private val _intensity = MutableStateFlow<ExerciseIntensity?>(null)
    val intensity: StateFlow<ExerciseIntensity?> = _intensity.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    // Non-null when editing an existing entry
    private var editingEntryId: String? = null
    private var editingCreatedAt: LocalDateTime? = null

    private val _prandialAccepted = MutableStateFlow(false)
    val prandialAccepted: StateFlow<Boolean> = _prandialAccepted.asStateFlow()

    private val _preExerciseAccepted = MutableStateFlow(false)
    val preExerciseAccepted: StateFlow<Boolean> = _preExerciseAccepted.asStateFlow()

    // ISF learning prompt: shown when the bolus is a clean correction with no recent food/exercise
    private val _showIsfLearningPrompt = MutableStateFlow(false)
    val showIsfLearningPrompt: StateFlow<Boolean> = _showIsfLearningPrompt.asStateFlow()

    private val _isfLearningEnabled = MutableStateFlow(false)
    val isfLearningEnabled: StateFlow<Boolean> = _isfLearningEnabled.asStateFlow()

    // ICR learning prompt: shown when the meal has CERTAIN/HIGH confidence and carbs > 0
    private val _showIcrLearningPrompt = MutableStateFlow(false)
    val showIcrLearningPrompt: StateFlow<Boolean> = _showIcrLearningPrompt.asStateFlow()

    private val _icrLearningEnabled = MutableStateFlow(false)
    val icrLearningEnabled: StateFlow<Boolean> = _icrLearningEnabled.asStateFlow()

    // Sleep learning state
    private val _sleepEndHour = MutableStateFlow(7)
    val sleepEndHour: StateFlow<Int> = _sleepEndHour.asStateFlow()

    private val _sleepEndMinute = MutableStateFlow(0)
    val sleepEndMinute: StateFlow<Int> = _sleepEndMinute.asStateFlow()

    private val _sleepEligible = MutableStateFlow(true)
    val sleepEligible: StateFlow<Boolean> = _sleepEligible.asStateFlow()

    private val _sleepWarning = MutableStateFlow<String?>(null)
    val sleepWarning: StateFlow<String?> = _sleepWarning.asStateFlow()


    /**
     * Called when the Bolus form opens. Checks whether conditions are met for ISF learning:
     * - No food logged in the past 90 minutes (COB would still be active)
     * - No exercise logged in the past 2 hours
     * If both conditions hold, this bolus is a clean correction tail candidate.
     */
    fun checkIsfLearningEligibility() {
        viewModelScope.launch {
            val now = Clock.System.now()
            val foodCutoff = (now - 3.hours).epochSeconds
            val exerciseCutoff = (now - 2.hours).epochSeconds
            val recentFood = repository.getRecentEntriesSince(foodCutoff)
                .any { (it.totalCarbs ?: 0f) > 0f }
            val recentExercise = repository.getRecentEntriesSince(exerciseCutoff)
                .any { it.exerciseType != null }
            _showIsfLearningPrompt.value = !recentFood && !recentExercise
        }
    }

    fun onIsfLearningToggle(enabled: Boolean) {
        _isfLearningEnabled.value = enabled
    }

    fun onIcrLearningToggle(enabled: Boolean) {
        _icrLearningEnabled.value = enabled
    }

    private fun updateIcrLearningPrompt() {
        val highConfidence = _carbConfidence.value == CarbConfidence.CERTAIN ||
                             _carbConfidence.value == CarbConfidence.HIGH
        val hasCarbs = (_totalCarbs.value.toFloatOrNull() ?: 0f) > 0f
        val eligible = highConfidence && hasCarbs
        _showIcrLearningPrompt.value = eligible
        if (!eligible) _icrLearningEnabled.value = false
    }

    fun onSleepEndTimeChange(hour: Int, minute: Int) {
        _sleepEndHour.value = hour
        _sleepEndMinute.value = minute
    }

    fun checkSleepEligibility() {
        viewModelScope.launch {
            val now = Clock.System.now()
            val cutoff = (now - 4.hours).epochSeconds
            val recentEntries = repository.getRecentEntriesSince(cutoff)
            val hasMealOrBolus = recentEntries.any {
                (it.totalCarbs ?: 0f) > 0f || (it.insulinUnits ?: 0f) > 0f
            }
            val exerciseCutoff = (now - 3.hours).epochSeconds
            val hasExercise = repository.getRecentEntriesSince(exerciseCutoff)
                .any { it.exerciseType != null }
            _sleepEligible.value = !hasMealOrBolus && !hasExercise
            _sleepWarning.value = when {
                hasMealOrBolus && hasExercise ->
                    "Recent meal/bolus and exercise detected. Data quality may be reduced."
                hasMealOrBolus ->
                    "Recent meal or bolus detected (last 4h). Data quality may be reduced."
                hasExercise ->
                    "Recent exercise detected (last 3h). Data quality may be reduced."
                else -> null
            }
        }
    }

    fun saveSleepEntry() {
        viewModelScope.launch {
            val snapshotDateTime = _selectedDateTime.value
            val snapshotDescription = _description.value
            val endHour = _sleepEndHour.value
            val endMinute = _sleepEndMinute.value

            val startInstant = snapshotDateTime.toInstant(TimeZone.currentSystemDefault())
            val crossesMidnight = endHour < snapshotDateTime.hour ||
                (endHour == snapshotDateTime.hour && endMinute <= snapshotDateTime.minute)
            val endInstant = if (crossesMidnight) startInstant + 24.hours else startInstant
            val endDate = endInstant.toLocalDateTime(TimeZone.currentSystemDefault()).date
            val sleepEnd = LocalDateTime(
                endDate.year, endDate.monthNumber, endDate.dayOfMonth, endHour, endMinute
            )

            val entryId = UUID.randomUUID().toString()
            val entry = Entry(
                id = entryId,
                timestamp = snapshotDateTime,
                createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                description = snapshotDescription.ifBlank { "Sleep learning" },
                foodId = null,
                quantity = null,
                totalCarbs = null,
                carbConfidence = null,
                mealType = null,
                registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = null,
                insulinType = null,
                recommendedUnits = null,
                currentGlucose = glucoseRepository.getReadingClosestTo(
                    snapshotDateTime.toInstant(TimeZone.currentSystemDefault()).epochSeconds
                )?.glucoseValue,
                exerciseType = null,
                durationOfExercise = null,
                intensity = null,
                recommendedCarbs = null,
                sleepWindowEnd = sleepEnd
            )
            repository.upsertEntry(entry)

            val sleepEndInstant = sleepEnd.toInstant(TimeZone.currentSystemDefault())
            val nowInstant = Clock.System.now()
            val delayMinutes = (sleepEndInstant - nowInstant).inWholeMinutes.coerceAtLeast(1L)

            val request = OneTimeWorkRequestBuilder<BasalLearningWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setInputData(workDataOf(BasalLearningWorker.KEY_ENTRY_ID to entryId))
                .build()
            WorkManager.getInstance(context).enqueue(request)

            _isSaved.value = true
        }
    }

    fun resetTime() {
        _selectedDateTime.value = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }

    fun loadEntry(entryId: String) {
        viewModelScope.launch {
            val entry = repository.getEntryById(entryId) ?: return@launch
            editingEntryId = entry.id
            editingCreatedAt = entry.createdAt
            _selectedDateTime.value = entry.timestamp
            _description.value = entry.description
            _mealType.value = entry.mealType
            _quantity.value = entry.quantity?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() } ?: ""
            _totalCarbs.value = entry.totalCarbs?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() } ?: ""
            _carbConfidence.value = entry.carbConfidence ?: CarbConfidence.HIGH
            _insulinUnits.value = entry.insulinUnits?.let { if (it % 1f == 0f) it.toInt().toString() else String.format(java.util.Locale.US, "%.1f", it) } ?: ""
            _insulinType.value = entry.insulinType
            _durationOfExercise.value = entry.durationOfExercise?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() } ?: ""
            _intensity.value = entry.intensity
            entry.exerciseType?.let { type ->
                _exerciseName.value = type
                val exercise = exerciseRepository.getAllExercises().first().find { it.type == type }
                _selectedExercise.value = exercise
            }
            entry.foodId?.let { fid ->
                val food = repository.getFoodById(fid).first()
                if (food != null) {
                    _selectedFood.value = food
                    _foodName.value = food.name
                }
            }
        }
    }

    fun saveEntry() {
        viewModelScope.launch {
            // Snapshot all state now — resetForm() may run before any suspension point below
            val snapshotSelectedFood    = _selectedFood.value
            val snapshotFoodName        = _foodName.value
            val snapshotFoodSearchQuery = _foodSearchQuery.value
            val snapshotSelectedExercise = _selectedExercise.value
            val snapshotExerciseName    = _exerciseName.value
            val snapshotQuantity        = _quantity.value
            val snapshotTotalCarbs      = _totalCarbs.value
            val snapshotCarbConfidence  = _carbConfidence.value
            val snapshotMealType        = _mealType.value
            val snapshotInsulinUnits    = _insulinUnits.value
            val snapshotInsulinType     = _insulinType.value
            val snapshotDuration        = _durationOfExercise.value
            val snapshotIntensity       = _intensity.value
            val snapshotDescription     = _description.value
            val snapshotDateTime        = _selectedDateTime.value
            val snapshotPrandialRec     = _prandialRecommendation.value
            val snapshotPreExerciseRec  = _preExerciseRecommendation.value
            val snapshotBasketItems     = _basketItems.value
            val snapshotEditingEntryId  = editingEntryId
            val snapshotEditingCreatedAt = editingCreatedAt
            val snapshotCurrentGlucose  = glucoseRepository.getReadingClosestTo(
                snapshotDateTime.toInstant(TimeZone.currentSystemDefault()).epochSeconds
            )?.glucoseValue
            val snapshotIsfLearning     = _isfLearningEnabled.value && _insulinUnits.value.isNotBlank()
            val snapshotIcrLearning     = _icrLearningEnabled.value

            // Multi-food meals share a mealGroupId; single-food entries get null
            val mealGroupId: String? = if (snapshotBasketItems.isNotEmpty()) UUID.randomUUID().toString() else null

            val foodId: String? = if (snapshotSelectedFood != null) {
                snapshotSelectedFood.id
            } else {
                val nameToCreate = snapshotFoodName.ifBlank { snapshotFoodSearchQuery }.trim()
                if (nameToCreate.isNotBlank()) {
                    val carbsPer100g: Float? =
                        if (snapshotQuantity.isNotBlank() && snapshotTotalCarbs.isNotBlank() && snapshotQuantity.toFloat() != 0f)
                            (snapshotTotalCarbs.toFloat() / snapshotQuantity.toFloat()) * 100f
                        else null
                    val newFood = Food(
                        id = UUID.randomUUID().toString(),
                        name = nameToCreate,
                        idCategory = "Custom",
                        carbsPer100g = carbsPer100g,
                        carbsRecommended = null,
                        glycemicIndex = null,
                        isCustom = true,
                        isIngredient = false,
                        ingredients = emptyList(),
                        entries = 0
                    )
                    repository.upsertFood(newFood)
                    newFood.id
                } else {
                    null
                }
            }

            val exerciseType: String? = if (snapshotSelectedExercise != null) {
                snapshotSelectedExercise.type
            } else if (snapshotExerciseName.isNotBlank()) {
                val newExercise = Exercise(
                    type = snapshotExerciseName,
                    expectedGlucoseDropPerHour = null,
                    confidencePercentage = null
                )
                exerciseRepository.upsertExercise(newExercise)
                newExercise.type
            } else {
                null
            }

            // Save each basket item as its own Entry row in the same mealGroupId
            for (item in snapshotBasketItems) {
                val itemFoodId: String? = item.foodId ?: run {
                    if (item.foodName.isNotBlank()) {
                        val carbsPer100g: Float? = item.quantity?.takeIf { it > 0f }?.let {
                            item.totalCarbs / it * 100f
                        }
                        val newFood = Food(
                            id = UUID.randomUUID().toString(),
                            name = item.foodName,
                            idCategory = "Custom",
                            carbsPer100g = carbsPer100g,
                            carbsRecommended = null,
                            glycemicIndex = null,
                            isCustom = true,
                            isIngredient = false,
                            ingredients = emptyList(),
                            entries = 0
                        )
                        repository.upsertFood(newFood)
                        newFood.id
                    } else null
                }
                val itemEntryId = UUID.randomUUID().toString()
                repository.upsertEntry(
                    Entry(
                        id = itemEntryId,
                        timestamp = snapshotDateTime,
                        createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                        description = "",
                        foodId = itemFoodId,
                        quantity = item.quantity,
                        totalCarbs = item.totalCarbs,
                        carbConfidence = item.carbConfidence,
                        mealType = snapshotMealType,
                        registrationMethod = RegistrationMethod.MANUAL,
                        insulinUnits = null,
                        insulinType = null,
                        recommendedUnits = null,
                        currentGlucose = snapshotCurrentGlucose,
                        exerciseType = null,
                        durationOfExercise = null,
                        intensity = null,
                        recommendedCarbs = null,
                        actualExerciseDrop = null,
                        mealGroupId = mealGroupId,
                        icrLearningEnabled = snapshotIcrLearning
                    )
                )
                if (itemFoodId != null) repository.incrementFoodEntries(itemFoodId)
                if (itemFoodId != null && item.totalCarbs > 0f) {
                    val mealFireAt = snapshotDateTime.toInstant(TimeZone.currentSystemDefault()) + 240.minutes
                    val mealDelayMs = (mealFireAt - Clock.System.now()).inWholeMilliseconds.coerceAtLeast(0L)
                    WorkManager.getInstance(context).enqueue(
                        OneTimeWorkRequestBuilder<MealEventWorker>()
                            .setInitialDelay(mealDelayMs, TimeUnit.MILLISECONDS)
                            .setInputData(workDataOf(MealEventWorker.KEY_ENTRY_ID to itemEntryId))
                            .build()
                    )
                }
            }

            val entryId = snapshotEditingEntryId ?: UUID.randomUUID().toString()

            val resolvedInsulinType = if (snapshotInsulinUnits.isNotBlank()) InsulinType.BOLUS else null

            val entry = Entry(
                id = entryId,
                timestamp = snapshotDateTime,
                createdAt = snapshotEditingCreatedAt ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                description = snapshotDescription,
                foodId = foodId,
                quantity = snapshotQuantity.toFloatOrNull(),
                totalCarbs = snapshotTotalCarbs.toFloatOrNull(),
                carbConfidence = if (foodId != null) snapshotCarbConfidence else null,
                mealType = snapshotMealType,
                registrationMethod = RegistrationMethod.MANUAL,
                insulinUnits = snapshotInsulinUnits.toFloatOrNull(),
                insulinType = resolvedInsulinType,
                recommendedUnits = snapshotPrandialRec?.recommendedUnits,
                currentGlucose = snapshotCurrentGlucose,
                exerciseType = exerciseType,
                durationOfExercise = snapshotDuration.toFloatOrNull(),
                intensity = snapshotIntensity,
                recommendedCarbs = null,
                actualExerciseDrop = null,
                mealGroupId = mealGroupId,
                isfLearningEnabled = snapshotIsfLearning,
                icrLearningEnabled = snapshotIcrLearning
            )

            repository.upsertEntry(entry)
            if (foodId != null && snapshotEditingEntryId == null) repository.incrementFoodEntries(foodId)

            if (snapshotPrandialRec != null && snapshotPrandialRec.recommendedUnits != null) {
                val userAction = if (snapshotInsulinUnits.isNotBlank() && roundValue(snapshotPrandialRec.recommendedUnits) == snapshotInsulinUnits) "ACCEPTED" else "REJECTED"
                recommendationRepository.writeLog(RecommendationType.PRANDIAL, snapshotPrandialRec, entryId, userAction)
            }

            if (snapshotPreExerciseRec != null && snapshotPreExerciseRec.recommendedCarbs != null) {
                val userAction = if (snapshotTotalCarbs.isNotBlank() && snapshotPreExerciseRec.recommendedCarbs.toString() == snapshotTotalCarbs) "ACCEPTED" else "REJECTED"
                recommendationRepository.writeLog(RecommendationType.PRE_EXERCISE, snapshotPreExerciseRec, entryId, userAction)
            }
            if (entry.foodId != null && entry.totalCarbs != null  && entry.totalCarbs > 0 ) {
                val mealFireAt = entry.timestamp.toInstant(TimeZone.currentSystemDefault()) + 240.minutes
                val mealDelayMs = (mealFireAt - Clock.System.now()).inWholeMilliseconds.coerceAtLeast(0L)
                val request = OneTimeWorkRequestBuilder<MealEventWorker>()
                    .setInitialDelay(mealDelayMs, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(MealEventWorker.KEY_ENTRY_ID to entry.id))
                    .build()
                WorkManager.getInstance(context).enqueue(request)
            }
            if (entry.exerciseType != null && entry.durationOfExercise != null) {
                val exerciseFireAt = entry.timestamp.toInstant(TimeZone.currentSystemDefault()) +
                    (entry.durationOfExercise + 90f).toLong().minutes
                val exerciseDelayMs = (exerciseFireAt - Clock.System.now()).inWholeMilliseconds.coerceAtLeast(0L)
                val request = OneTimeWorkRequestBuilder<ExerciseEventWorker>()
                    .setInitialDelay(exerciseDelayMs, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(ExerciseEventWorker.KEY_ENTRY_ID to entry.id))
                    .build()
                WorkManager.getInstance(context).enqueue(request)
            }

            // Schedule ISF learning worker to fire 4 hours after the bolus,
            // so the full correction tail window (60-240 min) is available.
            if (snapshotIsfLearning) {
                val request = OneTimeWorkRequestBuilder<IsfLearningWorker>()
                    .setInitialDelay(240L, TimeUnit.MINUTES)
                    .setInputData(workDataOf(IsfLearningWorker.KEY_ENTRY_ID to entry.id))
                    .build()
                WorkManager.getInstance(context).enqueue(request)
            }

            _isSaved.value = true
        }
    }

    fun addCurrentItemToBasket() {
        val carbs = _totalCarbs.value.toFloatOrNull() ?: return
        if (carbs <= 0f) return
        val food = _selectedFood.value
        val name = food?.name ?: _foodName.value.ifBlank { _foodSearchQuery.value }.trim()
        _basketItems.value = _basketItems.value + BasketItem(
            tempId = UUID.randomUUID().toString(),
            foodId = food?.id,
            foodName = name,
            quantity = _quantity.value.toFloatOrNull(),
            totalCarbs = carbs,
            carbConfidence = _carbConfidence.value
        )
        // Reset food fields for the next item; keep insulin/meal type/timestamp
        _foodName.value = ""
        _foodSearchQuery.value = ""
        _foodSearchResults.value = emptyList()
        _selectedFood.value = null
        _quantitySuggestion.value = null
        _quantity.value = ""
        _quantityError.value = null
        _totalCarbs.value = ""
        _totalCarbsError.value = null
        effectiveCarbsPer100g = null
        carbsAutoFill = false
        _carbsAutoFilled.value = false
        _carbsRecommended.value = null
        _carbConfidence.value = CarbConfidence.HIGH
        onFoodFieldChanged()
    }

    fun removeBasketItem(tempId: String) {
        _basketItems.value = _basketItems.value.filter { it.tempId != tempId }
        onFoodFieldChanged()
    }

    fun onFoodFieldChanged() {
        updateIcrLearningPrompt()
        val currentCarbs = _totalCarbs.value.toFloatOrNull() ?: 0f
        val basketCarbs = _basketItems.value.sumOf { it.totalCarbs.toDouble() }.toFloat()
        val totalMealCarbs = currentCarbs + basketCarbs
        if (totalMealCarbs > 0f) {
            prandialJob?.cancel()
            prandialJob = viewModelScope.launch {
                runCatching {
                    recommendationRepository.computePrandial(
                        carbs = totalMealCarbs,
                        mealTime = _selectedDateTime.value
                    )
                }.onSuccess { result ->
                    _prandialRecommendation.value = result
                    _prandialAccepted.value = false
                }.onFailure {
                    // recommendation is a helper, not critical — silently ignore
                }
            }
        } else {
            prandialJob?.cancel()
            _prandialRecommendation.value = null
        }
    }

    fun onExerciseFieldChanged() {
        val duration = _durationOfExercise.value.toFloatOrNull()
        val exerciseType =
            _selectedExercise.value?.type ?: _exerciseName.value.takeIf { it.isNotBlank() }
        if (duration != null && duration > 0f && exerciseType != null) {

            viewModelScope.launch {
                runCatching {
                    recommendationRepository.computePreExercise(
                        exerciseType = exerciseType,
                        durationMin = duration,
                        exerciseTime = _selectedDateTime.value
                    )
                }.onSuccess { result ->
                    _preExerciseRecommendation.value = result
                    _preExerciseAccepted.value = false
                }.onFailure {
                    // recommendation is a helper, not critical — silently ignore
                }
            }

        } else {
            _preExerciseRecommendation.value = null
        }
    }

    fun roundValue(units: Float): String {
        val rounded = ((units * 2).roundToInt() / 2.0f)
        return if (rounded % 1f == 0f) "${rounded.toInt()}" else String.format(java.util.Locale.US, "%.1f", rounded)
    }
    fun onPrandialRecommendationAction() {
        val units = _prandialRecommendation.value!!.recommendedUnits ?: return
        _insulinUnits.value = roundValue(units)
        _insulinType.value = InsulinType.BOLUS
        _prandialAccepted.value = true
    }

    fun onPreExerciseRecommendationAction() {
        val carbs = _preExerciseRecommendation.value!!.recommendedCarbs ?: return
        _totalCarbs.value = carbs.toString()
        _preExerciseAccepted.value = true
    }

    private var prandialJob: Job? = null

    // When true, carbs field is driven by quantity × carbsPer100g; a manual carbs edit breaks this
    private var carbsAutoFill = false
    private val _carbsAutoFilled = MutableStateFlow(false)
    val carbsAutoFilled: StateFlow<Boolean> = _carbsAutoFilled.asStateFlow()

    // DB value takes priority; engine-resolved value used as fallback when carbsPer100g is null
    private var effectiveCarbsPer100g: Float? = null

    init {
        viewModelScope.launch {
            _foodSearchQuery
                .debounce(300)
                .collect { query ->
                    _foodSearchResults.value = if (query.isBlank()) emptyList()
                    else repository.searchFoods(query)
                }
        }
    }

    fun onFoodSearchQueryChanged(query: String) {
        _foodSearchQuery.value = query
        if (_selectedFood.value != null && query != _selectedFood.value?.name) {
            _selectedFood.value = null
        }
    }

    fun onFoodQueryChange(input: String) {
        _foodName.value = input
        _selectedFood.value = null
        effectiveCarbsPer100g = null
        carbsAutoFill = false
        _carbsAutoFilled.value = false
        _carbsRecommended.value = null
    }

    fun onFoodSelected(food: Food) {
        _foodName.value = food.name
        _foodSearchQuery.value = food.name
        _selectedFood.value = food
        effectiveCarbsPer100g = food.carbsPer100g
        carbsAutoFill = food.carbsPer100g != null
        _carbsRecommended.value = food.carbsRecommended
        _carbsAutoFilled.value = carbsAutoFill
        recomputeCarbsFromQuantity()
        viewModelScope.launch {
            val cases = repository.getFoodCases(food.id)
            _quantitySuggestion.value = if (cases.isNotEmpty()) {
                QuantitySuggestion(
                    grams = cases.map { it.quantityG }.average().toInt(),
                    fromNEvents = cases.size
                )
            } else null
            if (food.carbsPer100g == null) {
                val carbsPer100gFromCases = if (cases.isNotEmpty()) {
                    cases.map { it.inferredCarbsPer100g }.average().toFloat()
                } else null
                val resolvedCarbsPer100g = carbsPer100gFromCases
                    ?: food.posteriorMean?.takeIf { food.nObservations >= 1 }
                effectiveCarbsPer100g = resolvedCarbsPer100g
                carbsAutoFill = resolvedCarbsPer100g != null
                recomputeCarbsFromQuantity()
                if (_totalCarbs.value.isBlank() && carbsPer100gFromCases != null) {
                    val avgQty = cases.map { it.quantityG }.average().toFloat()
                    val estimatedTotal = carbsPer100gFromCases * avgQty / 100f
                    _totalCarbs.value = estimatedTotal.toInt().toString()
                    _carbsAutoFilled.value = true
                    _carbConfidence.value = CarbConfidence.LOW
                    onFoodFieldChanged()
                }
            }
        }
    }

    fun onExerciseQueryChange(input: String) {
        _exerciseName.value = input
        _selectedExercise.value = null
    }

    fun onExerciseSelected(exercise: Exercise) {
        _exerciseName.value = exercise.type
        _selectedExercise.value = exercise
        onExerciseFieldChanged()
    }

    fun onDescriptionChange(input: String) {
        _description.value = input
    }

    fun onDateTimeChange(dateTime: LocalDateTime) {
        _selectedDateTime.value = dateTime
        onExerciseFieldChanged()
        onFoodFieldChanged()
    }

    fun onQuantityChange(input: String) {
        val input = input.replace(',', '.')
        _quantity.value = input
        _quantityError.value = validateNonNegativeFloat(input)
        recomputeCarbsFromQuantity()
        _totalCarbsError.value = validateTotalCarbs(_totalCarbs.value, input)
    }

    private fun recomputeCarbsFromQuantity() {
        if (!carbsAutoFill) return
        val carbsPer100g = effectiveCarbsPer100g ?: return
        val grams = _quantity.value.toFloatOrNull() ?: return
        val computed = (grams * carbsPer100g / 100f)
        val formatted = if (computed % 1f == 0f) computed.toInt().toString() else String.format(java.util.Locale.US, "%.1f", computed)
        _totalCarbs.value = formatted
        onFoodFieldChanged()
    }

    fun onTotalCarbsChange(input: String) {
        val input = input.replace(',', '.')
        if (input.isNotBlank()) {
            carbsAutoFill = false
            _carbsAutoFilled.value = false  // user took manual control
        }
        _totalCarbs.value = input
        _totalCarbsError.value = validateTotalCarbs(input, _quantity.value)
        onFoodFieldChanged()
        val rec = _preExerciseRecommendation.value?.recommendedCarbs
        if (rec == null || rec.toString() != input) {
            _preExerciseAccepted.value = false
        }
    }

    fun onCarbConfidenceChange(input: CarbConfidence) {
        _carbConfidence.value = input
        updateIcrLearningPrompt()
        onFoodFieldChanged()
    }

    fun onMealTypeChange(input: String?) {
        _mealType.value = input
    }

    fun onInsulinUnitsChange(input: String) {
        val normalized = input.replace(',', '.')
        _insulinUnits.value = normalized
        _insulinUnitsError.value = validateNonNegativeFloat(input)
        if (input.isNotBlank() && _insulinType.value == null) {
            _insulinType.value = InsulinType.BOLUS
        }
        val rec = _prandialRecommendation.value?.recommendedUnits
        if (rec == null || roundValue(rec) != input) {
            _prandialAccepted.value = false
        }
    }

    fun onInsulinTypeChange(input: InsulinType?) {
        _insulinType.value = input
    }

    fun onDurationOfExerciseChange(input: String) {
        val input = input.replace(',', '.')
        _durationOfExercise.value = input
        _durationOfExerciseError.value = validateNonNegativeFloat(input)
        onExerciseFieldChanged()
    }

    fun onIntensityChange(input: ExerciseIntensity?) {
        _intensity.value = input
    }

    fun resetForm() {
        editingEntryId = null
        editingCreatedAt = null
        effectiveCarbsPer100g = null
        carbsAutoFill = false
        _carbsAutoFilled.value = false
        _carbsRecommended.value = null
        _description.value = ""
        _selectedDateTime.value = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        _foodName.value = ""
        _foodSearchQuery.value = ""
        _foodSearchResults.value = emptyList()
        _selectedFood.value = null
        _quantitySuggestion.value = null
        _quantity.value = ""
        _quantityError.value = null
        _totalCarbs.value = ""
        _totalCarbsError.value = null
        _carbConfidence.value = CarbConfidence.HIGH
        _mealType.value = null
        _insulinUnits.value = ""
        _insulinUnitsError.value = null
        _insulinType.value = null
        _exerciseName.value = ""
        _selectedExercise.value = null
        _durationOfExercise.value = ""
        _durationOfExerciseError.value = null
        _intensity.value = null
        _prandialRecommendation.value = null
        _prandialAccepted.value = false
        _preExerciseRecommendation.value = null
        _preExerciseAccepted.value = false
        _basketItems.value = emptyList()
        _isSaved.value = false
        _showIsfLearningPrompt.value = false
        _isfLearningEnabled.value = false
        _showIcrLearningPrompt.value = false
        _icrLearningEnabled.value = false
        _sleepEndHour.value = 7
        _sleepEndMinute.value = 0
        _sleepEligible.value = true
        _sleepWarning.value = null
    }

    private fun validateNonNegativeFloat(input: String): String? = when {
        input.isBlank() -> null
        input.toFloatOrNull() == null -> "Must be a number"
        input.toFloat() < 0f -> "Must be ≥ 0"
        else -> null
    }

    private fun validateTotalCarbs(carbsInput: String, quantityInput: String): String? {
        val base = validateNonNegativeFloat(carbsInput)
        if (base != null) return base
        val carbs = carbsInput.toFloatOrNull() ?: return null
        val grams = quantityInput.toFloatOrNull()
        if (grams != null && carbs > grams) return "Cannot exceed food weight (${grams.toInt()}g)"
        return null
    }
}
