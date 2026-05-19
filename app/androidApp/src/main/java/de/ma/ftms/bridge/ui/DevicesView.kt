package de.ma.ftms.bridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ma.ftms.bridge.i18n.LocalStrings
import de.ma.ftms.bridge.i18n.Strings
import de.ma.ftms.bridge.navigation.DevicesComponent
import de.ma.ftms.bridge.runtime.BridgeUiState

@Composable
fun DevicesView(component: DevicesComponent) {
    val state by component.state.collectAsStateWithLifecycle()
    val strings = LocalStrings.current

    ScreenContainer(title = strings.devices.title) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { FtmsEquipmentSection(component, state, strings) }
            item { GarminSection(component, state, strings) }
            item { VerticalGap() }
        }
    }
}

@Composable
private fun FtmsEquipmentSection(component: DevicesComponent, state: BridgeUiState, strings: Strings) {
    Section(strings.devices.ftmsEquipment) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(strings.devices.bluetoothScanner, fontWeight = FontWeight.SemiBold)
                Text(
                    state.selectedTreadmillName ?: state.selectedTreadmillAddress ?: strings.devices.noEquipmentSelected,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                enabled = state.permissionsGranted && state.bleAvailable,
                onClick = component::onToggleScan,
            ) {
                Text(if (state.scanning) strings.common.stop else strings.devices.scan)
            }
        }

        if (state.scanning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (!state.permissionsGranted || !state.bleAvailable) {
            Text(startBlockReason(state, strings) ?: strings.devices.bluetoothUnavailable, color = MaterialTheme.colorScheme.error)
        }
        if (state.treadmills.isEmpty()) {
            Text(strings.devices.noFtmsEquipmentFound, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.treadmills.forEach { item ->
                SelectableDeviceRow(
                    selected = state.selectedTreadmillAddress == item.address,
                    title = item.name,
                    subtitle = "${item.address} · ${item.rssi} dBm",
                    badge = if (item.hasFtms) "FTMS" else strings.devices.badgeName,
                    badgeTone = if (item.hasFtms) BadgeTone.SUCCESS else BadgeTone.NEUTRAL,
                    onClick = { component.onSelectTreadmill(item.address) },
                )
            }
        }
    }
}

@Composable
private fun GarminSection(component: DevicesComponent, state: BridgeUiState, strings: Strings) {
    Section(strings.devices.garminWatch) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(strings.devices.connectIq, fontWeight = FontWeight.SemiBold)
                Text(
                    state.selectedGarminName ?: state.selectedGarminId?.toString() ?: strings.devices.noWatchSelected,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = component::onRefreshGarminDevices) {
                Text(strings.devices.refresh)
            }
        }

        if (state.garmins.isEmpty()) {
            Text(strings.devices.noGarminDeviceLoaded, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Text(strings.devices.garminDiagnostics, fontWeight = FontWeight.SemiBold)
        StatusRow(strings.devices.garminSdkStatus, state.garminSdkStatus)
        StatusRow(strings.devices.garminKnownDevices, state.garminKnownDeviceCount.toString())
        StatusRow(
            strings.devices.garminLastMessage,
            state.garminLastMessage.ifBlank { strings.common.missingValue },
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.garmins.forEach { item ->
                val installed = item.appInstalled == true
                SelectableDeviceRow(
                    selected = state.selectedGarminId == item.id,
                    title = item.name,
                    subtitle = "${item.status} · id ${item.id}",
                    badge = when (item.appInstalled) {
                        true -> strings.devices.badgeApp
                        false -> strings.devices.badgeNoApp
                        null -> strings.devices.badgeCheck
                    },
                    badgeTone = if (installed) BadgeTone.SUCCESS else BadgeTone.WARNING,
                    onClick = { component.onSelectGarmin(item.id) },
                )
            }
        }
    }
}
