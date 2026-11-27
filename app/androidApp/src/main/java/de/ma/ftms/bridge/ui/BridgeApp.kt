package de.ma.ftms.bridge.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import android.content.res.Configuration
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import de.ma.ftms.bridge.ble.requestableRuntimePermissions
import de.ma.ftms.bridge.i18n.LocalStrings
import de.ma.ftms.bridge.i18n.resolveStrings
import de.ma.ftms.bridge.navigation.BridgeTab
import de.ma.ftms.bridge.navigation.RootComponent
import de.ma.ftms.bridge.navigation.tabForConfig
import de.ma.ftms.bridge.runtime.BridgeRuntime
import de.ma.ftms.bridge.runtime.EquipmentType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FtmsBridgeApp(rootComponent: RootComponent) {
    val childStack by rootComponent.childStack.subscribeAsState()
    val state by BridgeRuntime.state.collectAsStateWithLifecycle()
    val strings = remember(state.settings.language) { resolveStrings(state.settings.language) }
    val activeTab = tabForConfig(childStack.active.configuration)
    val context = LocalContext.current
    val view = LocalView.current
    val onDashboard = activeTab == BridgeTab.DASHBOARD
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

    // The workout screen takes the place of everything else while it is up, see isWorkoutScreenShown.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val workoutScreenAvailable = state.running && state.selectedEquipmentType == EquipmentType.TREADMILL
    var savedWorkoutScreen by rememberSaveable(stateSaver = WorkoutScreenStateSaver) { mutableStateOf(WorkoutScreenState()) }
    // Used as it is now; kept for the next look, and across the display being turned.
    val workoutScreen = savedWorkoutScreen.after(
        available = workoutScreenAvailable,
        startedByWatch = state.startedByWatch,
        landscape = landscape,
    )
    LaunchedEffect(workoutScreen) {
        savedWorkoutScreen = workoutScreen
    }
    val showWorkoutScreen = isWorkoutScreenShown(
        available = workoutScreenAvailable,
        workoutActive = state.workoutFollow.active,
        landscape = landscape,
        state = workoutScreen,
    )
    val openWorkoutScreen = { savedWorkoutScreen = workoutScreen.openedByHand() }

    CompositionLocalProvider(LocalStrings provides strings, LocalWorkoutScreenOpener provides openWorkoutScreen) {
        BridgeTheme {
            if (showWorkoutScreen) {
                WorkoutScreenWindow(forceLandscape = workoutScreen.opened)
                WorkoutActiveScreen(
                    follow = state.workoutFollow,
                    sample = state.latest,
                    activeDurationMillis = state.activeDurationMillis,
                    sessionPaused = state.paused,
                    type = state.selectedEquipmentType,
                    fields = state.settings.workoutScreenFields,
                    control = state.treadmillControl,
                    steering = state.settings.treadmillControl,
                    strings = strings,
                    onStepTreadmill = BridgeRuntime::stepTreadmillValue,
                    onFieldsChanged = { fields ->
                        BridgeRuntime.updateSettings(state.settings.copy(workoutScreenFields = fields))
                    },
                    onClose = { savedWorkoutScreen = workoutScreen.closed() },
                )
                return@BridgeTheme
            }
            Surface(color = MaterialTheme.colorScheme.background) {
                Scaffold(
                    topBar = {
                        when (val child = childStack.active.instance) {
                            // The dashboard needs no name; its room goes to the values.
                            is RootComponent.Child.DashboardChild -> Unit
                            // The history names the session it shows and leads back from it.
                            is RootComponent.Child.HistoryChild -> HistoryTopBar(
                                state = state,
                                strings = strings,
                                onBack = { BridgeRuntime.selectHistorySession(null) },
                                onDeleteSessions = BridgeRuntime::deleteHistorySessions,
                                onClearMarks = BridgeRuntime::clearHistorySessionMarks,
                                onClearHistory = BridgeRuntime::clearHistory,
                            )
                            is RootComponent.Child.DevicesChild -> CenterAlignedTopAppBar(
                                title = { Text(activeTab.title(strings)) },
                            )
                            is RootComponent.Child.SettingsChild -> SettingsTopBar(child.component, strings)
                        }
                    },
                    bottomBar = {
                        Column {
                            // On the dashboard always; elsewhere only while there is a session to steer.
                            if (onDashboard || state.running) {
                                BridgeControlBar(
                                    state = state,
                                    strings = strings,
                                    detailed = onDashboard,
                                    onRequestPermissions = { permissionLauncher.launch(requestableRuntimePermissions()) },
                                    onStart = { BridgeRuntime.startBridgeByHand(context) },
                                    onPause = BridgeRuntime::pauseBridgeByHand,
                                    onStop = BridgeRuntime::stopBridgeByHand,
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
                        RootChildContent(child = child.instance)
                    }
                }
            }
        }
    }
}

private val WorkoutScreenStateSaver = listSaver<WorkoutScreenState, Boolean>(
    save = { listOf(it.opened, it.dismissed, it.watchStartHandled) },
    restore = { WorkoutScreenState(opened = it[0], dismissed = it[1], watchStartHandled = it[2]) },
)

@Composable
private fun RootChildContent(child: RootComponent.Child) {
    when (child) {
        is RootComponent.Child.DashboardChild -> DashboardView(component = child.component)
        is RootComponent.Child.HistoryChild -> HistoryView(component = child.component)
        is RootComponent.Child.DevicesChild -> DevicesView(component = child.component)
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
                // The label names the tab, so the symbol stays silent for a screen reader.
                icon = { Icon(tab.icon, contentDescription = null) },
                label = {
                    // One line each: a name that is too wide shrinks instead of breaking apart.
                    Text(
                        title,
                        maxLines = 1,
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
                    )
                },
            )
        }
    }
}
