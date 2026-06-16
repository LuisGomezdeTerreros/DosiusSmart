package com.dosius.smart.presentation.database

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dosius.smart.presentation.theme.DosiusPurple
import com.dosius.smart.presentation.theme.DosiusPurpleContainer
import com.dosius.smart.presentation.theme.LightBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExerciseScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddExerciseViewModel = hiltViewModel()
) {
    val type by viewModel.type.collectAsState()
    val expectedGlucoseDropPerHour by viewModel.expectedGlucoseDropPerHour.collectAsState()
    val expectedGlucoseDropPerHourError by viewModel.expectedGlucoseDropPerHourError.collectAsState()
    val confidencePercentage by viewModel.confidencePercentage.collectAsState()
    val confidencePercentageError by viewModel.confidencePercentageError.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()

    LaunchedEffect(isSaved) {
        if (isSaved) onNavigateBack()
    }

    val isSaveEnabled = type.isNotBlank() &&
        expectedGlucoseDropPerHourError == null &&
        confidencePercentageError == null

    Scaffold(
        containerColor = LightBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Add Exercise",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        text = "Exercise info",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Gray,
                        fontWeight = FontWeight.SemiBold
                    )
                    FormTextField(
                        value = type,
                        onValueChange = viewModel::onTypeChange,
                        label = "Type *",
                        placeholder = "e.g. Walking, Running"
                    )
                }
            }

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
                        text = "Physiological impact",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Gray,
                        fontWeight = FontWeight.SemiBold
                    )
                    FormTextField(
                        value = expectedGlucoseDropPerHour,
                        onValueChange = viewModel::onExpectedGlucoseDropPerHourChange,
                        label = "Glucose drop per hour",
                        placeholder = "e.g. 40.0",
                        suffix = "mg/dL",
                        keyboardType = KeyboardType.Decimal,
                        errorMessage = expectedGlucoseDropPerHourError
                    )
                    FormTextField(
                        value = confidencePercentage,
                        onValueChange = viewModel::onConfidencePercentageChange,
                        label = "Confidence",
                        placeholder = "0 – 100",
                        suffix = "%",
                        keyboardType = KeyboardType.Number,
                        errorMessage = confidencePercentageError
                    )
                }
            }

            Button(
                onClick = viewModel::saveExercise,
                enabled = isSaveEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DosiusPurple,
                    disabledContainerColor = DosiusPurpleContainer
                )
            ) {
                Text(
                    text = "Save exercise",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (isSaveEnabled) Color.White else Color.Gray
                )
            }

            Text(
                text = "* Required fields",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun FormTextField(
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
