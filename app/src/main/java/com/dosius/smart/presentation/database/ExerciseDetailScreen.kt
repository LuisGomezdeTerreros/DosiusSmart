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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dosius.smart.domain.model.Entry
import com.dosius.smart.domain.model.ExerciseCase
import com.dosius.smart.domain.model.ExerciseIntensity
import com.dosius.smart.domain.model.RegistrationMethod
import com.dosius.smart.presentation.theme.DosiusPurple
import com.dosius.smart.presentation.theme.LightBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExerciseDetailedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isEditing by viewModel.isEditing.collectAsState()
    val expectedGlucoseDropPerHour by viewModel.expectedGlucoseDropPerHour.collectAsState()
    val expectedGlucoseDropPerHourError by viewModel.expectedGlucoseDropPerHourError.collectAsState()

    val isSaveEnabled = expectedGlucoseDropPerHourError == null

    Scaffold(
        containerColor = LightBackground,
        topBar = {
            TopAppBar(
                title = {
                    val titleText = when (val s = uiState) {
                        is ExerciseDetailUiState.Success -> if (isEditing) "Edit Exercise" else s.exercise.type
                        else -> "Exercise Detail"
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
                    } else if (uiState is ExerciseDetailUiState.Success) {
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
            is ExerciseDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DosiusPurple)
                }
            }
            is ExerciseDetailUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is ExerciseDetailUiState.Success -> {
                val sortedEntries = state.entries.sortedByDescending { it.timestamp }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    item {
                        AnimatedVisibility(
                            visible = !isEditing,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            ExerciseHeroCard(state = state)
                        }
                    }

                    item {
                        AnimatedVisibility(
                            visible = isEditing,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            ExerciseEditForm(
                                expectedGlucoseDropPerHour = expectedGlucoseDropPerHour,
                                expectedGlucoseDropPerHourError = expectedGlucoseDropPerHourError,
                                onExpectedGlucoseDropPerHourChange = viewModel::onExpectedGlucoseDropPerHourChange
                            )
                        }
                    }

                    if (!isEditing && sortedEntries.isNotEmpty()) {
                        item {
                            ExerciseStatsCard(state = state)
                        }
                    }

                    if (!isEditing) {
                        item {
                            ExerciseLearningCard(state = state)
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Session history",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(color = DosiusPurple.copy(alpha = 0.1f), shape = CircleShape) {
                                Text(
                                    text = "${sortedEntries.size}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = DosiusPurple
                                )
                            }
                        }
                    }

                    if (sortedEntries.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.DirectionsRun,
                                        contentDescription = null,
                                        tint = Color.LightGray,
                                        modifier = Modifier.size(52.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "No sessions logged yet",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    items(sortedEntries.size) { index ->
                        ExerciseTimelineItem(
                            entry = sortedEntries[index],
                            isLast = index == sortedEntries.size - 1
                        )
                    }
                }
            }
        }
    }
}

// ── Hero card ─────────────────────────────────────────────────────────────────

