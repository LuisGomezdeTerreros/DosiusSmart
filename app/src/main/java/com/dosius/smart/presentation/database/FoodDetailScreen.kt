package com.dosius.smart.presentation.database

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dosius.smart.domain.model.Entry
import com.dosius.smart.domain.model.FoodCase
import com.dosius.smart.domain.model.RegistrationMethod
import com.dosius.smart.presentation.theme.DosiusPurple
import com.dosius.smart.presentation.theme.LightBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: FoodDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isEditing by viewModel.isEditing.collectAsState()
    val name by viewModel.name.collectAsState()
    val category by viewModel.category.collectAsState()
    val carbsPer100g by viewModel.carbsPer100g.collectAsState()
    val carbsPer100gError by viewModel.carbsPer100gError.collectAsState()
    val glycemicIndex by viewModel.glycemicIndex.collectAsState()
    val glycemicIndexError by viewModel.glycemicIndexError.collectAsState()
    val isIngredient by viewModel.isIngredient.collectAsState()
    val categories by viewModel.categories.collectAsState()

    val isSaveEnabled = name.isNotBlank() && category.isNotBlank() &&
        carbsPer100gError == null &&
        glycemicIndexError == null

    Scaffold(
        containerColor = LightBackground,
        topBar = {
            TopAppBar(
                title = {
                    val titleText = when (val s = uiState) {
                        is FoodDetailUiState.Success -> if (isEditing) "Edit Food" else s.food.name
                        else -> "Food Detail"
                    }
                    Text(text = titleText, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = if (isEditing) viewModel::onEditToggle else onNavigateBack) {
                        Icon(
                            imageVector = if (isEditing) Icons.Default.Close else Icons.Default.ArrowBack,
                            contentDescription = if (isEditing) "Cancel" else "Back"
                        )
                    }
                },
                actions = {
                    if (isEditing) {
                        TextButton(onClick = viewModel::saveChanges, enabled = isSaveEnabled) {
                            Text(
                                "Save",
                                color = if (isSaveEnabled) DosiusPurple else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    } else if (uiState is FoodDetailUiState.Success) {
                        IconButton(onClick = viewModel::onEditToggle) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = DosiusPurple)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black
                )
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is FoodDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DosiusPurple)
                }
            }

            is FoodDetailUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is FoodDetailUiState.Success -> {
                val sortedEvents = state.events.sortedByDescending { it.timestamp }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    item {
                        AnimatedVisibility(visible = !isEditing, enter = expandVertically(), exit = shrinkVertically()) {
                            FoodHeroCard(state = state)
                        }
                    }

                    item {
                        AnimatedVisibility(visible = isEditing, enter = expandVertically(), exit = shrinkVertically()) {
                            FoodEditForm(
                                name = name,
                                category = category,
                                categories = categories,
                                carbsPer100g = carbsPer100g,
                                carbsPer100gError = carbsPer100gError,
                                glycemicIndex = glycemicIndex,
                                glycemicIndexError = glycemicIndexError,
                                isIngredient = isIngredient,
                                onNameChange = viewModel::onNameChange,
                                onCategoryChange = viewModel::onCategoryChange,
                                onCarbsPer100gChange = viewModel::onCarbsPer100gChange,
                                onGlycemicIndexChange = viewModel::onGlycemicIndexChange,
                                onIsIngredientChange = viewModel::onIsIngredientChange
                            )
                        }
                    }

                    if (!isEditing && sortedEvents.isNotEmpty()) {
                        item {
                            FoodStatsCard(state = state)
                        }
                    }

                    if (!isEditing) {
                        item {
                            FoodLearningCard(state = state)
                        }
                    }

                    // Event history header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Event history",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(color = DosiusPurple.copy(alpha = 0.1f), shape = CircleShape) {
                                Text(
                                    text = "${sortedEvents.size}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = DosiusPurple
                                )
                            }
                        }
                    }

                    // Empty state
                    if (sortedEvents.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        tint = Color.LightGray,
                                        modifier = Modifier.size(52.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No events logged yet", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    // Timeline events
                    items(sortedEvents.size) { index ->
                        EventTimelineItem(
                            event = sortedEvents[index],
                            isLast = index == sortedEvents.size - 1
                        )
                    }
                }
            }
        }
    }
}

