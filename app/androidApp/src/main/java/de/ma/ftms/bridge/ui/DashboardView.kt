package de.ma.ftms.bridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ma.ftms.bridge.i18n.LocalStrings
import de.ma.ftms.bridge.i18n.Strings
import de.ma.ftms.bridge.navigation.DashboardComponent
import de.ma.ftms.bridge.runtime.BridgeUiState
import de.ma.ftms.bridge.runtime.LastSessionSummary
import de.ma.ftms.core.SmoothedTreadmillSample

@Composable
fun DashboardView(component: DashboardComponent, onRequestPermissions: () -> Unit) {
    val context = LocalContext.current
    val state by component.state.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val startBlockReason = startBlockReason(state, strings)

    ScreenContainer(title = strings.dashboard.title) {
        LazyColumn(
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
                    onStop = component::onStopBridge,
                )
            }
            item { ConnectionStatus(state = state, strings = strings) }
            item { Metrics(sample = state.latest, strings = strings) }
            item { LastSession(state.lastSummary, strings = strings) }
            item { VerticalGap() }
        }
    }
}

@Composable
private fun BridgeControlCard(
    state: BridgeUiState,
    strings: Strings,
    startBlockReason: String?,
    onRequestPermissions: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    InfoCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (state.running) strings.dashboard.bridgeRunning else strings.dashboard.bridgeIdle,
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
                enabled = startBlockReason == null,
                onClick = onStart,
                modifier = Modifier.weight(1f),
            ) {
                Text(strings.common.start)
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
private fun Metrics(sample: SmoothedTreadmillSample?, strings: Strings) {
    val raw = sample?.raw
    val missing = strings.common.missingValue
    Section(strings.dashboard.sectionLiveMetrics) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricTile(strings.common.speed, raw?.speedKmh?.let { "%.2f km/h".format(it) } ?: missing, Modifier.weight(1f))
                MetricTile(strings.common.incline, raw?.inclinePct?.let { "%.1f %%".format(it) } ?: missing, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricTile(strings.common.distance, sample?.distanceM?.let { "%.2f km".format(it / 1000.0) } ?: missing, Modifier.weight(1f))
                MetricTile(strings.common.ascent, sample?.ascentM?.let { "%.1f m".format(it) } ?: missing, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricTile(strings.common.power, raw?.powerW?.let { "$it W" } ?: missing, Modifier.weight(1f))
                MetricTile(strings.common.heartRate, raw?.heartRateBpm?.let { "$it bpm" } ?: missing, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricTile(strings.common.cadenceStep, raw?.cadenceOrStepRate()?.let { "%.1f".format(it) } ?: missing, Modifier.weight(1f))
                MetricTile(strings.common.elapsed, raw?.elapsedS?.let { "%02d:%02d".format(it / 60, it % 60) } ?: missing, Modifier.weight(1f))
            }
            StatusRow(strings.common.machine, raw?.kind?.let { machineLabel(it, strings) } ?: missing)
            StatusRow(strings.common.resistance, raw?.resistance?.let { "%.1f".format(it) } ?: missing)
        }
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
