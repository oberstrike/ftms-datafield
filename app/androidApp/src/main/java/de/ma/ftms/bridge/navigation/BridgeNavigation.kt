package de.ma.ftms.bridge.navigation

import android.content.Context
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushToFront
import com.arkivanov.decompose.value.Value
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import de.ma.ftms.bridge.i18n.Strings
import de.ma.ftms.bridge.runtime.BridgeRuntime
import de.ma.ftms.bridge.runtime.BridgeSettings
import de.ma.ftms.bridge.runtime.BridgeUiState
import kotlinx.coroutines.flow.StateFlow

interface RootComponent {
    val childStack: Value<ChildStack<*, Child>>

    fun navigateTo(tab: BridgeTab)

    sealed class Child {
        data class DashboardChild(val component: DashboardComponent) : Child()
        data class HistoryChild(val component: HistoryComponent) : Child()
        data class DevicesChild(val component: DevicesComponent) : Child()
        data class LogsChild(val component: LogsComponent) : Child()
        data class SettingsChild(val component: SettingsComponent) : Child()
    }
}

interface DashboardComponent {
    val state: StateFlow<BridgeUiState>
    fun onStartBridge(context: Context)
    fun onPauseBridge()
    fun onStopBridge()
}

interface DevicesComponent {
    val state: StateFlow<BridgeUiState>
    fun onToggleScan()
    fun onRefreshGarminDevices()
    fun onSelectTreadmill(address: String)
    fun onSelectGarmin(id: Long)
}

interface HistoryComponent {
    val state: StateFlow<BridgeUiState>
    fun onSelectSession(sessionId: String?)
    fun onDeleteSession(sessionId: String)
    fun onClearHistory()
}

interface LogsComponent {
    val state: StateFlow<BridgeUiState>
    fun onClearLogs()
    fun onToggleCapture()
    fun onShareLatestLog(context: Context)
}

interface SettingsComponent {
    val state: StateFlow<BridgeUiState>
    val childStack: Value<ChildStack<*, Child>>
    fun onUpdateSettings(settings: BridgeSettings)
    fun onClearRememberedSelections()
    fun navigateTo(config: SettingsConfig)
    fun onBack()

    sealed class Child {
        data object Overview : Child()
        data object Bridge : Child()
        data object Power : Child()
        data object Dashboard : Child()
        data object Timing : Child()
        data object Language : Child()
        data object Debug : Child()
        data object RememberedDevices : Child()
    }
}

