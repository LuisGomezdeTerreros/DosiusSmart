package com.dosius.smart.presentation.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dosius.smart.domain.engine.physiology.ForecastPoint
import com.dosius.smart.domain.engine.physiology.HistoricalPoint
import com.dosius.smart.domain.engine.recommendation.RecommendationResult
import com.dosius.smart.domain.model.GlucoseReading
import com.dosius.smart.domain.model.GlucoseTrend
import com.dosius.smart.presentation.entry.QuickEntrySheet
import com.dosius.smart.presentation.theme.GlucoseHigh
import com.dosius.smart.presentation.theme.GlucoseLow
import com.dosius.smart.presentation.theme.GlucoseNormal
import com.dosius.smart.presentation.theme.GlucoseVeryHigh
import com.dosius.smart.presentation.theme.GlucoseVeryLow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

// Internal chart point types — private to this file
private data class GlucosePt(val ms: Long, val value: Int)
private data class IobCobPt(val ms: Long, val iob: Float, val cob: Float, val cobMinAbs: Boolean = false)
private data class FcstChartPt(val ms: Long, val bg: Float, val iob: Float, val cob: Float)

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val correctionRec by viewModel.correctionRecommendation.collectAsStateWithLifecycle()
    val alarmStatus by viewModel.alarmStatus.collectAsStateWithLifecycle()
    var showQuickEntry by remember { mutableStateOf(false) }

    when (val state = uiState) {
        is DashboardUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is DashboardUiState.Success -> {
            DashboardContent(
                state = state,
                correctionRecommendation = correctionRec,
                alarmStatus = alarmStatus,
                onDismissCorrection = viewModel::dismissCorrectionRecommendation,
                onRequestCorrection = viewModel::requestCorrection,
                onLogMeal = { showQuickEntry = true },
                onDismissUAMPrompt = viewModel::dismissUAMPrompt
            )

            if (showQuickEntry) {
                QuickEntrySheet(
                    onEntryTypeSelected = { showQuickEntry = false },
                    onDismiss = { showQuickEntry = false }
                )
            }
        }
        is DashboardUiState.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ─── Success layout ───────────────────────────────────────────────────────────

@Composable
private fun DashboardContent(
    state: DashboardUiState.Success,
    correctionRecommendation: RecommendationResult?,
    alarmStatus: AlarmStatus,
    onDismissCorrection: () -> Unit,
    onRequestCorrection: () -> Unit,
    onLogMeal: () -> Unit,
    onDismissUAMPrompt: (String) -> Unit
) {
    val hypoWarning = remember(state.forecast) {
        state.forecast.take(6).any { it.predictedBg < 70f }
    }
    var hypoWarningDismissed by remember { mutableStateOf(false) }
    var cobWarningDismissed by remember { mutableStateOf(false) }
    val showCobWarning = state.cobMinAbsorptionPct > 40 && !cobWarningDismissed
        && state.historicalPoints.any { it.cob > 0f }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (state.isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.currentReading
            ?.let { GlucoseCard(it) }
            ?: Text(
                text = "No reading available",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

        if (correctionRecommendation == null) {
            OutlinedButton(
                onClick = onRequestCorrection,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Get correction recommendation", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (hypoWarning && !hypoWarningDismissed) {
            PredictiveHypoWarning(onDismiss = { hypoWarningDismissed = true })
        }

        AnimatedVisibility(visible = correctionRecommendation != null, enter = fadeIn()) {
            correctionRecommendation?.let { rec ->
                CorrectionRecommendationCard(result = rec, onDismiss = onDismissCorrection)
            }
        }

        state.uamPrompts.forEach { window ->
            UAMPromptCard(
                timeLabel = "%02d:%02d".format(window.startTime.hour, window.startTime.minute),
                onLogMeal = onLogMeal,
                onDismiss = { onDismissUAMPrompt(window.id) }
            )
        }

        if (showCobWarning) {
            COBAbsorptionWarningCard(
                minAbsorptionPct = state.cobMinAbsorptionPct,
                onDismiss = { cobWarningDismissed = true }
            )
        }

        CombinedChart(
            modifier = Modifier.weight(1f),
            history = state.history,
            forecast = state.forecast,
            historicalPoints = state.historicalPoints,
            alarmStatus = alarmStatus
        )
    }
}

// ─── Glucose card ─────────────────────────────────────────────────────────────

private val EllipseShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Generic(Path().apply { addOval(Rect(0f, 0f, size.width, size.height)) })
}

@Composable
private fun GlucoseCard(reading: GlucoseReading) {
    val valueColor = glucoseColor(reading.glucoseValue)

    Card(
        modifier = Modifier.wrapContentSize(),
        shape = EllipseShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(width = 2.dp, color = Color(0x148148148))
    ) {
        Row(
            modifier = Modifier.padding(start = 30.dp, end = 20.dp, top = 20.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = reading.glucoseValue.toString(),
                    style = TextStyle(
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        color = valueColor,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.LastLineBottom
                        )
                    )
                )
                Text(
                    text = "mg/dL",
                    modifier = Modifier.offset(y = (-10).dp),
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.FirstLineTop
                        )
                    )
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            TrendArrow(trend = reading.trend, color = valueColor, size = 56.dp)
        }
    }
}

