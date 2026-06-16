package com.dosius.smart.presentation.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosius.smart.data.alarm.GlucoseAlarmChecker
import com.dosius.smart.data.preferences.AlarmPreferences
import com.dosius.smart.domain.repository.GlucoseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlarmUiState(
    val hypoEnabled: Boolean = false,
    val hyperEnabled: Boolean = false,
    val hypoThreshold: Int = 70,
    val hyperThreshold: Int = 180
)

@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val alarmPreferences: AlarmPreferences,
    private val alarmChecker: GlucoseAlarmChecker,
    private val glucoseRepository: GlucoseRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AlarmUiState())
    val state: StateFlow<AlarmUiState> = _state

    init {
        viewModelScope.launch {
            _state.value = AlarmUiState(
                hypoEnabled = alarmPreferences.hypoEnabled.first(),
                hyperEnabled = alarmPreferences.hyperEnabled.first(),
                hypoThreshold = alarmPreferences.hypoThreshold.first(),
                hyperThreshold = alarmPreferences.hyperThreshold.first()
            )
        }
    }

    fun setHypoEnabled(enabled: Boolean) {
        _state.update { it.copy(hypoEnabled = enabled) }
        persistAndReinitialize()
    }

    fun setHyperEnabled(enabled: Boolean) {
        _state.update { it.copy(hyperEnabled = enabled) }
        persistAndReinitialize()
    }

    fun setHypoThreshold(value: Int) {
        _state.update { it.copy(hypoThreshold = value) }
        persist()
    }

    fun setHyperThreshold(value: Int) {
        _state.update { it.copy(hyperThreshold = value) }
        persist()
    }

    private fun persist() {
        val s = _state.value
        viewModelScope.launch {
            alarmPreferences.saveSettings(s.hypoEnabled, s.hyperEnabled, s.hypoThreshold, s.hyperThreshold)
        }
    }

    private fun persistAndReinitialize() {
        val s = _state.value
        viewModelScope.launch {
            alarmPreferences.saveSettings(s.hypoEnabled, s.hyperEnabled, s.hypoThreshold, s.hyperThreshold)
            alarmChecker.initializeState(glucoseRepository.getLatestReading()?.glucoseValue)
        }
    }
}
