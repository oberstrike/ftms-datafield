package de.ma.ftms.bridge.i18n

object EnStrings : Strings {
    override val common: CommonStrings = EnCommonStrings
    override val nav: NavigationStrings = EnNavigationStrings
    override val dashboard: DashboardStrings = EnDashboardStrings
    override val devices: DevicesStrings = EnDevicesStrings
    override val history: HistoryStrings = EnHistoryStrings
    override val logs: LogsStrings = EnLogsStrings
    override val settings: SettingsStrings = EnSettingsStrings
    override val notification: NotificationStrings = EnNotificationStrings
}

private object EnCommonStrings : CommonStrings {
    override val missingValue = "--"
    override val cancel = "Cancel"
    override val clear = "Clear"
    override val delete = "Delete"
    override val back = "Back"
    override val start = "Start"
    override val stop = "Stop"
    override val share = "Share"
    override val status = "Status"
    override val packets = "Packets"
    override val sends = "Sends"
    override val lastError = "Last error"
    override val distance = "Distance"
    override val ascent = "Ascent"
    override val duration = "Duration"
    override val treadmill = "Treadmill"
    override val garmin = "Garmin"
    override val unknown = "Unknown"
    override val machine = "Machine"
    override val resistance = "Resistance"
    override val speed = "Speed"
    override val incline = "Incline"
    override val power = "Power"
    override val heartRate = "HR"
    override val cadenceStep = "Cad / step"
    override val elapsed = "Elapsed"
    override fun sendSummary(successCount: Int, failureCount: Int) = "$successCount ok / $failureCount fail"
    override fun minValue(value: Double) = "Min %.1f".format(value)
    override fun maxValue(value: Double) = "Max %.1f".format(value)
}

private object EnNavigationStrings : NavigationStrings {
    override val dashboard = "Dashboard"
    override val history = "History"
    override val devices = "Devices"
    override val logs = "Logs"
    override val settings = "Settings"
}

private object EnDashboardStrings : DashboardStrings {
    override val title = "FTMS Bridge"
    override val bridgeRunning = "Bridge running"
    override val bridgeIdle = "Bridge idle"
    override val noFtmsEquipmentSelected = "No FTMS equipment selected"
    override val noGarminWatchSelected = "No Garmin watch selected"
    override val grantBluetoothPermissions = "Grant Bluetooth permissions"
    override val sectionStatus = "Status"
    override val sectionLiveMetrics = "Live metrics"
    override val sectionLastSession = "Last session"
    override val noPreviousSession = "No previous bridge session"
    override val bluetoothReady = "Bluetooth ready"
    override val bluetoothOff = "Bluetooth off"
    override val ftmsConnected = "FTMS connected"
    override val ftmsDisconnected = "FTMS disconnected"
    override val garminReady = "Garmin ready"
    override val garminPending = "Garmin pending"
    override val send = "Send"
    override val startBlockBridgeRunning = "Bridge is already running"
    override val startBlockBluetoothPermissions = "Bluetooth permissions are required"
    override val startBlockBluetoothUnavailable = "Bluetooth is unavailable or disabled"
    override val startBlockSelectFtms = "Select FTMS equipment"
    override val startBlockSelectGarmin = "Select a Garmin watch"
    override val startBlockGarminApp = "Refresh Garmin devices and select a watch with the data field installed"
    override val machineBike = "Bike"
    override val machineTreadmill = "Treadmill"
    override val machineCrossTrainer = "Cross trainer"
}

private object EnDevicesStrings : DevicesStrings {
    override val title = "Devices"
    override val ftmsEquipment = "FTMS equipment"
    override val bluetoothScanner = "Bluetooth scanner"
    override val noEquipmentSelected = "No equipment selected"
    override val scan = "Scan"
    override val noFtmsEquipmentFound = "No FTMS equipment found yet"
    override val garminWatch = "Garmin watch"
    override val connectIq = "Connect IQ"
    override val noWatchSelected = "No watch selected"
    override val refresh = "Refresh"
    override val noGarminDeviceLoaded = "No Garmin device loaded yet"
    override val badgeName = "Name"
    override val badgeApp = "App"
    override val badgeNoApp = "No app"
    override val badgeCheck = "Check"
    override val bluetoothUnavailable = "Bluetooth is unavailable"
    override val garminDiagnostics = "Garmin diagnostics"
    override val garminSdkStatus = "SDK status"
    override val garminKnownDevices = "Known devices"
    override val garminLastMessage = "Last message"
}

