package de.ma.ftms.bridge.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ma.ftms.bridge.i18n.LocalStrings
import de.ma.ftms.bridge.i18n.Strings
import de.ma.ftms.bridge.i18n.fmt
import de.ma.ftms.bridge.navigation.DashboardComponent
import de.ma.ftms.bridge.runtime.BridgeUiState
import de.ma.ftms.bridge.runtime.DashboardChartTimespan
import de.ma.ftms.bridge.runtime.DashboardMetricKey
import de.ma.ftms.bridge.runtime.EquipmentType
import de.ma.ftms.bridge.runtime.FALLBACK_POWER_RANGE
import de.ma.ftms.bridge.runtime.LastSessionSummary
import de.ma.ftms.bridge.runtime.PowerControlState
import de.ma.ftms.bridge.runtime.PowerControlStatus
import de.ma.ftms.bridge.runtime.SensorSources
import de.ma.ftms.bridge.runtime.SourcedMetric
import de.ma.ftms.bridge.runtime.TargetPowerPoint
import de.ma.ftms.bridge.runtime.ValueSource
import de.ma.ftms.bridge.runtime.isFootPodMode
import de.ma.ftms.bridge.runtime.parseTargetPowerInput
import de.ma.ftms.bridge.runtime.suggestedTargetPower
import de.ma.ftms.core.FtmsPowerRange
import de.ma.ftms.core.SensorReadings
import de.ma.ftms.core.SmoothedTreadmillSample

/** Four digits cover every target an indoor bike takes. */
private const val MAX_TARGET_POWER_INPUT_DIGITS = 4

/**
 * The dashboard. While a session runs it leads with the charts, each with its latest value, then
 * the remaining live values, the choice of sources and the connection status. Without a session
 * it shows what is connected and how the last session went. Start, pause and stop are not here:
 * they sit in the bar fixed below the page ([BridgeControlBar]).
 */
