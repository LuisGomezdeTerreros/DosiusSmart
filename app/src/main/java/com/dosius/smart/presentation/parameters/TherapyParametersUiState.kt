package com.dosius.smart.presentation.parameters

enum class ParamType { ISF, ICR }

val SLOT_BUCKETS = listOf(
    Triple("Morning",   "6:00 – 12:00",  6..11),
    Triple("Afternoon", "12:00 – 18:00", 12..17),
    Triple("Evening",   "18:00 – 0:00",  18..23),
    Triple("Night",     "0:00 – 6:00",   0..5),
)

data class SlotBucket(
    val label: String,
    val timeRange: String,
    val slotIndices: IntRange,
    val currentValue: Float,
    val suggestedMean: Float?,
    // Bayesian posterior stats — populated by M8 after first weekly fit
    val posteriorStd: Float?,
    val nObservations: Int,
)

data class EditTarget(
    val bucketLabel: String,
    val isBasal: Boolean,
    val paramType: ParamType?,
    val slotIndices: IntRange?,
    val currentValue: Float,
)

data class TherapyParamsViewState(
    val selectedParam: ParamType,
    val isfBuckets: List<SlotBucket>,
    val icrBuckets: List<SlotBucket>,
    val basalCurrentValue: Float,
    val basalSuggestedMean: Float?,
    val basalPosteriorStd: Float?,
    val basalNObservations: Int,
    val targetBgLow: Int,
    val targetBgHigh: Int,
    val editingTarget: EditTarget?,
    val hasProposedChanges: Boolean,
)

sealed class TherapyParametersUiState {
    object Loading : TherapyParametersUiState()
    data class Success(val viewState: TherapyParamsViewState) : TherapyParametersUiState()
    data class Error(val message: String) : TherapyParametersUiState()
}