package com.dosius.smart.presentation.database

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosius.smart.data.repository.EntryRepository
import com.dosius.smart.domain.model.Food
import com.dosius.smart.domain.model.FoodCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import javax.inject.Inject

@HiltViewModel
class FoodDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: EntryRepository,
) : ViewModel() {

    private val foodId: String = savedStateHandle["foodId"] ?: ""

    private val _uiState = MutableStateFlow<FoodDetailUiState>(FoodDetailUiState.Loading)
    val uiState: StateFlow<FoodDetailUiState> = _uiState.asStateFlow()

    private val _name = MutableStateFlow<String>("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _category = MutableStateFlow<String>("")
    val category: StateFlow<String> = _category.asStateFlow()

    private val _carbsPer100g = MutableStateFlow<String>("")
    val carbsPer100g: StateFlow<String> = _carbsPer100g.asStateFlow()

    private val _carbsPer100gError = MutableStateFlow<String?>(null)
    val carbsPer100gError: StateFlow<String?> = _carbsPer100gError.asStateFlow()

    private val _glycemicIndex = MutableStateFlow<String>("")
    val glycemicIndex: StateFlow<String> = _glycemicIndex.asStateFlow()

    private val _glycemicIndexError = MutableStateFlow<String?>(null)
    val glycemicIndexError: StateFlow<String?> = _glycemicIndexError.asStateFlow()

    private val _isIngredient = MutableStateFlow<Boolean>(false)
    val isIngredient: StateFlow<Boolean> = _isIngredient.asStateFlow()

    private val _isEditing = MutableStateFlow<Boolean>(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    val categories: StateFlow<List<String>> = repository.getDistinctCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<String>())


    private val _foodCases = MutableStateFlow<List<FoodCase>>(emptyList())

    init {
        viewModelScope.launch {
            _foodCases.value = repository.getFoodCases(foodId)
        }
        viewModelScope.launch {
            combine(
                repository.getFoodById(foodId),
                repository.getEntriesByFoodId(foodId),
                _foodCases
            ) { food, events, cases ->
                if (food == null) FoodDetailUiState.Error("Food not found")
                else FoodDetailUiState.Success(food = food, events = events, stats = buildStats(food, cases))
            }.collect { result ->
                _uiState.value = result
            }
        }
    }

    private fun buildStats(food: Food, cases: List<FoodCase>): FoodStats {
        val avgQty = if (cases.isEmpty()) null else cases.map { it.quantityG }.average().toFloat()
        val posteriorStd = food.posteriorVariance?.let { sqrt(it.toDouble()).toFloat() }
        return FoodStats(
            nCases = cases.size,
            averageQuantityG = avgQty,
            posteriorMean = food.posteriorMean,
            posteriorStd = posteriorStd,
            seededDefault = food.carbsPer100g,
            glycemicSuccessRate = null,
            recentCases = cases.take(10)
        )
    }

    fun saveChanges() {
        val current = (_uiState.value as? FoodDetailUiState.Success)?.food ?: return
        val updated = current.copy(
            name = _name.value,
            idCategory = _category.value,
            carbsPer100g = _carbsPer100g.value.toFloatOrNull(),
            glycemicIndex = _glycemicIndex.value.toFloatOrNull(),
            isIngredient = _isIngredient.value,
        )
        viewModelScope.launch {
            repository.upsertFood(updated)
            _isEditing.value = false
        }

    }

    fun onNameChange(input: String) { _name.value = input }
    fun onCategoryChange(input: String) { _category.value = input }

    fun onCarbsPer100gChange(input: String) {
        _carbsPer100g.value = input
        _carbsPer100gError.value = when {
            input.isBlank() -> null
            input.toFloatOrNull() == null -> "Must be a number"
            input.toFloat() !in 0f..100f -> "Must be 0–100"
            else -> null
        }
    }

    fun onGlycemicIndexChange(input: String) {
        _glycemicIndex.value = input
        _glycemicIndexError.value = when {
            input.isBlank() -> null
            input.toFloatOrNull() == null -> "Must be a number"
            input.toFloat() < 0f -> "Must be ≥ 0"
            else -> null
        }
    }

    fun onIsIngredientChange(input: Boolean) { _isIngredient.value = input }

    fun onEditToggle() {
        if (!_isEditing.value) {
            val food = (_uiState.value as?
                    FoodDetailUiState.Success)?.food ?: return

            _name.value = food.name
            _category.value = food.idCategory
            _carbsPer100g.value = food.carbsPer100g?.toString() ?: ""
            _glycemicIndex.value = food.glycemicIndex?.toString() ?: ""
            _isIngredient.value = food.isIngredient
            _carbsPer100gError.value = null
            _glycemicIndexError.value = null

        }
        _isEditing.value = !_isEditing.value
    }


}