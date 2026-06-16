package com.dosius.smart.presentation.parameters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dosius.smart.presentation.theme.DosiusPurple

@Composable
fun TherapyParametersScreen(
    viewModel: TherapyParametersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = Color(0xFFF7F6FB)
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is TherapyParametersUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is TherapyParametersUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is TherapyParametersUiState.Success -> {
                    TherapyParametersContent(
                        viewState = state.viewState,
                        onParamSelected = viewModel::selectParam,
                        onConfirmEdit = viewModel::confirmEdit,
                        onRangeUpdate = viewModel::updateTargetRange,
                        onStartEdit = viewModel::startEdit,
                        onAcceptAll = viewModel::acceptAll,
                        onAcceptSuggestion = viewModel::acceptSuggestion,
                    )
                }
            }
        }
    }
}

@Composable
private fun TherapyParametersContent(
    viewState: TherapyParamsViewState,
    onParamSelected: (ParamType) -> Unit,
    onConfirmEdit: (Float) -> Unit,
    onRangeUpdate: (Int, Int) -> Unit,
    onStartEdit: (EditTarget) -> Unit,
    onAcceptAll: () -> Unit,
    onAcceptSuggestion: (EditTarget, Float) -> Unit = { _, _ -> },
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Accept-all banner — only visible after the weekly fitter has run
        if (viewState.hasProposedChanges) {
            item {
                AcceptAllBanner(onAcceptAll = onAcceptAll)
            }
        }

        // Section 1 — BOLUS PARAMETERS
        item {
            SectionHeader("BOLUS PARAMETERS")
            Spacer(modifier = Modifier.height(8.dp))
            SectionCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    ParamTypeToggle(
                        selectedParam = viewState.selectedParam,
                        onParamSelected = onParamSelected
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ColumnHeaders()
                    
                    val buckets = if (viewState.selectedParam == ParamType.ISF) 
                        viewState.isfBuckets else viewState.icrBuckets
                    
                    buckets.forEachIndexed { index, bucket ->
                        val editTarget = EditTarget(
                            bucketLabel = bucket.label,
                            isBasal = false,
                            paramType = viewState.selectedParam,
                            slotIndices = bucket.slotIndices,
                            currentValue = bucket.currentValue
                        )
                        BucketRow(
                            bucket = bucket,
                            unit = if (viewState.selectedParam == ParamType.ISF) "U" else "g",
                            onConfirm = { newValue ->
                                onStartEdit(editTarget)
                                onConfirmEdit(newValue)
                            },
                            onAccept = bucket.suggestedMean?.let { mean ->
                                { onAcceptSuggestion(editTarget, mean) }
                            },
                        )
                        if (index < buckets.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                thickness = 0.5.dp,
                                color = Color.LightGray.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        // Section 2 — GLUCOSE OBJECTIVE
        item {
            SectionHeader("GLUCOSE OBJECTIVE")
            Spacer(modifier = Modifier.height(8.dp))
            SectionCard {
                GlucoseObjectiveContent(
                    targetLow = viewState.targetBgLow,
                    targetHigh = viewState.targetBgHigh,
                    onRangeUpdate = onRangeUpdate
                )
            }
        }

        // Section 3 — BASAL INSULIN
        item {
            SectionHeader("BASAL INSULIN")
            Spacer(modifier = Modifier.height(8.dp))
            SectionCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    ColumnHeaders()
                    val basalTarget = EditTarget(
                        bucketLabel = "Basal",
                        isBasal = true,
                        paramType = null,
                        slotIndices = null,
                        currentValue = viewState.basalCurrentValue
                    )
                    BasalRow(
                        currentValue = viewState.basalCurrentValue,
                        suggestedMean = viewState.basalSuggestedMean,
                        posteriorStd = viewState.basalPosteriorStd,
                        nObservations = viewState.basalNObservations,
                        onConfirm = { newValue ->
                            onStartEdit(basalTarget)
                            onConfirmEdit(newValue)
                        },
                        onAccept = viewState.basalSuggestedMean?.let { mean ->
                            { onAcceptSuggestion(basalTarget, mean) }
                        },
                    )
                }
            }
        }

    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Color.Gray,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        content()
    }
}

@Composable
private fun ParamTypeToggle(
    selectedParam: ParamType,
    onParamSelected: (ParamType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selectedParam == ParamType.ISF) Color.White else Color.Transparent)
                .clickable { onParamSelected(ParamType.ISF) }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "ISF",
                    fontWeight = if (selectedParam == ParamType.ISF) FontWeight.Bold else FontWeight.Medium,
                    color = if (selectedParam == ParamType.ISF) DosiusPurple else Color.Gray,
                    fontSize = 14.sp
                )
                Text(
                    text = "mg/dL for U",
                    color = if (selectedParam == ParamType.ISF) DosiusPurple else Color.Gray,
                    fontSize = 10.sp
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selectedParam == ParamType.ICR) Color.White else Color.Transparent)
                .clickable { onParamSelected(ParamType.ICR) }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "CR",
                    fontWeight = if (selectedParam == ParamType.ICR) FontWeight.Bold else FontWeight.Medium,
                    color = if (selectedParam == ParamType.ICR) DosiusPurple else Color.Gray,
                    fontSize = 14.sp
                )
                Text(
                    text = "g HC for U",
                    color = if (selectedParam == ParamType.ICR) DosiusPurple else Color.Gray,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun ColumnHeaders() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "TIME SLOT",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "CURRENT",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.weight(0.85f),
            textAlign = TextAlign.Center
        )
        Text(
            text = "SUGGESTED",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.weight(0.85f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.width(20.dp))
    }
}

