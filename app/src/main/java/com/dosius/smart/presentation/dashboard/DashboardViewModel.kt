package com.dosius.smart.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosius.smart.data.preferences.AlarmPreferences
import com.dosius.smart.data.repository.ContaminationRepository
import com.dosius.smart.data.repository.DatabaseSeeder
import com.dosius.smart.data.repository.EntryRepository
import com.dosius.smart.data.repository.ForecastRepository
import com.dosius.smart.data.repository.LibreLinkUpRepository
import com.dosius.smart.data.repository.RecommendationRepository
import com.dosius.smart.domain.engine.recommendation.RecommendationResult
import com.dosius.smart.domain.repository.GlucoseRepository
import com.dosius.smart.domain.model.ContaminationWindow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val glucoseRepository: GlucoseRepository,
    private val libreLinkUpRepository: LibreLinkUpRepository,
    private val entriesRepository: EntryRepository,
    private val recommendationRepository: RecommendationRepository,
    private val forecastRepository: ForecastRepository,
    private val contaminationRepository: ContaminationRepository,
    private val alarmPreferences: AlarmPreferences,
    private val seeder: DatabaseSeeder
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _correctionRecommendation = MutableStateFlow<RecommendationResult?>(null)
    val correctionRecommendation: StateFlow<RecommendationResult?> = _correctionRecommendation.asStateFlow()

    private val _uamPrompts = MutableStateFlow<List<ContaminationWindow>>(emptyList())

    val alarmStatus: StateFlow<AlarmStatus> = combine(
        alarmPreferences.hypoEnabled,
        alarmPreferences.hyperEnabled,
        alarmPreferences.hypoThreshold,
        alarmPreferences.hyperThreshold
    ) { hypoOn, hyperOn, hypoThr, hyperThr ->
        AlarmStatus(hypoOn, hyperOn, hypoThr, hyperThr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmStatus())

    private var correctionJob: Job? = null

    // Dismissed window IDs persist for the ViewModel lifetime so refreshes don't restore them.
    private val dismissedUamIds = mutableSetOf<String>()

    init {
        viewModelScope.launch { seeder.seedIfEmpty() }
        viewModelScope.launch {
            combine(
                glucoseRepository.getReadingsStream(),
                forecastRepository.observeLatest(),
                forecastRepository.historicalPoints,
                _uamPrompts
            ) { glucoseReadings, forecast, historicalPoints, uamPrompts ->
                val pointsWithCob = historicalPoints.filter { it.cob > 0f }
                val cobMinPct = if (pointsWithCob.isNotEmpty()) {
                    (pointsWithCob.map { it.cobMinAbsorptionRatio }.average() * 100).toInt()
                } else 0
                DashboardUiState.Success(
                    currentReading = glucoseReadings.firstOrNull(),
                    history = glucoseReadings,
                    isRefreshing = false,
                    forecast = forecast ?: emptyList(),
                    historicalPoints = historicalPoints,
                    uamPrompts = uamPrompts,
                    cobMinAbsorptionPct = cobMinPct
                )
            }.collect { result ->
                _uiState.value = result
            }
        }
        // Recompute IOB/COB/forecast whenever entries change (e.g. after logging a meal)
        viewModelScope.launch {
            entriesRepository.getAllEntries().collect {
                runCatching {
                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    forecastRepository.computeAndStore(now)
                }
            }
        }
        // Reload UAM prompts on every glucose refresh tick so new windows written
        // by GlucoseRefreshService appear without requiring an entry change.
        viewModelScope.launch {
            glucoseRepository.getReadingsStream().collect { _ ->
                val entries = entriesRepository.getAllEntries().first()
                val since = Clock.System.now().minus(6.hours).epochSeconds
                val tz = TimeZone.currentSystemDefault()
                val uamWindows = contaminationRepository.getUAM(since)
                    .filter { window ->
                        window.id !in dismissedUamIds &&
                        entries.none { entry ->
                            entry.totalCarbs != null &&
                            entry.timestamp >= window.startTime &&
                            entry.timestamp <= window.endTime
                        }
                    }
                _uamPrompts.value = collapseToMostRecent(uamWindows, tz)
            }
        }

        refresh()
    }

    fun requestCorrection() {
        val forecast = (_uiState.value as? DashboardUiState.Success)?.forecast
        if (forecast.isNullOrEmpty()) return
        correctionJob?.cancel()
        correctionJob = viewModelScope.launch {
            runCatching { recommendationRepository.computeSmartCorrection(forecast) }
                .onSuccess { _correctionRecommendation.value = it }
        }
    }

    fun dismissCorrectionRecommendation() {
        _correctionRecommendation.value = null
    }

    fun dismissUAMPrompt(windowId: String) {
        dismissedUamIds.add(windowId)
        _uamPrompts.value = _uamPrompts.value.filter { it.id != windowId }
    }

    // Collapses overlapping/near windows into distinct episodes and returns only the latest one.
    internal fun collapseToMostRecent(
        windows: List<ContaminationWindow>,
        tz: TimeZone
    ): List<ContaminationWindow> {
        if (windows.isEmpty()) return emptyList()
        // Iterate most-recent first; add a window only if it doesn't overlap (±30 min) any already kept.
        val kept = mutableListOf<ContaminationWindow>()
        for (window in windows.sortedByDescending { it.startTime.toInstant(tz).epochSeconds }) {
            val wStart = window.startTime.toInstant(tz).epochSeconds
            val wEnd = window.endTime.toInstant(tz).epochSeconds
            val overlaps = kept.any { existing ->
                val eStart = existing.startTime.toInstant(tz).epochSeconds
                val eEnd = existing.endTime.toInstant(tz).epochSeconds
                wStart <= eEnd + 30.minutes.inWholeSeconds && wEnd >= eStart - 30.minutes.inWholeSeconds
            }
            if (!overlaps) {
                kept.add(window)
                break  // show at most one prompt at a time
            }
        }
        return kept
    }

    fun refresh() {
        viewModelScope.launch {
            (_uiState.value as? DashboardUiState.Success)?.let {
                _uiState.value = it.copy(isRefreshing = true)
            }
            try {
                libreLinkUpRepository.refresh()
            } catch (e: Exception) {
                (_uiState.value as? DashboardUiState.Success)?.let {
                    _uiState.value = it.copy(isRefreshing = false)
                }
            }
            // Recompute forecast/IOB/COB with latest glucose readings
            runCatching {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                forecastRepository.computeAndStore(now)
            }
        }
    }

}

data class AlarmStatus(
    val hypoEnabled: Boolean = false,
    val hyperEnabled: Boolean = false,
    val hypoThreshold: Int = 70,
    val hyperThreshold: Int = 180
)
