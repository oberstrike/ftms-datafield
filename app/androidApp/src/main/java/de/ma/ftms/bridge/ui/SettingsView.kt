package de.ma.ftms.bridge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import de.ma.ftms.bridge.i18n.AppLanguage
import de.ma.ftms.bridge.i18n.LocalStrings
import de.ma.ftms.bridge.i18n.displayLabel
import de.ma.ftms.bridge.navigation.SettingsComponent
import de.ma.ftms.bridge.navigation.SettingsConfig
import de.ma.ftms.bridge.runtime.DashboardChartTimespan
import de.ma.ftms.bridge.runtime.DEFAULT_DASHBOARD_GRAPHS
import de.ma.ftms.bridge.runtime.DashboardMetricKey
import de.ma.ftms.bridge.runtime.HeartRateSource
import de.ma.ftms.bridge.runtime.PowerSource

@Composable
fun SettingsView(component: SettingsComponent) {
    val childStack by component.childStack.subscribeAsState()
    Children(stack = childStack) { child ->
        when (child.instance) {
            SettingsComponent.Child.Overview -> SettingsOverviewView(component)
            SettingsComponent.Child.Bridge -> BridgeSettingsView(component)
            SettingsComponent.Child.Power -> PowerSettingsView(component)
            SettingsComponent.Child.Dashboard -> DashboardSettingsView(component)
            SettingsComponent.Child.Timing -> TimingSettingsView(component)
            SettingsComponent.Child.Language -> LanguageSettingsView(component)
            SettingsComponent.Child.Debug -> DebugSettingsView(component)
            SettingsComponent.Child.RememberedDevices -> RememberedDevicesSettingsView(component)
        }
    }
}

@Composable
private fun SettingsOverviewView(component: SettingsComponent) {
    val strings = LocalStrings.current

    ScreenContainer(title = strings.settings.title) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsDestinationCard(
                    title = strings.settings.bridgeBehavior,
                    subtitle = strings.settings.bridgeBehaviorSubtitle,
                    onClick = { component.navigateTo(SettingsConfig.Bridge) },
                )
            }
            item {
                SettingsDestinationCard(
                    title = strings.settings.powerEstimate,
                    subtitle = strings.settings.powerEstimateSubtitle,
                    onClick = { component.navigateTo(SettingsConfig.Power) },
                )
            }
            item {
                SettingsDestinationCard(
                    title = strings.settings.dashboard,
                    subtitle = strings.settings.dashboardSubtitle,
                    onClick = { component.navigateTo(SettingsConfig.Dashboard) },
                )
            }
            item {
                SettingsDestinationCard(
                    title = strings.settings.timing,
                    subtitle = strings.settings.timingSubtitle,
                    onClick = { component.navigateTo(SettingsConfig.Timing) },
                )
            }
            item {
                SettingsDestinationCard(
                    title = strings.settings.language,
                    subtitle = strings.settings.languageSubtitle,
                    onClick = { component.navigateTo(SettingsConfig.Language) },
                )
            }
            item {
                SettingsDestinationCard(
                    title = strings.settings.debug,
                    subtitle = strings.settings.debugSubtitle,
                    onClick = { component.navigateTo(SettingsConfig.Debug) },
                )
            }
            item {
                SettingsDestinationCard(
                    title = strings.settings.rememberedDevices,
                    subtitle = strings.settings.rememberedDevicesSubtitle,
                    onClick = { component.navigateTo(SettingsConfig.RememberedDevices) },
                )
            }
            item { VerticalGap() }
        }
    }
}

@Composable
private fun SettingsDestinationCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    InfoCard(modifier = Modifier.clickable(onClick = onClick)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsPageScaffold(
    title: String,
    component: SettingsComponent,
    content: @Composable () -> Unit,
) {
    ScreenContainer(title = title) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = component::onBack, modifier = Modifier.weight(1f)) {
                        Text(LocalStrings.current.common.back)
                    }
                }
            }
            item { content() }
            item { VerticalGap() }
        }
    }
}

@Composable
private fun BridgeSettingsView(component: SettingsComponent) {
    val state by component.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val strings = LocalStrings.current

    SettingsPageScaffold(title = strings.settings.bridgeBehavior, component = component) {
        Section(strings.settings.bridgeBehavior) {
            SettingsToggleRow(
                title = strings.settings.autoReconnect,
                subtitle = strings.settings.autoReconnectSubtitle,
                checked = settings.autoReconnect,
                onCheckedChange = {
                    component.onUpdateSettings(settings.copy(autoReconnect = it))
                },
            )
            SettingsToggleRow(
                title = strings.settings.notificationShortcut,
                subtitle = strings.settings.notificationShortcutSubtitle,
                checked = settings.showOpenAppNotificationAction,
                onCheckedChange = {
                    component.onUpdateSettings(settings.copy(showOpenAppNotificationAction = it))
                },
            )
            ChoiceRow(
                title = strings.settings.heartRateSource,
                selected = settings.heartRateSource,
                options = HeartRateSource.entries.toList(),
                label = { it.label(strings) },
                onSelected = {
                    component.onUpdateSettings(settings.copy(heartRateSource = it))
                },
            )
        }
    }
}

