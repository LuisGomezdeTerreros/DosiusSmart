package com.dosius.smart.presentation.database

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Grass
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
fun AddFoodScreen(
    onNavigateBack: () -> Unit,
    viewModel: AddFoodViewModel = hiltViewModel()
) {
    val name by viewModel.name.collectAsState()
    val category by viewModel.category.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val carbsPer100g by viewModel.carbsPer100g.collectAsState()
    val carbsPer100gError by viewModel.carbsPer100gError.collectAsState()
    val glycemicIndex by viewModel.glycemicIndex.collectAsState()
    val glycemicIndexError by viewModel.glycemicIndexError.collectAsState()
    val isIngredient by viewModel.isIngredient.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()

    LaunchedEffect(isSaved) {
        if (isSaved) onNavigateBack()
    }

    val isSaveEnabled = name.isNotBlank() && category.isNotBlank() &&
        carbsPer100gError == null &&
        glycemicIndexError == null

    Scaffold(
        containerColor = LightBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Add Food",
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
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Type",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Gray,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TypeChip(
                            label = "Meal",
                            icon = Icons.Default.Fastfood,
                            selected = !isIngredient,
                            onClick = { viewModel.onIsIngredientChange(false) },
                            modifier = Modifier.weight(1f)
                        )
                        TypeChip(
                            label = "Ingredient",
                            icon = Icons.Default.Grass,
                            selected = isIngredient,
                            onClick = { viewModel.onIsIngredientChange(true) },
                            modifier = Modifier.weight(1f)
                        )
                    }
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
                        text = "Basic info",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Gray,
                        fontWeight = FontWeight.SemiBold
                    )
                    FormTextField(
                        value = name,
                        onValueChange = viewModel::onNameChange,
                        label = "Name *",
                        placeholder = "e.g. Chicken and rice"
                    )
                    CategoryAutocompleteField(
                        value = category,
                        onValueChange = viewModel::onCategoryChange,
                        suggestions = categories
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
                        text = "Nutritional info",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Gray,
                        fontWeight = FontWeight.SemiBold
                    )
                    FormTextField(
                        value = carbsPer100g,
                        onValueChange = viewModel::onCarbsPer100gChange,
                        label = "Carbs per 100g",
                        placeholder = "e.g. 45.0",
                        suffix = "g",
                        keyboardType = KeyboardType.Decimal,
                        errorMessage = carbsPer100gError
                    )
                    FormTextField(
                        value = glycemicIndex,
                        onValueChange = viewModel::onGlycemicIndexChange,
                        label = "Glycemic index",
                        placeholder = "0 – 100",
                        keyboardType = KeyboardType.Decimal,
                        errorMessage = glycemicIndexError
                    )
                }
            }

            Button(
                onClick = viewModel::saveFood,
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
                    text = "Save food",
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
private fun TypeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) DosiusPurple else Color(0xFFF5F5F5),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else Color.Gray,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.White else Color.Gray,
                fontSize = 14.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryAutocompleteField(
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
            label = { Text("Category *") },
            placeholder = { Text("e.g. Proteins, Cereals", color = Color.LightGray) },
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
