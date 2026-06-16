package com.dosius.smart.presentation.settings


data class SettingsUiState(
    val email: String = "",
    val password: String = "",
    val isSaving: Boolean = false,
    val saveResult: SaveResult = SaveResult.Idle
) {

}

sealed class SaveResult {
    data object Idle : SaveResult()
    data object Success : SaveResult()
    data class Error(val message: String) : SaveResult()
}
