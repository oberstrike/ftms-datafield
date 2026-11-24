package de.ma.ftms.bridge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ma.ftms.bridge.i18n.LocalStrings
import de.ma.ftms.bridge.i18n.fmt
import de.ma.ftms.bridge.i18n.formatNeutral
import de.ma.ftms.bridge.i18n.Strings
import de.ma.ftms.bridge.navigation.HistoryComponent
import de.ma.ftms.bridge.runtime.EquipmentType
import de.ma.ftms.bridge.runtime.SourcedMetric
import de.ma.ftms.core.SensorReadings
import de.ma.ftms.core.storage.SessionSampleRecord
import de.ma.ftms.core.storage.WorkoutSessionRecord
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryView(component: HistoryComponent) {
    val state by component.state.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val selected = state.sessions.firstOrNull { it.sessionId == state.selectedHistorySessionId }

    ScreenContainer(title = strings.history.title) {
        if (selected == null) {
            HistoryList(
                strings = strings,
                sessions = state.sessions,
                onSelectSession = component::onSelectSession,
                onClearHistory = component::onClearHistory,
            )
        } else {
            HistoryDetail(
                strings = strings,
                session = selected,
                samples = state.selectedHistorySamples,
                onBack = { component.onSelectSession(null) },
                onDelete = { component.onDeleteSession(selected.sessionId) },
            )
        }
    }
}