@Composable
private fun TrendArrow(trend: GlucoseTrend, color: Color, size: Dp) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
        contentDescription = null,
        tint = color,
        modifier = Modifier
            .size(size)
            .graphicsLayer { rotationZ = trend.angleDeg }
    )
}

// ─── Predictive hypo warning ──────────────────────────────────────────────────

@Composable
private fun PredictiveHypoWarning(onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "⚠ Hypoglycemia predicted in the next 30 min",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ─── UAM prompt card ─────────────────────────────────────────────────────────

@Composable
private fun UAMPromptCard(
    timeLabel: String,
    onLogMeal: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Unlogged glucose rise detected",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Around $timeLabel — did you eat something?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onLogMeal,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Log meal",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ─── COB absorption warning card ─────────────────────────────────────────────

@Composable
private fun COBAbsorptionWarningCard(
    minAbsorptionPct: Int,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "COB may be inaccurate",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE65100)
                )
                Text(
                    text = "$minAbsorptionPct% of carb absorption used minimum rate instead of observed BG deviations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFBF360C)
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color(0xFFE65100),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ─── Combined glucose + IOB/COB chart ────────────────────────────────────────

@Composable
private fun CombinedChart(
    modifier: Modifier = Modifier,
    history: List<GlucoseReading>,
    forecast: List<ForecastPoint>,
    historicalPoints: List<HistoricalPoint>,
    alarmStatus: AlarmStatus = AlarmStatus()
) {
    val textMeasurer = rememberTextMeasurer()
    val tz = remember { TimeZone.currentSystemDefault() }
    val nowMs = remember(history, forecast, historicalPoints) {
        Clock.System.now().toEpochMilliseconds()
    }

    // Time window: 3 h before now → end of forecast (min 2.5 h after)
    val windowStartMs = remember(nowMs) { nowMs - 3L * 60 * 60 * 1000L }
    val windowEndMs = remember(nowMs, forecast) {
        if (forecast.isNotEmpty())
            (nowMs + forecast.last().deltaMinutes.toLong() * 60_000L)
                .coerceAtLeast(nowMs + 150L * 60_000L)
        else
            nowMs + 150L * 60_000L
    }

    // History within window, sorted ascending
    val histPts = remember(history, windowStartMs, tz) {
        history
            .map { GlucosePt(it.timestamp.toInstant(tz).toEpochMilliseconds(), it.glucoseValue) }
            .filter { it.ms in windowStartMs..nowMs }
            .sortedBy { it.ms }
    }

    // Forecast chart points
    val fcstPts = remember(forecast, nowMs, windowEndMs) {
        forecast
            .map { FcstChartPt(nowMs + it.deltaMinutes.toLong() * 60_000L, it.predictedBg, it.predictedIob, it.predictedCob) }
            .filter { it.ms in nowMs..windowEndMs }
    }

    // Historical IOB/COB within window
    val histIobCob = remember(historicalPoints, windowStartMs, tz) {
        historicalPoints
            .map { IobCobPt(it.timestamp.toInstant(tz).toEpochMilliseconds(), it.iob, it.cob, it.cobMinAbsorptionRatio > 0f) }
            .filter { it.ms in windowStartMs..nowMs }
            .sortedBy { it.ms }
    }

    // Glucose Y range — bottom is always 0, top at least 250
    val minBg = 0f
    val maxBg = remember(histPts, fcstPts) {
        val dataMax = (histPts.map { it.value.toFloat() } + fcstPts.map { it.bg }).maxOrNull() ?: 250f
        (dataMax + 20f).coerceAtLeast(250f)
    }

    // IOB/COB Y ranges — at least 1 to avoid /0
    val maxIob = remember(histIobCob, fcstPts) {
        (histIobCob.map { it.iob } + fcstPts.map { it.iob }).maxOrNull()?.coerceAtLeast(1f) ?: 1f
    }
    val maxCob = remember(histIobCob, fcstPts) {
        (histIobCob.map { it.cob } + fcstPts.map { it.cob }).maxOrNull()?.coerceAtLeast(1f) ?: 1f
    }

    // Pre-measure axis labels (avoids TextMeasurer calls inside Canvas on every frame)
    val axisLabelStyle = remember { TextStyle(fontSize = 10.sp, color = Color.Gray) }
    val yGridValues = remember(minBg, maxBg) {
        listOf(0, 50, 100, 150, 200, 250, 300).filter { it >= minBg && it <= maxBg }
    }
    val yLabels = remember(yGridValues) {
        yGridValues.map { gv -> gv to textMeasurer.measure(gv.toString(), axisLabelStyle) }
    }
    val hourMs = 60L * 60_000L
    val xLabels = remember(windowStartMs, windowEndMs, tz) {
        val first = (windowStartMs / hourMs + 1) * hourMs
        generateSequence(first) { it + hourMs }
            .takeWhile { it <= windowEndMs }
            .map { ms ->
                val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz)
                val label = "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
                ms to textMeasurer.measure(label, axisLabelStyle)
            }
            .toList()
    }
    val iobLegend = remember {
        textMeasurer.measure("IOB", TextStyle(fontSize = 9.sp, color = Color(0xFF42A5F5), fontWeight = FontWeight.Bold))
    }
    val cobLegend = remember {
        textMeasurer.measure("COB", TextStyle(fontSize = 9.sp, color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold))
    }

    val hasData = histPts.isNotEmpty() || fcstPts.isNotEmpty()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        if (!hasData) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No glucose data available", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Card
        }

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Layout constants (density-independent) ────────────────────────────
            val lp = 40.dp.toPx()  // left margin for y-axis labels
            val rp = 10.dp.toPx()  // right padding
            val tp = 12.dp.toPx()  // top padding
            val bp = 30.dp.toPx()  // bottom padding for x-axis labels
            val gap = 16.dp.toPx() // gap between main and sub chart
            val subRatio = 0.26f

            val plotW = size.width - lp - rp
            val totalH = size.height - tp - bp
            val mainH = totalH * (1f - subRatio) - gap / 2f
            val subH = totalH * subRatio - gap / 2f

            // Guard: skip drawing if the canvas is too small to produce a valid layout
            if (plotW <= 0f || mainH <= 0f || subH <= 0f) return@Canvas
            val mainTop = tp
            val subTop = tp + mainH + gap

            val wRange = (windowEndMs - windowStartMs).toFloat()
            val bgRange = maxBg - minBg

            fun msToX(ms: Long) = lp + plotW * (ms - windowStartMs).toFloat() / wRange
            fun bgToY(bg: Float) = mainTop + mainH * (1f - (bg - minBg) / bgRange)
            fun iobToY(v: Float) = subTop + subH * (1f - v / maxIob)
            fun cobToY(v: Float) = subTop + subH * (1f - v / maxCob)

            val nowX = msToX(nowMs)

            // ── Target range band (70–180, subtle green) ──────────────────────────
            val band180 = bgToY(180f).coerceIn(mainTop, mainTop + mainH)
            val band70  = bgToY(70f).coerceIn(mainTop, mainTop + mainH)
            drawRect(
                color = Color(0xFF4CAF50).copy(alpha = 0.07f),
                topLeft = Offset(lp, band180),
                size = Size(plotW, band70 - band180)
            )

            // ── Horizontal grid lines ─────────────────────────────────────────────
            for (gv in yGridValues) {
                val y = bgToY(gv.toFloat())
                val isTargetLine = gv == 70 || gv == 180
                drawLine(
                    color = Color.Gray.copy(alpha = if (isTargetLine) 0.70f else 0.50f),
                    start = Offset(lp, y),
                    end = Offset(lp + plotW, y),
                    strokeWidth = if (isTargetLine) 1.dp.toPx() else 0.8.dp.toPx(),
                    pathEffect = if (isTargetLine)
                        PathEffect.dashPathEffect(floatArrayOf(8f, 5f)) else null
                )
            }
            // Target boundary lines at 70 and 180 if not already in grid
            for (boundary in listOf(70, 180)) {
                if (boundary !in yGridValues && boundary >= minBg && boundary <= maxBg) {
                    val y = bgToY(boundary.toFloat())
                    drawLine(
                        color = Color(0xFF4CAF50).copy(alpha = 0.55f),
                        start = Offset(lp, y), end = Offset(lp + plotW, y),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f))
                    )
                }
            }

            // ── Alarm threshold lines (only when alarm is enabled) ────────────────
            val alarmDash = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
            if (alarmStatus.hypoEnabled) {
                val y = bgToY(alarmStatus.hypoThreshold.toFloat())
                if (y in mainTop..(mainTop + mainH)) {
                    drawLine(
                        color = Color(0xFFF44336).copy(alpha = 0.80f),
                        start = Offset(lp, y), end = Offset(lp + plotW, y),
                        strokeWidth = 1.8.dp.toPx(),
                        pathEffect = alarmDash
                    )
                }
            }
            if (alarmStatus.hyperEnabled) {
                val y = bgToY(alarmStatus.hyperThreshold.toFloat())
                if (y in mainTop..(mainTop + mainH)) {
                    drawLine(
                        color = Color(0xFFFF9800).copy(alpha = 0.80f),
                        start = Offset(lp, y), end = Offset(lp + plotW, y),
                        strokeWidth = 1.8.dp.toPx(),
                        pathEffect = alarmDash
                    )
                }
            }

            // ── Future region shading ─────────────────────────────────────────────
            if (nowX in lp..(lp + plotW)) {
                drawRect(
                    color = Color.Gray.copy(alpha = 0.04f),
                    topLeft = Offset(nowX, mainTop),
                    size = Size(lp + plotW - nowX, mainH)
                )
            }

            // ── "Now" vertical dashed separator ───────────────────────────────────
            drawLine(
                color = Color.Gray.copy(alpha = 0.55f),
                start = Offset(nowX, mainTop),
                end = Offset(nowX, subTop + subH),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f), 0f)
            )

            // ── Glucose history (color-coded segments, gap-aware) ─────────────────
            val fcstDash = PathEffect.dashPathEffect(floatArrayOf(12f, 7f), 0f)
            val gapMs = 15L * 60_000L

            for (i in 0 until histPts.size - 1) {
                val p1 = histPts[i]; val p2 = histPts[i + 1]
                if (p2.ms - p1.ms > gapMs) continue
                drawLine(
                    color = glucoseColor(p1.value),
                    start = Offset(msToX(p1.ms), bgToY(p1.value.toFloat())),
                    end   = Offset(msToX(p2.ms), bgToY(p2.value.toFloat())),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            // Dot at most-recent history point
            histPts.lastOrNull()?.let { pt ->
                drawCircle(
                    color = glucoseColor(pt.value),
                    radius = 4.dp.toPx(),
                    center = Offset(msToX(pt.ms), bgToY(pt.value.toFloat()))
                )
            }

            // ── Glucose forecast (dashed, color-coded) ────────────────────────────
            for (i in 0 until fcstPts.size - 1) {
                val p1 = fcstPts[i]; val p2 = fcstPts[i + 1]
                drawLine(
                    color = glucoseColor(p1.bg.toInt()).copy(alpha = 0.55f),
                    start = Offset(msToX(p1.ms), bgToY(p1.bg)),
                    end   = Offset(msToX(p2.ms), bgToY(p2.bg)),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = fcstDash,
                    cap = StrokeCap.Round
                )
            }

            // ── Sub chart: continuous IOB / COB curves ────────────────────────────
            val iobColor = Color(0xFF42A5F5)
            val cobColor = Color(0xFFFFB74D)
            val subDash  = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f)

            val allIobPts: List<Pair<Long, Float>> =
                histIobCob.map { it.ms to it.iob } + fcstPts.map { it.ms to it.iob }
            val allCobPts: List<Pair<Long, Float>> =
                histIobCob.map { it.ms to it.cob } + fcstPts.map { it.ms to it.cob }

            // COB filled area + stroke
            if (allCobPts.size >= 2) {
                val path = Path().apply {
                    moveTo(msToX(allCobPts.first().first), subTop + subH)
                    lineTo(msToX(allCobPts.first().first), cobToY(allCobPts.first().second))
                    for (i in 1 until allCobPts.size)
                        lineTo(msToX(allCobPts[i].first), cobToY(allCobPts[i].second))
                    lineTo(msToX(allCobPts.last().first), subTop + subH)
                    close()
                }
                drawPath(path, color = cobColor.copy(alpha = 0.18f))
                for (i in 0 until allCobPts.size - 1) {
                    val (ms1, c1) = allCobPts[i]; val (ms2, c2) = allCobPts[i + 1]
                    drawLine(
                        color = cobColor.copy(alpha = if (ms1 >= nowMs) 0.50f else 0.85f),
                        start = Offset(msToX(ms1), cobToY(c1)),
                        end   = Offset(msToX(ms2), cobToY(c2)),
                        strokeWidth = 1.8f,
                        pathEffect = if (ms1 >= nowMs) subDash else null
                    )
                }
                for (pt in histIobCob) {
                    if (pt.cobMinAbs && pt.cob > 0f) {
                        drawCircle(
                            color = Color(0xFFFF9800),
                            radius = 3.dp.toPx(),
                            center = Offset(msToX(pt.ms), cobToY(pt.cob))
                        )
                    }
                }
            }

            // IOB filled area + stroke
            if (allIobPts.size >= 2) {
                val path = Path().apply {
                    moveTo(msToX(allIobPts.first().first), subTop + subH)
                    lineTo(msToX(allIobPts.first().first), iobToY(allIobPts.first().second))
                    for (i in 1 until allIobPts.size)
                        lineTo(msToX(allIobPts[i].first), iobToY(allIobPts[i].second))
                    lineTo(msToX(allIobPts.last().first), subTop + subH)
                    close()
                }
                drawPath(path, color = iobColor.copy(alpha = 0.15f))
                for (i in 0 until allIobPts.size - 1) {
                    val (ms1, v1) = allIobPts[i]; val (ms2, v2) = allIobPts[i + 1]
                    drawLine(
                        color = iobColor.copy(alpha = if (ms1 >= nowMs) 0.50f else 0.85f),
                        start = Offset(msToX(ms1), iobToY(v1)),
                        end   = Offset(msToX(ms2), iobToY(v2)),
                        strokeWidth = 1.8f,
                        pathEffect = if (ms1 >= nowMs) subDash else null
                    )
                }
            }

            // ── Axis lines ────────────────────────────────────────────────────────
            // Left axis: only spans the glucose chart — stops at the 50 line
            drawLine(
                color = Color.Gray.copy(alpha = 0.25f),
                start = Offset(lp, mainTop), end = Offset(lp, mainTop + mainH), strokeWidth = 1f
            )
            // Sub-chart bottom border
            drawLine(
                color = Color.Gray.copy(alpha = 0.20f),
                start = Offset(lp, subTop + subH), end = Offset(lp + plotW, subTop + subH), strokeWidth = 1f
            )

            // ── Y-axis labels (mg/dL) ─────────────────────────────────────────────
            for ((gv, layout) in yLabels) {
                val y = bgToY(gv.toFloat())
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = lp - layout.size.width.toFloat() - 3.dp.toPx(),
                        y = y - layout.size.height.toFloat() / 2f
                    )
                )
            }

            // ── X-axis labels (HH:mm) ─────────────────────────────────────────────
            val xLabelY = subTop + subH + 8f
            for ((ms, layout) in xLabels) {
                val x = msToX(ms)
                val left = (x - layout.size.width / 2f).coerceIn(lp, lp + plotW - layout.size.width.toFloat())
                drawText(textLayoutResult = layout, topLeft = Offset(left, xLabelY))
            }

            // ── IOB / COB legend ──────────────────────────────────────────────────
            drawText(iobLegend, topLeft = Offset(lp + 6f, subTop + 4f))
            drawText(cobLegend, topLeft = Offset(lp + iobLegend.size.width.toFloat() + 12f, subTop + 4f))
        }
    }
}

// ─── Correction recommendation card ──────────────────────────────────────────

@Composable
private fun CorrectionRecommendationCard(result: RecommendationResult, onDismiss: () -> Unit) {
    val isHypo = result.recommendedCarbs != null
    val accentColor = if (isHypo) Color(0xFFF44336) else Color(0xFFFF9800)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        result.label ?: if (isHypo) "Hypo correction" else "Hyper correction",
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor
                    )
                    val mainValue = if (result.recommendedUnits != null)
                        "%.1f U".format(result.recommendedUnits)
                    else
                        "${result.recommendedCarbs} g"
                    Text(mainValue, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.Gray)
                }
            }
            result.warnings.forEach { warning ->
                Text("⚠ $warning", style = MaterialTheme.typography.labelSmall, color = accentColor)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                "Decision-support only. Verify with your healthcare provider.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun glucoseColor(value: Int): Color = when {
    value < 54   -> GlucoseVeryLow
    value < 70   -> GlucoseLow
    value <= 180 -> GlucoseNormal
    value <= 250 -> GlucoseHigh
    else         -> GlucoseVeryHigh
}
