package de.ma.ftms.bridge.i18n

interface Strings {
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
    val resistance: String
    val speed: String
    val incline: String
    val power: String
    val heartRate: String
    val cadenceStep: String
    val elapsed: String
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
    val bridgeIdle: String
    val noFtmsEquipmentSelected: String
    val noGarminWatchSelected: String
    val grantBluetoothPermissions: String
    val sectionStatus: String
    val sectionLiveMetrics: String
    val sectionLastSession: String
    val noPreviousSession: String
    val bluetoothReady: String
    val bluetoothOff: String
    val ftmsConnected: String
    val ftmsDisconnected: String
    val garminReady: String
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
}

interface HistoryStrings {
    val title: String
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
    val sendToAllGarminVariants: String
    val sendToAllGarminVariantsSubtitle: String
    val autoReconnect: String
    val autoReconnectSubtitle: String
    val timing: String
    val packetTimeout: String
    val sendInterval: String
    val maxLogEntries: String
    val debug: String
    val debugLogging: String
    val debugLoggingEnabledSubtitle: String
    val debugLoggingDisabledSubtitle: String
    val rememberedDevices: String
    val ftmsEquipment: String
    val garminWatch: String
    val clearRememberedSelections: String
    val clearRememberedSelectionsSubtitle: String
    val clearRememberedDevicesTitle: String
    val clearRememberedDevicesText: String
    val language: String
    val languageSystemDefault: String
}

interface NotificationStrings {
    val channel: String
    val title: String
    val text: String
    val stopAction: String
    val shareDebugLogSubject: String
    val shareDebugLogChooser: String
    val noDebugLogFile: String
}
