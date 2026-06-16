package com.dosius.smart.presentation.database

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosius.smart.data.repository.EntryRepository
import com.dosius.smart.domain.model.Food
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddFoodViewModel @Inject constructor(
    private val repository: EntryRepository,

) : ViewModel() {

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

    private val _isSaved = MutableStateFlow<Boolean>(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    val categories: StateFlow<List<String>> = repository.getDistinctCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<String>())



    fun saveFood() {
        val food = Food(
            id = java.util.UUID.randomUUID().toString(),
            name = _name.value,
            idCategory = _category.value,
            carbsPer100g = _carbsPer100g.value.toFloatOrNull(),
            carbsRecommended = null,
            glycemicIndex = _glycemicIndex.value.toFloatOrNull(),
            isCustom = true,
            isIngredient = _isIngredient.value,
            ingredients = emptyList(),
            entries = 0
        )
        viewModelScope.launch {
            repository.upsertFood(food)
            _isSaved.value = true
        }

    }

    fun onNameChange(input: String) {
        _name.value = input
    }

    fun onCategoryChange(input: String) {
        _category.value = input
    }

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

    fun onIsIngredientChange(input: Boolean) {
        _isIngredient.value = input
    }


}