private object EnHistoryStrings : HistoryStrings {
    override val title = "History"
    override val storedSessions = "Stored sessions"
    override val noSessionsStored = "No sessions stored yet"
    override val clearHistoryTitle = "Clear history?"
    override val clearHistoryText = "This removes all locally stored bridge sessions from this phone."
    override val deleteSessionTitle = "Delete session?"
    override val deleteSessionText = "This removes this locally stored bridge session from this phone."
    override val noGraphSamplesStored = "No graph samples stored"
    override val notEnoughSamples = "Not enough samples"
    override val chartSpeed = "Speed"
    override val chartIncline = "Incline"
    override val chartAscent = "Ascent"
    override val chartPower = "Power"
    override val chartHeartRate = "Heart rate"
    override val chartCadenceStep = "Cadence / step"
    override val chartResistance = "Resistance"
    override fun savedSessions(count: Int) = "$count saved"
}

private object EnLogsStrings : LogsStrings {
    override val title = "Logs"
    override val debugLog = "Debug log"
    override val diagnosticLog = "Run diagnostics"
    override val diagnosticLogPath = "Saved file"
    override val noDiagnosticLogFile = "No saved run log yet"
    override val androidLogcatLog = "Android logcat"
    override val noDebugLogEntries = "No debug log entries"
    override val live = "Live"
    override val stopped = "Stopped"
    override val searchLogs = "Search logs"
    override val levelDebug = "Debug"
    override val levelInfo = "Info"
    override val levelWarn = "Warn"
    override val levelError = "Error"
    override fun entryCount(filteredCount: Int, totalCount: Int, capturing: Boolean): String =
        if (capturing) "$filteredCount shown of $totalCount entries, capturing" else "$filteredCount shown of $totalCount entries"
}

private object EnSettingsStrings : SettingsStrings {
    override val title = "Settings"
    override val bridgeBehavior = "Bridge behavior"
    override val sendToAllGarminVariants = "Send to all Garmin variants"
    override val sendToAllGarminVariantsSubtitle = "Updates every installed FTMS data field in the same workout."
    override val autoReconnect = "Auto reconnect"
    override val autoReconnectSubtitle = "Retry FTMS equipment connection after dropouts and packet timeouts."
    override val timing = "Timing"
    override val packetTimeout = "Packet timeout"
    override val sendInterval = "Send interval"
    override val maxLogEntries = "Max log entries"
    override val debug = "Debug"
    override val debugLogging = "Debug logging"
    override val debugLoggingEnabledSubtitle = "Capture app and Android logcat output for troubleshooting."
    override val debugLoggingDisabledSubtitle = "Enable detailed local logs before reproducing treadmill or Garmin issues."
    override val rememberedDevices = "Remembered devices"
    override val ftmsEquipment = "FTMS equipment"
    override val garminWatch = "Garmin watch"
    override val clearRememberedSelections = "Clear remembered selections"
    override val clearRememberedSelectionsSubtitle = "Forget the selected treadmill and Garmin watch on this phone."
    override val clearRememberedDevicesTitle = "Clear remembered devices?"
    override val clearRememberedDevicesText = "The bridge will forget the selected FTMS equipment and Garmin watch."
    override val language = "Language"
    override val languageSystemDefault = "System default"
}

private object EnNotificationStrings : NotificationStrings {
    override val channel = "FTMS bridge"
    override val title = "FTMS Bridge running"
    override val text = "Forwarding treadmill data to Garmin"
    override val stopAction = "Stop"
    override val shareDebugLogSubject = "FTMS Bridge debug log"
    override val shareDebugLogChooser = "Share debug log"
    override val noDebugLogFile = "No debug log file available to share"
}
