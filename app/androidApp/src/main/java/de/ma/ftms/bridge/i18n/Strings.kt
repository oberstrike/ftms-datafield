package de.ma.ftms.bridge.i18n

interface Strings {
    /** Locale used for number and date formatting in this language. */
    val locale: java.util.Locale
    val common: CommonStrings
    val nav: NavigationStrings
    val dashboard: DashboardStrings
    val devices: DevicesStrings
    val history: HistoryStrings
    val logs: LogsStrings
    val settings: SettingsStrings
    val notification: NotificationStrings
}

interface CommonStrings {
    val missingValue: String
    val cancel: String
    val clear: String
    val delete: String
    val back: String
    val start: String
    val pause: String
    val stop: String
    val share: String
    val status: String
    val packets: String
    val sends: String
    val lastError: String
    val distance: String
    val ascent: String
    val duration: String
    val treadmill: String
    val garmin: String
    val unknown: String
    val machine: String
    val sourceEquipment: String
    val sourceWatch: String
    val resistance: String
    val speed: String
    val averageSpeed: String
    val incline: String
    val power: String
    val averagePower: String
    val heartRate: String
    val averageHeartRate: String
    val cadenceStep: String
    val averageCadenceStep: String
    val elapsed: String
    val pace: String
    val averagePace: String
    val ascentRate: String
    val averageAscentRate: String
    val averageIncline: String
    val averageResistance: String
    fun sendSummary(successCount: Int, failureCount: Int): String
    fun minValue(value: Double): String
    fun maxValue(value: Double): String
}

interface NavigationStrings {
    val dashboard: String
    val history: String
    val devices: String
    val logs: String
    val settings: String
}

interface DashboardStrings {
    val title: String
    val bridgeRunning: String
    val bridgePaused: String
    val bridgeIdle: String
    val noFtmsEquipmentSelected: String
    val noGarminWatchSelected: String
    val autoStartWaiting: String
    val autoStartReady: String
    val autoStartHolding: String
    val grantBluetoothPermissions: String
    val sectionStatus: String
    val sectionSources: String
    val sourcesHint: String
    val sectionLiveMetrics: String
    val sectionLiveCharts: String
    val sectionLastSession: String
    val noPreviousSession: String
    val viewMetrics: String
    val viewCharts: String
    val noLiveGraphSamples: String
    val bluetoothReady: String
    val bluetoothOff: String
    val ftmsConnected: String
    val ftmsDisconnected: String
    val garminReady: String
    val footPod: String
    val footPodOff: String
    val footPodTreadmillOnly: String
    val footPodUnsupported: String
    val footPodPermissionMissing: String
    val footPodAdvertising: String
    val footPodConnected: String
    val footPodFailed: String
    val footPodHint: String
    val garminPending: String
    val send: String
    val startBlockBridgeRunning: String
    val startBlockBluetoothPermissions: String
    val startBlockBluetoothUnavailable: String
    val startBlockSelectFtms: String
    val startBlockSelectGarmin: String
    val startBlockGarminApp: String
    val machineBike: String
    val machineTreadmill: String
    val machineCrossTrainer: String
    fun equipmentTypeMismatch(selected: String, received: String): String
    val targetPower: String
    val targetPowerNotSet: String
    val targetPowerDisconnected: String
    val targetPowerUnsupported: String
    val targetPowerReady: String
    val targetPowerSending: String
    val targetPowerActive: String
    val targetPowerRejected: String
    val targetPowerFailed: String
    val targetPowerNoPresets: String
    fun targetPowerRange(minWatts: Int, maxWatts: Int): String
    fun chartSummary(title: String, latest: String, min: String, max: String, start: String, end: String): String
}

interface DevicesStrings {
    val title: String
    val ftmsEquipment: String
    val bluetoothScanner: String
    val noEquipmentSelected: String
    val scan: String
    val noFtmsEquipmentFound: String
    val garminWatch: String
    val connectIq: String
    val noWatchSelected: String
    val refresh: String
    val noGarminDeviceLoaded: String
    val badgeName: String
    val badgeApp: String
    val badgeNoApp: String
    val badgeCheck: String
    val bluetoothUnavailable: String
    val garminDiagnostics: String
    val garminSdkStatus: String
    val garminKnownDevices: String
    val garminLastMessage: String
    val garminConnectNotInstalled: String
    val garminConnectUpgradeNeeded: String
    val garminConnectServiceError: String
    val installGarminConnect: String
    val updateGarminConnect: String
    val equipmentType: String
    val equipmentTypeSubtitle: String
    val equipmentTypeLockedWhileRunning: String
}