class DefaultRootComponent(
    componentContext: ComponentContext,
) : RootComponent,
    ComponentContext by componentContext {

    private val navigation = StackNavigation<NavigationConfig>()

    override val childStack: Value<ChildStack<*, RootComponent.Child>> = childStack(
        source = navigation,
        serializer = null,
        initialConfiguration = NavigationConfig.Dashboard,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    override fun navigateTo(tab: BridgeTab) {
        navigation.bringToFront(tab.config)
    }

    private fun createChild(config: NavigationConfig, componentContext: ComponentContext): RootComponent.Child =
        when (config) {
            NavigationConfig.Dashboard -> RootComponent.Child.DashboardChild(DefaultDashboardComponent(componentContext))
            NavigationConfig.History -> RootComponent.Child.HistoryChild(DefaultHistoryComponent(componentContext))
            NavigationConfig.Devices -> RootComponent.Child.DevicesChild(DefaultDevicesComponent(componentContext))
            NavigationConfig.Logs -> RootComponent.Child.LogsChild(DefaultLogsComponent(componentContext))
            NavigationConfig.Settings -> RootComponent.Child.SettingsChild(DefaultSettingsComponent(componentContext))
        }
}

private class DefaultDashboardComponent(
    componentContext: ComponentContext,
) : DashboardComponent,
    ComponentContext by componentContext {
    override val state: StateFlow<BridgeUiState> = BridgeRuntime.state
    override fun onStartBridge(context: Context) = BridgeRuntime.startBridge(context)
    override fun onPauseBridge() = BridgeRuntime.pauseBridge()
    override fun onStopBridge() = BridgeRuntime.stopBridge()
}

private class DefaultDevicesComponent(
    componentContext: ComponentContext,
) : DevicesComponent,
    ComponentContext by componentContext {
    override val state: StateFlow<BridgeUiState> = BridgeRuntime.state
    override fun onToggleScan() {
        if (state.value.scanning) BridgeRuntime.stopScan() else BridgeRuntime.startScan()
    }
    override fun onRefreshGarminDevices() = BridgeRuntime.refreshGarminDevices()
    override fun onSelectTreadmill(address: String) = BridgeRuntime.selectTreadmill(address)
    override fun onSelectGarmin(id: Long) = BridgeRuntime.selectGarmin(id)
}

private class DefaultHistoryComponent(
    componentContext: ComponentContext,
) : HistoryComponent,
    ComponentContext by componentContext {
    override val state: StateFlow<BridgeUiState> = BridgeRuntime.state
    override fun onSelectSession(sessionId: String?) = BridgeRuntime.selectHistorySession(sessionId)
    override fun onDeleteSession(sessionId: String) = BridgeRuntime.deleteHistorySession(sessionId)
    override fun onClearHistory() = BridgeRuntime.clearHistory()
}

private class DefaultLogsComponent(
    componentContext: ComponentContext,
) : LogsComponent,
    ComponentContext by componentContext {
    override val state: StateFlow<BridgeUiState> = BridgeRuntime.state
    override fun onClearLogs() = BridgeRuntime.clearLogs()
    override fun onToggleCapture() = BridgeRuntime.toggleLogCapture()
    override fun onShareLatestLog(context: Context) = BridgeRuntime.shareLatestLog(context)
}

private class DefaultSettingsComponent(
    componentContext: ComponentContext,
) : SettingsComponent,
    ComponentContext by componentContext {
    private val navigation = StackNavigation<SettingsConfig>()

    override val state: StateFlow<BridgeUiState> = BridgeRuntime.state
    override val childStack: Value<ChildStack<*, SettingsComponent.Child>> = childStack(
        source = navigation,
        serializer = null,
        initialConfiguration = SettingsConfig.Overview,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    override fun onUpdateSettings(settings: BridgeSettings) = BridgeRuntime.updateSettings(settings)
    override fun onClearRememberedSelections() = BridgeRuntime.clearRememberedSelections()
    override fun navigateTo(config: SettingsConfig) = navigation.pushToFront(config)
    override fun onBack() = navigation.pop()

    private fun createChild(config: SettingsConfig, componentContext: ComponentContext): SettingsComponent.Child =
        when (config) {
            SettingsConfig.Overview -> SettingsComponent.Child.Overview
            SettingsConfig.Bridge -> SettingsComponent.Child.Bridge
            SettingsConfig.Power -> SettingsComponent.Child.Power
            SettingsConfig.Dashboard -> SettingsComponent.Child.Dashboard
            SettingsConfig.Timing -> SettingsComponent.Child.Timing
            SettingsConfig.Language -> SettingsComponent.Child.Language
            SettingsConfig.Debug -> SettingsComponent.Child.Debug
            SettingsConfig.RememberedDevices -> SettingsComponent.Child.RememberedDevices
        }
}

sealed class SettingsConfig {
    data object Overview : SettingsConfig()
    data object Bridge : SettingsConfig()
    data object Power : SettingsConfig()
    data object Dashboard : SettingsConfig()
    data object Timing : SettingsConfig()
    data object Language : SettingsConfig()
    data object Debug : SettingsConfig()
    data object RememberedDevices : SettingsConfig()
}

sealed class NavigationConfig {
    data object Dashboard : NavigationConfig()
    data object History : NavigationConfig()
    data object Devices : NavigationConfig()
    data object Logs : NavigationConfig()
    data object Settings : NavigationConfig()
}

enum class BridgeTab(
    val icon: ImageVector,
    val config: NavigationConfig,
) {
    DASHBOARD(BridgeIcons.Dashboard, NavigationConfig.Dashboard),
    HISTORY(BridgeIcons.History, NavigationConfig.History),
    DEVICES(BridgeIcons.Devices, NavigationConfig.Devices),
    LOGS(BridgeIcons.Logs, NavigationConfig.Logs),
    SETTINGS(BridgeIcons.Settings, NavigationConfig.Settings);

    fun title(strings: Strings): String =
        when (this) {
            DASHBOARD -> strings.nav.dashboard
            HISTORY -> strings.nav.history
            DEVICES -> strings.nav.devices
            LOGS -> strings.nav.logs
            SETTINGS -> strings.nav.settings
        }
}

fun tabForConfig(config: Any?): BridgeTab =
    when (config) {
        NavigationConfig.History -> BridgeTab.HISTORY
        NavigationConfig.Devices -> BridgeTab.DEVICES
        NavigationConfig.Logs -> BridgeTab.LOGS
        NavigationConfig.Settings -> BridgeTab.SETTINGS
        else -> BridgeTab.DASHBOARD
    }

private object BridgeIcons {
    val Dashboard: ImageVector = Builder("BridgeDashboard", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 3f)
            lineTo(10f, 3f)
            lineTo(10f, 10f)
            lineTo(3f, 10f)
            close()
            moveTo(14f, 3f)
            lineTo(21f, 3f)
            lineTo(21f, 10f)
            lineTo(14f, 10f)
            close()
            moveTo(3f, 14f)
            lineTo(10f, 14f)
            lineTo(10f, 21f)
            lineTo(3f, 21f)
            close()
            moveTo(14f, 14f)
            lineTo(21f, 14f)
            lineTo(21f, 21f)
            lineTo(14f, 21f)
            close()
        }
    }.build()

    val History: ImageVector = Builder("BridgeHistory", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 19f)
            lineTo(4f, 5f)
            lineTo(6f, 5f)
            lineTo(6f, 17f)
            lineTo(21f, 17f)
            lineTo(21f, 19f)
            close()
            moveTo(8f, 15f)
            lineTo(8f, 10f)
            lineTo(11f, 10f)
            lineTo(11f, 15f)
            close()
            moveTo(13f, 15f)
            lineTo(13f, 7f)
            lineTo(16f, 7f)
            lineTo(16f, 15f)
            close()
            moveTo(18f, 15f)
            lineTo(18f, 12f)
            lineTo(21f, 12f)
            lineTo(21f, 15f)
            close()
        }
    }.build()

    val Devices: ImageVector = Builder("BridgeDevices", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(11f, 2f)
            lineTo(17f, 8f)
            lineTo(13f, 12f)
            lineTo(17f, 16f)
            lineTo(11f, 22f)
            lineTo(11f, 14.5f)
            lineTo(7.5f, 18f)
            lineTo(6f, 16.5f)
            lineTo(10.5f, 12f)
            lineTo(6f, 7.5f)
            lineTo(7.5f, 6f)
            lineTo(11f, 9.5f)
            close()
            moveTo(13f, 6.8f)
            lineTo(13f, 9.2f)
            lineTo(14.2f, 8f)
            close()
            moveTo(13f, 14.8f)
            lineTo(13f, 17.2f)
            lineTo(14.2f, 16f)
            close()
        }
    }.build()

    val Logs: ImageVector = Builder("BridgeLogs", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 4f)
            lineTo(20f, 4f)
            lineTo(20f, 20f)
            lineTo(4f, 20f)
            close()
            moveTo(6f, 7f)
            lineTo(10f, 10f)
            lineTo(6f, 13f)
            lineTo(6f, 10.5f)
            lineTo(8f, 10f)
            lineTo(6f, 9.5f)
            close()
            moveTo(11f, 14f)
            lineTo(18f, 14f)
            lineTo(18f, 16f)
            lineTo(11f, 16f)
            close()
        }
    }.build()

    val Settings: ImageVector = Builder("BridgeSettings", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 8f)
            arcToRelative(4f, 4f, 0f, true, true, 0f, 8f)
            arcToRelative(4f, 4f, 0f, false, true, 0f, -8f)
            close()
            moveTo(12f, 10f)
            arcToRelative(2f, 2f, 0f, true, false, 0f, 4f)
            arcToRelative(2f, 2f, 0f, false, false, 0f, -4f)
            close()
            moveTo(11f, 2f)
            lineTo(13f, 2f)
            lineTo(14f, 5f)
            lineTo(17f, 6.2f)
            lineTo(20f, 5f)
            lineTo(21f, 7f)
            lineTo(18.7f, 9.2f)
            lineTo(19f, 12f)
            lineTo(18.7f, 14.8f)
            lineTo(21f, 17f)
            lineTo(20f, 19f)
            lineTo(17f, 17.8f)
            lineTo(14f, 19f)
            lineTo(13f, 22f)
            lineTo(11f, 22f)
            lineTo(10f, 19f)
            lineTo(7f, 17.8f)
            lineTo(4f, 19f)
            lineTo(3f, 17f)
            lineTo(5.3f, 14.8f)
            lineTo(5f, 12f)
            lineTo(5.3f, 9.2f)
            lineTo(3f, 7f)
            lineTo(4f, 5f)
            lineTo(7f, 6.2f)
            lineTo(10f, 5f)
            close()
        }
    }.build()
}
