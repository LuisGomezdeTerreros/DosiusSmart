package com.dosius.smart.presentation.logs

import androidx.compose.foundation.BorderStroke
import com.dosius.smart.domain.model.CarbConfidence
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dosius.smart.domain.model.Entry
import com.dosius.smart.domain.model.Food
import com.dosius.smart.domain.model.InsulinType
import com.dosius.smart.presentation.entry.QuickEntrySheet
import com.dosius.smart.presentation.entry.QuickEntryType
import com.dosius.smart.presentation.theme.DosiusPurple
import com.dosius.smart.presentation.theme.GlucoseHigh
import kotlinx.datetime.toLocalDateTime
import com.dosius.smart.presentation.theme.GlucoseLow
import com.dosius.smart.presentation.theme.GlucoseNormal
import com.dosius.smart.presentation.theme.GlucoseVeryHigh
import com.dosius.smart.presentation.theme.GlucoseVeryLow
import kotlinx.datetime.LocalDate

// ─── Entry type helpers ───────────────────────────────────────────────────────

private enum class EntryDisplayType { FOOD, EXERCISE, BOLUS, FAST_CARBS }

private fun classifyEntry(entry: Entry): EntryDisplayType = when {
    entry.exerciseType != null -> EntryDisplayType.EXERCISE
    entry.foodId != null || entry.mealType != null -> EntryDisplayType.FOOD
    entry.insulinUnits != null && entry.totalCarbs == null && entry.foodId == null -> EntryDisplayType.BOLUS
    entry.totalCarbs != null && entry.foodId == null && entry.insulinUnits == null -> EntryDisplayType.FAST_CARBS
    entry.totalCarbs != null -> EntryDisplayType.FOOD
    else -> EntryDisplayType.BOLUS
}

private fun EntryDisplayType.toQuickEntryType() = QuickEntryType.valueOf(this.name)