@Composable
private fun BucketRow(
    bucket: SlotBucket,
    unit: String,
    onConfirm: (Float) -> Unit,
    onAccept: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = bucket.label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(text = bucket.timeRange, color = Color.Gray, fontSize = 12.sp)
        }

        Box(modifier = Modifier.weight(0.85f), contentAlignment = Alignment.Center) {
            InlineEditField(
                value = bucket.currentValue,
                unit = unit,
                onConfirm = onConfirm
            )
        }

        Box(modifier = Modifier.weight(0.85f), contentAlignment = Alignment.Center) {
            SuggestedBadge(
                currentValue = bucket.currentValue,
                suggestedMean = bucket.suggestedMean,
                posteriorStd = bucket.posteriorStd,
                nObservations = bucket.nObservations,
            )
        }

        if (onAccept != null) {
            IconButton(onClick = onAccept, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Accept suggestion",
                    tint = DosiusPurple,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.LightGray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun InlineEditField(
    value: Float,
    unit: String,
    onConfirm: (Float) -> Unit
) {
    var textValue by remember(value) { mutableStateOf(value.toInt().toString()) }
    val focusManager = LocalFocusManager.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        BasicTextField(
            value = textValue,
            onValueChange = { newValue ->
                if (newValue.all { it.isDigit() } && newValue.length <= 3) {
                    textValue = newValue
                }
            },
            textStyle = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = Color.Black
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    textValue.toFloatOrNull()?.let { onConfirm(it) }
                    focusManager.clearFocus()
                }
            ),
            modifier = Modifier
                .widthIn(min = 44.dp)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        textValue.toFloatOrNull()?.let { onConfirm(it) }
                    }
                },
            cursorBrush = SolidColor(DosiusPurple),
            singleLine = true
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = unit,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.Black
        )
    }
}

