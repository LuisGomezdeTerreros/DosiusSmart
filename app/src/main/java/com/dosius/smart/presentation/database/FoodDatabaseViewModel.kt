package com.dosius.smart.presentation.database

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosius.smart.data.repository.DatabaseSeeder
import com.dosius.smart.data.repository.EntryRepository
import com.dosius.smart.domain.model.Entry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FoodDatabaseViewModel @Inject constructor(
    private val repository: EntryRepository, private val seeder: DatabaseSeeder,
) : ViewModel() {

    private val _uiState = MutableStateFlow<FoodDatabaseUiState>(FoodDatabaseUiState.Loading)
    val uiState: StateFlow<FoodDatabaseUiState> = _uiState.asStateFlow()

    private val _search = MutableStateFlow("")

    init {
        viewModelScope.launch {
            seeder.seedIfEmpty()
            val casesByFoodId = repository.getAllFoodCases().groupBy { it.foodId }
            combine(
                _search, repository.getAllEntries(), repository.getAllFoods()
            ) { search, events, foods ->

                val eventsByfoodId = events.groupBy { it.foodId }

                var foodsWithStats = foods.map { food ->
                    val foodEvents = eventsByfoodId.getOrDefault(food.id, emptyList<Entry>())
                    val cases = casesByFoodId[food.id]
                    val avgQty = cases?.takeIf { it.isNotEmpty() }?.map { it.quantityG }?.average()?.toInt()
                    FoodWithStats(
                        food = food, numberEvents = foodEvents.size, avgQuantityGrams = avgQty
                    )
                }
                if (search != "") {
                    foodsWithStats = foodsWithStats.filter {
                        it.food.name.contains(
                            search, ignoreCase = true
                        )
                    }
                }
                val totalNumberFood = foodsWithStats.size
                val foodsWithStatsbyidCategory = foodsWithStats.groupBy { it.food.idCategory }

                val foodGroupedByCategory =
                    foodsWithStatsbyidCategory.map { (categoryId, foodsWithStats) ->
                        val numberEvents = foodsWithStats.sumOf { food -> food.numberEvents }
                        FoodCategoryGroup(
                            categoryId = categoryId,
                            foods = foodsWithStats,
                            numberEvents = numberEvents
                        )
                    }

                FoodDatabaseUiState.Success(
                    foodData = foodGroupedByCategory,
                    search = search,
                    totalNumberFood = totalNumberFood
                )

            }.collect { result ->
                _uiState.value = result
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _search.value = query
    }
}