interface HistoryStrings {
    val title: String
    val allTime: String
    val totalSessions: String
    val storedSessions: String
    val noSessionsStored: String
    val clearHistoryTitle: String
    val clearHistoryText: String
    val deleteSessionTitle: String
    val deleteSessionText: String
    val noGraphSamplesStored: String
    val notEnoughSamples: String
    val chartSpeed: String
    val chartIncline: String
    val chartAscent: String
    val chartPower: String
    val chartHeartRate: String
    val chartCadenceStep: String
    val chartResistance: String
    val viewMetrics: String
    val viewCharts: String
    val filterAll: String
    val sourceComparison: String
    fun savedSessions(count: Int): String
}

interface LogsStrings {
    val title: String
    val debugLog: String
    val diagnosticLog: String
    val diagnosticLogPath: String
    val noDiagnosticLogFile: String
    val androidLogcatLog: String
    val noDebugLogEntries: String
    val live: String
    val stopped: String
    val searchLogs: String
    val levelDebug: String
    val levelInfo: String
    val levelWarn: String
    val levelError: String
    fun entryCount(filteredCount: Int, totalCount: Int, capturing: Boolean): String
}

interface SettingsStrings {
    val title: String
    val bridgeBehavior: String
    val bridgeBehaviorSubtitle: String
    val dashboard: String
    val dashboardSubtitle: String
    val dashboardMetrics: String
    val dashboardMetricsSubtitle: String
    val dashboardGraphs: String
    val dashboardGraphsSubtitle: String
    val dashboardEquipmentType: String
    val dataSources: String
    val dataSourcesSubtitle: String
    val dataSourcesHint: String
    val dataSourcesEquipmentType: String
    val indoorCycling: String
    val indoorCyclingSubtitle: String
    val targetPowerPresets: String
    val targetPowerPresetsSubtitle: String
    val targetPowerNewPreset: String
    val targetPowerAddPreset: String
    val targetPowerStep: String
    val targetPowerStepSubtitle: String
    val targetPowerRestoreDefaults: String
    val targetPowerRestoreDefaultsSubtitle: String
    val targetPowerRestoreAction: String
    fun targetPowerPresetLimits(minWatts: Int, maxWatts: Int, maxCount: Int): String
    fun targetPowerRemovePreset(watts: Int): String
    val dashboardChartTimespan: String
    val chartTimespanFullSession: String
    val keepOneDashboardOptionSelected: String
    val notificationShortcut: String
    val notificationShortcutSubtitle: String
    val powerEstimate: String
    val powerEstimateSubtitle: String
    val powerSource: String
    val powerSourceFtmsPreferred: String
    val powerSourceCalculated: String
    val powerBodyMassKg: String
    val powerFlatCost: String
    val powerFlatCostHint: String
    val powerInvalidSettings: String
    val autoReconnect: String
    val autoReconnectSubtitle: String
    val autoStart: String
    val autoStartSubtitle: String
    val autoStop: String
    val autoStopSubtitle: String
    val followWatchTimer: String
    val followWatchTimerSubtitle: String
    val virtualFootPod: String
    val virtualFootPodSubtitle: String
    val timing: String
    val timingSubtitle: String
    val packetTimeout: String
    val sendInterval: String
    val maxLogEntries: String
    val debug: String
    val debugSubtitle: String
    val debugLogging: String
    val debugLoggingEnabledSubtitle: String
    val debugLoggingDisabledSubtitle: String
    val rememberedDevices: String
    val rememberedDevicesSubtitle: String
    val ftmsEquipment: String
    val garminWatch: String
    val clearRememberedSelections: String
    val clearRememberedSelectionsSubtitle: String
    val clearRememberedDevicesTitle: String
    val clearRememberedDevicesText: String
    val language: String
    val languageSubtitle: String
    val languageSystemDefault: String
    val appVersion: String
    val openSource: String
    val openSourceSubtitle: String
    val openSourceLibraries: String
    val thirdPartySdk: String
    val libraryVersion: String
    val license: String
    val projectWebsite: String
    val garminSdkNote: String
    fun appVersionValue(version: String): String
    fun chartTimespanMinutes(minutes: Int): String
}

interface NotificationStrings {
    val channel: String
    val title: String
    val text: String
    val pausedText: String
    val openAppAction: String
    val pauseAction: String
    val resumeAction: String
    val stopAction: String
    val shareDebugLogSubject: String
    val shareDebugLogChooser: String
    val noDebugLogFile: String
}
