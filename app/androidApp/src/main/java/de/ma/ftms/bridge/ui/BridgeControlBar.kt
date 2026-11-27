package de.ma.ftms.bridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.ma.ftms.bridge.i18n.Strings
import de.ma.ftms.bridge.runtime.AutoStartStatus
import de.ma.ftms.bridge.runtime.BridgeUiState
import de.ma.ftms.bridge.runtime.isEquipmentTypeMismatch

/**
 * The state of the bridge with its start, pause and stop, fixed above the navigation, so it stays
 * in reach wherever the page is scrolled to.
 *
 * @param detailed also names the selected equipment before the start and whatever keeps the
 * bridge from starting. The dashboard shows this form; the other tabs show the short one while a
 * session runs.
 */
@Composable
internal fun BridgeControlBar(
    state: BridgeUiState,
    strings: Strings,
    detailed: Boolean,
    onRequestPermissions: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    val startBlockReason = startBlockReason(state, strings)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // The dot repeats what the words next to it say.
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = when {
                                state.paused -> MaterialTheme.colorScheme.tertiary
                                state.running -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outline
                            },
                            shape = CircleShape,
                        ),
                )
                Text(
                    when {
                        state.paused -> strings.dashboard.bridgePaused
                        state.running -> strings.dashboard.bridgeRunning
                        else -> strings.dashboard.bridgeIdle
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (state.running) {
                    Text(
                        formatElapsedTime(state.activeDurationMillis / 1000.0),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (detailed) {
                BridgeControlDetails(state = state, strings = strings, startBlockReason = startBlockReason)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                when {
                    !state.running && !state.permissionsGranted && detailed ->
                        Button(onClick = onRequestPermissions, modifier = Modifier.weight(1f)) {
                            Text(strings.dashboard.grantBluetoothPermissions)
                        }
                    !state.running ->
                        Button(enabled = startBlockReason == null, onClick = onStart, modifier = Modifier.weight(1f)) {
                            Text(strings.common.start)
                        }
                    // The activity on the watch started this session and decides about its pause and end.
                    state.watchLed ->
                        Text(
                            strings.dashboard.watchLedSession,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                        )
                    else -> {
                        if (state.paused) {
                            Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                                Text(strings.notification.resumeAction)
                            }
                        } else {
                            FilledTonalButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                                Text(strings.common.pause)
                            }
                        }
                        OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                            Text(strings.common.stop)
                        }
                    }
                }
            }
        }
    }
}

/** Before the start the selected equipment and watch; always one line on what the bridge waits for or lacks. */
@Composable
private fun BridgeControlDetails(state: BridgeUiState, strings: Strings, startBlockReason: String?) {
    val equipmentName = state.selectedTreadmillName ?: state.selectedTreadmillAddress
    val equipment = if (equipmentName == null) {
        strings.dashboard.noFtmsEquipmentSelected
    } else {
        "$equipmentName · ${equipmentTypeLabel(state.selectedEquipmentType, strings)}"
    }
    val watch = state.selectedGarminName ?: state.selectedGarminId?.toString() ?: strings.dashboard.noGarminWatchSelected
    // Before the start it matters what would be started; during the session the room goes to the page.
    if (!state.running) {
        Text(
            "$equipment · $watch",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }

    val receivedKind = state.latest?.raw?.kind?.takeIf { state.running }
    val autoStartText = when (state.autoStartStatus) {
        AutoStartStatus.OFF -> null
        AutoStartStatus.WAITING -> strings.dashboard.autoStartWaiting
        AutoStartStatus.READY -> strings.dashboard.autoStartReady
        AutoStartStatus.HOLDING -> strings.dashboard.autoStartHolding
    }
    when {
        receivedKind != null && isEquipmentTypeMismatch(state.selectedEquipmentType, receivedKind) ->
            Text(
                strings.dashboard.equipmentTypeMismatch(
                    selected = equipmentTypeLabel(state.selectedEquipmentType, strings),
                    received = machineLabel(receivedKind, strings),
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        // The permission has its own button below.
        !state.running && state.permissionsGranted && startBlockReason != null ->
            Text(startBlockReason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        autoStartText != null ->
            Text(autoStartText, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
    }
}
