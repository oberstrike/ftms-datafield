package de.ma.ftms.bridge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextOverflow
import de.ma.ftms.bridge.i18n.Strings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import de.ma.ftms.bridge.BuildConfig
import de.ma.ftms.bridge.about.OpenSourceLibrary
import de.ma.ftms.bridge.about.ThirdPartySdk
import de.ma.ftms.bridge.about.appVersionLabel
import de.ma.ftms.bridge.about.openSourceLibraries
import de.ma.ftms.bridge.about.thirdPartySdks
import de.ma.ftms.bridge.i18n.AppLanguage
import de.ma.ftms.bridge.i18n.LocalStrings
import de.ma.ftms.bridge.i18n.displayLabel
import de.ma.ftms.bridge.navigation.SettingsComponent
import de.ma.ftms.bridge.navigation.SettingsConfig
import de.ma.ftms.bridge.runtime.DEFAULT_TARGET_POWER_PRESETS
import de.ma.ftms.bridge.runtime.DashboardChartTimespan
import de.ma.ftms.bridge.runtime.MAX_TARGET_POWER_PRESETS
import de.ma.ftms.bridge.runtime.TARGET_POWER_PRESET_RANGE
import de.ma.ftms.bridge.runtime.TARGET_POWER_STEP_OPTIONS
import de.ma.ftms.bridge.runtime.isNewTargetPowerPresetAllowed
import de.ma.ftms.bridge.runtime.sanitizeTargetPowerPresets
import de.ma.ftms.bridge.runtime.EquipmentType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import de.ma.ftms.bridge.ble.advertisePermissions
import de.ma.ftms.footpod.hasAdvertisePermission
import de.ma.ftms.bridge.runtime.BridgeRuntime
import de.ma.ftms.bridge.runtime.dashboardGraphOptions
import de.ma.ftms.bridge.runtime.historyChartOptions
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.Role
import de.ma.ftms.bridge.runtime.dashboardMetricOptions
import de.ma.ftms.bridge.runtime.SourcedMetric
import de.ma.ftms.bridge.runtime.ValueSource
import de.ma.ftms.bridge.runtime.PowerSource
import de.ma.ftms.bridge.runtime.LARGE_SPEED_INCREASE
import de.ma.ftms.bridge.runtime.MAX_TREADMILL_PRESETS
import de.ma.ftms.bridge.runtime.TREADMILL_FOLLOW_MAX_SPEED_OPTIONS
import de.ma.ftms.bridge.runtime.TREADMILL_FOLLOW_SHORT_STEP_OPTIONS
import de.ma.ftms.bridge.runtime.TREADMILL_FOLLOW_TRANSITION_OPTIONS
import de.ma.ftms.bridge.runtime.TREADMILL_RAMP_MIN_CHANGE
import de.ma.ftms.bridge.runtime.TREADMILL_SPEED_STEP_OPTIONS
import de.ma.ftms.bridge.runtime.TreadmillRampProfile
import de.ma.ftms.bridge.runtime.TreadmillSetting
import de.ma.ftms.bridge.runtime.WorkoutTransitionMode
import de.ma.ftms.bridge.runtime.defaultPresets
import de.ma.ftms.bridge.runtime.isNewTreadmillPresetAllowed
import de.ma.ftms.bridge.runtime.parseTreadmillValue
import de.ma.ftms.bridge.runtime.presetGrid
import de.ma.ftms.bridge.runtime.presetRange

