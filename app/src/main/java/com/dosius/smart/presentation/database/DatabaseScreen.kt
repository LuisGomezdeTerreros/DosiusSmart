package com.dosius.smart.presentation.database

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dosius.smart.presentation.theme.DosiusPurple

enum class DatabaseTab { FOOD, EXERCISE }

@Composable
fun DatabaseScreen(
    onNavigateToAddFood: () -> Unit,
    onNavigateToAddExercise: () -> Unit = {},
    onNavigateToFoodDetail: (String) -> Unit = {},
    onNavigateToExerciseDetail: (String) -> Unit = {},
    initialTab: DatabaseTab = DatabaseTab.FOOD,
    foodViewModel: FoodDatabaseViewModel = hiltViewModel(),
    exerciseViewModel: ExerciseDatabaseViewModel = hiltViewModel()
) {
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }

    Scaffold(containerColor = Color(0xFFF7F6FB)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            when (selectedTab) {
                DatabaseTab.FOOD -> {
                    val foodUiState by foodViewModel.uiState.collectAsState()
                    FoodDatabaseContent(
                        uiState = foodUiState,
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        onSearchChange = foodViewModel::onSearchQueryChange,
                        onAddClick = onNavigateToAddFood,
                        onFoodDetailClick = onNavigateToFoodDetail
                    )
                }
                DatabaseTab.EXERCISE -> {
                    val exerciseUiState by exerciseViewModel.uiState.collectAsState()
                    ExerciseDatabaseContent(
                        uiState = exerciseUiState,
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        onSearchChange = exerciseViewModel::onSearchQueryChange,
                        onAddClick = onNavigateToAddExercise,
                        onExerciseDetailClick = onNavigateToExerciseDetail
                    )
                }
            }
        }
    }
}

@Composable
private fun DatabaseTabToggle(
    selectedTab: DatabaseTab,
    onTabSelected: (DatabaseTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DatabaseTab.entries.forEach { tab ->
            val isSelected = selectedTab == tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Color.White else Color.Transparent)
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) DosiusPurple else Color.Gray
                )
            }
        }
    }
}

@Composable
private fun FoodDatabaseContent(
    uiState: FoodDatabaseUiState,
    selectedTab: DatabaseTab,
    onTabSelected: (DatabaseTab) -> Unit,
    onSearchChange: (String) -> Unit,
    onAddClick: () -> Unit,
    onFoodDetailClick: (String) -> Unit
) {
    when (val state = uiState) {
        is FoodDatabaseUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is FoodDatabaseUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
        }
        is FoodDatabaseUiState.Success -> {
            Column {
                HeaderSection(
                    search = state.search,
                    onSearchChange = onSearchChange,
                    onAddClick = onAddClick,
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    totalNumber = state.totalNumberFood,
                    searchPlaceholder = "Search foods"
                )

                val allFoods = state.foodData.flatMap { it.foods }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allFoods, key = { it.food.id }) { foodWithStats ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            FoodItemRow(
                                item = foodWithStats,
                                onClick = { onFoodDetailClick(foodWithStats.food.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseDatabaseContent(
    uiState: ExerciseDatabaseUiState,
    selectedTab: DatabaseTab,
    onTabSelected: (DatabaseTab) -> Unit,
    onSearchChange: (String) -> Unit,
    onAddClick: () -> Unit,
    onExerciseDetailClick: (String) -> Unit = {}
) {
    when (val state = uiState) {
        is ExerciseDatabaseUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ExerciseDatabaseUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
        }
        is ExerciseDatabaseUiState.Success -> {
            Column {
                HeaderSection(
                    search = state.search,
                    onSearchChange = onSearchChange,
                    onAddClick = onAddClick,
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    totalNumber = state.totalNumberExercise,
                    searchPlaceholder = "Search exercises"
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.exerciseData) { exerciseDetails ->
                        ExerciseItemCard(
                            item = exerciseDetails,
                            onClick = { onExerciseDetailClick(exerciseDetails.exercise.type) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderSection(
    search: String,
    onSearchChange: (String) -> Unit,
    onAddClick: () -> Unit,
    selectedTab: DatabaseTab,
    onTabSelected: (DatabaseTab) -> Unit,
    totalNumber: Int,
    searchPlaceholder: String,
    content: @Composable (ColumnScope.() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            DatabaseTabToggle(selectedTab = selectedTab, onTabSelected = onTabSelected)

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicTextField(
                    value = search,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(10.dp)),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.Black, fontSize = 15.sp
                    ),
                    cursorBrush = SolidColor(Color.Black),
                    decorationBox = { innerTextField ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (search.isEmpty()) {
                                    Text(
                                        searchPlaceholder,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray,
                                        maxLines = 1
                                    )
                                }
                                innerTextField()
                            }
                        }
                    }
                )

                FilledTonalButton(
                    onClick = onAddClick,
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = DosiusPurple.copy(alpha = 0.12f),
                        contentColor = DosiusPurple
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "New",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (content != null) {
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "$totalNumber results",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = Color.DarkGray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun ExerciseItemCard(item: ExerciseDetails, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(DosiusPurple)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Text(
                    text = item.exercise.type,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Bold
                )
                val durationText = item.avgDurationMinutes?.let { " · ~${it} min avg" } ?: ""
                Text(
                    text = "-${item.exercise.expectedGlucoseDropPerHour} mg/dL per hour · ${item.numberEntries} entries$durationText",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun CategoryGroupCard(
    group: FoodCategoryGroup,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onFoodClick: (String) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandClick() }
                .height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(DosiusPurple)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = group.categoryId,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${group.foods.size} foods · ${group.numberEvents} entries",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isExpanded) {
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    group.foods.forEach { foodWithStats ->
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 1.dp,
                            color = Color(0xFFE0E0E0)
                        )
                        FoodItemRow(item = foodWithStats, onClick = { onFoodClick(foodWithStats.food.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FoodItemRow(item: FoodWithStats, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .width(2.5.dp)
                .fillMaxHeight()
                .background(DosiusPurple.copy(alpha = 0.4f), shape = RoundedCornerShape(2.dp))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = item.food.name,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val carbsText = item.food.carbsPer100g?.let { "${it.toInt()}g carbs" } ?: "unknown carbs"
                val giValue = item.food.glycemicIndex?.toInt()

                Text(
                    text = "$carbsText · IG ",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                if (giValue != null) {
                    Text(
                        text = giValue.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = getGIColor(giValue),
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(text = "--", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Text(
                    text = " · ${item.numberEvents} entries",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                item.avgQuantityGrams?.let { avg ->
                    Text(
                        text = " · ~${avg}g avg",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

private fun getGIColor(gi: Int): Color {
    return when {
        gi <= 55 -> Color(0xFF4CAF50)
        gi <= 69 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
}