@Composable
private fun PowerSettingsView(component: SettingsComponent) {
    val state by component.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val strings = LocalStrings.current

    SettingsPageScaffold(title = strings.settings.powerEstimate, component = component) {
        Section(strings.settings.powerEstimate) {
            SettingsToggleRow(
                title = strings.settings.powerEstimate,
                subtitle = strings.settings.powerEstimateSubtitle,
                checked = settings.powerCalculationEnabled,
                onCheckedChange = {
                    component.onUpdateSettings(settings.copy(powerCalculationEnabled = it))
                },
            )
            ChoiceRow(
                title = strings.settings.powerSource,
                selected = settings.powerSource,
                options = PowerSource.entries.toList(),
                label = { it.label(strings) },
                onSelected = {
                    component.onUpdateSettings(settings.copy(powerSource = it))
                },
            )
            NumericSettingsRow(
                title = strings.settings.powerBodyMassKg,
                value = settings.powerBodyMassKg,
                onValueChange = {
                    component.onUpdateSettings(settings.copy(powerBodyMassKg = it))
                },
            )
            NumericSettingsRow(
                title = strings.settings.powerFlatCost,
                subtitle = strings.settings.powerFlatCostHint,
                value = settings.powerFlatCost,
                onValueChange = {
                    component.onUpdateSettings(settings.copy(powerFlatCost = it))
                },
            )
            if (settings.powerCalculationEnabled && !settings.hasValidPowerEstimateSettings()) {
                Text(
                    strings.settings.powerInvalidSettings,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun DashboardSettingsView(component: SettingsComponent) {
    val state by component.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val strings = LocalStrings.current

    SettingsPageScaffold(title = strings.settings.dashboard, component = component) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Section(strings.settings.dashboardMetrics) {
                Text(
                    strings.settings.dashboardMetricsSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MultiChoiceRow(
                    selected = settings.dashboardMetrics,
                    options = DashboardMetricKey.entries.toList(),
                    label = { it.label(strings) },
                    minimumSelectionText = strings.settings.keepOneDashboardOptionSelected,
                    onSelected = {
                        component.onUpdateSettings(settings.copy(dashboardMetrics = it))
                    },
                )
            }
            Section(strings.settings.dashboardGraphs) {
                Text(
                    strings.settings.dashboardGraphsSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MultiChoiceRow(
                    selected = settings.dashboardGraphs,
                    options = DEFAULT_DASHBOARD_GRAPHS,
                    label = { it.label(strings) },
                    minimumSelectionText = strings.settings.keepOneDashboardOptionSelected,
                    onSelected = {
                        component.onUpdateSettings(settings.copy(dashboardGraphs = it))
                    },
                )
                ChoiceRow(
                    title = strings.settings.dashboardChartTimespan,
                    selected = settings.dashboardChartTimespan,
                    options = DashboardChartTimespan.entries.toList(),
                    label = { it.label(strings) },
                    onSelected = {
                        component.onUpdateSettings(settings.copy(dashboardChartTimespan = it))
                    },
                )
            }
        }
    }
}

@Composable
private fun TimingSettingsView(component: SettingsComponent) {
    val state by component.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val strings = LocalStrings.current

    SettingsPageScaffold(title = strings.settings.timing, component = component) {
        Section(strings.settings.timing) {
            ChoiceRow(
                title = strings.settings.packetTimeout,
                selected = settings.packetTimeoutSeconds,
                options = listOf(10, 15, 30, 60),
                label = { "${it}s" },
                onSelected = {
                    component.onUpdateSettings(settings.copy(packetTimeoutSeconds = it))
                },
            )
            ChoiceRow(
                title = strings.settings.sendInterval,
                selected = settings.sendIntervalMillis,
                options = listOf(500L, 1_000L, 2_000L),
                label = { "${it}ms" },
                onSelected = {
                    component.onUpdateSettings(settings.copy(sendIntervalMillis = it))
                },
            )
            ChoiceRow(
                title = strings.settings.maxLogEntries,
                selected = settings.maxLogEntries,
                options = listOf(500, 1_000, 5_000),
                label = { it.toString() },
                onSelected = {
                    component.onUpdateSettings(settings.copy(maxLogEntries = it))
                },
            )
        }
    }
}

@Composable
private fun LanguageSettingsView(component: SettingsComponent) {
    val state by component.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val strings = LocalStrings.current

    SettingsPageScaffold(title = strings.settings.language, component = component) {
        Section(strings.settings.language) {
            ChoiceRow(
                title = strings.settings.language,
                selected = settings.language,
                options = AppLanguage.entries.toList(),
                label = { it.displayLabel(strings) },
                onSelected = {
                    component.onUpdateSettings(settings.copy(language = it))
                },
            )
        }
    }
}

@Composable
private fun DebugSettingsView(component: SettingsComponent) {
    val state by component.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val strings = LocalStrings.current

    SettingsPageScaffold(title = strings.settings.debug, component = component) {
        Section(strings.settings.debug) {
            SettingsToggleRow(
                title = strings.settings.debugLogging,
                subtitle = if (settings.debugLoggingEnabled) {
                    strings.settings.debugLoggingEnabledSubtitle
                } else {
                    strings.settings.debugLoggingDisabledSubtitle
                },
                checked = settings.debugLoggingEnabled,
                onCheckedChange = {
                    component.onUpdateSettings(settings.copy(debugLoggingEnabled = it))
                },
            )
        }
    }
}

@Composable
private fun RememberedDevicesSettingsView(component: SettingsComponent) {
    val state by component.state.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    var showClearSelectionsConfirm by rememberSaveable { mutableStateOf(false) }

    SettingsPageScaffold(title = strings.settings.rememberedDevices, component = component) {
        Section(strings.settings.rememberedDevices) {
            StatusRow(
                strings.settings.ftmsEquipment,
                state.selectedTreadmillName ?: state.selectedTreadmillAddress ?: strings.common.missingValue,
            )
            StatusRow(
                strings.settings.garminWatch,
                state.selectedGarminName ?: state.selectedGarminId?.toString() ?: strings.common.missingValue,
            )
            ActionRow(
                title = strings.settings.clearRememberedSelections,
                subtitle = strings.settings.clearRememberedSelectionsSubtitle,
                actionLabel = strings.common.clear,
                enabled = state.selectedTreadmillAddress != null || state.selectedGarminId != null,
                onClick = { showClearSelectionsConfirm = true },
            )
        }
    }

    if (showClearSelectionsConfirm) {
        ConfirmActionDialog(
            title = strings.settings.clearRememberedDevicesTitle,
            text = strings.settings.clearRememberedDevicesText,
            confirmLabel = strings.common.clear,
            onConfirm = {
                showClearSelectionsConfirm = false
                component.onClearRememberedSelections()
            },
            onDismiss = { showClearSelectionsConfirm = false },
        )
    }
}

private fun HeartRateSource.label(strings: de.ma.ftms.bridge.i18n.Strings): String =
    when (this) {
        HeartRateSource.GARMIN -> strings.settings.heartRateSourceGarmin
        HeartRateSource.MACHINE -> strings.settings.heartRateSourceMachine
    }

private fun PowerSource.label(strings: de.ma.ftms.bridge.i18n.Strings): String =
    when (this) {
        PowerSource.FTMS_PREFERRED -> strings.settings.powerSourceFtmsPreferred
        PowerSource.CALCULATED -> strings.settings.powerSourceCalculated
    }

private fun de.ma.ftms.bridge.runtime.BridgeSettings.hasValidPowerEstimateSettings(): Boolean =
    powerBodyMassKg != null && powerBodyMassKg > 0.0 && powerFlatCost != null && powerFlatCost > 0.0

@Composable
private fun NumericSettingsRow(
    title: String,
    value: Double?,
    onValueChange: (Double?) -> Unit,
    subtitle: String? = null,
) {
    var text by rememberSaveable { mutableStateOf(value?.formatForSettings().orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { updated ->
                text = updated
                onValueChange(updated.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 })
            },
            label = { Text(title) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Double.formatForSettings(): String =
    if (this % 1.0 == 0.0) "%.0f".format(this) else toString()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> MultiChoiceRow(
    selected: List<T>,
    options: List<T>,
    label: (T) -> String,
    minimumSelectionText: String,
    onSelected: (List<T>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                val isSelected = option in selected
                val isOnlySelectedOption = isSelected && selected.size == 1
                FilterChip(
                    selected = isSelected,
                    enabled = !isOnlySelectedOption,
                    onClick = {
                        val updated = if (isSelected) {
                            selected - option
                        } else {
                            options.filter { it in selected || it == option }
                        }
                        onSelected(updated)
                    },
                    label = { Text(label(option)) },
                )
            }
        }
        if (selected.size <= 1) {
            Text(
                minimumSelectionText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(
    title: String,
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}
