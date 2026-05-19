package de.ma.ftms.bridge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import de.ma.ftms.bridge.i18n.Strings
import de.ma.ftms.bridge.navigation.HistoryComponent
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

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(strings.history.storedSessions, fontWeight = FontWeight.Bold)
            Text(strings.history.savedSessions(sessions.size), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
        items(sessions, key = { it.sessionId }) { session ->
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

@Composable
private fun SessionCard(strings: Strings, session: WorkoutSessionRecord, onClick: () -> Unit) {
    InfoCard(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(sessionTitle(session), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(formatDuration(session.durationMillis), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusBadge(
                label = session.finalStatus,
                tone = if (session.finalStatus == "stopped") BadgeTone.SUCCESS else BadgeTone.WARNING,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile(strings.common.distance, "%.2f km".format(session.finalDistanceM / 1000.0), Modifier.weight(1f))
            MetricTile(strings.common.ascent, "%.1f m".format(session.finalAscentM), Modifier.weight(1f))
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricTile(strings.common.distance, "%.2f km".format(session.finalDistanceM / 1000.0), Modifier.weight(1f))
                    MetricTile(strings.common.ascent, "%.1f m".format(session.finalAscentM), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricTile(strings.common.duration, formatDuration(session.durationMillis), Modifier.weight(1f))
                    MetricTile(strings.common.packets, session.packetCount.toString(), Modifier.weight(1f))
                }
                StatusRow(strings.common.status, session.finalStatus)
                StatusRow(strings.common.treadmill, session.treadmillName ?: strings.common.missingValue)
                StatusRow(strings.common.garmin, session.garminName ?: strings.common.missingValue)
                StatusRow(strings.common.sends, strings.common.sendSummary(session.sendSuccessCount, session.sendFailureCount))
                if (session.lastError.isNotBlank()) {
                    StatusRow(strings.common.lastError, session.lastError)
                }
            }
        }
        if (samples.isEmpty()) {
            item {
                InfoCard {
                    Text(strings.history.noGraphSamplesStored, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            item { MetricChart(strings, strings.history.chartSpeed, "km/h", samples.points { speedKmh }) }
            item { MetricChart(strings, strings.common.distance, "m", samples.points(monotonic = true) { smoothedDistanceM }) }
            item { MetricChart(strings, strings.history.chartIncline, "%", samples.points { inclinePct }) }
            item { MetricChart(strings, strings.history.chartAscent, "m", samples.points(monotonic = true) { ascentM }) }
            if (samples.any { it.powerW != null }) {
                item { MetricChart(strings, strings.history.chartPower, "W", samples.points { powerW?.toDouble() }) }
            }
            if (samples.any { it.heartRateBpm != null }) {
                item { MetricChart(strings, strings.history.chartHeartRate, "bpm", samples.points { heartRateBpm?.toDouble() }) }
            }
            if (samples.any { it.cadenceRpm != null || it.stepRateSpm != null }) {
                item { MetricChart(strings, strings.history.chartCadenceStep, "rpm/spm", samples.points { cadenceRpm ?: stepRateSpm }) }
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
private fun MetricChart(strings: Strings, title: String, unit: String, points: List<ChartPoint>) {
    Section(if (unit.isBlank()) title else "$title ($unit)") {
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
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(strings.common.minValue(min), color = labelColor, style = MaterialTheme.typography.bodySmall)
            Text(strings.common.maxValue(max), color = labelColor, style = MaterialTheme.typography.bodySmall)
        }
        SimpleLineChart(
            points = points,
            minY = min,
            maxY = max,
            lineColor = lineColor,
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

@Composable
private fun SimpleLineChart(
    points: List<ChartPoint>,
    minY: Double,
    maxY: Double,
    lineColor: Color,
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
        val minX = points.first().x
        val maxX = points.last().x
        val xRange = (maxX - minX).takeIf { it > 0.0 } ?: 1.0
        val yRange = (maxY - minY).takeIf { it > 0.0 } ?: 1.0

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
        points.forEach { point ->
            val x = leftPadding + (((point.x - minX) / xRange).toFloat() * chartWidth)
            val y = topPadding + ((1f - ((point.y - minY) / yRange).toFloat()) * chartHeight)
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

private fun sessionTitle(session: WorkoutSessionRecord): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(session.startedAtMillis))

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun formatChartTime(seconds: Double): String =
    formatDuration((seconds * 1_000.0).toLong())