private fun matchesFilter(entry: Entry, filter: LogsFilter): Boolean {
    if (filter == LogsFilter.ALL) return true
    return when (classifyEntry(entry)) {
        EntryDisplayType.FOOD -> filter == LogsFilter.FOOD
        EntryDisplayType.EXERCISE -> filter == LogsFilter.EXERCISE
        EntryDisplayType.BOLUS -> filter == LogsFilter.BOLUS
        EntryDisplayType.FAST_CARBS -> filter == LogsFilter.FAST_CARBS
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun LogsScreen(viewModel: LogsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var entryPendingDelete by remember { mutableStateOf<Entry?>(null) }
    var editEntryId by remember { mutableStateOf<String?>(null) }
    var editEntryType by remember { mutableStateOf<QuickEntryType?>(null) }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    entryPendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryPendingDelete = null },
            title = { Text("Delete entry") },
            text = { Text("This entry will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteEntry(entry)
                        entryPendingDelete = null
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { entryPendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (editEntryId != null) {
        QuickEntrySheet(
            onEntryTypeSelected = { editEntryId = null; editEntryType = null },
            onDismiss = { editEntryId = null; editEntryType = null },
            editEntryId = editEntryId,
            editEntryType = editEntryType
        )
    }

    Scaffold { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FilterChipsRow(
            activeFilter = uiState.activeFilter,
            onFilterSelected = viewModel::setFilter
        )

        val sortedDates = remember(uiState.entriesByDay) {
            uiState.entriesByDay.keys.sortedDescending()
        }

        if (sortedDates.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No entries yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        sortedDates.forEach { date ->
            val allEntries = (uiState.entriesByDay[date] ?: emptyList())
                .sortedByDescending { it.timestamp }
            val filtered = allEntries.filter { matchesFilter(it, uiState.activeFilter) }
            if (filtered.isEmpty()) return@forEach

            DaySection(
                date = date,
                entries = filtered,
                allEntriesForDay = allEntries,
                foodsById = uiState.foodsById,
                onAcceptMealReview = viewModel::acceptMealReview,
                onDismissMealReview = viewModel::dismissMealReview,
                onAcceptExerciseReview = viewModel::acceptExerciseReview,
                onDismissExerciseReview = viewModel::dismissExerciseReview,
                onEditEntry = { entryId ->
                    val entry = uiState.entriesByDay.values.flatten().find { it.id == entryId }
                    editEntryType = entry?.let { classifyEntry(it).toQuickEntryType() }
                    editEntryId = entryId
                },
                onDeleteEntry = { entryPendingDelete = it }
            )
        }
    }
    } // Scaffold
}

// ─── Filter chips ─────────────────────────────────────────────────────────────

@Composable
private fun FilterChipsRow(activeFilter: LogsFilter, onFilterSelected: (LogsFilter) -> Unit) {
    val filters = listOf(
        LogsFilter.ALL        to "All",
        LogsFilter.FOOD       to "Food",
        LogsFilter.EXERCISE   to "Exercise",
        LogsFilter.BOLUS      to "Bolus",
        LogsFilter.FAST_CARBS to "Fast carbs"
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { (filter, label) ->
            val isSelected = activeFilter == filter
            Surface(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onFilterSelected(filter) },
                color = if (isSelected) DosiusPurple else Color(0xFFF5F5F5),
                shape = CircleShape
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else Color.Black,
                    maxLines = 1
                )
            }
        }
    }
}

// ─── Day section ──────────────────────────────────────────────────────────────

@Composable
private fun DaySection(
    date: LocalDate,
    entries: List<Entry>,
    allEntriesForDay: List<Entry>,
    foodsById: Map<String, Food>,
    onAcceptMealReview: (Entry) -> Unit,
    onDismissMealReview: (Entry) -> Unit,
    onAcceptExerciseReview: (Entry) -> Unit,
    onDismissExerciseReview: (Entry) -> Unit,
    onEditEntry: (String) -> Unit,
    onDeleteEntry: (Entry) -> Unit
) {
    val totalCarbs = remember(allEntriesForDay) {
        allEntriesForDay.mapNotNull { it.totalCarbs }.sum().toInt()
            .takeIf { it > 0 }
    }
    val totalUnits = remember(allEntriesForDay) {
        allEntriesForDay.mapNotNull { it.insulinUnits }.sum()
            .takeIf { it > 0f }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEEEEEE))
    ) {
        Column {
            // Day header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8F8F8))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDate(date),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (totalCarbs != null) {
                            DailySummaryChip(value = "${totalCarbs}g", color = Color(0xFFFF9800))
                        }
                        if (totalUnits != null) {
                            val formatted = if (totalUnits % 1f == 0f) "${totalUnits.toInt()}U" else "%.1fU".format(totalUnits)
                            DailySummaryChip(value = formatted, color = DosiusPurple)
                        }
                    }
                }
            }

            // Entry rows
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                entries.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EntryRow(
                            entry = entry,
                            foodsById = foodsById,
                            onEdit = { onEditEntry(entry.id) },
                            onDelete = { onDeleteEntry(entry) }
                        )

                        // Inline review for meal entries
                        if (entry.eventClockStatus == "PENDING" &&
                            entry.inferredCarbsPostEvent != null &&
                            entry.totalCarbs != null &&
                            entry.carbConfidence != CarbConfidence.CERTAIN &&
                            entry.carbConfidence != CarbConfidence.HIGH
                        ) {
                            InlineMealReview(
                                entry = entry,
                                onAccept = { onAcceptMealReview(entry) },
                                onKeep = { onDismissMealReview(entry) }
                            )
                        }

                        // Inline review for exercise entries
                        if (entry.eventClockStatus == "PENDING_EXERCISE" &&
                            entry.actualExerciseDrop != null
                        ) {
                            InlineExerciseReview(
                                entry = entry,
                                onAccept = { onAcceptExerciseReview(entry) },
                                onKeep = { onDismissExerciseReview(entry) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailySummaryChip(value: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = value,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// ─── Entry row ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: Entry,
    foodsById: Map<String, Food>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val type = classifyEntry(entry)

    var menuExpanded by remember { mutableStateOf(false) }

    val typeLabel: String = when (type) {
        EntryDisplayType.FOOD -> entry.mealType?.uppercase() ?: "FOOD"
        EntryDisplayType.EXERCISE -> when (entry.intensity?.name) {
            "LOW" -> "LOW INTENSITY"
            "HIGH" -> "HIGH INTENSITY"
            else -> "MEDIUM INTENSITY"
        }
        EntryDisplayType.BOLUS -> "BOLUS"
        EntryDisplayType.FAST_CARBS -> "FAST CARBS"
    }

    val typeIcon: ImageVector = when (type) {
        EntryDisplayType.FOOD -> Icons.Default.Restaurant
        EntryDisplayType.EXERCISE -> Icons.AutoMirrored.Filled.DirectionsRun
        EntryDisplayType.BOLUS -> Icons.Default.Vaccines
        EntryDisplayType.FAST_CARBS -> Icons.Default.Bolt
    }

    val typeColor: Color = when (type) {
        EntryDisplayType.FOOD -> Color(0xFFBF360C)
        EntryDisplayType.EXERCISE -> Color(0xFF1565C0)
        EntryDisplayType.BOLUS -> DosiusPurple
        EntryDisplayType.FAST_CARBS -> Color(0xFFE65100)
    }

    val label: String? = when (type) {
        EntryDisplayType.FOOD -> entry.foodId?.let { foodsById[it]?.name }
            ?: entry.description.takeIf { it.isNotBlank() }
        EntryDisplayType.EXERCISE -> entry.exerciseType
        EntryDisplayType.BOLUS -> entry.description.takeIf { it.isNotBlank() }
        EntryDisplayType.FAST_CARBS -> entry.description.takeIf { it.isNotBlank() }
    }

    Box {
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Default.Edit, null) },
                onClick = { menuExpanded = false; onEdit() }
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menuExpanded = false; onDelete() }
            )
        }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { menuExpanded = true }
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Time
        Text(
            text = "%02d:%02d".format(entry.timestamp.hour, entry.timestamp.minute),
            style = TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            ),
            modifier = Modifier.width(48.dp)
        )

        // Glucose circle
        entry.currentGlucose?.let { GlucoseCircle(value = it) }
            ?: Spacer(Modifier.size(48.dp))

        // Value boxes
        when (type) {
            EntryDisplayType.FOOD -> {
                entry.totalCarbs?.let { carbs ->
                    val v = if (carbs % 1f == 0f) carbs.toInt().toString() else carbs.toString()
                    ValueBox(value = v, unit = "g", containerColor = Color(0xFFFFE082))
                }
                entry.insulinUnits?.let { u ->
                    val v = if (u % 1f == 0f) u.toInt().toString() else "%.1f".format(u)
                    ValueBox(value = v, unit = "u", containerColor = Color(0xFF81D4FA))
                }
            }
            EntryDisplayType.EXERCISE -> {
                entry.durationOfExercise?.let { dur ->
                    ValueBox(
                        value = dur.toInt().toString(),
                        unit = "min",
                        containerColor = Color(0xFFBBDEFB)
                    )
                }
            }
            EntryDisplayType.BOLUS -> {
                entry.insulinUnits?.let { u ->
                    val v = if (u % 1f == 0f) u.toInt().toString() else "%.1f".format(u)
                    ValueBox(value = v, unit = "u", containerColor = Color(0xFFCBB8FF))
                }
            }
            EntryDisplayType.FAST_CARBS -> {
                entry.totalCarbs?.let { carbs ->
                    val v = if (carbs % 1f == 0f) carbs.toInt().toString() else carbs.toString()
                    ValueBox(value = v, unit = "g", containerColor = Color(0xFFFFE082))
                }
            }
        }

        // Type chip + label
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(11.dp))
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = typeColor
                )
            }
            if (label != null) {
                Text(
                    text = label,
                    style = TextStyle(fontSize = 15.sp, color = Color.Black),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    } // Box
}

