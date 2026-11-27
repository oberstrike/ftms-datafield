package de.ma.ftms.bridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.ma.ftms.bridge.i18n.LocalStrings
import de.ma.ftms.core.FootPodStatus
import de.ma.ftms.bridge.runtime.isFootPodMode
import de.ma.ftms.bridge.runtime.isWatchReadyForStart
import de.ma.ftms.bridge.i18n.Strings
import de.ma.ftms.bridge.runtime.BridgeUiState
import de.ma.ftms.bridge.runtime.EquipmentType
import de.ma.ftms.bridge.runtime.SourcedMetric
import de.ma.ftms.bridge.runtime.ValueSource
import de.ma.ftms.core.SensorReadings
import kotlin.math.roundToInt
import de.ma.ftms.bridge.runtime.GarminConnectProblem
import de.ma.ftms.core.FtmsMachineKind

@Composable
fun ScreenContainer(
    @Suppress("UNUSED_PARAMETER") title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        content()
    }
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    InfoCard {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        content()
    }
}

@Composable
fun StatusRow(label: String, value: String) {
    StatusRow(label, AnnotatedString(value))
}

/** The same for a value of which a part is set apart, such as a second number in another colour. */
@Composable
fun StatusRow(label: String, value: AnnotatedString) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.weight(0.9f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            value,
            modifier = Modifier.weight(1.1f),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            overflow = TextOverflow.Ellipsis,
            maxLines = 3,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun StatusBadge(
    label: String,
    tone: BadgeTone,
    modifier: Modifier = Modifier,
) {
    val colors = badgeColors(tone)
    Surface(
        modifier = modifier.defaultMinSize(minHeight = 32.dp),
        shape = MaterialTheme.shapes.small,
        color = colors.first,
        contentColor = colors.second,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

enum class BadgeTone {
    NEUTRAL,
    SUCCESS,
    WARNING,
    ERROR,
}

@Composable
private fun badgeColors(tone: BadgeTone): Pair<Color, Color> =
    when (tone) {
        BadgeTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurface
        BadgeTone.SUCCESS -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        BadgeTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        BadgeTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }

@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    ElevatedCard(
        modifier = modifier.defaultMinSize(minHeight = 86.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2)
            if (subtitle != null) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun SelectableDeviceRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    badge: String,
    badgeTone: BadgeTone,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (selected) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            StatusBadge(label = badge, tone = badgeTone)
        }
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun ActionRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    enabled: Boolean = true,
    /** Puts the button below the text instead of next to it, where both together are too wide. */
    stacked: Boolean = false,
    onClick: () -> Unit,
) {
    if (stacked) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(enabled = enabled, onClick = onClick, modifier = Modifier.align(Alignment.End)) {
                Text(actionLabel)
            }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(enabled = enabled, onClick = onClick) {
            Text(actionLabel)
        }
    }
}

@Composable
fun ConfirmActionDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.common.cancel)
            }
        },
    )
}

@Composable
fun SelectedText(label: String, value: String?) {
    val strings = LocalStrings.current
    Text(
        "$label: ${value ?: strings.common.missingValue}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
fun VerticalGap(height: Int = 8) {
    Spacer(Modifier.height(height.dp))
}

fun startBlockReason(state: BridgeUiState, strings: Strings): String? =
    when {
        state.running && !state.paused -> strings.dashboard.startBlockBridgeRunning
        !state.permissionsGranted -> strings.dashboard.startBlockBluetoothPermissions
        !state.bleAvailable -> strings.dashboard.startBlockBluetoothUnavailable
        state.selectedTreadmillAddress == null -> strings.dashboard.startBlockSelectFtms
        state.garminConnectProblem != null -> garminConnectProblemText(state.garminConnectProblem, strings)
        state.selectedGarminId == null -> strings.dashboard.startBlockSelectGarmin
        !isWatchReadyForStart(state) -> strings.dashboard.startBlockGarminApp
        else -> null
    }

fun garminConnectProblemText(problem: GarminConnectProblem, strings: Strings): String =
    when (problem) {
        GarminConnectProblem.NOT_INSTALLED -> strings.devices.garminConnectNotInstalled
        GarminConnectProblem.UPGRADE_NEEDED -> strings.devices.garminConnectUpgradeNeeded
        GarminConnectProblem.SERVICE_ERROR -> strings.devices.garminConnectServiceError
    }

fun valueSourceLabel(source: ValueSource, strings: Strings): String =
    when (source) {
        ValueSource.EQUIPMENT -> strings.common.sourceEquipment
        ValueSource.WATCH -> strings.common.sourceWatch
    }

fun sourcedMetricLabel(metric: SourcedMetric, strings: Strings, type: EquipmentType): String =
    when (metric) {
        SourcedMetric.HEART_RATE -> strings.common.heartRate
        SourcedMetric.POWER -> strings.common.power
        SourcedMetric.CADENCE -> cadenceLabel(type, strings)
    }

/** What the cadence of [type] is called: a step rate while running, a cadence otherwise. */
fun cadenceLabel(type: EquipmentType, strings: Strings): String =
    if (type.cadenceIsStepRate) strings.common.stepRate else strings.common.cadence

fun averageCadenceLabel(type: EquipmentType, strings: Strings): String =
    if (type.cadenceIsStepRate) strings.common.averageStepRate else strings.common.averageCadence

/** Steps per minute while running, revolutions per minute otherwise. */
fun cadenceUnit(type: EquipmentType): String =
    if (type.cadenceIsStepRate) "spm" else "rpm"

/** What the running sensor for the watch is doing, for the status card. */
internal fun footPodStatusLabel(state: BridgeUiState, strings: Strings): String =
    when (state.footPodStatus) {
        FootPodStatus.OFF ->
            if (state.selectedEquipmentType == EquipmentType.TREADMILL) {
                strings.dashboard.footPodOff
            } else {
                strings.dashboard.footPodTreadmillOnly
            }
        FootPodStatus.UNSUPPORTED -> strings.dashboard.footPodUnsupported
        FootPodStatus.PERMISSION_MISSING -> strings.dashboard.footPodPermissionMissing
        FootPodStatus.ADVERTISING -> strings.dashboard.footPodAdvertising
        FootPodStatus.CONNECTED -> strings.dashboard.footPodConnected
        FootPodStatus.FAILED -> strings.dashboard.footPodFailed
    }

/** One source's value of [metric] as shown to the user, or null if the source has none. */
internal fun formatSourceValue(metric: SourcedMetric, readings: SensorReadings?, type: EquipmentType): String? =
    when (metric) {
        SourcedMetric.HEART_RATE -> readings?.heartRateBpm?.let { "$it bpm" }
        SourcedMetric.POWER -> readings?.powerW?.let { "$it W" }
        SourcedMetric.CADENCE -> readings?.cadenceRpm?.let { "${it.roundToInt()} ${cadenceUnit(type)}" }
    }

fun equipmentTypeLabel(type: EquipmentType, strings: Strings): String =
    machineLabel(type.machineKind, strings)

fun machineLabel(kind: Int, strings: Strings): String =
    when (kind) {
        FtmsMachineKind.INDOOR_BIKE -> strings.dashboard.machineBike
        FtmsMachineKind.TREADMILL -> strings.dashboard.machineTreadmill
        FtmsMachineKind.CROSS_TRAINER -> strings.dashboard.machineCrossTrainer
        else -> strings.common.unknown
    }
