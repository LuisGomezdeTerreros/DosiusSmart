package com.dosius.smart.presentation.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dosius.smart.R
import com.dosius.smart.presentation.theme.DosiusPurple

private val BackgroundColor = Color(0xFFF7F6FB)
private val TotalSteps = 3

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.finished) {
        if (uiState.finished) onFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StepIndicator(currentStep = uiState.step, totalSteps = TotalSteps)
        Spacer(Modifier.height(32.dp))

        AnimatedContent(
            targetState = uiState.step,
            transitionSpec = {
                (slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(200)))
            },
            modifier = Modifier.weight(1f),
            label = "onboarding_step"
        ) { step ->
            when (step) {
                0 -> WelcomePage(
                    tosAccepted = uiState.tosAccepted,
                    onTosAccepted = viewModel::onTosAccepted,
                    onNext = viewModel::nextStep
                )
                1 -> LibreLinkPage(
                    email = uiState.email,
                    password = uiState.password,
                    isConnecting = uiState.isConnecting,
                    connectionResult = uiState.connectionResult,
                    libreLinked = uiState.libreLinked,
                    onEmailChange = viewModel::onEmailChange,
                    onPasswordChange = viewModel::onPasswordChange,
                    onConnect = viewModel::connectLibre,
                    onNext = viewModel::nextStep
                )
                2 -> TherapyParamsPage(
                    isfInput = uiState.isfInput,
                    icrInput = uiState.icrInput,
                    basalInput = uiState.basalInput,
                    isSaving = uiState.isSaving,
                    onIsfChange = viewModel::onIsfChange,
                    onIcrChange = viewModel::onIcrChange,
                    onBasalChange = viewModel::onBasalChange,
                    onFinish = viewModel::finishOnboarding
                )
            }
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int, totalSteps: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(totalSteps) { index ->
            val isActive = index == currentStep
            val isDone = index < currentStep
            Box(
                modifier = Modifier
                    .size(if (isActive) 24.dp else 8.dp, 8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isDone || isActive -> DosiusPurple
                            else -> DosiusPurple.copy(alpha = 0.2f)
                        }
                    )
            )
        }
    }
}

@Composable
private fun WelcomePage(
    tosAccepted: Boolean,
    onTosAccepted: (Boolean) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_dosius),
            contentDescription = null,
            modifier = Modifier.size(96.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Dosius Smart",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Intelligent decision support for insulin-dependent diabetes",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .height(220.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Terms of Service",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = """
[PLACEHOLDER — replace with final legal text before release]

1. Scope. Dosius Smart is a decision-support tool intended as a complement to — not a replacement for — qualified medical supervision. All recommendations are informational only.

2. Not a medical device. This application has not been cleared or approved as a medical device by any regulatory authority. Do not use it as the sole basis for clinical decisions.

3. User responsibility. You are solely responsible for any actions taken based on information provided by this app. Always consult your healthcare provider before adjusting insulin therapy.

4. Data privacy. All glucose data and therapy parameters are stored locally on your device. Connection to LibreLinkUp is made directly from your device and credentials are stored encrypted in the system keystore.

5. Limitation of liability. The developers disclaim all liability for any harm arising from use of this application.

6. Academic context. This application is a proof-of-concept developed as a Final Degree Project (TFG) at the University of Seville and is not intended for commercial distribution.
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(1.dp, if (tosAccepted) DosiusPurple else Color.LightGray, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = tosAccepted, onCheckedChange = onTosAccepted)
            Spacer(Modifier.width(4.dp))
            Text(
                text = "I have read and accept the Terms of Service",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onNext,
            enabled = tosAccepted,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = DosiusPurple)
        ) {
            Text("Next", modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
private fun LibreLinkPage(
    email: String,
    password: String,
    isConnecting: Boolean,
    connectionResult: ConnectionResult,
    libreLinked: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Connect your CGM",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Enter your LibreLinkUp credentials to receive automatic glucose readings every 5 minutes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text("LibreLinkUp email") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                when (connectionResult) {
                    is ConnectionResult.Success -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Connected successfully", color = Color(0xFF4CAF50),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    is ConnectionResult.Error -> Text(
                        text = connectionResult.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    is ConnectionResult.Idle -> Unit
                }

                Button(
                    onClick = onConnect,
                    enabled = !isConnecting && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DosiusPurple)
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("Connect")
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onNext,
                enabled = libreLinked,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DosiusPurple)
            ) {
                Text("Next", modifier = Modifier.padding(vertical = 4.dp))
            }
            TextButton(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Skip for now",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun TherapyParamsPage(
    isfInput: String,
    icrInput: String,
    basalInput: String,
    isSaving: Boolean,
    onIsfChange: (String) -> Unit,
    onIcrChange: (String) -> Unit,
    onBasalChange: (String) -> Unit,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Therapy Parameters",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Optional. Enter your starting values if you know them — the app will refine these automatically over time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ParamField(
                    label = "Insulin Sensitivity Factor (ISF)",
                    unit = "mg/dL per unit",
                    hint = "e.g. 50",
                    value = isfInput,
                    onValueChange = onIsfChange
                )
                ParamField(
                    label = "Insulin-to-Carb Ratio (ICR)",
                    unit = "g of carbs per unit",
                    hint = "e.g. 10",
                    value = icrInput,
                    onValueChange = onIcrChange
                )
                ParamField(
                    label = "Long-acting Insulin",
                    unit = "total units / day (e.g. Lantus, Tresiba)",
                    hint = "e.g. 20",
                    value = basalInput,
                    onValueChange = onBasalChange
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DosiusPurple.copy(alpha = 0.07f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Text(
                text = "These values apply uniformly across all 24 hours. You can set time-of-day variations later in the Parameters screen.",
                style = MaterialTheme.typography.bodySmall,
                color = DosiusPurple,
                modifier = Modifier.padding(12.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onFinish,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = DosiusPurple)
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else {
                Text("Finish setup", modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun ParamField(
    label: String,
    unit: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text(unit, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.matches(Regex("^\\d*\\.?\\d*$"))) onValueChange(it) },
            placeholder = { Text(hint, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
