package com.dosius.smart.presentation.parameters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosius.smart.data.repository.TherapyParameterRepository
import com.dosius.smart.domain.engine.learning.BayesianParameterFitter
import com.dosius.smart.domain.model.TherapyParameter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import kotlin.math.sqrt

@HiltViewModel
class TherapyParametersViewModel @Inject constructor(
    private val repository: TherapyParameterRepository
) : ViewModel() {

    private val _allRows: StateFlow<List<TherapyParameter>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedParam = MutableStateFlow(ParamType.ISF)
    private val _editingTarget = MutableStateFlow<EditTarget?>(null)
    private val _mockState = MutableStateFlow(MOCK_VIEW_STATE)

    val uiState: StateFlow<TherapyParametersUiState> = if (USE_MOCK) {
        combine(_selectedParam, _editingTarget, _mockState) { selectedParam, editingTarget, mock ->
            TherapyParametersUiState.Success(
                mock.copy(selectedParam = selectedParam, editingTarget = editingTarget)
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TherapyParametersUiState.Loading)
    } else {
        combine(
            _allRows, _selectedParam, _editingTarget
        ) { rows, selectedParam, editingTarget ->
            TherapyParametersUiState.Success(buildViewState(rows, selectedParam, editingTarget))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TherapyParametersUiState.Loading)
    }

    fun selectParam(type: ParamType) {
        _selectedParam.value = type
    }

    fun startEdit(target: EditTarget) {
        _editingTarget.value = target
    }

    fun cancelEdit() {
        _editingTarget.value = null
    }

    fun confirmEdit(newValue: Float) {
        val target = _editingTarget.value ?: return
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val allRows = _allRows.value

        viewModelScope.launch {
            val toUpdate: List<TherapyParameter> = if (target.isBasal) {
                allRows
                    .filter { it.slotIndex == 0 && it.parameterType == "BASAL" }
                    .map { it.copy(currentValue = newValue, lastUpdated = now) }
            } else {
                val typeName = target.paramType!!.name  // "ISF" or "ICR"
                target.slotIndices!!.mapNotNull { slot ->
                    allRows
                        .find { it.slotIndex == slot && it.parameterType == typeName }
                        ?.copy(currentValue = newValue, lastUpdated = now)
                }
            }
            repository.upsertAll(toUpdate)
            _editingTarget.value = null
        }
    }

    /**
     * Accepts all Bayesian suggestions by copying each posterior mean → currentValue
     * for every ISF and ICR row that has at least one observation.
     */
    fun acceptSuggestion(target: EditTarget, suggestedMean: Float) {
        if (USE_MOCK) {
            val s = _mockState.value
            val updated = if (target.isBasal) {
                s.copy(basalCurrentValue = suggestedMean)
            } else {
                val newIsf = if (target.paramType == ParamType.ISF)
                    s.isfBuckets.map { if (it.label == target.bucketLabel) it.copy(currentValue = suggestedMean) else it }
                else s.isfBuckets
                val newIcr = if (target.paramType == ParamType.ICR)
                    s.icrBuckets.map { if (it.label == target.bucketLabel) it.copy(currentValue = suggestedMean) else it }
                else s.icrBuckets
                s.copy(isfBuckets = newIsf, icrBuckets = newIcr)
            }
            _mockState.value = updated.copy(hasProposedChanges = mockHasPending(updated))
            return
        }
        val guardrail = when (target.paramType) {
            ParamType.ICR  -> BayesianParameterFitter.ICR_GUARDRAIL
            ParamType.ISF  -> BayesianParameterFitter.ISF_GUARDRAIL
            else           -> BayesianParameterFitter.BASAL_GUARDRAIL
        }
        val delta = suggestedMean - target.currentValue
        val maxDelta = kotlin.math.abs(target.currentValue) * guardrail
        val clampedValue = if (kotlin.math.abs(delta) > maxDelta)
            target.currentValue + kotlin.math.sign(delta) * maxDelta
        else
            suggestedMean
        startEdit(target)
        confirmEdit(clampedValue)
    }

    fun acceptAll() {
        if (USE_MOCK) {
            val s = _mockState.value
            val updated = s.copy(
                isfBuckets = s.isfBuckets.map { b -> b.suggestedMean?.let { b.copy(currentValue = it) } ?: b },
                icrBuckets = s.icrBuckets.map { b -> b.suggestedMean?.let { b.copy(currentValue = it) } ?: b },
                basalCurrentValue = s.basalSuggestedMean ?: s.basalCurrentValue,
            )
            _mockState.value = updated.copy(hasProposedChanges = mockHasPending(updated))
            return
        }
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val allRows = _allRows.value
        viewModelScope.launch {
            val toUpdate = allRows
                .filter {
                    it.parameterType in listOf("ISF", "ICR", "BASAL") &&
                    it.nObservations >= if (it.parameterType == "ICR") MIN_ICR_OBSERVATIONS else 1
                }
                .map { row ->
                    val guardrail = when (row.parameterType) {
                        "ISF"   -> BayesianParameterFitter.ISF_GUARDRAIL
                        "ICR"   -> BayesianParameterFitter.ICR_GUARDRAIL
                        "BASAL" -> BayesianParameterFitter.BASAL_GUARDRAIL
                        else    -> BayesianParameterFitter.ISF_GUARDRAIL
                    }
                    val delta = row.mean - row.currentValue
                    val maxDelta = kotlin.math.abs(row.currentValue) * guardrail
                    val accepted = if (kotlin.math.abs(delta) > maxDelta)
                        row.currentValue + kotlin.math.sign(delta) * maxDelta
                    else
                        row.mean
                    row.copy(currentValue = accepted, lastUpdated = now)
                }
            if (toUpdate.isNotEmpty()) repository.upsertAll(toUpdate)
        }
    }

    fun updateTargetRange(low: Int, high: Int) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val allRows = _allRows.value
        viewModelScope.launch {
            val toUpdate = allRows
                .filter { it.slotIndex == 0 && it.parameterType in listOf("TARGET_LOW", "TARGET_HIGH") }
                .map { row ->
                    val newValue = if (row.parameterType == "TARGET_LOW") low.toFloat() else high.toFloat()
                    row.copy(currentValue = newValue, lastUpdated = now)
                }
            repository.upsertAll(toUpdate)
        }
    }

    companion object {
        private const val MIN_ICR_OBSERVATIONS = 3

        const val USE_MOCK = false

        val MOCK_VIEW_STATE = TherapyParamsViewState(
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
        )
    }

    private fun mockHasPending(s: TherapyParamsViewState): Boolean =
        s.isfBuckets.any { it.suggestedMean != null && kotlin.math.abs(it.suggestedMean - it.currentValue) > 0.5f } ||
        s.icrBuckets.any { it.suggestedMean != null && kotlin.math.abs(it.suggestedMean - it.currentValue) > 0.5f } ||
        (s.basalSuggestedMean != null && kotlin.math.abs(s.basalSuggestedMean - s.basalCurrentValue) > 0.5f)

    private fun buildViewState(
        rows: List<TherapyParameter>,
        selectedParam: ParamType,
        editingTarget: EditTarget?,
    ): TherapyParamsViewState {
        fun bucketsFor(type: String): List<SlotBucket> = SLOT_BUCKETS.map { (label, timeRange, slots) ->
            val slotRows = slots.mapNotNull { h -> rows.find { it.slotIndex == h && it.parameterType == type } }
            val representative = slotRows.firstOrNull()
            val minObs = if (type == "ICR") MIN_ICR_OBSERVATIONS else 1
            val fittedRows = slotRows.filter { it.nObservations >= minObs }
            val suggestedMean = if (fittedRows.isEmpty()) null
                                else fittedRows.map { it.mean }.average().toFloat()
            val posteriorStd  = if (fittedRows.isEmpty()) null
                                else fittedRows.map { sqrt(it.variance.coerceAtLeast(0f)) }.average().toFloat()
            val nObs = fittedRows.maxOfOrNull { it.nObservations } ?: 0
            SlotBucket(
                label = label,
                timeRange = timeRange,
                slotIndices = slots,
                currentValue = representative?.currentValue ?: 0f,
                suggestedMean = suggestedMean,
                posteriorStd = posteriorStd,
                nObservations = nObs,
            )
        }

        val basalRow = rows.find { it.slotIndex == 0 && it.parameterType == "BASAL" }
        val targetLow  = rows.find { it.slotIndex == 0 && it.parameterType == "TARGET_LOW"  }?.currentValue?.toInt() ?: 70
        val targetHigh = rows.find { it.slotIndex == 0 && it.parameterType == "TARGET_HIGH" }?.currentValue?.toInt() ?: 180

        val hasProposedChanges = rows.any { row ->
            row.parameterType in listOf("ISF", "ICR", "BASAL") &&
            row.nObservations >= (if (row.parameterType == "ICR") MIN_ICR_OBSERVATIONS else 1) &&
            kotlin.math.abs(row.mean - row.currentValue) > 0.5f
        }

        return TherapyParamsViewState(
            selectedParam = selectedParam,
            isfBuckets = bucketsFor("ISF"),
            icrBuckets = bucketsFor("ICR"),
            basalCurrentValue = basalRow?.currentValue ?: 0f,
            basalSuggestedMean = basalRow?.takeIf { it.nObservations > 0 }?.mean,
            basalPosteriorStd = basalRow?.takeIf { it.nObservations > 0 }?.let { sqrt(it.variance.coerceAtLeast(0f)) },
            basalNObservations = basalRow?.nObservations ?: 0,
            targetBgLow = targetLow,
            targetBgHigh = targetHigh,
            editingTarget = editingTarget,
            hasProposedChanges = hasProposedChanges,
        )
    }
}
