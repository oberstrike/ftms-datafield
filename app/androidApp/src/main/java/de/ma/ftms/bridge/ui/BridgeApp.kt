package de.ma.ftms.bridge.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import de.ma.ftms.bridge.ble.requiredRuntimePermissions
import de.ma.ftms.bridge.i18n.LocalStrings
import de.ma.ftms.bridge.i18n.resolveStrings
import de.ma.ftms.bridge.navigation.BridgeTab
import de.ma.ftms.bridge.navigation.RootComponent
import de.ma.ftms.bridge.navigation.tabForConfig
import de.ma.ftms.bridge.runtime.BridgeRuntime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FtmsBridgeApp(rootComponent: RootComponent) {
    val childStack by rootComponent.childStack.subscribeAsState()
    val state by BridgeRuntime.state.collectAsStateWithLifecycle()
    val strings = remember(state.settings.language) { resolveStrings(state.settings.language) }
    val activeTab = tabForConfig(childStack.active.configuration)
    val context = LocalContext.current
    val view = LocalView.current
    var dashboardControlsVisible by rememberSaveable { mutableStateOf(true) }
    val showSessionActionBar = state.running && (activeTab != BridgeTab.DASHBOARD || !dashboardControlsVisible)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        BridgeRuntime.refreshPermissionState()
    }

    LaunchedEffect(Unit) {
        BridgeRuntime.refreshPermissionState()
    }

    DisposableEffect(state.running) {
        view.keepScreenOn = state.running
        onDispose {
            view.keepScreenOn = false
        }
    }

    CompositionLocalProvider(LocalStrings provides strings) {
        MaterialTheme {
            Surface(color = MaterialTheme.colorScheme.background) {
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text(activeTab.title(strings)) },
                        )
                    },
                    bottomBar = {
                        Column {
                            if (showSessionActionBar) {
                                SessionActionBar(
                                    paused = state.paused,
                                    onStart = { BridgeRuntime.startBridge(context) },
                                    onPause = BridgeRuntime::pauseBridge,
                                    onStop = { BridgeRuntime.stopBridge() },
                                )
                            }
                            BridgeBottomNavigation(
                                activeTab = activeTab,
                                onTabSelected = rootComponent::navigateTo,
                            )
                        }
                    },
                ) { padding ->
                    Children(
                        stack = childStack,
                        modifier = Modifier.padding(padding),
                    ) { child ->
                        RootChildContent(
                            child = child.instance,
                            onRequestPermissions = { permissionLauncher.launch(requiredRuntimePermissions()) },
                            onDashboardControlsVisibleChange = { dashboardControlsVisible = it },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RootChildContent(
    child: RootComponent.Child,
    onRequestPermissions: () -> Unit,
    onDashboardControlsVisibleChange: (Boolean) -> Unit,
) {
    when (child) {
        is RootComponent.Child.DashboardChild -> DashboardView(
            component = child.component,
            onRequestPermissions = onRequestPermissions,
            onPrimaryControlsVisibleChange = onDashboardControlsVisibleChange,
        )
        is RootComponent.Child.HistoryChild -> HistoryView(component = child.component)
        is RootComponent.Child.DevicesChild -> DevicesView(component = child.component)
        is RootComponent.Child.LogsChild -> LogsView(component = child.component)
        is RootComponent.Child.SettingsChild -> SettingsView(component = child.component)
    }
}

@Composable
private fun SessionActionBar(
    paused: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    val strings = LocalStrings.current
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                enabled = paused,
                onClick = onStart,
                modifier = Modifier.weight(1f),
            ) {
                Text(strings.common.start)
            }
            OutlinedButton(
                enabled = !paused,
                onClick = onPause,
                modifier = Modifier.weight(1f),
            ) {
                Text(strings.common.pause)
            }
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier.weight(1f),
            ) {
                Text(strings.common.stop)
            }
        }
    }
}

@Composable
private fun BridgeBottomNavigation(
    activeTab: BridgeTab,
    onTabSelected: (BridgeTab) -> Unit,
) {
    val strings = LocalStrings.current
    NavigationBar {
        BridgeTab.entries.forEach { tab ->
            val title = tab.title(strings)
            NavigationBarItem(
                selected = tab == activeTab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = title) },
                label = { Text(title) },
            )
        }
    }
}