@Composable
fun SettingsView(component: SettingsComponent) {
    val childStack by component.childStack.subscribeAsState()
    Children(stack = childStack) { created ->
        when (val child = created.instance) {
            SettingsComponent.Child.Overview -> SettingsOverviewView(component)
            SettingsComponent.Child.Bridge -> BridgeSettingsView(component)
            SettingsComponent.Child.Power -> PowerSettingsView(component)
            SettingsComponent.Child.Dashboard -> DashboardSettingsView(component)
            SettingsComponent.Child.History -> HistorySettingsView(component)
            SettingsComponent.Child.IndoorCycling -> IndoorCyclingSettingsView(component)
            SettingsComponent.Child.Treadmill -> TreadmillSettingsView(component)
            SettingsComponent.Child.Sources -> SourcesSettingsView(component)
            SettingsComponent.Child.Timing -> TimingSettingsView(component)
            SettingsComponent.Child.Language -> LanguageSettingsView(component)
            SettingsComponent.Child.Debug -> DebugSettingsView(component)
            is SettingsComponent.Child.Logs -> LogsView(component = child.component)
            SettingsComponent.Child.RememberedDevices -> RememberedDevicesSettingsView(component)
            SettingsComponent.Child.OpenSource -> OpenSourceSettingsView(component)
        }
    }
}

/** The pages of the settings in the order and under the headings the overview lists them. */
internal enum class SettingsGroup(val pages: List<SettingsConfig>) {
    BRIDGE(
        listOf(
            SettingsConfig.Bridge,
            SettingsConfig.Sources,
            SettingsConfig.Power,
            SettingsConfig.IndoorCycling,
            SettingsConfig.Treadmill,
            SettingsConfig.Timing,
        ),
    ),
    DISPLAY(listOf(SettingsConfig.Dashboard, SettingsConfig.History, SettingsConfig.Language)),
    DIAGNOSTICS(listOf(SettingsConfig.Logs, SettingsConfig.Debug, SettingsConfig.RememberedDevices)),
    ABOUT(listOf(SettingsConfig.OpenSource)),
}

private fun SettingsGroup.title(strings: Strings): String =
    when (this) {
        SettingsGroup.BRIDGE -> strings.settings.groupBridge
        SettingsGroup.DISPLAY -> strings.settings.groupDisplay
        SettingsGroup.DIAGNOSTICS -> strings.settings.groupDiagnostics
        SettingsGroup.ABOUT -> strings.settings.groupAbout
    }

internal fun SettingsConfig.title(strings: Strings): String =
    when (this) {
        SettingsConfig.Overview -> strings.settings.title
        SettingsConfig.Bridge -> strings.settings.bridgeBehavior
        SettingsConfig.Power -> strings.settings.powerEstimate
        SettingsConfig.IndoorCycling -> strings.settings.indoorCycling
        SettingsConfig.Treadmill -> strings.settings.treadmill
        SettingsConfig.Sources -> strings.settings.dataSources
        SettingsConfig.Dashboard -> strings.settings.dashboard
        SettingsConfig.History -> strings.settings.history
        SettingsConfig.Timing -> strings.settings.timing
        SettingsConfig.Language -> strings.settings.language
        SettingsConfig.Debug -> strings.settings.debug
        SettingsConfig.Logs -> strings.settings.logs
        SettingsConfig.RememberedDevices -> strings.settings.rememberedDevices
        SettingsConfig.OpenSource -> strings.settings.openSource
    }

private fun SettingsConfig.subtitle(strings: Strings): String =
    when (this) {
        SettingsConfig.Overview -> ""
        SettingsConfig.Bridge -> strings.settings.bridgeBehaviorSubtitle
        SettingsConfig.Power -> strings.settings.powerEstimateSubtitle
        SettingsConfig.IndoorCycling -> strings.settings.indoorCyclingSubtitle
        SettingsConfig.Treadmill -> strings.settings.treadmillSubtitle
        SettingsConfig.Sources -> strings.settings.dataSourcesSubtitle
        SettingsConfig.Dashboard -> strings.settings.dashboardSubtitle
        SettingsConfig.History -> strings.settings.historySubtitle
        SettingsConfig.Timing -> strings.settings.timingSubtitle
        SettingsConfig.Language -> strings.settings.languageSubtitle
        SettingsConfig.Debug -> strings.settings.debugSubtitle
        SettingsConfig.Logs -> strings.settings.logsSubtitle
        SettingsConfig.RememberedDevices -> strings.settings.rememberedDevicesSubtitle
        SettingsConfig.OpenSource -> strings.settings.openSourceSubtitle
    }