@Composable
fun DashboardView(component: DashboardComponent) {
    val state by component.state.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val type = state.selectedEquipmentType
    val layout = state.settings.dashboardLayout(type)
    // What this equipment and the watch delivered in the running session; null before the first sample.
    val delivered = remember(state.liveDashboardSamples) {
        state.liveDashboardSamples.takeIf { it.isNotEmpty() }?.let(::deliveredDashboardMetrics)
    }
    val sourcedMetrics = remember(state.liveDashboardSamples) {
        SourcedMetric.entries.filter { it in deliveredSourcedMetrics(state.liveDashboardSamples) }
    }
    val charts = if (delivered == null) emptyList() else layout.graphs.filter { it in delivered }
    // A bike connected ahead of the session can already be given a target.
    val showTargetPower = type == EquipmentType.INDOOR_BIKE && (state.running || state.powerControl.canSetTarget)
    val targetPowerCard: @Composable () -> Unit = {
        TargetPowerCard(
            control = state.powerControl,
            // The bike holds the target, so its own measurement stands next to it.
            measuredWatts = state.latest?.raw?.let { it.equipment?.powerW ?: it.powerW },
            // Directly below the power chart, that chart already shows the measured value.
            suggestedWatts = suggestedTargetPower(state.lastOwnTargetPower, state.selectedTreadmillAddress),
            showMeasured = DashboardMetricKey.POWER !in charts,
            presets = state.settings.targetPowerPresets,
            stepWatts = state.settings.targetPowerStepWatts,
            strings = strings,
            onSetTarget = component::onSetTargetPower,
            onAdjust = component::onAdjustTargetPower,
            onClearTarget = component::onClearTargetPower,
        )
    }

    // A list keeps the item it shows in place when another is added above it. The page is to
    // open on the first chart, so it goes back to the top whenever a new item takes the lead.
    val listState = rememberLazyListState()
    val leadingItem = charts.firstOrNull()
    LaunchedEffect(state.running, delivered != null, leadingItem) {
        listState.scrollToItem(0)
    }

    ScreenContainer(title = strings.dashboard.title) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.running && delivered == null) {
                item(key = "waiting") {
                    InfoCard {
                        Text(strings.dashboard.waitingForData, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // The target belongs to the power chart: directly below it, or first where there is none.
            if (showTargetPower && DashboardMetricKey.POWER !in charts) {
                item(key = "target-power") { targetPowerCard() }
            }
            charts.forEach { key ->
                item(key = "chart-${key.name}") {
                    val showsTarget = key == DashboardMetricKey.POWER && type == EquipmentType.INDOOR_BIKE
                    MetricChart(
                        strings = strings,
                        key = key,
                        type = type,
                        samples = state.liveDashboardSamples,
                        timespan = state.settings.dashboardChartTimespan,
                        targetTrace = if (showsTarget) state.targetPowerTrace else emptyList(),
                    )
                }
                if (showTargetPower && key == DashboardMetricKey.POWER) {
                    item(key = "target-power") { targetPowerCard() }
                }
            }
            if (delivered != null) {
                item(key = "values") {
                    Metrics(
                        sample = state.latest,
                        samples = state.liveDashboardSamples,
                        activeDurationMillis = state.activeDurationMillis,
                        metricKeys = layout.metrics.filter { it in delivered },
                        type = type,
                        strings = strings,
                    )
                }
            }
            if (sourcedMetrics.isNotEmpty()) {
                item(key = "sources") {
                    SourcesCard(
                        metrics = sourcedMetrics,
                        sources = state.settings.sensorSources(type),
                        equipment = state.latest?.raw?.equipment,
                        watch = state.latest?.raw?.watch,
                        type = type,
                        strings = strings,
                        onSelect = component::onSelectSensorSource,
                    )
                }
            }
            item(key = "status") { ConnectionStatus(state = state, strings = strings) }
            if (!state.running) {
                item(key = "last-session") { LastSession(state.lastSummary, showAscent = type.showsClimb, strings = strings) }
            }
            item(key = "end") { VerticalGap() }
        }
    }
}

/**
 * The target power of an indoor bike: what the bike measures next to what is asked of it, the
 * target as a number to type in, and steps up and down.
 *
 * There are two step sizes: the smallest the bike takes, for an exact target, and [stepWatts]
 * from the settings, to get there quickly. They merge into one pair where both are the same.
 *
 * @param suggestedWatts the target last set in the app on this bike; the input starts with it
 * while no target is set.
 * @param showMeasured whether the card shows the measured power itself; not needed where the
 * power chart stands right above it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetPowerCard(
    control: PowerControlState,
    measuredWatts: Int?,
    suggestedWatts: Int?,
    showMeasured: Boolean,
    presets: List<Int>,
    stepWatts: Int,
    strings: Strings,
    onSetTarget: (Int) -> Unit,
    onAdjust: (Int) -> Unit,
    onClearTarget: () -> Unit,
) {
    val canSet = control.canSetTarget
    val failed = control.status == PowerControlStatus.REJECTED || control.status == PowerControlStatus.FAILED
    val statusText = when (control.status) {
        PowerControlStatus.DISCONNECTED -> strings.dashboard.targetPowerDisconnected
        PowerControlStatus.UNSUPPORTED -> strings.dashboard.targetPowerUnsupported
        PowerControlStatus.READY -> strings.dashboard.targetPowerReady
        PowerControlStatus.SENDING -> strings.dashboard.targetPowerSending
        PowerControlStatus.ACTIVE ->
            if (control.targetIsReported) strings.dashboard.targetPowerReported else strings.dashboard.targetPowerActive
        PowerControlStatus.REJECTED -> strings.dashboard.targetPowerRejected
        PowerControlStatus.FAILED -> strings.dashboard.targetPowerFailed
    }
    val range = control.range ?: FALLBACK_POWER_RANGE
    val fineStep = control.stepWatts.coerceAtLeast(1)
    val steps = listOf(fineStep, stepWatts).filter { it >= fineStep }.distinct().sorted()
    var showInput by rememberSaveable { mutableStateOf(false) }

    InfoCard {
        Text(
            strings.dashboard.targetPower,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (showMeasured) {
                PowerValue(
                    label = strings.dashboard.targetPowerCurrent,
                    value = measuredWatts?.let { "$it W" } ?: strings.dashboard.targetPowerNotSet,
                    modifier = Modifier.weight(1f),
                )
            }
            PowerValue(
                label = strings.dashboard.targetPowerTarget,
                value = control.targetWatts?.let { "$it W" } ?: strings.dashboard.targetPowerNotSet,
                modifier = Modifier.weight(1f),
                onClick = { showInput = true }.takeIf { canSet },
                onClickLabel = strings.dashboard.targetPowerEnter.takeIf { canSet },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            (steps.reversed().map { -it } + steps).forEach { delta ->
                val description = if (delta < 0) {
                    strings.dashboard.targetPowerDecrease(-delta)
                } else {
                    strings.dashboard.targetPowerIncrease(delta)
                }
                OutlinedButton(
                    enabled = canSet,
                    onClick = { onAdjust(delta) },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = description },
                ) {
                    Text(if (delta < 0) "−${-delta}" else "+$delta", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (presets.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                presets.forEach { watts ->
                    FilterChip(
                        selected = control.targetWatts == watts,
                        enabled = canSet,
                        onClick = { onSetTarget(watts) },
                        label = { Text("$watts W") },
                    )
                }
            }
        }
        // One quiet line: what the bike does with the target, and what it accepts.
        Text(
            listOfNotNull(
                statusText,
                presets.isEmpty().takeIf { it }?.let { strings.dashboard.targetPowerNoPresets },
                control.range?.let { strings.dashboard.targetPowerRange(it.minWatts, it.maxWatts) },
            ).joinToString(" "),
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (showInput && canSet) {
        TargetPowerInputDialog(
            initialWatts = control.targetWatts
                ?: suggestedWatts?.let(control::acceptedTarget)
                ?: measuredWatts?.takeIf { it > 0 }?.let(control::acceptedTarget),
            range = range,
            stepWatts = control.stepWatts,
            strings = strings,
            onConfirm = { watts ->
                showInput = false
                onSetTarget(watts)
            },
            // Only with a target is there one to give up.
            onClear = {
                showInput = false
                onClearTarget()
            }.takeIf { control.targetWatts != null },
            onDismiss = { showInput = false },
        )
    }
}

/** One power value with its name; with [onClick] it is a field to tap, drawn with an outline. */
@Composable
private fun PowerValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    val shape = MaterialTheme.shapes.medium
    val frame = if (onClick != null) {
        Modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .then(frame)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        if (onClickLabel != null) {
            Text(
                onClickLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Asks for a target power as a number. Only a value the bike accepts can be confirmed. With
 * [onClear] the dialog also offers to give the target up, which the app then no longer sets, in
 * this session or the next.
 */
@Composable
private fun TargetPowerInputDialog(
    initialWatts: Int?,
    range: FtmsPowerRange,
    stepWatts: Int,
    strings: Strings,
    onConfirm: (Int) -> Unit,
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var field by remember {
        val text = initialWatts?.toString().orEmpty()
        // Selected, so that typing replaces the old value.
        mutableStateOf(TextFieldValue(text = text, selection = TextRange(0, text.length)))
    }
    val watts = parseTargetPowerInput(field.text, range)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.dashboard.targetPowerEnter) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { new ->
                    if (new.text.length <= MAX_TARGET_POWER_INPUT_DIGITS && new.text.all(Char::isDigit)) {
                        field = new
                    }
                },
                label = { Text(strings.dashboard.targetPowerInputLabel) },
                suffix = { Text("W") },
                singleLine = true,
                isError = field.text.isNotEmpty() && watts == null,
                supportingText = {
                    Text(strings.dashboard.targetPowerInputRange(range.minWatts, range.maxWatts, stepWatts))
                },
                textStyle = MaterialTheme.typography.headlineSmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { watts?.let(onConfirm) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(enabled = watts != null, onClick = { watts?.let(onConfirm) }) {
                Text(strings.dashboard.targetPowerSet)
            }
        },
        dismissButton = {
            Row {
                if (onClear != null) {
                    TextButton(onClick = onClear) { Text(strings.dashboard.targetPowerClear) }
                }
                TextButton(onClick = onDismiss) { Text(strings.common.cancel) }
            }
        },
    )
}

/**
 * Shows what equipment and watch report next to each other and lets the user pick per value.
 * [metrics] are the values at least one of the two delivers; the others have nothing to pick.
 */
@Composable
private fun SourcesCard(
    metrics: List<SourcedMetric>,
    sources: SensorSources,
    equipment: SensorReadings?,
    watch: SensorReadings?,
    type: EquipmentType,
    strings: Strings,
    onSelect: (SourcedMetric, ValueSource) -> Unit,
) {
    Section(strings.dashboard.sectionSources) {
        metrics.forEach { metric ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    sourcedMetricLabel(metric, strings, type),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(0.8f),
                )
                ValueSource.entries.forEach { source ->
                    val readings = if (source == ValueSource.EQUIPMENT) equipment else watch
                    val value = formatSourceValue(metric, readings, type) ?: strings.common.missingValue
                    FilterChip(
                        selected = sources.of(metric) == source,
                        onClick = { onSelect(metric, source) },
                        label = {
                            Column {
                                Text(valueSourceLabel(source, strings), style = MaterialTheme.typography.labelSmall)
                                Text(value, style = MaterialTheme.typography.titleMedium)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Text(
            strings.dashboard.sourcesHint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
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
        if (state.settings.virtualFootPod) {
            StatusRow(strings.dashboard.footPod, footPodStatusLabel(state, strings))
            if (isFootPodMode(state.settings, state.selectedEquipmentType)) {
                Text(
                    strings.dashboard.footPodHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
    type: EquipmentType,
    strings: Strings,
) {
    Section(strings.dashboard.sectionLiveMetrics) {
        if (metricKeys.isEmpty()) {
            Text(strings.dashboard.noDeliveredValues, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            metricKeys.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { key ->
                        MetricTile(
                            key.label(strings, type),
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

/**
 * One value of the running session over time.
 *
 * @param samples all samples of the session; running averages need them from its start.
 * @param timespan how much of the session's end is drawn.
 * @param targetTrace the changes of the target power, to draw it as a line next to the measured
 * power; empty for every other chart.
 */
@Composable
private fun MetricChart(
    strings: Strings,
    key: DashboardMetricKey,
    type: EquipmentType,
    samples: List<SmoothedTreadmillSample>,
    timespan: DashboardChartTimespan,
    targetTrace: List<TargetPowerPoint>,
) {
    val title = key.label(strings, type)
    val targetLabel = strings.dashboard.targetPowerTarget
    val lines = remember(samples, key, timespan, targetTrace, title, targetLabel) {
        val points = samples
            .points(key, monotonic = key == DashboardMetricKey.ASCENT)
            .lastMillis(timespan.durationMillis)
        val origin = samples.firstOrNull()?.raw?.timestampMillis
        when {
            points.size < 2 -> emptyList()
            origin == null || targetTrace.isEmpty() -> listOf(ChartLine(title, points))
            else -> listOf(
                ChartLine(title, points),
                ChartLine(
                    label = targetLabel,
                    points = targetPowerLine(targetTrace, origin, fromSeconds = points.first().x, toSeconds = points.last().x),
                    role = ChartLineRole.TARGET,
                ),
            )
        }
    }
    InfoCard {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (lines.isEmpty()) {
            Text(strings.history.notEnoughSamples, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@InfoCard
        }

        TimeSeriesChart(
            title = title,
            lines = lines,
            unit = key.unit(type),
            strings = strings,
            format = key.chartFormat(strings),
            reverseY = key.reverseChartYAxis(),
            showStats = key.chartShowsStats(),
            chartHeight = 150.dp,
        )
    }
}

private fun List<SmoothedTreadmillSample>.points(
    key: DashboardMetricKey,
    monotonic: Boolean,
): List<ChartPoint> {
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
        ChartPoint(x, chartY)
    }
}

@Composable
private fun LastSession(summary: LastSessionSummary?, showAscent: Boolean, strings: Strings) {
    Section(strings.dashboard.sectionLastSession) {
        if (summary == null) {
            Text(strings.dashboard.noPreviousSession, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Section
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile(strings.common.distance, strings.fmt("%.2f km", summary.finalDistanceM / 1000.0), Modifier.weight(1f))
            if (showAscent) {
                MetricTile(strings.common.ascent, strings.fmt("%.1f m", summary.finalAscentM), Modifier.weight(1f))
            }
        }
        StatusRow(strings.common.status, summary.finalStatus)
        StatusRow(strings.common.packets, summary.packetCount.toString())
        StatusRow(strings.common.sends, strings.common.sendSummary(summary.sendSuccessCount, summary.sendFailureCount))
        if (summary.lastError.isNotBlank()) {
            StatusRow(strings.common.lastError, summary.lastError)
        }
    }
}
