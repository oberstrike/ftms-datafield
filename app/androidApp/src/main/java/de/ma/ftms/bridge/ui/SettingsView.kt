package de.ma.ftms.bridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ma.ftms.bridge.i18n.AppLanguage
import de.ma.ftms.bridge.i18n.LocalStrings
import de.ma.ftms.bridge.i18n.displayLabel
import de.ma.ftms.bridge.navigation.SettingsComponent

@Composable
fun SettingsView(component: SettingsComponent) {
    val state by component.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val strings = LocalStrings.current
    var showClearSelectionsConfirm by rememberSaveable { mutableStateOf(false) }

    ScreenContainer(title = strings.settings.title) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Section(strings.settings.bridgeBehavior) {
                    SettingsToggleRow(
                        title = strings.settings.sendToAllGarminVariants,
                        subtitle = strings.settings.sendToAllGarminVariantsSubtitle,
                        checked = settings.sendToAllInstalledVariants,
                        onCheckedChange = {
                            component.onUpdateSettings(settings.copy(sendToAllInstalledVariants = it))
                        },
                    )
                    SettingsToggleRow(
                        title = strings.settings.autoReconnect,
                        subtitle = strings.settings.autoReconnectSubtitle,
                        checked = settings.autoReconnect,
                        onCheckedChange = {
                            component.onUpdateSettings(settings.copy(autoReconnect = it))
                        },
                    )
                }
            }

            item {
                Section(strings.settings.language) {
                    ChoiceRow(
                        title = strings.settings.language,
                        selected = settings.language,
                        options = AppLanguage.entries,
                        label = { it.displayLabel(strings) },
                        onSelected = {
                            component.onUpdateSettings(settings.copy(language = it))
                        },
                    )
                }
            }

            item {
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

            item {
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

            item {
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
            item { VerticalGap() }
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