// ── Hero card ────────────────────────────────────────────────────────────────

@Composable
private fun FoodHeroCard(state: FoodDetailUiState.Success) {
    val food = state.food
    val confidence = if (food.nObservations > 0)
        minOf(100f, 30f + food.nObservations * 10f) else null
    val confidenceColor = if (confidence != null) confidenceColor(confidence) else Color.Gray
    val giValue = food.glycemicIndex?.toInt()

    Surface(modifier = Modifier.fillMaxWidth(), color = Color.White, shadowElevation = 2.dp) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Type badge
                    Surface(
                        color = if (food.isIngredient) Color(0xFFE8F5E9) else Color(0xFFEDE7F6),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (food.isIngredient) Icons.Default.Grass else Icons.Default.Fastfood,
                                contentDescription = null,
                                tint = if (food.isIngredient) Color(0xFF388E3C) else DosiusPurple,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (food.isIngredient) "Ingredient" else "Meal",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (food.isIngredient) Color(0xFF388E3C) else DosiusPurple
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = food.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = food.idCategory, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }

                // Confidence ring
                if (confidence != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { confidence / 100f },
                                modifier = Modifier.size(64.dp),
                                color = confidenceColor,
                                trackColor = confidenceColor.copy(alpha = 0.12f),
                                strokeWidth = 5.dp
                            )
                            Text(
                                text = "${confidence.toInt()}%",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = confidenceColor
                            )
                        }
                        Text("confidence", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFF0F0F0))
            Spacer(modifier = Modifier.height(16.dp))

            // Nutritional quick facts
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NutrientChip(
                    label = "Carbs/100g",
                    value = food.carbsPer100g?.let { "${it.toInt()}g" } ?: "—",
                    color = Color(0xFF1976D2),
                    modifier = Modifier.weight(1f)
                )
                NutrientChip(
                    label = "Glyc. Index",
                    value = giValue?.toString() ?: "—",
                    color = if (giValue != null) giColor(giValue) else Color.Gray,
                    modifier = Modifier.weight(1f)
                )
                NutrientChip(
                    label = "Logged",
                    value = "${state.events.size}×",
                    color = DosiusPurple,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NutrientChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = color.copy(alpha = 0.08f), shape = RoundedCornerShape(10.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = color)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

// ── Stats card ───────────────────────────────────────────────────────────────

@Composable
private fun FoodStatsCard(state: FoodDetailUiState.Success) {
    val avgCarbs = state.events.mapNotNull { it.totalCarbs }.average().takeIf { !it.isNaN() }
    val manualCount = state.events.count { it.registrationMethod == RegistrationMethod.MANUAL }
    val chatCount = state.events.count { it.registrationMethod == RegistrationMethod.CHAT }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Usage statistics", style = MaterialTheme.typography.labelLarge, color = Color.Gray, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatChip(
                    label = "Avg carbs",
                    value = avgCarbs?.let { "${it.toInt()}g" } ?: "—",
                    icon = Icons.Default.Star,
                    color = Color(0xFFFF6D00),
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    label = "Manual / AI",
                    value = "$manualCount / $chatCount",
                    icon = Icons.Default.Edit,
                    color = DosiusPurple,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = color.copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Column {
                Text(text = value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

// ── Learning card ─────────────────────────────────────────────────────────────

@Composable
private fun FoodLearningCard(state: FoodDetailUiState.Success) {
    val stats = state.stats
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Learning data", style = MaterialTheme.typography.labelLarge, color = Color.Gray, fontWeight = FontWeight.SemiBold)

            // Carbs per 100g comparison
            if (stats.seededDefault != null || stats.posteriorMean != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Carbs per 100g", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    if (stats.seededDefault != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Seeded default", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text("${stats.seededDefault.toInt()} g/100g", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (stats.posteriorMean != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Your data", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            val stdStr = if (stats.posteriorStd != null) " ± ${stats.posteriorStd.toInt()}" else ""
                            val nStr = if (state.food.nObservations > 0) "  (${state.food.nObservations} events)" else ""
                            Text(
                                "${stats.posteriorMean.toInt()}$stdStr g/100g$nStr",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFFF0F0F0))
            }

            // Typical quantity
            if (stats.averageQuantityG != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Average portion", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text("~${stats.averageQuantityG.toInt()}g  (from ${stats.nCases} events)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider(color = Color(0xFFF0F0F0))
            }

            // Past FoodCase list
            Text("Past cases", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            if (stats.recentCases.isEmpty()) {
                Text(
                    "No learning data yet. Events will appear after your first logged meal with this food.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            } else {
                stats.recentCases.forEach { case ->
                    FoodCaseRow(case = case)
                }
            }
        }
    }
}

@Composable
private fun FoodCaseRow(case: FoodCase) {
    val timeBucket = when (case.timeOfDayBucket) {
        0 -> "Night"
        1 -> "Morning"
        2 -> "Afternoon"
        else -> "Evening"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(timeBucket, style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.width(64.dp))
        Text("${case.quantityG.toInt()}g", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(48.dp))
        Text("entered ${case.enteredCarbsPer100g.toInt()}g/100g", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text("inferred ${case.inferredCarbsPer100g.toInt()}g/100g", style = MaterialTheme.typography.labelSmall, color = DosiusPurple)
    }
}

// ── Timeline event item ───────────────────────────────────────────────────────

@Composable
private fun EventTimelineItem(event: Entry, isLast: Boolean) {
    val confidenceColor = event.carbConfidence?.let { confidenceColorForEnum(it) } ?: Color.Gray

    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp)) {
        // Timeline spine
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(20.dp)) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(DosiusPurple))
            if (!isLast) {
                Box(modifier = Modifier.width(2.dp).height(88.dp).background(Color(0xFFE0E0E0)))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = if (isLast) 0.dp else 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Top row: date + method badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = formatDate(event.timestamp), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Surface(
                        color = if (event.registrationMethod == RegistrationMethod.CHAT)
                            DosiusPurple.copy(alpha = 0.1f) else Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (event.registrationMethod == RegistrationMethod.CHAT) "AI" else "Manual",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (event.registrationMethod == RegistrationMethod.CHAT) DosiusPurple else Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom row: stats + confidence badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (event.quantity != null) {
                            EventStat(label = "Qty", value = "${event.quantity.toInt()}g")
                        }
                        if (event.totalCarbs != null) {
                            EventStat(label = "Carbs", value = "${event.totalCarbs.toInt()}g", valueColor = Color(0xFFFF6D00))
                        }
                        if (event.mealType != null) {
                            EventStat(label = "Type", value = event.mealType)
                        }
                    }

                    if (event.carbConfidence != null) {
                        val label = when (event.carbConfidence) {
                            com.dosius.smart.domain.model.CarbConfidence.CERTAIN -> "Measured"
                            com.dosius.smart.domain.model.CarbConfidence.HIGH    -> "Pretty sure"
                            com.dosius.smart.domain.model.CarbConfidence.MEDIUM  -> "Roughly"
                            com.dosius.smart.domain.model.CarbConfidence.LOW     -> "Guessing"
                        }
                        Surface(color = confidenceColor.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = confidenceColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventStat(label: String, value: String, valueColor: Color = Color.Black) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

// ── Edit form ─────────────────────────────────────────────────────────────────

@Composable
private fun FoodEditForm(
    name: String, category: String, categories: List<String>,
    carbsPer100g: String, carbsPer100gError: String?,
    glycemicIndex: String, glycemicIndexError: String?,
    isIngredient: Boolean,
    onNameChange: (String) -> Unit, onCategoryChange: (String) -> Unit,
    onCarbsPer100gChange: (String) -> Unit, onGlycemicIndexChange: (String) -> Unit,
    onIsIngredientChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Type", style = MaterialTheme.typography.labelLarge, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailTypeChip(label = "Meal", icon = Icons.Default.Fastfood, selected = !isIngredient, onClick = { onIsIngredientChange(false) }, modifier = Modifier.weight(1f))
                    DetailTypeChip(label = "Ingredient", icon = Icons.Default.Grass, selected = isIngredient, onClick = { onIsIngredientChange(true) }, modifier = Modifier.weight(1f))
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Basic info", style = MaterialTheme.typography.labelLarge, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                DetailFormField(value = name, onValueChange = onNameChange, label = "Name", placeholder = "Food name")
                DetailCategoryAutocompleteField(value = category, onValueChange = onCategoryChange, suggestions = categories)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Nutritional info", style = MaterialTheme.typography.labelLarge, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                DetailFormField(value = carbsPer100g, onValueChange = onCarbsPer100gChange, label = "Carbs per 100g", placeholder = "e.g. 45.0", suffix = "g", keyboardType = KeyboardType.Decimal, errorMessage = carbsPer100gError)
                DetailFormField(value = glycemicIndex, onValueChange = onGlycemicIndexChange, label = "Glycemic index", placeholder = "0 – 100", keyboardType = KeyboardType.Decimal, errorMessage = glycemicIndexError)
            }
        }
    }
}

@Composable
private fun DetailTypeChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, onClick = onClick, shape = RoundedCornerShape(12.dp), color = if (selected) DosiusPurple else Color(0xFFF5F5F5)) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = if (selected) Color.White else Color.Gray, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Color.Gray, fontSize = 14.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailCategoryAutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>
) {
    val filtered = remember(value, suggestions) {
        if (value.isBlank()) suggestions
        else suggestions.filter { it.contains(value, ignoreCase = true) }
    }
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && filtered.isNotEmpty(),
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text("Category") },
            placeholder = { Text("e.g. Proteins", color = Color.LightGray) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable),
            singleLine = true,
            trailingIcon = {
                if (filtered.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && filtered.isNotEmpty())
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DosiusPurple,
                focusedLabelColor = DosiusPurple,
                cursorColor = DosiusPurple
            )
        )
        ExposedDropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            filtered.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category) },
                    onClick = {
                        onValueChange(category)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DetailFormField(
    value: String, onValueChange: (String) -> Unit, label: String,
    placeholder: String, suffix: String? = null, keyboardType: KeyboardType = KeyboardType.Text,
    errorMessage: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            val accept = when (keyboardType) {
                KeyboardType.Number -> new.all { it.isDigit() }
                KeyboardType.Decimal -> new.matches(Regex("^\\d*\\.?\\d*$"))
                else -> true
            }
            if (accept) onValueChange(new)
        },
        label = { Text(label) },
        placeholder = { Text(placeholder, color = Color.LightGray) },
        suffix = suffix?.let { { Text(it, color = Color.Gray) } },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = errorMessage != null,
        supportingText = errorMessage?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DosiusPurple,
            focusedLabelColor = DosiusPurple,
            cursorColor = DosiusPurple
        )
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatDate(timestamp: kotlinx.datetime.LocalDateTime): String {
    val day = timestamp.dayOfMonth.toString().padStart(2, '0')
    val month = timestamp.monthNumber.toString().padStart(2, '0')
    val hour = timestamp.hour.toString().padStart(2, '0')
    val minute = timestamp.minute.toString().padStart(2, '0')
    return "$day/$month · $hour:$minute"
}

private fun confidenceColor(confidence: Float): Color = when {
    confidence >= 80f -> Color(0xFF4CAF50)
    confidence >= 70f -> Color(0xFF8BC34A)
    confidence >= 60f -> Color(0xFFFFD600)
    confidence >= 50f -> Color(0xFFFFB300)
    confidence >= 40f -> Color(0xFFFF9800)
    else -> Color(0xFFF44336)
}

private fun confidenceColorForEnum(confidence: com.dosius.smart.domain.model.CarbConfidence): Color = when (confidence) {
    com.dosius.smart.domain.model.CarbConfidence.CERTAIN -> Color(0xFF4CAF50)
    com.dosius.smart.domain.model.CarbConfidence.HIGH    -> Color(0xFF8BC34A)
    com.dosius.smart.domain.model.CarbConfidence.MEDIUM  -> Color(0xFFFFB300)
    com.dosius.smart.domain.model.CarbConfidence.LOW     -> Color(0xFFF44336)
}

private fun giColor(gi: Int): Color = when {
    gi <= 55 -> Color(0xFF4CAF50)
    gi <= 69 -> Color(0xFFFF9800)
    else -> Color(0xFFF44336)
}
