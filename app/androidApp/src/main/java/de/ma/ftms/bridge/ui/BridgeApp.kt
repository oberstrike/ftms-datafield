package de.ma.ftms.bridge.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        BridgeRuntime.refreshPermissionState()
    }

    LaunchedEffect(Unit) {
        BridgeRuntime.refreshPermissionState()
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
                        BridgeBottomNavigation(
                            activeTab = activeTab,
                            onTabSelected = rootComponent::navigateTo,
                        )
                    },
                ) { padding ->
                    Children(
                        stack = childStack,
                        modifier = Modifier.padding(padding),
                    ) { child ->
                        RootChildContent(
                            child = child.instance,
                            onRequestPermissions = { permissionLauncher.launch(requiredRuntimePermissions()) },
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
) {
    when (child) {
        is RootComponent.Child.DashboardChild -> DashboardView(
            component = child.component,
            onRequestPermissions = onRequestPermissions,
        )
        is RootComponent.Child.HistoryChild -> HistoryView(component = child.component)
        is RootComponent.Child.DevicesChild -> DevicesView(component = child.component)
        is RootComponent.Child.LogsChild -> LogsView(component = child.component)
        is RootComponent.Child.SettingsChild -> SettingsView(component = child.component)
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
