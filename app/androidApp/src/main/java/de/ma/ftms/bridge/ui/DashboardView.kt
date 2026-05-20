package de.ma.ftms.bridge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ma.ftms.bridge.i18n.LocalStrings
import de.ma.ftms.bridge.i18n.Strings
import de.ma.ftms.bridge.navigation.DashboardComponent
import de.ma.ftms.bridge.runtime.BridgeUiState
import de.ma.ftms.bridge.runtime.DashboardMetricKey
import de.ma.ftms.bridge.runtime.LastSessionSummary
import de.ma.ftms.core.SmoothedTreadmillSample
import kotlinx.coroutines.flow.distinctUntilChanged

private const val MAX_CHART_DRAW_POINTS = 240

@Composable
fun DashboardView(
    component: DashboardComponent,
    onRequestPermissions: () -> Unit,
    onPrimaryControlsVisibleChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val state by component.state.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val startBlockReason = startBlockReason(state, strings)
    var dashboardPage by rememberSaveable { mutableStateOf(DashboardPage.METRICS) }
    val listState = rememberLazyListState()
    val liveDashboardSamples = remember(state.liveDashboardSamples, state.settings.dashboardChartTimespan) {
        state.liveDashboardSamples.filterForTimespan(state.settings.dashboardChartTimespan)
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 }
            .distinctUntilChanged()
            .collect { onPrimaryControlsVisibleChange(it) }
    }

    ScreenContainer(title = strings.dashboard.title) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                BridgeControlCard(
                    state = state,
                    strings = strings,
                    startBlockReason = startBlockReason,
                    onRequestPermissions = onRequestPermissions,
                    onStart = { component.onStartBridge(context) },
                    onPause = component::onPauseBridge,
                    onStop = component::onStopBridge,
                )
            }
            item { ConnectionStatus(state = state, strings = strings) }
            item {
                DashboardModeSwitch(
                    selected = dashboardPage,
                    strings = strings,
                    onSelected = { dashboardPage = it },
                )
            }
            if (dashboardPage == DashboardPage.METRICS) {
                item {
                    Metrics(
                        sample = state.latest,
                        samples = state.liveDashboardSamples,
                        activeDurationMillis = state.activeDurationMillis,
                        metricKeys = state.settings.dashboardMetrics,
                        strings = strings,
                    )
                }
            } else if (liveDashboardSamples.isEmpty()) {
                item {
                    Section(strings.dashboard.sectionLiveCharts) {
                        Text(strings.dashboard.noLiveGraphSamples, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(
                    items = state.settings.dashboardGraphs,
                    key = { key -> "dashboard-chart-${key.name}" },
                ) { key ->
                    MetricChart(strings = strings, key = key, samples = liveDashboardSamples)
                }
            }
            item { LastSession(state.lastSummary, strings = strings) }
            item { VerticalGap() }
        }
    }
}

private enum class DashboardPage {
    METRICS,
    CHARTS,
}