@Composable
private fun ExerciseHeroCard(state: ExerciseDetailUiState.Success) {
    val exercise = state.exercise

    Surface(modifier = Modifier.fillMaxWidth(), color = Color.White, shadowElevation = 2.dp) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
            Surface(
                color = Color(0xFFE3F2FD),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsRun,
                        contentDescription = null,
                        tint = Color(0xFF1565C0),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Exercise",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1565C0)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = exercise.type,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${state.entries.size} sessions logged",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFF0F0F0))
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val dropValue = exercise.expectedGlucoseDropPerHour
                ExerciseMetricChip(
                    label = "Drop/hour",
                    value = dropValue?.let { "-${it.toInt()} mg/dL" } ?: "—",
                    color = Color(0xFF1565C0),
                    modifier = Modifier.weight(1f)
                )
                ExerciseMetricChip(
                    label = "Logged",
                    value = "${state.entries.size}×",
                    color = DosiusPurple,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ExerciseMetricChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
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

// ── Stats card ────────────────────────────────────────────────────────────────

@Composable
private fun ExerciseStatsCard(state: ExerciseDetailUiState.Success) {
    val avgDuration = state.entries.mapNotNull { it.durationOfExercise }.average().takeIf { !it.isNaN() }
    val lowCount = state.entries.count { it.intensity == ExerciseIntensity.LOW }
    val mediumCount = state.entries.count { it.intensity == ExerciseIntensity.MEDIUM }
    val highCount = state.entries.count { it.intensity == ExerciseIntensity.HIGH }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Session statistics",
                style = MaterialTheme.typography.labelLarge,
                color = Color.Gray,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExerciseStatChip(
                    label = "Avg duration",
                    value = avgDuration?.let { "${it.toInt()} min" } ?: "—",
                    icon = Icons.Default.Timer,
                    color = Color(0xFF1565C0),
                    modifier = Modifier.weight(1f)
                )
                ExerciseStatChip(
                    label = "L / M / H",
                    value = "$lowCount / $mediumCount / $highCount",
                    icon = Icons.Default.FitnessCenter,
                    color = DosiusPurple,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ExerciseStatChip(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
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
private fun ExerciseLearningCard(state: ExerciseDetailUiState.Success) {
    val stats = state.stats
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Learning data",
                style = MaterialTheme.typography.labelLarge,
                color = Color.Gray,
                fontWeight = FontWeight.SemiBold
            )

            if (stats.currentDropPerHour != null || stats.averageObservedDropPerHour != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Drop per hour", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    if (stats.currentDropPerHour != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Estimated", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(
                                "-${stats.currentDropPerHour.toInt()} mg/dL/h",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (stats.averageObservedDropPerHour != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Observed average", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text(
                                "-${stats.averageObservedDropPerHour.toInt()} mg/dL/h  (${stats.nCases} sessions)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFFF0F0F0))
            }

            if (stats.averageDurationMinutes != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Average duration", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text(
                        "~${stats.averageDurationMinutes.toInt()} min  (from ${stats.nCases} sessions)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                HorizontalDivider(color = Color(0xFFF0F0F0))
            }

            Text("Past cases", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            if (stats.recentCases.isEmpty()) {
                Text(
                    "No learning data yet. Cases will appear after exercise sessions are processed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            } else {
                stats.recentCases.forEach { case ->
                    ExerciseCaseRow(case = case)
                }
            }
        }
    }
}

@Composable
private fun ExerciseCaseRow(case: ExerciseCase) {
    val intensityColor = when (case.intensity.uppercase()) {
        "LOW"    -> Color(0xFF4CAF50)
        "MEDIUM" -> Color(0xFFFF9800)
        "HIGH"   -> Color(0xFFF44336)
        else     -> Color.Gray
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(color = intensityColor.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
            Text(
                case.intensity,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = intensityColor
            )
        }
        Text(
            "${case.durationMinutes.toInt()} min",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.width(44.dp)
        )
        Text(
            "exp. -${case.expectedDrop} mg/dL",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
        Text(
            "obs. -${case.actualDrop} mg/dL",
            style = MaterialTheme.typography.labelSmall,
            color = DosiusPurple
        )
    }
}

// ── Timeline item ─────────────────────────────────────────────────────────────

@Composable
private fun ExerciseTimelineItem(entry: Entry, isLast: Boolean) {
    val intensityColor = when (entry.intensity) {
        ExerciseIntensity.LOW -> Color(0xFF4CAF50)
        ExerciseIntensity.MEDIUM -> Color(0xFFFF9800)
        ExerciseIntensity.HIGH -> Color(0xFFF44336)
        null -> Color.Gray
    }

    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(20.dp)) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(DosiusPurple))
            if (!isLast) {
                Box(modifier = Modifier.width(2.dp).height(88.dp).background(Color(0xFFE0E0E0)))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (isLast) 0.dp else 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = exerciseFormatDate(entry.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Surface(
                        color = if (entry.registrationMethod == RegistrationMethod.CHAT)
                            DosiusPurple.copy(alpha = 0.1f) else Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (entry.registrationMethod == RegistrationMethod.CHAT) "AI" else "Manual",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (entry.registrationMethod == RegistrationMethod.CHAT) DosiusPurple else Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (entry.durationOfExercise != null) {
                            ExerciseSessionStat(
                                label = "Duration",
                                value = "${entry.durationOfExercise.toInt()} min"
                            )
                        }
                        if (entry.currentGlucose != 0) {
                            ExerciseSessionStat(
                                label = "Glucose",
                                value = "${entry.currentGlucose} mg/dL",
                                valueColor = Color(0xFF1565C0)
                            )
                        }
                    }

                    if (entry.intensity != null) {
                        Surface(
                            color = intensityColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = entry.intensity.name,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = intensityColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseSessionStat(label: String, value: String, valueColor: Color = Color.Black) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

// ── Edit form ─────────────────────────────────────────────────────────────────

@Composable
private fun ExerciseEditForm(
    expectedGlucoseDropPerHour: String,
    expectedGlucoseDropPerHourError: String?,
    onExpectedGlucoseDropPerHourChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Physiology data",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Gray,
                    fontWeight = FontWeight.SemiBold
                )
                ExerciseFormField(
                    value = expectedGlucoseDropPerHour,
                    onValueChange = onExpectedGlucoseDropPerHourChange,
                    label = "Expected glucose drop/hour",
                    placeholder = "e.g. 30.0",
                    suffix = "mg/dL",
                    keyboardType = KeyboardType.Decimal,
                    errorMessage = expectedGlucoseDropPerHourError
                )
            }
        }
    }
}

@Composable
private fun ExerciseFormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
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

private fun exerciseFormatDate(timestamp: kotlinx.datetime.LocalDateTime): String {
    val day = timestamp.dayOfMonth.toString().padStart(2, '0')
    val month = timestamp.monthNumber.toString().padStart(2, '0')
    val hour = timestamp.hour.toString().padStart(2, '0')
    val minute = timestamp.minute.toString().padStart(2, '0')
    return "$day/$month · $hour:$minute"
}