/**
 * The title bar of the settings: the name of the page shown, and on every page below the
 * overview the way back to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTopBar(component: SettingsComponent, strings: Strings) {
    val childStack by component.childStack.subscribeAsState()
    val page = childStack.active.configuration as? SettingsConfig ?: SettingsConfig.Overview
    if (page == SettingsConfig.Overview) {
        CenterAlignedTopAppBar(title = { Text(page.title(strings)) })
    } else {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = component::onBack) {
                    Icon(BridgeUiIcons.ArrowBack, contentDescription = strings.common.back)
                }
            },
            title = { Text(page.title(strings), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
    }
}

@Composable
private fun SettingsOverviewView(component: SettingsComponent) {
    val strings = LocalStrings.current

    ScreenContainer(title = strings.settings.title) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SettingsGroup.entries.forEach { group ->
                item(key = group.name) {
                    SettingsGroupCard(title = group.title(strings)) {
                        group.pages.forEachIndexed { index, page ->
                            if (index > 0) SettingsRowDivider()
                            SettingsDestinationRow(
                                title = page.title(strings),
                                subtitle = page.subtitle(strings),
                                onClick = { component.navigateTo(page) },
                            )
                        }
                        if (group == SettingsGroup.ABOUT) {
                            SettingsRowDivider()
                            SettingsValueRow(
                                title = strings.settings.appVersion,
                                value = strings.settings.appVersionValue(
                                    appVersionLabel(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                                ),
                            )
                        }
                    }
                }
            }
            item { VerticalGap() }
        }
    }
}

/** One heading with the pages below it in a single card, a row each. */
@Composable
private fun SettingsGroupCard(title: String, rows: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 14.dp).semantics { heading() },
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            content = rows,
        )
    }
}

@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 14.dp),
    )
}

@Composable
private fun SettingsDestinationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            BridgeUiIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsValueRow(title: String, value: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A page below the overview. Its name and the way back stand in the title bar. */
@Composable
private fun SettingsPageScaffold(
    title: String,
    content: @Composable () -> Unit,
) {
    ScreenContainer(title = title) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
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
    val context = LocalContext.current
    val advertisePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        BridgeRuntime.refreshPermissionState()
    }

    SettingsPageScaffold(title = strings.settings.bridgeBehavior) {
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
                title = strings.settings.autoStart,
                subtitle = strings.settings.autoStartSubtitle,
                checked = settings.autoStart,
                onCheckedChange = {
                    component.onUpdateSettings(settings.copy(autoStart = it))
                },
            )
            SettingsToggleRow(
                title = strings.settings.autoStop,
                subtitle = strings.settings.autoStopSubtitle,
                checked = settings.autoStop,
                onCheckedChange = {
                    component.onUpdateSettings(settings.copy(autoStop = it))
                },
            )
            SettingsToggleRow(
                title = strings.settings.followWatchTimer,
                subtitle = strings.settings.followWatchTimerSubtitle,
                checked = settings.followWatchTimer,
                onCheckedChange = {
                    component.onUpdateSettings(settings.copy(followWatchTimer = it))
                },
            )
            if (settings.followWatchTimer) {
                SettingsToggleRow(
                    title = strings.settings.waitForWatch,
                    subtitle = strings.settings.waitForWatchSubtitle,
                    checked = settings.waitForWatch,
                    onCheckedChange = {
                        component.onUpdateSettings(settings.copy(waitForWatch = it))
                    },
                )
            }
            SettingsToggleRow(
                title = strings.settings.virtualFootPod,
                subtitle = strings.settings.virtualFootPodSubtitle,
                checked = settings.virtualFootPod,
                onCheckedChange = {
                    component.onUpdateSettings(settings.copy(virtualFootPod = it))
                    if (it && !hasAdvertisePermission(context)) {
                        advertisePermissionLauncher.launch(advertisePermissions())
                    }
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
        }
    }
}

