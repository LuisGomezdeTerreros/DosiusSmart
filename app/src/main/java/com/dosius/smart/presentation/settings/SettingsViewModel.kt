package com.dosius.smart.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosius.smart.data.preferences.CredentialPreferences
import com.dosius.smart.data.repository.LibreLinkUpRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val credentialPreferences: CredentialPreferences,
    private val libreLinkUpRepository: LibreLinkUpRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                credentialPreferences.email, credentialPreferences.password
            ) { email, password ->
                email to password
            }.collect { (email, password) ->
                _uiState.update { it.copy(email = email, password = password) }
            }
        }
    }

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, saveResult = SaveResult.Idle) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, saveResult = SaveResult.Idle) }
    }

    fun saveAndConnect() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                credentialPreferences.saveCredentials(
                    _uiState.value.email.trim(), _uiState.value.password
                )
                libreLinkUpRepository.refresh()
                _uiState.update { it.copy(isSaving = false, saveResult = SaveResult.Success) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveResult = SaveResult.Error(e.message ?: "Connection failed")
                    )
                }
            }
        }
    }


}