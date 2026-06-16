package com.dosius.smart.presentation.entry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dosius.smart.domain.engine.recommendation.RecommendationResult
import com.dosius.smart.domain.model.CarbConfidence
import com.dosius.smart.domain.model.Exercise
import com.dosius.smart.domain.model.ExerciseIntensity
import com.dosius.smart.domain.model.Food
import com.dosius.smart.presentation.theme.DosiusPurple
import com.dosius.smart.presentation.theme.DosiusPurpleContainer
import com.dosius.smart.presentation.theme.OnLight
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

private val ExerciseBlue       = Color(0xFF42A5F5)
private val ExerciseContainer  = Color(0xFFBBDEFB)
private val FoodOrange         = Color(0xFFBF360C)
private val FoodContainer      = Color(0xFFFFCCBC)
private val FastCarbsAmber     = Color(0xFFE65100)
private val FastCarbsContainer = Color(0xFFFFE082)
private val SleepIndigo        = Color(0xFF3949AB)
private val SleepContainer     = Color(0xFFC5CAE9)

// ─── Sheet root ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntrySheet(
    onEntryTypeSelected: (QuickEntryType) -> Unit,
    onDismiss: () -> Unit,
    editEntryId: String? = null,
    editEntryType: QuickEntryType? = null,
    viewModel: AddEntryViewModel = hiltViewModel()
) {
    var activeType by remember { mutableStateOf(editEntryType) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(editEntryId) {
        if (editEntryId != null) viewModel.loadEntry(editEntryId)
        else viewModel.resetTime()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        when (activeType) {
            QuickEntryType.BOLUS -> BolusEntryContent(
                onBack = { if (editEntryId != null) onDismiss() else { viewModel.resetForm(); activeType = null } },
                onSave = { onEntryTypeSelected(QuickEntryType.BOLUS) },
                viewModel = viewModel
            )
            QuickEntryType.EXERCISE -> ExerciseEntryContent(
                onBack = { if (editEntryId != null) onDismiss() else { viewModel.resetForm(); activeType = null } },
                onSave = { onEntryTypeSelected(QuickEntryType.EXERCISE) },
                viewModel = viewModel
            )
            QuickEntryType.FOOD -> FoodEntryContent(
                onBack = { if (editEntryId != null) onDismiss() else { viewModel.resetForm(); activeType = null } },
                onSave = { onEntryTypeSelected(QuickEntryType.FOOD) },
                viewModel = viewModel
            )
            QuickEntryType.FAST_CARBS -> FastCarbsEntryContent(
                onBack = { if (editEntryId != null) onDismiss() else { viewModel.resetForm(); activeType = null } },
                onSave = { onEntryTypeSelected(QuickEntryType.FAST_CARBS) },
                viewModel = viewModel
            )
            QuickEntryType.SLEEP -> SleepEntryContent(
                onBack = { if (editEntryId != null) onDismiss() else { viewModel.resetForm(); activeType = null } },
                onSave = { onEntryTypeSelected(QuickEntryType.SLEEP) },
                viewModel = viewModel
            )
            else -> EntryTypeSelectionContent(
                onTypeSelected = { type ->
                    when (type) {
                        QuickEntryType.BOLUS      -> activeType = QuickEntryType.BOLUS
                        QuickEntryType.EXERCISE   -> activeType = QuickEntryType.EXERCISE
                        QuickEntryType.FOOD       -> activeType = QuickEntryType.FOOD
                        QuickEntryType.FAST_CARBS -> activeType = QuickEntryType.FAST_CARBS
                        QuickEntryType.SLEEP      -> activeType = QuickEntryType.SLEEP
                        else                      -> onEntryTypeSelected(type)
                    }
                }
            )
        }
    }
}

// ─── Bolus form ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BolusEntryContent(
    onBack: () -> Unit,
    onSave: () -> Unit,
    viewModel: AddEntryViewModel
) {
    val insulinUnits by viewModel.insulinUnits.collectAsState()
    val notes by viewModel.description.collectAsState()
    val selectedDateTime by viewModel.selectedDateTime.collectAsState()
    val showIsfPrompt by viewModel.showIsfLearningPrompt.collectAsState()
    val isfLearningEnabled by viewModel.isfLearningEnabled.collectAsState()

    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var hour   by remember { mutableStateOf(selectedDateTime.hour) }
    var minute by remember { mutableStateOf(selectedDateTime.minute) }

    // Check eligibility once when the bolus form opens
    LaunchedEffect(Unit) { viewModel.checkIsfLearningEligibility() }

    LaunchedEffect(selectedDateTime) {
        hour = selectedDateTime.hour
        minute = selectedDateTime.minute
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SheetHeader("Bolus insulin", selectedDateTime.date, hour, minute,
            onDateClick = { showDatePicker = true },
            onTimeClick = { showTimePicker = true }
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = DosiusPurpleContainer
        ) {
            Column(
                modifier = Modifier.padding(vertical = 24.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (insulinUnits.isBlank()) "0" else formatDose(insulinUnits.toFloatOrNull() ?: 0f),
                        fontSize = 72.sp, fontWeight = FontWeight.Bold,
                        color = DosiusPurple, lineHeight = 72.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "u", style = MaterialTheme.typography.titleLarge,
                        color = DosiusPurple.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepperButton("−1",   Modifier.weight(1f), false, DosiusPurple) {
                        val current = insulinUnits.toFloatOrNull() ?: 0f
                        viewModel.onInsulinUnitsChange(viewModel.roundValue(maxOf(0f, current - 1f)))
                    }
                    StepperButton("−0.5", Modifier.weight(1f), false, DosiusPurple) {
                        val current = insulinUnits.toFloatOrNull() ?: 0f
                        viewModel.onInsulinUnitsChange(viewModel.roundValue(maxOf(0f, current - 0.5f)))
                    }
                    StepperButton("+0.5", Modifier.weight(1f), true,  DosiusPurple) {
                        val current = insulinUnits.toFloatOrNull() ?: 0f
                        viewModel.onInsulinUnitsChange(viewModel.roundValue(current + 0.5f))
                    }
                    StepperButton("+1",   Modifier.weight(1f), true,  DosiusPurple) {
                        val current = insulinUnits.toFloatOrNull() ?: 0f
                        viewModel.onInsulinUnitsChange(viewModel.roundValue(current + 1f))
                    }
                }
            }
        }

        NotesField(notes, viewModel::onDescriptionChange, DosiusPurple)

        AnimatedVisibility(visible = showIsfPrompt, enter = fadeIn() + expandVertically()) {
            IsfLearningCard(
                enabled = isfLearningEnabled,
                onToggle = viewModel::onIsfLearningToggle
            )
        }

        SheetActions(
            onBack = onBack,
            onSave = {
                viewModel.onInsulinTypeChange(com.dosius.smart.domain.model.InsulinType.BOLUS)
                viewModel.saveEntry()
                viewModel.resetForm()
                onSave()
            },
            accentColor = DosiusPurple
        )
    }

    SheetTimePicker(showTimePicker, hour, minute,
        onConfirm = { h, m ->
            hour = h; minute = m; showTimePicker = false
            val dt = viewModel.selectedDateTime.value
            viewModel.onDateTimeChange(LocalDateTime(dt.year, dt.month, dt.dayOfMonth, h, m))
        },
        onDismiss = { showTimePicker = false }
    )

    SheetDatePicker(
        show = showDatePicker,
        selectedDate = selectedDateTime.date,
        onConfirm = { date ->
            showDatePicker = false
            viewModel.onDateTimeChange(LocalDateTime(date.year, date.month, date.dayOfMonth, hour, minute))
        },
        onDismiss = { showDatePicker = false }
    )
}