@Composable
private fun PowerSettingsView(component: SettingsComponent) {
    val state by component.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val strings = LocalStrings.current

    SettingsPageScaffold(title = strings.settings.powerEstimate) {
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

    var editedType by rememberSaveable { mutableStateOf(state.selectedEquipmentType) }
    val layout = settings.dashboardLayout(editedType)

    SettingsPageScaffold(title = strings.settings.dashboard) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Section(strings.devices.equipmentType) {
                ChoiceRow(
                    title = strings.settings.dashboardEquipmentType,
                    selected = editedType,
                    options = EquipmentType.entries.toList(),
                    label = { equipmentTypeLabel(it, strings) },
                    onSelected = { editedType = it },
                )
            }
            Section(strings.settings.dashboardMetrics) {
                Text(
                    strings.settings.dashboardMetricsSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MultiChoiceRow(
                    selected = layout.metrics,
                    options = dashboardMetricOptions(editedType),
                    label = { it.label(strings, editedType) },
                    minimumSelectionText = strings.settings.keepOneDashboardOptionSelected,
                    onSelected = {
                        component.onUpdateSettings(settings.withDashboardLayout(editedType, layout.copy(metrics = it)))
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
                    selected = layout.graphs,
                    options = dashboardGraphOptions(editedType),
                    label = { it.label(strings, editedType) },
                    minimumSelectionText = strings.settings.keepOneDashboardOptionSelected,
                    onSelected = {
                        component.onUpdateSettings(settings.withDashboardLayout(editedType, layout.copy(graphs = it)))
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

/** Which charts a recorded session shows, and in which order, per equipment type. */
@Composable
private fun HistorySettingsView(component: SettingsComponent) {
    val state by component.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val strings = LocalStrings.current

    var editedType by rememberSaveable { mutableStateOf(state.selectedEquipmentType) }
    val charts = settings.historyCharts(editedType)

    SettingsPageScaffold(title = strings.settings.history) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Section(strings.devices.equipmentType) {
                ChoiceRow(
                    title = strings.settings.historyEquipmentType,
                    selected = editedType,
                    options = EquipmentType.entries.toList(),
                    label = { equipmentTypeLabel(it, strings) },
                    onSelected = { editedType = it },
                )
            }
            Section(strings.settings.historyCharts) {
                Text(
                    strings.settings.historyChartsSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OrderedChoiceList(
                    selected = charts,
                    options = historyChartOptions(editedType),
                    label = { it.label(strings, editedType) },
                    minimumSelectionText = strings.settings.keepOneDashboardOptionSelected,
                    onChanged = { component.onUpdateSettings(settings.withHistoryCharts(editedType, it)) },
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

    SettingsPageScaffold(title = strings.settings.timing) {
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

    SettingsPageScaffold(title = strings.settings.language) {
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
private fun SourcesSettingsView(component: SettingsComponent) {
    val state by component.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val strings = LocalStrings.current
    var editedType by rememberSaveable { mutableStateOf(state.selectedEquipmentType) }
    val sources = settings.sensorSources(editedType)

    SettingsPageScaffold(title = strings.settings.dataSources) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Section(strings.devices.equipmentType) {
                ChoiceRow(
                    title = strings.settings.dataSourcesEquipmentType,
                    selected = editedType,
                    options = EquipmentType.entries.toList(),
                    label = { equipmentTypeLabel(it, strings) },
                    onSelected = { editedType = it },
                )
            }
            Section(strings.settings.dataSources) {
                SourcedMetric.entries.forEach { metric ->
                    ChoiceRow(
                        title = sourcedMetricLabel(metric, strings, editedType),
                        selected = sources.of(metric),
                        options = ValueSource.entries.toList(),
                        label = { valueSourceLabel(it, strings) },
                        onSelected = {
                            component.onUpdateSettings(settings.withSensorSource(editedType, metric, it))
                        },
                    )
                }
                Text(
                    strings.settings.dataSourcesHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IndoorCyclingSettingsView(component: SettingsComponent) {
    val state by component.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val strings = LocalStrings.current
    val presets = settings.targetPowerPresets
    var newPresetText by rememberSaveable { mutableStateOf("") }
    val newPreset = newPresetText.trim().toIntOrNull()
    val canAdd = isNewTargetPowerPresetAllowed(newPreset, presets)

    SettingsPageScaffold(title = strings.settings.indoorCycling) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Section(strings.settings.targetPowerPresets) {
                Text(
                    strings.settings.targetPowerPresetsSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { watts ->
                        val removeLabel = strings.settings.targetPowerRemovePreset(watts)
                        InputChip(
                            selected = false,
                            onClick = {
                                component.onUpdateSettings(settings.copy(targetPowerPresets = presets - watts))
                            },
                            label = { Text("$watts W  ✕") },
                            modifier = Modifier.semantics { contentDescription = removeLabel },
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = newPresetText,
                        onValueChange = { updated -> newPresetText = updated.filter(Char::isDigit).take(4) },
                        label = { Text(strings.settings.targetPowerNewPreset) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        enabled = canAdd,
                        onClick = {
                            val added = newPreset ?: return@OutlinedButton
                            component.onUpdateSettings(
                                settings.copy(targetPowerPresets = sanitizeTargetPowerPresets(presets + added)),
                            )
                            newPresetText = ""
                        },
                    ) {
                        Text(strings.settings.targetPowerAddPreset)
                    }
                }
                Text(
                    strings.settings.targetPowerPresetLimits(
                        TARGET_POWER_PRESET_RANGE.first,
                        TARGET_POWER_PRESET_RANGE.last,
                        MAX_TARGET_POWER_PRESETS,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ActionRow(
                    title = strings.settings.targetPowerRestoreDefaults,
                    subtitle = strings.settings.targetPowerRestoreDefaultsSubtitle,
                    actionLabel = strings.settings.targetPowerRestoreAction,
                    enabled = presets != DEFAULT_TARGET_POWER_PRESETS,
                    stacked = true,
                    onClick = {
                        component.onUpdateSettings(settings.copy(targetPowerPresets = DEFAULT_TARGET_POWER_PRESETS))
                    },
                )
            }
            Section(strings.settings.targetPowerStep) {
                ChoiceRow(
                    title = strings.settings.targetPowerStepSubtitle,
                    selected = settings.targetPowerStepWatts,
                    options = TARGET_POWER_STEP_OPTIONS,
                    label = { "$it W" },
                    onSelected = {
                        component.onUpdateSettings(settings.copy(targetPowerStepWatts = it))
                    },
                )
            }
        }
    }
}

/** The settings for steering a treadmill: the switch for it, the quick choices and the step. */
@Composable
private fun TreadmillSettingsView(component: SettingsComponent) {
    val state by component.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val strings = LocalStrings.current
    val locale = strings.locale

    SettingsPageScaffold(title = strings.settings.treadmill) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Section(strings.settings.treadmillControlSection) {
                SettingsToggleRow(
                    title = strings.settings.treadmillControl,
                    subtitle = strings.settings.treadmillControlSubtitle,
                    checked = settings.treadmillControl,
                    onCheckedChange = { component.onUpdateSettings(settings.copy(treadmillControl = it)) },
                )
                SettingsToggleRow(
                    title = strings.settings.treadmillConfirmLargeIncrease,
                    subtitle = strings.settings.treadmillConfirmLargeIncreaseSubtitle(
                        formatTreadmillValue(TreadmillSetting.SPEED, LARGE_SPEED_INCREASE, locale, compact = true),
                    ),
                    checked = settings.treadmillConfirmLargeIncrease,
                    onCheckedChange = { component.onUpdateSettings(settings.copy(treadmillConfirmLargeIncrease = it)) },
                )
            }
            Section(strings.settings.treadmillRampSection) {
                ChoiceRow(
                    title = strings.settings.treadmillRamp,
                    selected = settings.treadmillRamp,
                    options = TreadmillRampProfile.entries,
                    label = {
                        when (it) {
                            TreadmillRampProfile.DIRECT -> strings.settings.treadmillRampDirect
                            TreadmillRampProfile.GENTLE -> strings.settings.treadmillRampGentle
                            TreadmillRampProfile.VERY_GENTLE -> strings.settings.treadmillRampVeryGentle
                        }
                    },
                    onSelected = { component.onUpdateSettings(settings.copy(treadmillRamp = it)) },
                )
                Text(
                    strings.settings.treadmillRampSubtitle(
                        formatTreadmillValue(TreadmillSetting.SPEED, TREADMILL_RAMP_MIN_CHANGE, locale),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Section(strings.settings.treadmillFollowSection) {
                SettingsToggleRow(
                    title = strings.settings.treadmillFollowWorkout,
                    subtitle = strings.settings.treadmillFollowWorkoutSubtitle,
                    checked = settings.treadmillFollowWorkout,
                    onCheckedChange = { component.onUpdateSettings(settings.copy(treadmillFollowWorkout = it)) },
                )
                ChoiceRow(
                    title = strings.settings.treadmillFollowMode,
                    selected = settings.treadmillFollowMode,
                    options = WorkoutTransitionMode.entries,
                    label = {
                        when (it) {
                            WorkoutTransitionMode.OFF -> strings.settings.treadmillFollowModeOff
                            WorkoutTransitionMode.GENTLE -> strings.settings.treadmillFollowModeGentle
                            WorkoutTransitionMode.SMART -> strings.settings.treadmillFollowModeSmart
                        }
                    },
                    onSelected = { component.onUpdateSettings(settings.copy(treadmillFollowMode = it)) },
                )
                Text(
                    when (settings.treadmillFollowMode) {
                        WorkoutTransitionMode.OFF -> strings.settings.treadmillFollowModeOffSubtitle
                        WorkoutTransitionMode.GENTLE -> strings.settings.treadmillFollowModeGentleSubtitle
                        WorkoutTransitionMode.SMART -> strings.settings.treadmillFollowModeSmartSubtitle
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (settings.treadmillFollowMode != WorkoutTransitionMode.OFF) {
                    ChoiceRow(
                        title = strings.settings.treadmillFollowTransitionSeconds,
                        selected = settings.treadmillFollowTransitionSeconds,
                        options = TREADMILL_FOLLOW_TRANSITION_OPTIONS,
                        label = {
                            if (it == 0) strings.settings.treadmillFollowTransitionByLevel else strings.settings.treadmillFollowSecondsLabel(it)
                        },
                        onSelected = { component.onUpdateSettings(settings.copy(treadmillFollowTransitionSeconds = it)) },
                    )
                }
                if (settings.treadmillFollowMode == WorkoutTransitionMode.SMART) {
                    ChoiceRow(
                        title = strings.settings.treadmillFollowShortStep,
                        selected = settings.treadmillFollowShortStepSeconds,
                        options = TREADMILL_FOLLOW_SHORT_STEP_OPTIONS,
                        label = {
                            if (it == 0) {
                                strings.settings.treadmillFollowShortStepNone
                            } else {
                                strings.settings.treadmillFollowShortStepLabel(formatMinutesSeconds(it))
                            }
                        },
                        onSelected = { component.onUpdateSettings(settings.copy(treadmillFollowShortStepSeconds = it)) },
                    )
                }
                ChoiceRow(
                    title = strings.settings.treadmillFollowMaxSpeed,
                    selected = settings.treadmillFollowMaxSpeed,
                    options = TREADMILL_FOLLOW_MAX_SPEED_OPTIONS,
                    label = { formatTreadmillValue(TreadmillSetting.SPEED, it, locale, compact = true) },
                    onSelected = { component.onUpdateSettings(settings.copy(treadmillFollowMaxSpeed = it)) },
                )
                SettingsToggleRow(
                    title = strings.settings.treadmillFollowTone,
                    subtitle = strings.settings.treadmillFollowToneSubtitle,
                    checked = settings.treadmillFollowTone,
                    onCheckedChange = { component.onUpdateSettings(settings.copy(treadmillFollowTone = it)) },
                )
            }
            TreadmillSetting.entries.forEach { setting ->
                TreadmillPresetSection(
                    setting = setting,
                    presets = settings.treadmillPresets(setting),
                    onChanged = { component.onUpdateSettings(settings.withTreadmillPresets(setting, it)) },
                )
            }
            Section(strings.settings.targetPowerStep) {
                ChoiceRow(
                    title = strings.settings.treadmillSpeedStep,
                    selected = settings.treadmillSpeedStep,
                    options = TREADMILL_SPEED_STEP_OPTIONS,
                    label = { formatTreadmillValue(TreadmillSetting.SPEED, it, locale) },
                    onSelected = { component.onUpdateSettings(settings.copy(treadmillSpeedStep = it)) },
                )
                Text(
                    strings.settings.treadmillSpeedStepSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The quick choices of [setting]: a tap removes one, the field below adds one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TreadmillPresetSection(
    setting: TreadmillSetting,
    presets: List<Int>,
    onChanged: (List<Int>) -> Unit,
) {
    val strings = LocalStrings.current
    val locale = strings.locale
    var newPresetText by rememberSaveable(setting) { mutableStateOf("") }
    val newPreset = parseTreadmillValue(setting, newPresetText)
    val canAdd = isNewTreadmillPresetAllowed(setting, newPreset, presets)
    val defaults = setting.defaultPresets
    val value = { preset: Int -> formatTreadmillValue(setting, preset, locale, compact = true) }

    Section(
        when (setting) {
            TreadmillSetting.SPEED -> strings.settings.treadmillSpeedPresets
            TreadmillSetting.INCLINE -> strings.settings.treadmillInclinePresets
        },
    ) {
        Text(
            strings.settings.targetPowerPresetsSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.forEach { preset ->
                val removeLabel = strings.settings.treadmillRemovePreset(value(preset))
                InputChip(
                    selected = false,
                    onClick = { onChanged(presets - preset) },
                    label = { Text("${value(preset)}  ✕") },
                    modifier = Modifier.semantics { contentDescription = removeLabel },
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = newPresetText,
                onValueChange = { updated ->
                    newPresetText = updated.filter { it.isDigit() || it == ',' || it == '.' }.take(5)
                },
                label = {
                    Text(
                        when (setting) {
                            TreadmillSetting.SPEED -> strings.settings.treadmillNewSpeedPreset
                            TreadmillSetting.INCLINE -> strings.settings.treadmillNewInclinePreset
                        },
                    )
                },
                singleLine = true,
                isError = newPresetText.isNotBlank() && !canAdd,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                enabled = canAdd,
                onClick = {
                    val added = newPreset ?: return@OutlinedButton
                    onChanged(presets + added)
                    newPresetText = ""
                },
            ) {
                Text(strings.settings.targetPowerAddPreset)
            }
        }
        Text(
            strings.settings.treadmillPresetLimits(
                min = formatTreadmillNumber(setting, setting.presetRange.first, locale, compact = true),
                max = value(setting.presetRange.last),
                step = value(setting.presetGrid),
                maxCount = MAX_TREADMILL_PRESETS,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ActionRow(
            title = strings.settings.targetPowerRestoreDefaults,
            subtitle = strings.settings.treadmillRestoreDefaultsSubtitle(defaults.joinToString(", ") { value(it) }),
            actionLabel = strings.settings.targetPowerRestoreAction,
            enabled = presets != defaults,
            stacked = true,
            onClick = { onChanged(defaults) },
        )
    }
}

@Composable
private fun DebugSettingsView(component: SettingsComponent) {
    val state by component.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val strings = LocalStrings.current

    SettingsPageScaffold(title = strings.settings.debug) {
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

    SettingsPageScaffold(title = strings.settings.rememberedDevices) {
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

@Composable
private fun OpenSourceSettingsView(component: SettingsComponent) {
    val strings = LocalStrings.current

    SettingsPageScaffold(title = strings.settings.openSource) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Section(strings.settings.openSourceLibraries) {
                openSourceLibraries.forEachIndexed { index, library ->
                    OpenSourceLibraryNotice(library)
                    if (index < openSourceLibraries.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            Section(strings.settings.thirdPartySdk) {
                thirdPartySdks.forEachIndexed { index, sdk ->
                    ThirdPartySdkNotice(sdk)
                    if (index < thirdPartySdks.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenSourceLibraryNotice(library: OpenSourceLibrary) {
    val strings = LocalStrings.current

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(library.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        StatusRow(strings.settings.libraryVersion, library.version)
        StatusRow(strings.settings.license, library.license)
        StatusRow(strings.settings.projectWebsite, library.url)
    }
}

@Composable
private fun ThirdPartySdkNotice(sdk: ThirdPartySdk) {
    val strings = LocalStrings.current

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(sdk.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        StatusRow(strings.settings.libraryVersion, sdk.version)
        StatusRow(strings.settings.projectWebsite, sdk.url)
        Text(
            strings.settings.garminSdkNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
    if (this % 1.0 == 0.0) String.format(java.util.Locale.ROOT, "%.0f", this) else toString()

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

/**
 * A choice of several of [options] in an order of the user's own: the chosen ones first, in their
 * order and with arrows to move them, then the others in the order of [options]. Ticking one adds
 * it at the end of the chosen ones. The last chosen one cannot be unticked.
 */
@Composable
private fun <T> OrderedChoiceList(
    selected: List<T>,
    options: List<T>,
    label: (T) -> String,
    minimumSelectionText: String,
    onChanged: (List<T>) -> Unit,
) {
    val strings = LocalStrings.current
    Column {
        selected.forEachIndexed { index, option ->
            val name = label(option)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .toggleable(
                            value = true,
                            enabled = selected.size > 1,
                            role = Role.Checkbox,
                            onValueChange = { onChanged(selected - option) },
                        )
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(checked = true, onCheckedChange = null, enabled = selected.size > 1)
                    Text(name, modifier = Modifier.padding(start = 12.dp))
                }
                IconButton(enabled = index > 0, onClick = { onChanged(movedBy(selected, index, -1)) }) {
                    Icon(BridgeUiIcons.ArrowUp, contentDescription = strings.settings.moveUp(name))
                }
                IconButton(enabled = index < selected.lastIndex, onClick = { onChanged(movedBy(selected, index, 1)) }) {
                    Icon(BridgeUiIcons.ArrowDown, contentDescription = strings.settings.moveDown(name))
                }
            }
        }
        options.filter { it !in selected }.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .toggleable(value = false, role = Role.Checkbox, onValueChange = { onChanged(selected + option) })
                    .padding(vertical = 4.dp),
            ) {
                Checkbox(checked = false, onCheckedChange = null)
                Text(
                    label(option),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
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
internal fun <T> ChoiceRow(
    title: String,
    selected: T,
    options: List<T>,
    label: (T) -> String,
    enabled: Boolean = true,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    enabled = enabled,
                    onClick = { onSelected(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}
