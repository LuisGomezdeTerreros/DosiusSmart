package com.dosius.smart.presentation.dashboard

import com.dosius.smart.domain.engine.physiology.ForecastPoint
import com.dosius.smart.domain.engine.physiology.HistoricalPoint
import com.dosius.smart.domain.model.ContaminationWindow
import com.dosius.smart.domain.model.GlucoseReading

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Success(
        val currentReading: GlucoseReading?,
        val history: List<GlucoseReading>,
        val isRefreshing: Boolean = false,
        val forecast: List<ForecastPoint> = emptyList(),
        val historicalPoints: List<HistoricalPoint> = emptyList(),
        val uamPrompts: List<ContaminationWindow> = emptyList(),
        val cobMinAbsorptionPct: Int = 0
    ) : DashboardUiState()

    data class Error(val message: String) : DashboardUiState()
}