// ─── ISF learning card ────────────────────────────────────────────────────────

@Composable
private fun IsfLearningCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) DosiusPurpleContainer else Color.White
        ),
        border = BorderStroke(1.5.dp, DosiusPurple.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Use for ISF learning",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = DosiusPurple
                )
                Text(
                    "No recent food detected. This correction can help the system learn how much 1 unit lowers your glucose.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

// ─── ICR learning card ────────────────────────────────────────────────────────

@Composable
private fun IcrLearningCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) FoodContainer else Color.White
        ),
        border = BorderStroke(1.5.dp, FoodOrange.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Use for ICR learning",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = FoodOrange
                )
                Text(
                    "High-confidence carb estimate. This meal can help the system learn how many carbs 1 unit covers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

// ─── Exercise form ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseEntryContent(
    onBack: () -> Unit,
    onSave: () -> Unit,
    viewModel: AddEntryViewModel
) {
    val exerciseName        by viewModel.exerciseName.collectAsState()
    val exercises           by viewModel.exercises.collectAsState()
    val selectedExercise    by viewModel.selectedExercise.collectAsState()
    val duration            by viewModel.durationOfExercise.collectAsState()
    val intensity           by viewModel.intensity.collectAsState()
    val totalCarbs          by viewModel.totalCarbs.collectAsState()
    val notes               by viewModel.description.collectAsState()
    val preExerciseRec      by viewModel.preExerciseRecommendation.collectAsState()
    val preExerciseAccepted by viewModel.preExerciseAccepted.collectAsState()
    val selectedDateTime    by viewModel.selectedDateTime.collectAsState()

    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var hour   by remember { mutableStateOf(selectedDateTime.hour) }
    var minute by remember { mutableStateOf(selectedDateTime.minute) }

    LaunchedEffect(selectedDateTime) {
        hour = selectedDateTime.hour
        minute = selectedDateTime.minute
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SheetHeader("Exercise", selectedDateTime.date, hour, minute,
            onDateClick = { showDatePicker = true },
            onTimeClick = { showTimePicker = true }
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = ExerciseContainer
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                // Activity type
                ExerciseAutocompleteField(
                    query = exerciseName,
                    onQueryChange = viewModel::onExerciseQueryChange,
                    exercises = exercises,
                    selectedExercise = selectedExercise,
                    onExerciseSelected = viewModel::onExerciseSelected,
                    onClearSelection = { viewModel.onExerciseQueryChange("") },
                    accentColor = ExerciseBlue,
                    container = ExerciseContainer
                )

                // Duration
                OutlinedTextField(
                    value = duration,
                    onValueChange = viewModel::onDurationOfExerciseChange,
                    label = { Text("Duration") },
                    placeholder = { Text("minutes", color = Color.LightGray) },
                    suffix = { Text("min", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(16.dp),
                    colors = cardFieldColors(ExerciseBlue, ExerciseContainer)
                )

                // Intensity chips
                Text("Intensity", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExerciseIntensity.entries.forEach { level ->
                        val selected = intensity == level
                        Surface(
                            onClick = { viewModel.onIntensityChange(if (selected) null else level) },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(50),
                            color = if (selected) ExerciseBlue else Color.White,
                            contentColor = if (selected) Color.White else ExerciseBlue,
                            border = BorderStroke(1.5.dp, ExerciseBlue),
                            tonalElevation = 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    level.name.lowercase().replaceFirstChar { it.uppercaseChar() },
                                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Carbs
                OutlinedTextField(
                    value = totalCarbs,
                    onValueChange = viewModel::onTotalCarbsChange,
                    label = { Text("Carbs") },
                    placeholder = { Text("grams", color = Color.LightGray) },
                    suffix = { Text("g", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(16.dp),
                    colors = cardFieldColors(ExerciseBlue, ExerciseContainer)
                )
            }
        }

        // Pre-exercise recommendation
        AnimatedVisibility(visible = preExerciseRec != null, enter = fadeIn() + expandVertically()) {
            preExerciseRec?.let { rec ->
                QuickRecommendationCard(
                    result = rec,
                    label = "Pre-exercise carbs",
                    accepted = preExerciseAccepted,
                    accentColor = ExerciseBlue,
                    formatValue = viewModel::roundValue,
                    onAccept = viewModel::onPreExerciseRecommendationAction
                )
            }
        }

        NotesField(notes, viewModel::onDescriptionChange, ExerciseBlue)
        SheetActions(
            onBack = onBack,
            onSave = { viewModel.saveEntry(); viewModel.resetForm(); onSave() },
            accentColor = ExerciseBlue
        )
    }

    SheetTimePicker(showTimePicker, hour, minute,
        onConfirm = { h, m ->
            hour = h; minute = m; showTimePicker = false
            val dt = viewModel.selectedDateTime.value
            viewModel.onDateTimeChange(LocalDateTime(dt.year, dt.month, dt.dayOfMonth, h, m))
        },
        onDismiss = { showTimePicker = false }
    )

    SheetDatePicker(
        show = showDatePicker,
        selectedDate = selectedDateTime.date,
        onConfirm = { date ->
            showDatePicker = false
            viewModel.onDateTimeChange(LocalDateTime(date.year, date.month, date.dayOfMonth, hour, minute))
        },
        onDismiss = { showDatePicker = false }
    )
}

// ─── Fast carbs form ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FastCarbsEntryContent(
    onBack: () -> Unit,
    onSave: () -> Unit,
    viewModel: AddEntryViewModel
) {
    val totalCarbs by viewModel.totalCarbs.collectAsState()
    val notes by viewModel.description.collectAsState()
    val selectedDateTime by viewModel.selectedDateTime.collectAsState()

    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var hour   by remember { mutableStateOf(selectedDateTime.hour) }
    var minute by remember { mutableStateOf(selectedDateTime.minute) }

    LaunchedEffect(selectedDateTime) {
        hour = selectedDateTime.hour
        minute = selectedDateTime.minute
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SheetHeader("Fast carbs", selectedDateTime.date, hour, minute,
            onDateClick = { showDatePicker = true },
            onTimeClick = { showTimePicker = true }
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = FastCarbsContainer.copy(alpha = 0.7f)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 24.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                OutlinedTextField(
                    value = totalCarbs,
                    onValueChange = viewModel::onTotalCarbsChange,
                    label = { Text("Grams") },
                    placeholder = { Text("e.g. 15", color = Color.LightGray) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(16.dp),
                    colors = cardFieldColors(FastCarbsAmber, FastCarbsContainer)
                )
            }
        }

        NotesField(notes, viewModel::onDescriptionChange, FastCarbsAmber)
        SheetActions(
            onBack = onBack,
            onSave = { viewModel.saveEntry(); viewModel.resetForm(); onSave() },
            accentColor = FastCarbsAmber
        )
    }

    SheetTimePicker(showTimePicker, hour, minute,
        onConfirm = { h, m ->
            hour = h; minute = m; showTimePicker = false
            val dt = viewModel.selectedDateTime.value
            viewModel.onDateTimeChange(LocalDateTime(dt.year, dt.month, dt.dayOfMonth, h, m))
        },
        onDismiss = { showTimePicker = false }
    )

    SheetDatePicker(
        show = showDatePicker,
        selectedDate = selectedDateTime.date,
        onConfirm = { date ->
            showDatePicker = false
            viewModel.onDateTimeChange(LocalDateTime(date.year, date.month, date.dayOfMonth, hour, minute))
        },
        onDismiss = { showDatePicker = false }
    )
}

// ─── Food form ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodEntryContent(
    onBack: () -> Unit,
    onSave: () -> Unit,
    viewModel: AddEntryViewModel
) {
    val foodSearchQuery  by viewModel.foodSearchQuery.collectAsState()
    val foodSearchResults by viewModel.foodSearchResults.collectAsState()
    val selectedFood     by viewModel.selectedFood.collectAsState()
    val quantitySuggestion by viewModel.quantitySuggestion.collectAsState()
    val quantity         by viewModel.quantity.collectAsState()
    val totalCarbs       by viewModel.totalCarbs.collectAsState()
    val carbsAutoFilled  by viewModel.carbsAutoFilled.collectAsState()
    val carbsRecommended by viewModel.carbsRecommended.collectAsState()
    val insulinUnits     by viewModel.insulinUnits.collectAsState()
    val carbConfidence   by viewModel.carbConfidence.collectAsState()
    val mealType         by viewModel.mealType.collectAsState()
    val notes            by viewModel.description.collectAsState()
    val prandialRec      by viewModel.prandialRecommendation.collectAsState()
    val prandialAccepted by viewModel.prandialAccepted.collectAsState()
    val selectedDateTime by viewModel.selectedDateTime.collectAsState()
    val basketItems      by viewModel.basketItems.collectAsState()
    val showIcrPrompt    by viewModel.showIcrLearningPrompt.collectAsState()
    val icrLearningEnabled by viewModel.icrLearningEnabled.collectAsState()

    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var hour   by remember { mutableStateOf(selectedDateTime.hour) }
    var minute by remember { mutableStateOf(selectedDateTime.minute) }

    LaunchedEffect(selectedDateTime) {
        hour = selectedDateTime.hour
        minute = selectedDateTime.minute
    }

    val mealOptions = listOf(
        "Breakfast" to Icons.Default.WbSunny,
        "Lunch"     to Icons.Default.LightMode,
        "Dinner"    to Icons.Default.NightsStay,
        "Snack"     to Icons.Default.Cookie
    )
    val confidenceLabels = mapOf(
        CarbConfidence.CERTAIN to "I measured it",
        CarbConfidence.HIGH    to "Pretty sure",
        CarbConfidence.MEDIUM  to "Roughly",
        CarbConfidence.LOW     to "Guessing"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SheetHeader("Food", selectedDateTime.date, hour, minute,
            onDateClick = { showDatePicker = true },
            onTimeClick = { showTimePicker = true }
        )

        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = FoodContainer) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                // Basket: items added to this meal so far
                if (basketItems.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Added to meal", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        basketItems.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    item.foodName.ifBlank { "Unknown food" },
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "${item.totalCarbs.toInt()} g",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FoodOrange,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(
                                    onClick = { viewModel.removeBasketItem(item.tempId) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                        val basketTotal = basketItems.sumOf { it.totalCarbs.toDouble() }.toFloat()
                        val currentCarbs = totalCarbs.toFloatOrNull() ?: 0f
                        Text(
                            "Meal total: ${(basketTotal + currentCarbs).toInt()} g carbs",
                            style = MaterialTheme.typography.labelSmall,
                            color = FoodOrange,
                            fontWeight = FontWeight.Bold
                        )
                        HorizontalDivider()
                    }
                }

                // Food autocomplete
                FoodAutocompleteField(
                    query = foodSearchQuery,
                    onQueryChange = viewModel::onFoodSearchQueryChanged,
                    searchResults = foodSearchResults,
                    selectedFood = selectedFood,
                    onFoodSelected = viewModel::onFoodSelected,
                    onClearSelection = { viewModel.onFoodSearchQueryChanged("") },
                    accentColor = FoodOrange
                )

                // Quantity + Carbs
                val carbsPer100g = selectedFood?.carbsPer100g
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = quantity,
                            onValueChange = viewModel::onQuantityChange,
                            label = { Text("Amount") },
                            suffix = { Text("g", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(16.dp),
                            colors = cardFieldColors(FoodOrange, FoodContainer)
                        )
                        val suggestion = quantitySuggestion
                        if (suggestion != null && quantity.isBlank()) {
                            Text(
                                "Typical: ~${suggestion.grams}g  (${suggestion.fromNEvents} past events)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .clickable { viewModel.onQuantityChange(suggestion.grams.toString()) }
                            )
                        } else if (carbsPer100g != null) {
                            Text(
                                "%.1f g carbs/100g".format(carbsPer100g),
                                style = MaterialTheme.typography.labelSmall,
                                color = FoodOrange,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = totalCarbs,
                            onValueChange = viewModel::onTotalCarbsChange,
                            label = { Text("Carbs") },
                            suffix = { Text("g", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(16.dp),
                            colors = cardFieldColors(FoodOrange, FoodContainer)
                        )
                        if (carbsAutoFilled) {
                            Text(
                                "Auto-calculated",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        } else if (carbsRecommended != null && totalCarbs.isBlank()) {
                            Text(
                                "Typical: ${carbsRecommended!!.toInt()} g",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }

                // Bolus
                OutlinedTextField(
                    value = insulinUnits,
                    onValueChange = viewModel::onInsulinUnitsChange,
                    label = { Text("Bolus") },
                    suffix = { Text("u", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(16.dp),
                    colors = cardFieldColors(FoodOrange, FoodContainer)
                )

                // Meal type
                Text("Meal type", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    mealOptions.forEach { (name, icon) ->
                        val selected = mealType == name
                        Surface(
                            onClick = { viewModel.onMealTypeChange(if (selected) null else name) },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(50),
                            color = if (selected) FoodOrange else Color.White,
                            contentColor = if (selected) Color.White else FoodOrange,
                            border = BorderStroke(1.5.dp, FoodOrange),
                            tonalElevation = 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                // Meal type labels row (aligned under chips)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    mealOptions.forEach { (name, _) ->
                        Text(
                            name, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // Carb confidence
                Text("Confidence", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CarbConfidence.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { level ->
                                val selected = carbConfidence == level
                                Surface(
                                    onClick = { viewModel.onCarbConfidenceChange(level) },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(50),
                                    color = if (selected) FoodOrange else Color.White,
                                    contentColor = if (selected) Color.White else FoodOrange,
                                    border = BorderStroke(1.5.dp, FoodOrange),
                                    tonalElevation = 0.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            confidenceLabels[level] ?: level.name,
                                            fontWeight = FontWeight.SemiBold, fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                            // Fill empty slot if odd number
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Add another food to the same meal
        val canAddToBasket = totalCarbs.isNotBlank() && (totalCarbs.toFloatOrNull() ?: 0f) > 0f
        if (canAddToBasket || basketItems.isNotEmpty()) {
            OutlinedButton(
                onClick = viewModel::addCurrentItemToBasket,
                modifier = Modifier.fillMaxWidth(),
                enabled = canAddToBasket,
                border = BorderStroke(1.dp, if (canAddToBasket) FoodOrange else Color.LightGray),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FoodOrange),
                shape = RoundedCornerShape(50)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add another food to this meal")
            }
        }

        // Prandial recommendation
        AnimatedVisibility(visible = prandialRec != null, enter = fadeIn() + expandVertically()) {
            prandialRec?.let { rec ->
                QuickRecommendationCard(
                    result = rec,
                    label = "Recommended bolus",
                    accepted = prandialAccepted,
                    accentColor = FoodOrange,
                    formatValue = viewModel::roundValue,
                    onAccept = viewModel::onPrandialRecommendationAction
                )
            }
        }

        AnimatedVisibility(visible = showIcrPrompt, enter = fadeIn() + expandVertically()) {
            IcrLearningCard(
                enabled = icrLearningEnabled,
                onToggle = viewModel::onIcrLearningToggle
            )
        }

        NotesField(notes, viewModel::onDescriptionChange, FoodOrange)
        SheetActions(
            onBack = onBack,
            onSave = { viewModel.saveEntry(); viewModel.resetForm(); onSave() },
            accentColor = FoodOrange
        )
    }

    SheetTimePicker(showTimePicker, hour, minute,
        onConfirm = { h, m ->
            hour = h; minute = m; showTimePicker = false
            val dt = viewModel.selectedDateTime.value
            viewModel.onDateTimeChange(LocalDateTime(dt.year, dt.month, dt.dayOfMonth, h, m))
        },
        onDismiss = { showTimePicker = false }
    )

    SheetDatePicker(
        show = showDatePicker,
        selectedDate = selectedDateTime.date,
        onConfirm = { date ->
            showDatePicker = false
            viewModel.onDateTimeChange(LocalDateTime(date.year, date.month, date.dayOfMonth, hour, minute))
        },
        onDismiss = { showDatePicker = false }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodAutocompleteField(
    query: String,
    onQueryChange: (String) -> Unit,
    searchResults: List<Food>,
    selectedFood: Food?,
    onFoodSelected: (Food) -> Unit,
    onClearSelection: () -> Unit,
    accentColor: Color
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && searchResults.isNotEmpty(),
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { onQueryChange(it); expanded = true },
            label = { Text("Food") },
            placeholder = { Text("Search food…", color = Color.LightGray) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
            singleLine = true,
            trailingIcon = {
                if (selectedFood != null) {
                    IconButton(onClick = onClearSelection) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = accentColor)
                    }
                } else if (searchResults.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded && searchResults.isNotEmpty())
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = cardFieldColors(accentColor, FoodContainer)
        )
        ExposedDropdownMenu(
            expanded = expanded && searchResults.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            searchResults.forEach { food ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(food.name, style = MaterialTheme.typography.bodyMedium)
                            Text(food.idCategory, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    },
                    onClick = { onFoodSelected(food); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseAutocompleteField(
    query: String,
    onQueryChange: (String) -> Unit,
    exercises: List<Exercise>,
    selectedExercise: Exercise?,
    onExerciseSelected: (Exercise) -> Unit,
    onClearSelection: () -> Unit,
    accentColor: Color,
    container: Color
) {
    val filtered = remember(query, exercises) {
        if (query.isBlank()) emptyList()
        else exercises.filter { it.type.contains(query, ignoreCase = true) }
    }
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && filtered.isNotEmpty(),
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { onQueryChange(it); expanded = true },
            label = { Text("Activity type") },
            placeholder = { Text("e.g. Running, Cycling", color = Color.LightGray) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
            singleLine = true,
            trailingIcon = {
                if (selectedExercise != null) {
                    IconButton(onClick = onClearSelection) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = accentColor)
                    }
                } else if (filtered.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded && filtered.isNotEmpty())
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = cardFieldColors(accentColor, container)
        )
        ExposedDropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            filtered.forEach { exercise ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(exercise.type, style = MaterialTheme.typography.bodyMedium)
                            exercise.expectedGlucoseDropPerHour?.let { drop ->
                                Text(
                                    "~$drop mg/dL/h drop",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    },
                    onClick = { onExerciseSelected(exercise); expanded = false }
                )
            }
        }
    }
}

// ─── Recommendation card ──────────────────────────────────────────────────────

@Composable
private fun QuickRecommendationCard(
    result: RecommendationResult,
    label: String,
    accepted: Boolean,
    accentColor: Color,
    formatValue: (Float) -> String,
    onAccept: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // Header: value
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    val displayValue = if (result.recommendedUnits != null)
                        "${formatValue(result.recommendedUnits)} U"
                    else
                        "${result.recommendedCarbs} g"
                    Text(displayValue, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }

            // Breakdown (only for unit-based recommendations)
            if (result.recommendedUnits != null) {
                HorizontalDivider()
                with(result.components) {
                    val grossDose = carbDose + correctionDose
                    if (grossDose != 0f) {
                        RecComponentRow("Carbs + correction", grossDose, "U", formatValue = formatValue)
                        if (carbDose > 0f)
                            RecComponentRow("Carb dose", carbDose, "U", indent = true, formatValue = formatValue)
                        if (correctionDose != 0f)
                            RecComponentRow("BG correction", correctionDose, "U", indent = true, formatValue = formatValue)
                    }
                    if (iobOffset != 0f)
                        RecComponentRow("IOB offset", iobOffset, "U", formatValue = formatValue)
                    if (trendAdjustment != 0f)
                        RecComponentRow("Trend", trendAdjustment, "U", formatValue = formatValue)
                }
            }

            result.warnings.forEach { warning ->
                Text("⚠ $warning", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF9800))
            }

            // Accept / accepted state
            if (accepted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (result.recommendedUnits != null) "Applied to bolus field" else "Applied to carbs field",
                        color = Color(0xFF4CAF50),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Accept recommendation")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            Text(
                "Decision-support only. Verify with your healthcare provider.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray
            )
        }
    }
}

@Composable
private fun RecComponentRow(
    label: String, value: Float, unit: String,
    indent: Boolean = false, formatValue: (Float) -> String
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            if (indent) "  $label" else label,
            style = MaterialTheme.typography.bodySmall,
            color = if (indent) Color.LightGray else Color.Gray
        )
        val sign = if (value < 0f) "−" else "+"
        Text(
            "$sign${formatValue(kotlin.math.abs(value))} $unit",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (indent) FontWeight.Normal else FontWeight.Medium
        )
    }
}

// ─── Sleep form ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepEntryContent(
    onBack: () -> Unit,
    onSave: () -> Unit,
    viewModel: AddEntryViewModel
) {
    val notes by viewModel.description.collectAsState()
    val selectedDateTime by viewModel.selectedDateTime.collectAsState()
    val sleepEndHour by viewModel.sleepEndHour.collectAsState()
    val sleepEndMinute by viewModel.sleepEndMinute.collectAsState()
    val sleepEligible by viewModel.sleepEligible.collectAsState()
    val sleepWarning by viewModel.sleepWarning.collectAsState()

    var showStartTimePicker by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var startHour by remember { mutableStateOf(selectedDateTime.hour) }
    var startMinute by remember { mutableStateOf(selectedDateTime.minute) }

    LaunchedEffect(Unit) { viewModel.checkSleepEligibility() }
    LaunchedEffect(selectedDateTime) {
        startHour = selectedDateTime.hour
        startMinute = selectedDateTime.minute
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Sleep learning",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Declare your sleep window so the system can learn your basal dose from overnight fasting glucose.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = SleepContainer.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Bedtime
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bedtime", fontWeight = FontWeight.SemiBold, color = SleepIndigo)
                        Text("When you go to sleep", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Row {
                        TextButton(onClick = { showStartDatePicker = true }) {
                            Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(16.dp), tint = SleepIndigo)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                formatDate(selectedDateTime.date),
                                color = SleepIndigo,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(onClick = { showStartTimePicker = true }) {
                            Icon(Icons.Default.Schedule, null, modifier = Modifier.size(16.dp), tint = SleepIndigo)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                String.format("%02d:%02d", startHour, startMinute),
                                color = SleepIndigo,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                HorizontalDivider(color = SleepIndigo.copy(alpha = 0.15f))

                // Wake time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Wake up", fontWeight = FontWeight.SemiBold, color = SleepIndigo)
                        Text("Expected wake time", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    TextButton(onClick = { showEndTimePicker = true }) {
                        Icon(Icons.Default.Schedule, null, modifier = Modifier.size(16.dp), tint = SleepIndigo)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            String.format("%02d:%02d", sleepEndHour, sleepEndMinute),
                            color = SleepIndigo,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Eligibility warning
        sleepWarning?.let { warning ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE65100)
                    )
                }
            }
        }

        // Info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SleepContainer.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "How it works",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SleepIndigo
                )
                Text(
                    "During fasting sleep, glucose drift reveals whether your basal dose is correct. " +
                        "The system collects overnight data and proposes an adjusted dose you can review on the Parameters screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }

        NotesField(notes, viewModel::onDescriptionChange, SleepIndigo)

        SheetActions(
            onBack = onBack,
            onSave = {
                viewModel.saveSleepEntry()
                viewModel.resetForm()
                onSave()
            },
            accentColor = SleepIndigo
        )
    }

    SheetTimePicker(showStartTimePicker, startHour, startMinute,
        onConfirm = { h, m ->
            startHour = h; startMinute = m; showStartTimePicker = false
            val dt = viewModel.selectedDateTime.value
            viewModel.onDateTimeChange(LocalDateTime(dt.year, dt.month, dt.dayOfMonth, h, m))
        },
        onDismiss = { showStartTimePicker = false }
    )

    SheetTimePicker(showEndTimePicker, sleepEndHour, sleepEndMinute,
        onConfirm = { h, m ->
            showEndTimePicker = false
            viewModel.onSleepEndTimeChange(h, m)
        },
        onDismiss = { showEndTimePicker = false }
    )

    SheetDatePicker(
        show = showStartDatePicker,
        selectedDate = selectedDateTime.date,
        onConfirm = { date ->
            showStartDatePicker = false
            viewModel.onDateTimeChange(LocalDateTime(date.year, date.month, date.dayOfMonth, startHour, startMinute))
        },
        onDismiss = { showStartDatePicker = false }
    )
}

// ─── Shared sheet components ──────────────────────────────────────────────────

@Composable
private fun SheetHeader(
    title: String,
    selectedDate: LocalDate,
    hour: Int, minute: Int,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        TextButton(onClick = onDateClick) {
            Icon(Icons.Default.CalendarToday, "Change date", modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(formatDate(selectedDate))
        }
        TextButton(onClick = onTimeClick) {
            Icon(Icons.Default.Schedule, "Change time", modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(String.format("%02d:%02d", hour, minute))
        }
    }
}

@Composable
private fun NotesField(value: String, onValueChange: (String) -> Unit, accentColor: Color) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text("Notes (optional)") },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accentColor,
            unfocusedBorderColor = Color.LightGray
        )
    )
}

@Composable
private fun SheetActions(onBack: () -> Unit, onSave: () -> Unit, accentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(50)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(18.dp))
        }
        Button(
            onClick = onSave,
            modifier = Modifier.weight(3f).height(48.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
        ) {
            Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Save", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StepperButton(
    label: String, modifier: Modifier = Modifier, filled: Boolean,
    accentColor: Color, onClick: () -> Unit
) {
    Surface(
        onClick = onClick, modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(50),
        color = if (filled) accentColor else Color.White,
        contentColor = if (filled) Color.White else accentColor,
        border = if (!filled) BorderStroke(1.5.dp, accentColor) else null,
        tonalElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetTimePicker(
    show: Boolean, hour: Int, minute: Int,
    onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit
) {
    if (!show) return
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetDatePicker(
    show: Boolean,
    selectedDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return
    val epochMillis = selectedDate.toEpochDays().toLong() * 86400L * 1000L
    val state = rememberDatePickerState(initialSelectedDateMillis = epochMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val epochDays = (millis / (86400L * 1000L)).toInt()
                    onConfirm(LocalDate.fromEpochDays(epochDays))
                } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun cardFieldColors(accent: Color, container: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = accent,
    unfocusedBorderColor = container,
    unfocusedContainerColor = Color.White,
    focusedContainerColor = Color.White
)

private fun formatDose(dose: Float): String =
    if (dose == kotlin.math.floor(dose).toFloat()) dose.toInt().toString()
    else String.format(java.util.Locale.US, "%.1f", dose)

private fun formatDate(date: LocalDate): String {
    val month = date.month.name.lowercase().replaceFirstChar { it.uppercaseChar() }.take(3)
    return "${date.dayOfMonth} $month"
}

// ─── Entry type selection ─────────────────────────────────────────────────────

@Composable
private fun EntryTypeSelectionContent(onTypeSelected: (QuickEntryType) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("New entry", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickEntryButton("Food",     Icons.Default.Restaurant,               Color(0xFFFFCCBC), OnLight, Modifier.weight(1f)) { onTypeSelected(QuickEntryType.FOOD) }
            QuickEntryButton("Exercise", Icons.AutoMirrored.Filled.DirectionsRun, Color(0xFF90CAF9), OnLight, Modifier.weight(1f)) { onTypeSelected(QuickEntryType.EXERCISE) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickEntryButton("Bolus",      Icons.Default.Vaccines, Color(0xFFCBB8FF), OnLight, Modifier.weight(1f)) { onTypeSelected(QuickEntryType.BOLUS) }
            QuickEntryButton("Fast carbs", Icons.Default.Bolt,     Color(0xFFFFE082), OnLight, Modifier.weight(1f)) { onTypeSelected(QuickEntryType.FAST_CARBS) }
        }
        QuickEntryButton("Sleep learning", Icons.Default.DarkMode, SleepContainer, OnLight, Modifier.fillMaxWidth()) { onTypeSelected(QuickEntryType.SLEEP) }
    }
}

@Composable
private fun QuickEntryButton(
    label: String, icon: ImageVector, backgroundColor: Color,
    contentColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Surface(
        onClick = onClick, modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(50), color = backgroundColor,
        contentColor = contentColor, tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