// ─── Inline review rows ───────────────────────────────────────────────────────

@Composable
private fun InlineMealReview(
    entry: Entry,
    onAccept: () -> Unit,
    onKeep: () -> Unit
) {
    val entered = entry.totalCarbs?.toInt() ?: return
    val inferred = entry.inferredCarbsPostEvent?.toInt() ?: return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF5F5F5)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "+${entered}g → ${inferred}g from CGM",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = onKeep,
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, Color.Gray)
            ) {
                Text("Keep", fontSize = 11.sp, color = Color.Gray)
            }
            Button(
                onClick = onAccept,
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = DosiusPurple)
            ) {
                Text("Use ${inferred}g", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun InlineExerciseReview(
    entry: Entry,
    onAccept: () -> Unit,
    onKeep: () -> Unit
) {
    val drop = entry.actualExerciseDrop?.toInt() ?: return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF5F5F5)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Glucose dropped ~${drop}mg/dL",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = onKeep,
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, Color.Gray)
            ) {
                Text("Keep", fontSize = 11.sp, color = Color.Gray)
            }
            Button(
                onClick = onAccept,
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5))
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("Confirm", fontSize = 11.sp)
            }
        }
    }
}

// ─── Shared small composables ─────────────────────────────────────────────────

private val EllipseShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Generic(Path().apply { addOval(Rect(0f, 0f, size.width, size.height)) })
}

@Composable
private fun GlucoseCircle(value: Int) {
    val color = glucoseColor(value)
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(color, EllipseShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value.toString(),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )
            Text(
                text = "mg/dl",
                style = TextStyle(
                    fontSize = 8.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )
        }
    }
}

@Composable
private fun ValueBox(value: String, unit: String, containerColor: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(containerColor, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                style = TextStyle(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )
            Text(
                text = unit,
                style = TextStyle(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black.copy(alpha = 0.7f),
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun formatDate(date: LocalDate): String {
    val today = kotlinx.datetime.Clock.System.now()
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
    val yesterday = kotlinx.datetime.Clock.System.now()
        .minus(kotlin.time.Duration.parse("24h"))
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
    val prefix = when (date) {
        today -> "TODAY  "
        yesterday -> "YESTERDAY  "
        else -> ""
    }
    val dayName = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    val monthName = date.month.name.lowercase().replaceFirstChar { it.uppercase() }
    return "$prefix$dayName, ${date.dayOfMonth} $monthName"
}

private fun glucoseColor(value: Int): Color = when {
    value < 54   -> GlucoseVeryLow
    value < 70   -> GlucoseLow
    value <= 180 -> GlucoseNormal
    value <= 250 -> GlucoseHigh
    else         -> GlucoseVeryHigh
}