@Composable
private fun HistoryList(
    strings: Strings,
    sessions: List<WorkoutSessionRecord>,
    onSelectSession: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    var typeFilter by rememberSaveable { mutableStateOf<EquipmentType?>(null) }
    val recordedTypes = recordedEquipmentTypes(sessions)
    val activeFilter = typeFilter?.takeIf { it in recordedTypes }
    val shownSessions = filterSessionsByType(sessions, activeFilter)

    if (recordedTypes.size > 1) {
        EquipmentTypeFilter(
            strings = strings,
            types = recordedTypes,
            selected = activeFilter,
            onSelected = { typeFilter = it },
        )
    }
    if (shownSessions.isNotEmpty()) {
        AllTimeSummary(
            strings = strings,
            totals = allTimeHistoryTotals(shownSessions),
            showAscent = shownSessions.any { it.equipmentType.showsClimb },
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(strings.history.storedSessions, fontWeight = FontWeight.Bold)
            Text(strings.history.savedSessions(shownSessions.size), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(enabled = sessions.isNotEmpty(), onClick = { showClearConfirm = true }) {
            Text(strings.common.clear)
        }
    }

    if (sessions.isEmpty()) {
        InfoCard {
            Text(strings.history.noSessionsStored, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(shownSessions, key = { it.sessionId }) { session ->
            SessionCard(strings = strings, session = session, onClick = { onSelectSession(session.sessionId) })
        }
        item { VerticalGap() }
    }

    if (showClearConfirm) {
        ConfirmActionDialog(
            title = strings.history.clearHistoryTitle,
            text = strings.history.clearHistoryText,
            confirmLabel = strings.common.clear,
            onConfirm = {
                showClearConfirm = false
                onClearHistory()
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EquipmentTypeFilter(
    strings: Strings,
    types: List<EquipmentType>,
    selected: EquipmentType?,
    onSelected: (EquipmentType?) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelected(null) },
            label = { Text(strings.history.filterAll) },
        )
        types.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelected(type) },
                label = { Text(equipmentTypeLabel(type, strings)) },
            )
        }
    }
}

@Composable
private fun AllTimeSummary(strings: Strings, totals: AllTimeHistoryTotals, showAscent: Boolean) {
    Section(strings.history.allTime) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile(strings.history.totalSessions, totals.sessionCount.toString(), Modifier.weight(1f))
            MetricTile(strings.common.distance, strings.fmt("%.2f km", totals.totalDistanceM / 1000.0), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (showAscent) {
                MetricTile(strings.common.ascent, strings.fmt("%.1f m", totals.totalAscentM), Modifier.weight(1f))
            }
            MetricTile(strings.common.duration, formatDuration(totals.totalDurationMillis), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SessionCard(strings: Strings, session: WorkoutSessionRecord, onClick: () -> Unit) {
    InfoCard(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(sessionTitle(session), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${equipmentTypeLabel(session.equipmentType, strings)} · ${formatDuration(session.durationMillis)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(
                label = session.finalStatus,
                tone = if (session.finalStatus == "stopped") BadgeTone.SUCCESS else BadgeTone.WARNING,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile(strings.common.distance, strings.fmt("%.2f km", session.finalDistanceM / 1000.0), Modifier.weight(1f))
            if (session.equipmentType.showsClimb) {
                MetricTile(strings.common.ascent, strings.fmt("%.1f m", session.finalAscentM), Modifier.weight(1f))
            } else {
                MetricTile(
                    strings.common.averageSpeed,
                    averageSpeedKmh(session.finalDistanceM, session.durationMillis)?.let { strings.fmt("%.1f km/h", it) }
                        ?: strings.common.missingValue,
                    Modifier.weight(1f),
                )
            }
        }
        StatusRow(strings.common.packets, session.packetCount.toString())
        StatusRow(strings.common.sends, strings.common.sendSummary(session.sendSuccessCount, session.sendFailureCount))
    }
}

@Composable
private fun HistoryDetail(
    strings: Strings,
    session: WorkoutSessionRecord,
    samples: List<SessionSampleRecord>,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var historyPage by rememberSaveable { mutableStateOf(HistoryPage.METRICS) }
    val activeDurationMillis = samples.lastOrNull()?.offsetMillis ?: session.durationMillis
    val finalDistanceM = maxOf(session.finalDistanceM, samples.maxOfOrNull { it.smoothedDistanceM } ?: 0.0)
    val finalAscentM = maxOf(session.finalAscentM, samples.maxOfOrNull { it.ascentM } ?: 0.0)
    val type = session.equipmentType
    val averagePace = formatAveragePace(finalDistanceM, activeDurationMillis)
    val averageIncline = averageInclinePct(samples)
    val historyMetrics = historyMetricTiles(
        strings = strings,
        type = type,
        distanceM = finalDistanceM,
        ascentM = finalAscentM,
        activeDurationMillis = activeDurationMillis,
        averagePace = averagePace,
        averageIncline = averageIncline,
        samples = samples,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text(strings.common.back) }
                Button(onClick = { showDeleteConfirm = true }, modifier = Modifier.weight(1f)) { Text(strings.common.delete) }
            }
        }
        item {
            Section(sessionTitle(session)) {
                MetricGrid(
                    historySummaryTiles(
                        strings = strings,
                        type = type,
                        distanceM = finalDistanceM,
                        ascentM = finalAscentM,
                        activeDurationMillis = activeDurationMillis,
                        packetCount = session.packetCount,
                        averagePace = averagePace,
                        averageIncline = averageIncline,
                        samples = samples,
                    ),
                )
                StatusRow(strings.common.status, session.finalStatus)
                StatusRow(equipmentTypeLabel(type, strings), session.treadmillName ?: strings.common.missingValue)
                StatusRow(strings.common.garmin, session.garminName ?: strings.common.missingValue)
                StatusRow(strings.common.sends, strings.common.sendSummary(session.sendSuccessCount, session.sendFailureCount))
                if (session.lastError.isNotBlank()) {
                    StatusRow(strings.common.lastError, session.lastError)
                }
            }
        }
        item {
            HistoryModeSwitch(
                selected = historyPage,
                strings = strings,
                onSelected = { historyPage = it },
            )
        }
        if (samples.isEmpty()) {
            item {
                InfoCard {
                    Text(strings.history.noGraphSamplesStored, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (historyPage == HistoryPage.METRICS) {
            item {
                Section(strings.history.viewMetrics) {
                    MetricGrid(historyMetrics)
                }
            }
            val comparisonTiles = sourceComparisonTiles(strings, type, samples)
            if (comparisonTiles.isNotEmpty()) {
                item {
                    Section(strings.history.sourceComparison) {
                        MetricGrid(comparisonTiles)
                    }
                }
            }
        } else {
            item { MetricChart(strings, strings.history.chartSpeed, "km/h", samples.points { speedKmh }) }
            if (type.showsPace) {
                item { MetricChart(strings, strings.common.pace, "min/km", samples.points { speedKmh?.takeIf { it > 0.0 }?.let { speed -> 60.0 / speed } }, reverseY = true) }
                item { MetricChart(strings, strings.common.averagePace, "min/km", samples.averagePacePoints(), reverseY = true) }
            }
            item { MetricChart(strings, strings.common.distance, "m", samples.points(monotonic = true) { smoothedDistanceM }) }
            if (type.showsClimb) {
                item { MetricChart(strings, strings.history.chartIncline, "%", samples.points { inclinePct }) }
                item { MetricChart(strings, strings.common.averageIncline, "%", samples.averageInclinePoints()) }
                item { MetricChart(strings, strings.history.chartAscent, "m", samples.points(monotonic = true) { ascentM }) }
            }
            if (samples.any { it.powerW != null }) {
                item { MetricChart(strings, strings.history.chartPower, "W", samples.points { powerW?.toDouble() }) }
            }
            if (samples.any { it.heartRateBpm != null }) {
                item { MetricChart(strings, strings.history.chartHeartRate, "bpm", samples.points { heartRateBpm?.toDouble() }) }
            }
            if (samples.any { it.cadenceRpm != null || it.stepRateSpm != null }) {
                item { MetricChart(strings, strings.history.chartCadenceStep, "rpm/spm", samples.points { cadenceRpm ?: stepRateSpm }) }
            }
            SourcedMetric.entries.forEach { metric ->
                val equipmentPoints = samples.points { sourceValue(metric, equipment) }
                val watchPoints = samples.points { sourceValue(metric, watch) }
                if (equipmentPoints.size >= 2 && watchPoints.size >= 2) {
                    item {
                        MetricChart(
                            strings = strings,
                            title = "${sourcedMetricLabel(metric, strings)}: ${strings.history.sourceComparison}",
                            unit = "",
                            points = equipmentPoints,
                            comparison = watchPoints,
                        )
                    }
                }
            }
            if (samples.any { it.resistance != null }) {
                item { MetricChart(strings, strings.history.chartResistance, "", samples.points { resistance }) }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (showDeleteConfirm) {
        ConfirmActionDialog(
            title = strings.history.deleteSessionTitle,
            text = strings.history.deleteSessionText,
            confirmLabel = strings.common.delete,
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun MetricGrid(metrics: List<HistoryMetricTile>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        metrics.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { metric ->
                    MetricTile(metric.label, metric.value, Modifier.weight(1f))
                }
                if (row.size == 1) {
                    Column(modifier = Modifier.weight(1f)) {}
                }
            }
        }
    }
}

private data class HistoryMetricTile(
    val label: String,
    val value: String,
)

internal data class AllTimeHistoryTotals(
    val sessionCount: Int = 0,
    val totalDistanceM: Double = 0.0,
    val totalAscentM: Double = 0.0,
    val totalDurationMillis: Long = 0L,
)

private enum class HistoryPage {
    METRICS,
    CHARTS,
}

@Composable
private fun HistoryModeSwitch(
    selected: HistoryPage,
    strings: Strings,
    onSelected: (HistoryPage) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FilterChip(
            selected = selected == HistoryPage.METRICS,
            onClick = { onSelected(HistoryPage.METRICS) },
            label = { Text(strings.history.viewMetrics) },
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = selected == HistoryPage.CHARTS,
            onClick = { onSelected(HistoryPage.CHARTS) },
            label = { Text(strings.history.viewCharts) },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A line chart of [points]. With [comparison], the watch's values are drawn as a second line over
 * the equipment's and both get a legend.
 */
@Composable
private fun MetricChart(
    strings: Strings,
    title: String,
    unit: String,
    points: List<ChartPoint>,
    reverseY: Boolean = false,
    comparison: List<ChartPoint> = emptyList(),
) {
    Section(if (unit.isBlank()) title else "$title ($unit)") {
        if (points.size < 2) {
            Text(strings.history.notEnoughSamples, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Section
        }

        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        val allPoints = points + comparison
        val min = allPoints.minOf { it.y }
        val max = allPoints.maxOf { it.y }
        val startSeconds = allPoints.minOf { it.x }
        val endSeconds = allPoints.maxOf { it.x }
        val lineColor = MaterialTheme.colorScheme.primary
        val comparisonColor = MaterialTheme.colorScheme.tertiary
        val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        if (comparison.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("● ${strings.common.sourceEquipment}", color = lineColor, style = MaterialTheme.typography.bodySmall)
                Text("● ${strings.common.sourceWatch}", color = comparisonColor, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(strings.common.minValue(min), color = labelColor, style = MaterialTheme.typography.bodySmall)
            Text(strings.common.maxValue(max), color = labelColor, style = MaterialTheme.typography.bodySmall)
        }
        SimpleLineChart(
            series = listOfNotNull(
                ChartSeries(points, lineColor),
                comparison.takeIf { it.isNotEmpty() }?.let { ChartSeries(it, comparisonColor) },
            ),
            minX = startSeconds,
            maxX = endSeconds,
            minY = min,
            maxY = max,
            reverseY = reverseY,
            gridColor = gridColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        )
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(formatChartTime(startSeconds), color = labelColor, style = MaterialTheme.typography.bodySmall)
            Text(formatChartTime(endSeconds), color = labelColor, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private class ChartSeries(val points: List<ChartPoint>, val color: Color)

@Composable
private fun SimpleLineChart(
    series: List<ChartSeries>,
    minX: Double,
    maxX: Double,
    minY: Double,
    maxY: Double,
    reverseY: Boolean,
    gridColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val leftPadding = 6.dp.toPx()
        val rightPadding = 6.dp.toPx()
        val topPadding = 8.dp.toPx()
        val bottomPadding = 8.dp.toPx()
        val chartWidth = (size.width - leftPadding - rightPadding).coerceAtLeast(1f)
        val chartHeight = (size.height - topPadding - bottomPadding).coerceAtLeast(1f)
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

        series.forEach { line ->
            var previous: Offset? = null
            line.points.forEach { point ->
                val x = leftPadding + (((point.x - minX) / xRange).toFloat() * chartWidth)
                val y = topPadding + (chartYFraction(point.y, minY, maxY, reverseY) * chartHeight)
                val current = Offset(x, y)
                previous?.let {
                    drawLine(
                        color = line.color,
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
}

private data class ChartPoint(val x: Double, val y: Double)

private fun List<SessionSampleRecord>.points(
    monotonic: Boolean = false,
    value: SessionSampleRecord.() -> Double?,
): List<ChartPoint> {
    var lastX: Double? = null
    var lastY: Double? = null
    return mapNotNull { sample ->
        val y = sample.value() ?: return@mapNotNull null
        val x = sample.offsetMillis.toDouble() / 1000.0
        if (lastX != null && x <= lastX) {
            return@mapNotNull null
        }

        val chartY = if (monotonic) {
            y.coerceAtLeast(lastY ?: y)
        } else {
            y
        }
        lastX = x
        lastY = chartY
        ChartPoint(x, chartY)
    }
}

private fun List<SessionSampleRecord>.averagePacePoints(): List<ChartPoint> =
    points {
        val distanceKm = smoothedDistanceM / 1000.0
        val minutes = offsetMillis.toDouble() / 60_000.0
        if (distanceKm > 0.0 && minutes > 0.0) minutes / distanceKm else null
    }

private fun List<SessionSampleRecord>.averageInclinePoints(): List<ChartPoint> {
    var previousDistance = firstOrNull()?.smoothedDistanceM ?: 0.0
    var weightedDistance = 0.0
    var weightedIncline = 0.0
    val simpleInclines = mutableListOf<Double>()
    return mapNotNull { sample ->
        val x = sample.offsetMillis.toDouble() / 1000.0
        val incline = sample.inclinePct
        if (incline != null) {
            simpleInclines += incline
            val deltaDistance = (sample.smoothedDistanceM - previousDistance).coerceAtLeast(0.0)
            if (deltaDistance > 0.0) {
                weightedDistance += deltaDistance
                weightedIncline += deltaDistance * incline
            }
        }
        previousDistance = sample.smoothedDistanceM
        val average = if (weightedDistance > 0.0) {
            weightedIncline / weightedDistance
        } else {
            simpleInclines.takeIf { it.isNotEmpty() }?.average()
        }
        average?.let { ChartPoint(x, it) }
    }
}

private fun averageInclinePct(samples: List<SessionSampleRecord>): Double? =
    samples.averageInclinePoints().lastOrNull()?.y

/** The handful of values shown at the top of a session, chosen for its equipment type. */
private fun historySummaryTiles(
    strings: Strings,
    type: EquipmentType,
    distanceM: Double,
    ascentM: Double,
    activeDurationMillis: Long,
    packetCount: Int,
    averagePace: String?,
    averageIncline: Double?,
    samples: List<SessionSampleRecord>,
): List<HistoryMetricTile> {
    val missing = strings.common.missingValue
    val distance = HistoryMetricTile(strings.common.distance, strings.fmt("%.2f km", distanceM / 1000.0))
    val duration = HistoryMetricTile(strings.common.duration, formatDuration(activeDurationMillis))
    val packets = HistoryMetricTile(strings.common.packets, packetCount.toString())
    val ascent = HistoryMetricTile(strings.common.ascent, strings.fmt("%.1f m", ascentM))
    val incline = HistoryMetricTile(
        strings.common.averageIncline,
        averageIncline?.let { strings.fmt("%.1f %%", it) } ?: missing,
    )
    val speed = HistoryMetricTile(
        strings.common.averageSpeed,
        averageSpeedKmh(distanceM, activeDurationMillis)?.let { strings.fmt("%.1f km/h", it) } ?: missing,
    )
    return when (type) {
        EquipmentType.TREADMILL -> listOf(
            distance,
            ascent,
            duration,
            packets,
            HistoryMetricTile(strings.common.averagePace, averagePace ?: missing),
            incline,
        )
        EquipmentType.INDOOR_BIKE -> listOf(
            distance,
            duration,
            speed,
            HistoryMetricTile(
                strings.common.averagePower,
                averagePowerWatts(samples)?.let { strings.fmt("%.0f W", it) } ?: missing,
            ),
            HistoryMetricTile(
                strings.common.averageCadenceStep,
                averageCadenceOrStep(samples)?.let { strings.fmt("%.0f", it) } ?: missing,
            ),
            packets,
        )
        EquipmentType.CROSS_TRAINER -> listOf(distance, ascent, duration, packets, speed, incline)
    }
}

private fun historyMetricTiles(
    strings: Strings,
    type: EquipmentType,
    distanceM: Double,
    ascentM: Double,
    activeDurationMillis: Long,
    averagePace: String?,
    averageIncline: Double?,
    samples: List<SessionSampleRecord>,
): List<HistoryMetricTile> {
    val missing = strings.common.missingValue
    val metrics = mutableListOf(
        HistoryMetricTile(strings.common.distance, strings.fmt("%.2f km", distanceM / 1000.0)),
        HistoryMetricTile(strings.common.duration, formatDuration(activeDurationMillis)),
        HistoryMetricTile(
            strings.common.averageSpeed,
            averageSpeedKmh(distanceM, activeDurationMillis)?.let { strings.fmt("%.2f km/h", it) } ?: missing,
        ),
    )
    if (type.showsPace) {
        metrics += HistoryMetricTile(strings.common.averagePace, averagePace ?: missing)
    }
    if (type.showsClimb) {
        metrics.add(1, HistoryMetricTile(strings.common.ascent, strings.fmt("%.1f m", ascentM)))
        metrics += HistoryMetricTile(
            strings.common.averageIncline,
            averageIncline?.let { strings.fmt("%.1f %%", it) } ?: missing,
        )
        metrics += HistoryMetricTile(
            strings.common.averageAscentRate,
            averageAscentRateMetersPerMinute(ascentM, activeDurationMillis)?.let { strings.fmt("%.1f m/min", it) } ?: missing,
        )
    }
    averagePowerWatts(samples)?.let { metrics += HistoryMetricTile(strings.common.averagePower, strings.fmt("%.0f W", it)) }
    averageHeartRateBpm(samples)?.let { metrics += HistoryMetricTile(strings.common.averageHeartRate, strings.fmt("%.0f bpm", it)) }
    averageCadenceOrStep(samples)?.let { metrics += HistoryMetricTile(strings.common.averageCadenceStep, strings.fmt("%.1f", it)) }
    averageResistance(samples)?.let { metrics += HistoryMetricTile(strings.common.averageResistance, strings.fmt("%.1f", it)) }
    return metrics
}

/** The averages of one value from both sources, over the samples where both delivered it. */
internal data class SourceComparison(
    val equipmentAverage: Double,
    val watchAverage: Double,
) {
    /** How far the watch is above (positive) or below the equipment, in percent. */
    val differencePercent: Double?
        get() = if (equipmentAverage == 0.0) null else (watchAverage - equipmentAverage) / equipmentAverage * 100.0
}

internal fun sourceValue(metric: SourcedMetric, readings: SensorReadings?): Double? =
    when (metric) {
        SourcedMetric.HEART_RATE -> readings?.heartRateBpm?.toDouble()
        SourcedMetric.POWER -> readings?.powerW?.toDouble()
        SourcedMetric.CADENCE -> readings?.cadenceRpm
    }

/**
 * Compares the two sources of [metric]. Only samples where both have a value count, so a source
 * that joined late or dropped out does not shift its average. Null if there is no such sample.
 */
internal fun compareSources(samples: List<SessionSampleRecord>, metric: SourcedMetric): SourceComparison? {
    val pairs = samples.mapNotNull { sample ->
        val equipment = sourceValue(metric, sample.equipment) ?: return@mapNotNull null
        val watch = sourceValue(metric, sample.watch) ?: return@mapNotNull null
        equipment to watch
    }
    if (pairs.isEmpty()) {
        return null
    }

    return SourceComparison(
        equipmentAverage = pairs.map { it.first }.average(),
        watchAverage = pairs.map { it.second }.average(),
    )
}

private fun sourceComparisonTiles(
    strings: Strings,
    type: EquipmentType,
    samples: List<SessionSampleRecord>,
): List<HistoryMetricTile> =
    SourcedMetric.entries.flatMap { metric ->
        val comparison = compareSources(samples, metric) ?: return@flatMap emptyList()
        val label = sourcedMetricLabel(metric, strings)
        val unit = when (metric) {
            SourcedMetric.HEART_RATE -> "bpm"
            SourcedMetric.POWER -> "W"
            SourcedMetric.CADENCE -> if (type.showsPace) "spm" else "rpm"
        }
        listOf(
            HistoryMetricTile(
                "$label · ${strings.common.sourceEquipment}",
                strings.fmt("%.0f $unit", comparison.equipmentAverage),
            ),
            HistoryMetricTile(
                "$label · ${strings.common.sourceWatch}",
                comparison.differencePercent
                    ?.let { strings.fmt("%.0f $unit (%+.1f %%)", comparison.watchAverage, it) }
                    ?: strings.fmt("%.0f $unit", comparison.watchAverage),
            ),
        )
    }

internal val WorkoutSessionRecord.equipmentType: EquipmentType
    get() = EquipmentType.fromMachineKind(equipmentKind)

/** The equipment types that occur in [sessions], in the order the app lists types. */
internal fun recordedEquipmentTypes(sessions: List<WorkoutSessionRecord>): List<EquipmentType> =
    EquipmentType.entries.filter { type -> sessions.any { it.equipmentType == type } }

internal fun filterSessionsByType(
    sessions: List<WorkoutSessionRecord>,
    type: EquipmentType?,
): List<WorkoutSessionRecord> =
    if (type == null) sessions else sessions.filter { it.equipmentType == type }

internal fun averageSpeedKmh(distanceM: Double, durationMillis: Long): Double? {
    if (distanceM <= 0.0 || durationMillis <= 0L) {
        return null
    }
    return distanceM / 1000.0 / (durationMillis.toDouble() / 3_600_000.0)
}

internal fun averageAscentRateMetersPerMinute(ascentM: Double, durationMillis: Long): Double? {
    if (ascentM <= 0.0 || durationMillis <= 0L) {
        return null
    }
    return ascentM / (durationMillis.toDouble() / 60_000.0)
}

internal fun averagePowerWatts(samples: List<SessionSampleRecord>): Double? =
    samples.averageOfNonNull { powerW?.toDouble() }

internal fun averageHeartRateBpm(samples: List<SessionSampleRecord>): Double? =
    samples.averageOfNonNull { heartRateBpm?.toDouble() }

internal fun averageCadenceOrStep(samples: List<SessionSampleRecord>): Double? =
    samples.averageOfNonNull { cadenceRpm ?: stepRateSpm }

internal fun averageResistance(samples: List<SessionSampleRecord>): Double? =
    samples.averageOfNonNull { resistance }

internal fun allTimeHistoryTotals(sessions: List<WorkoutSessionRecord>): AllTimeHistoryTotals =
    sessions.fold(AllTimeHistoryTotals()) { totals, session ->
        totals.copy(
            sessionCount = totals.sessionCount + 1,
            totalDistanceM = totals.totalDistanceM + session.finalDistanceM.coerceAtLeast(0.0),
            totalAscentM = totals.totalAscentM + session.finalAscentM.coerceAtLeast(0.0),
            totalDurationMillis = totals.totalDurationMillis + session.durationMillis,
        )
    }

private fun List<SessionSampleRecord>.averageOfNonNull(value: SessionSampleRecord.() -> Double?): Double? {
    val values = mapNotNull { it.value() }
    return values.takeIf { it.isNotEmpty() }?.average()
}

private fun sessionTitle(session: WorkoutSessionRecord): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(session.startedAtMillis))

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        formatNeutral("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        formatNeutral("%02d:%02d", minutes, seconds)
    }
}

private fun formatChartTime(seconds: Double): String =
    formatDuration((seconds * 1_000.0).toLong())
