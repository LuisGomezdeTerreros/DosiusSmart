package com.dosius.smart.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosius.smart.data.preferences.AppPreferences
import com.dosius.smart.data.preferences.CredentialPreferences
import com.dosius.smart.data.repository.LibreLinkUpRepository
import com.dosius.smart.data.repository.TherapyParameterRepository
import com.dosius.smart.domain.model.TherapyParameter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

data class OnboardingUiState(
    val step: Int = 0,
    val tosAccepted: Boolean = false,
    val email: String = "",
    val password: String = "",
    val isConnecting: Boolean = false,
    val connectionResult: ConnectionResult = ConnectionResult.Idle,
    val libreLinked: Boolean = false,
    val isfInput: String = "",
    val icrInput: String = "",
    val basalInput: String = "",
    val isSaving: Boolean = false,
    val finished: Boolean = false
)

sealed class ConnectionResult {
    data object Idle : ConnectionResult()
    data object Success : ConnectionResult()
    data class Error(val message: String) : ConnectionResult()
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val credentialPreferences: CredentialPreferences,
    private val libreLinkUpRepository: LibreLinkUpRepository,
    private val therapyParameterRepository: TherapyParameterRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        // Existing users who already have credentials saved skip onboarding automatically.
        // This handles installs that pre-date the onboarding feature.
        viewModelScope.launch {
            val existingEmail = credentialPreferences.email.first()
            if (existingEmail.isNotBlank()) {
                appPreferences.setOnboardingComplete()
            }
        }
    }

    fun onTosAccepted(accepted: Boolean) {
        _uiState.update { it.copy(tosAccepted = accepted) }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, connectionResult = ConnectionResult.Idle) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, connectionResult = ConnectionResult.Idle) }
    }

    fun connectLibre() {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, connectionResult = ConnectionResult.Idle) }
            try {
                credentialPreferences.saveCredentials(
                    _uiState.value.email.trim(),
                    _uiState.value.password
                )
                libreLinkUpRepository.refresh()
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        connectionResult = ConnectionResult.Success,
                        libreLinked = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        connectionResult = ConnectionResult.Error(e.message ?: "Connection failed")
                    )
                }
            }
        }
    }

    fun nextStep() {
        _uiState.update { it.copy(step = it.step + 1) }
    }

    fun onIsfChange(value: String) {
        _uiState.update { it.copy(isfInput = value) }
    }

    fun onIcrChange(value: String) {
        _uiState.update { it.copy(icrInput = value) }
    }

    fun onBasalChange(value: String) {
        _uiState.update { it.copy(basalInput = value) }
    }

    fun finishOnboarding() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            saveTherapyParametersIfProvided()
            appPreferences.setOnboardingComplete()
            _uiState.update { it.copy(isSaving = false, finished = true) }
        }
    }

    private suspend fun saveTherapyParametersIfProvided() {
        val state = _uiState.value
        val isfVal = state.isfInput.toFloatOrNull()
        val icrVal = state.icrInput.toFloatOrNull()
        val basalVal = state.basalInput.toFloatOrNull()

        if (isfVal == null && icrVal == null && basalVal == null) return

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val existingRows = therapyParameterRepository.getAllSuspend()

        if (existingRows.isEmpty()) {
            val rows = (0..23).flatMap { slot ->
                listOf(
                    TherapyParameter(slot, "ISF",   isfVal ?: 50f,  100f, 0, now, isfVal ?: 50f),
                    TherapyParameter(slot, "ICR",   icrVal ?: 10f,  4f,   0, now, icrVal ?: 10f),
                    TherapyParameter(slot, "BASAL", (basalVal?.div(24f)) ?: 0f, 1f,   0, now, (basalVal?.div(24f)) ?: 0f)
                )
            }
            therapyParameterRepository.upsertAll(rows)
        } else {
            val updated = existingRows.mapNotNull { row ->
                when (row.parameterType) {
                    "ISF"   -> isfVal?.let   { row.copy(currentValue = it, mean = it, lastUpdated = now) }
                    "ICR"   -> icrVal?.let   { row.copy(currentValue = it, mean = it, lastUpdated = now) }
                    "BASAL" -> basalVal?.let { row.copy(currentValue = it / 24f, mean = it / 24f, lastUpdated = now) }
                    else -> null
                }
            }
            therapyParameterRepository.upsertAll(updated)
        }
    }
}