@Composable
private fun DashboardModeSwitch(
    selected: DashboardPage,
    strings: Strings,
    onSelected: (DashboardPage) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FilterChip(
            selected = selected == DashboardPage.METRICS,
            onClick = { onSelected(DashboardPage.METRICS) },
            label = { Text(strings.dashboard.viewMetrics) },
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = selected == DashboardPage.CHARTS,
            onClick = { onSelected(DashboardPage.CHARTS) },
            label = { Text(strings.dashboard.viewCharts) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BridgeControlCard(
    state: BridgeUiState,
    strings: Strings,
    startBlockReason: String?,
    onRequestPermissions: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when {
                    state.paused -> strings.dashboard.bridgePaused
                    state.running -> strings.dashboard.bridgeRunning
                    else -> strings.dashboard.bridgeIdle
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                state.selectedTreadmillName ?: state.selectedTreadmillAddress ?: strings.dashboard.noFtmsEquipmentSelected,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                state.selectedGarminName ?: state.selectedGarminId?.toString() ?: strings.dashboard.noGarminWatchSelected,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                enabled = startBlockReason == null || state.paused,
                onClick = onStart,
                modifier = Modifier.weight(1f),
            ) {
                Text(strings.common.start)
            }
            OutlinedButton(
                enabled = state.running && !state.paused,
                onClick = onPause,
                modifier = Modifier.weight(1f),
            ) {
                Text(strings.common.pause)
            }
            OutlinedButton(
                enabled = state.running,
                onClick = onStop,
                modifier = Modifier.weight(1f),
            ) {
                Text(strings.common.stop)
            }
        }

        if (!state.permissionsGranted) {
            Button(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) {
                Text(strings.dashboard.grantBluetoothPermissions)
            }
        } else if (startBlockReason != null && !state.running) {
            Text(startBlockReason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionStatus(state: BridgeUiState, strings: Strings) {
    Section(strings.dashboard.sectionStatus) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusBadge(
                label = if (state.bleAvailable) strings.dashboard.bluetoothReady else strings.dashboard.bluetoothOff,
                tone = if (state.bleAvailable) BadgeTone.SUCCESS else BadgeTone.ERROR,
            )
            StatusBadge(
                label = if (state.treadmillConnected) strings.dashboard.ftmsConnected else strings.dashboard.ftmsDisconnected,
                tone = if (state.treadmillConnected) BadgeTone.SUCCESS else BadgeTone.WARNING,
            )
            StatusBadge(
                label = if (state.garminReady) strings.dashboard.garminReady else strings.dashboard.garminPending,
                tone = if (state.garminReady) BadgeTone.SUCCESS else BadgeTone.WARNING,
            )
            StatusBadge(label = state.reconnectStatus, tone = BadgeTone.NEUTRAL)
        }

        StatusRow(strings.dashboard.send, state.lastSendStatus)
        StatusRow(strings.common.packets, state.packetCount.toString())
        StatusRow(strings.common.sends, strings.common.sendSummary(state.sendSuccessCount, state.sendFailureCount))
        if (state.lastError.isNotBlank()) {
            StatusRow(strings.common.lastError, state.lastError)
        }
    }
}

@Composable
private fun Metrics(
    sample: SmoothedTreadmillSample?,
    samples: List<SmoothedTreadmillSample>,
    activeDurationMillis: Long,
    metricKeys: List<DashboardMetricKey>,
    strings: Strings,
) {
    Section(strings.dashboard.sectionLiveMetrics) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            metricKeys.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { key ->
                        MetricTile(
                            key.label(strings),
                            key.format(sample, strings, samples, activeDurationMillis),
                            Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) {
                        Column(modifier = Modifier.weight(1f)) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricChart(strings: Strings, key: DashboardMetricKey, samples: List<SmoothedTreadmillSample>) {
    val points = remember(samples, key) {
        samples.points(key, monotonic = key == DashboardMetricKey.DISTANCE || key == DashboardMetricKey.ASCENT)
    }
    val unit = key.unit()
    val title = if (unit.isBlank()) key.label(strings) else "${key.label(strings)} ($unit)"
    Section(title) {
        if (points.size < 2) {
            Text(strings.history.notEnoughSamples, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Section
        }

        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        val min = points.minOf { it.y }
        val max = points.maxOf { it.y }
        val startSeconds = points.first().x
        val endSeconds = points.last().x
        val lineColor = MaterialTheme.colorScheme.primary
        val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        val startTime = formatChartTime(startSeconds)
        val endTime = formatChartTime(endSeconds)
        val chartDescription = strings.dashboard.chartSummary(
            title = title,
            latest = "%.1f".format(points.last().y),
            min = "%.1f".format(min),
            max = "%.1f".format(max),
            start = startTime,
            end = endTime,
        )
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(strings.common.minValue(min), color = labelColor, style = MaterialTheme.typography.bodySmall)
            Text(strings.common.maxValue(max), color = labelColor, style = MaterialTheme.typography.bodySmall)
        }
        SimpleLineChart(
            points = points,
            minY = min,
            maxY = max,
            reverseY = key.reverseChartYAxis(),
            lineColor = lineColor,
            gridColor = gridColor,
            contentDescription = chartDescription,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        )
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(startTime, color = labelColor, style = MaterialTheme.typography.bodySmall)
            Text(endTime, color = labelColor, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SimpleLineChart(
    points: List<DashboardChartPoint>,
    minY: Double,
    maxY: Double,
    reverseY: Boolean,
    lineColor: Color,
    gridColor: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val drawPoints = remember(points) { points.downsampleForDrawing() }
    Canvas(
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
        },
    ) {
        val leftPadding = 6.dp.toPx()
        val rightPadding = 6.dp.toPx()
        val topPadding = 8.dp.toPx()
        val bottomPadding = 8.dp.toPx()
        val chartWidth = (size.width - leftPadding - rightPadding).coerceAtLeast(1f)
        val chartHeight = (size.height - topPadding - bottomPadding).coerceAtLeast(1f)
        val minX = drawPoints.first().x
        val maxX = drawPoints.last().x
        val xRange = (maxX - minX).takeIf { it > 0.0 } ?: 1.0

        repeat(4) { index ->
            val y = topPadding + chartHeight * index / 3f
            drawLine(
                color = gridColor,
                start = Offset(leftPadding, y),
                end = Offset(leftPadding + chartWidth, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        var previous: Offset? = null
        drawPoints.forEach { point ->
            val x = leftPadding + (((point.x - minX) / xRange).toFloat() * chartWidth)
            val y = topPadding + (chartYFraction(point.y, minY, maxY, reverseY) * chartHeight)
            val current = Offset(x, y)
            previous?.let {
                drawLine(
                    color = lineColor,
                    start = it,
                    end = current,
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            previous = current
        }
    }
}

private data class DashboardChartPoint(val x: Double, val y: Double)

private fun List<DashboardChartPoint>.downsampleForDrawing(maxPoints: Int = MAX_CHART_DRAW_POINTS): List<DashboardChartPoint> {
    if (size <= maxPoints || maxPoints < 4) {
        return this
    }

    val bucketCount = ((maxPoints - 2) / 2).coerceAtLeast(1)
    val interiorStart = 1
    val interiorEndExclusive = lastIndex
    val interiorCount = interiorEndExclusive - interiorStart
    val bucketSize = interiorCount.toDouble() / bucketCount
    val result = mutableListOf(first())
    repeat(bucketCount) { bucket ->
        val startIndex = (interiorStart + (bucket * bucketSize).toInt()).coerceIn(interiorStart, interiorEndExclusive - 1)
        val endIndex = (interiorStart + ((bucket + 1) * bucketSize).toInt())
            .coerceIn(startIndex + 1, interiorEndExclusive)
        val bucketPoints = subList(startIndex, endIndex)
        val minPoint = bucketPoints.minBy { it.y }
        val maxPoint = bucketPoints.maxBy { it.y }
        if (minPoint.x <= maxPoint.x) {
            result.addIfNew(minPoint)
            result.addIfNew(maxPoint)
        } else {
            result.addIfNew(maxPoint)
            result.addIfNew(minPoint)
        }
    }
    result.addIfNew(last())
    return result
}

private fun MutableList<DashboardChartPoint>.addIfNew(point: DashboardChartPoint) {
    if (lastOrNull() != point) {
        add(point)
    }
}

private fun List<SmoothedTreadmillSample>.points(
    key: DashboardMetricKey,
    monotonic: Boolean,
): List<DashboardChartPoint> {
    val firstTimestamp = firstOrNull()?.raw?.timestampMillis
    var lastX: Double? = null
    var lastY: Double? = null
    var previousDistance = firstOrNull()?.distanceM ?: 0.0
    var weightedDistance = 0.0
    var weightedIncline = 0.0
    val simpleInclines = mutableListOf<Double>()
    return mapIndexedNotNull { index, sample ->
        val timestamp = sample.raw.timestampMillis
        val x = if (timestamp != null && firstTimestamp != null) {
            (timestamp - firstTimestamp).coerceAtLeast(0L).toDouble() / 1000.0
        } else {
            index.toDouble()
        }
        if (lastX != null && x <= lastX) {
            return@mapIndexedNotNull null
        }

        val y = when (key) {
            DashboardMetricKey.AVG_PACE -> {
                val distanceKm = sample.distanceM / 1000.0
                if (distanceKm > 0.0 && x > 0.0) x / 60.0 / distanceKm else null
            }
            DashboardMetricKey.AVG_INCLINE -> {
                val incline = sample.raw.inclinePct
                if (incline != null) {
                    simpleInclines += incline
                    val deltaDistance = (sample.distanceM - previousDistance).coerceAtLeast(0.0)
                    if (deltaDistance > 0.0) {
                        weightedDistance += deltaDistance
                        weightedIncline += deltaDistance * incline
                    }
                }
                if (weightedDistance > 0.0) {
                    weightedIncline / weightedDistance
                } else {
                    simpleInclines.takeIf { it.isNotEmpty() }?.average()
                }
            }
            else -> key.chartValue(sample)
        } ?: return@mapIndexedNotNull null

        previousDistance = sample.distanceM
        val chartY = if (monotonic) {
            y.coerceAtLeast(lastY ?: y)
        } else {
            y
        }
        lastX = x
        lastY = chartY
        DashboardChartPoint(x, chartY)
    }
}

private fun formatChartTime(seconds: Double): String {
    val totalSeconds = seconds.toLong()
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val remainingSeconds = totalSeconds % 60L
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
    } else {
        "%02d:%02d".format(minutes, remainingSeconds)
    }
}

@Composable
private fun LastSession(summary: LastSessionSummary?, strings: Strings) {
    Section(strings.dashboard.sectionLastSession) {
        if (summary == null) {
            Text(strings.dashboard.noPreviousSession, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Section
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile(strings.common.distance, "%.2f km".format(summary.finalDistanceM / 1000.0), Modifier.weight(1f))
            MetricTile(strings.common.ascent, "%.1f m".format(summary.finalAscentM), Modifier.weight(1f))
        }
        StatusRow(strings.common.status, summary.finalStatus)
        StatusRow(strings.common.packets, summary.packetCount.toString())
        StatusRow(strings.common.sends, strings.common.sendSummary(summary.sendSuccessCount, summary.sendFailureCount))
        if (summary.lastError.isNotBlank()) {
            StatusRow(strings.common.lastError, summary.lastError)
        }
    }
}