@Composable
private fun SuggestedBadge(
    currentValue: Float,
    suggestedMean: Float?,
    posteriorStd: Float? = null,
    nObservations: Int = 0,
) {
    if (suggestedMean == null) {
        Text(text = "—", color = Color.Gray)
    } else {
        val delta = suggestedMean - currentValue
        val deltaText = when {
            delta > 0.05f -> "+${delta.toInt()}"
            delta < -0.05f -> "${delta.toInt()}"
            else -> "=${currentValue.toInt()}"
        }
        val color = when {
            delta > 0.05f -> Color(0xFF4CAF50)
            delta < -0.05f -> Color(0xFFF44336)
            else -> Color.Gray
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${suggestedMean.toInt()}",
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Surface(
                color = color.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = deltaText,
                    color = color,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
            // Show posterior confidence stats when the Bayesian fitter has run
            if (posteriorStd != null && nObservations > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "±${posteriorStd.toInt()} (n=$nObservations)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun GlucoseObjectiveContent(
    targetLow: Int,
    targetHigh: Int,
    onRangeUpdate: (Int, Int) -> Unit
) {
    var range by remember(targetLow, targetHigh) {
        mutableStateOf(targetLow.toFloat()..targetHigh.toFloat())
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Target range", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                text = "${range.start.toInt()} – ${range.endInclusive.toInt()} mg/dL",
                color = DosiusPurple,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        RangeSlider(
            value = range,
            onValueChange = { range = it },
            valueRange = 50f..300f,
            onValueChangeFinished = {
                onRangeUpdate(range.start.toInt(), range.endInclusive.toInt())
            },
            colors = SliderDefaults.colors(
                thumbColor = DosiusPurple,
                activeTrackColor = DosiusPurple,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "50", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(text = "300", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
private fun BasalRow(
    currentValue: Float,
    suggestedMean: Float?,
    posteriorStd: Float? = null,
    nObservations: Int = 0,
    onConfirm: (Float) -> Unit,
    onAccept: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Morning", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(text = "Once daily", color = Color.Gray, fontSize = 12.sp)
        }

        Box(modifier = Modifier.weight(0.85f), contentAlignment = Alignment.Center) {
            InlineEditField(
                value = currentValue,
                unit = "U",
                onConfirm = onConfirm
            )
        }

        Box(modifier = Modifier.weight(0.85f), contentAlignment = Alignment.Center) {
            SuggestedBadge(
                currentValue = currentValue,
                suggestedMean = suggestedMean,
                posteriorStd = posteriorStd,
                nObservations = nObservations
            )
        }

        if (onAccept != null) {
            IconButton(onClick = onAccept, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Accept suggestion",
                    tint = DosiusPurple,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.LightGray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F6FB)
@Composable
private fun TherapyParametersPreview() {
    MaterialTheme {
        TherapyParametersContent(
            viewState = TherapyParamsViewState(
                selectedParam = ParamType.ISF,
                isfBuckets = listOf(
                    SlotBucket("Morning",   "6:00 – 12:00",  6..11,  currentValue = 42f, suggestedMean = 47f, posteriorStd = 4f,  nObservations = 5),
                    SlotBucket("Afternoon", "12:00 – 18:00", 12..17, currentValue = 38f, suggestedMean = 36f, posteriorStd = 3f,  nObservations = 4),
                    SlotBucket("Evening",   "18:00 – 0:00",  18..23, currentValue = 40f, suggestedMean = 40f, posteriorStd = 5f,  nObservations = 3),
                    SlotBucket("Night",     "0:00 – 6:00",   0..5,   currentValue = 50f, suggestedMean = null, posteriorStd = null, nObservations = 0),
                ),
                icrBuckets = listOf(
                    SlotBucket("Morning",   "6:00 – 12:00",  6..11,  currentValue = 10f, suggestedMean = 12f, posteriorStd = 1f,  nObservations = 4),
                    SlotBucket("Afternoon", "12:00 – 18:00", 12..17, currentValue = 12f, suggestedMean = 11f, posteriorStd = 2f,  nObservations = 3),
                    SlotBucket("Evening",   "18:00 – 0:00",  18..23, currentValue = 11f, suggestedMean = null, posteriorStd = null, nObservations = 0),
                    SlotBucket("Night",     "0:00 – 6:00",   0..5,   currentValue = 13f, suggestedMean = null, posteriorStd = null, nObservations = 0),
                ),
                basalCurrentValue = 18f,
                basalSuggestedMean = 20f,
                basalPosteriorStd = 2f,
                basalNObservations = 6,
                targetBgLow = 90,
                targetBgHigh = 140,
                editingTarget = null,
                hasProposedChanges = true,
            ),
            onParamSelected = {},
            onConfirmEdit = {},
            onRangeUpdate = { _, _ -> },
            onStartEdit = {},
            onAcceptAll = {}
        )
    }
}

@Composable
private fun AcceptAllBanner(onAcceptAll: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DosiusPurple.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "New parameter suggestions",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = DosiusPurple
                )
                Text(
                    text = "The weekly fit has updated its recommendations. Review below or accept all.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onAcceptAll,
                colors = ButtonDefaults.buttonColors(containerColor = DosiusPurple),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Accept all", fontSize = 12.sp)
            }
        }
    }
}

