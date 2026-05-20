package de.ma.ftms.bridge.runtime

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import java.io.File
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import de.ma.ftms.bridge.ble.TreadmillBleClient
import de.ma.ftms.bridge.ble.hasRuntimePermissions
import de.ma.ftms.bridge.debug.AndroidLogCaptureService
import de.ma.ftms.bridge.debug.BridgeLogger
import de.ma.ftms.bridge.debug.DiagnosticRunLogger
import de.ma.ftms.bridge.debug.LogCaptureService
import de.ma.ftms.bridge.garmin.GarminBridgeClient
import de.ma.ftms.bridge.i18n.resolveStrings
import de.ma.ftms.core.BridgeMessage
import de.ma.ftms.core.DistanceSource
import de.ma.ftms.core.FtmsMachineKind
import de.ma.ftms.core.FtmsParser
import de.ma.ftms.core.FtmsSample
import de.ma.ftms.core.RscMeasurementParser
import de.ma.ftms.core.SmoothedTreadmillSample
import de.ma.ftms.core.TreadmillSampleSmoother
import de.ma.ftms.core.storage.SessionHistoryRepository
import de.ma.ftms.core.storage.WorkoutSessionRecord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val GARMIN_HEART_RATE_MAX_AGE_MS = 5_000L
private const val GARMIN_HEART_RATE_DIAGNOSTIC_INTERVAL_MS = 10_000L

object BridgeRuntime : TreadmillBleClient.Callbacks, GarminBridgeClient.Callbacks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val smoother = TreadmillSampleSmoother()
    private val backoff = ReconnectBackoff()
    private val stats = SessionStats()
    private val _state = MutableStateFlow(BridgeUiState())
    private var appContext: Context? = null
    private var preferences: BridgePreferences? = null
    private var sessionHistory: SessionHistoryRepository? = null
    private var logCapture: LogCaptureService? = null
    private var diagnostics: DiagnosticRunLogger? = null
    private var treadmillClient: TreadmillBleClient? = null
    private var garminClient: GarminBridgeClient? = null
    private var senderJob: Job? = null
    private var reconnectJob: Job? = null
    private var packetWatchdogJob: Job? = null
    private var historyWriteJob: Job? = null
    private var sessionsObserverJob: Job? = null
    private var samplesObserverJob: Job? = null
    private var logsObserverJob: Job? = null
    private var diagnosticLogsObserverJob: Job? = null
    private var captureStateObserverJob: Job? = null
    private var activeSessionId: String? = null
    private var activeSessionCreation: CompletableDeferred<WorkoutSessionRecord?>? = null
    private var activeSessionStartedAtMillis: Long = 0
    private var pausedStartedAtMillis: Long = 0
    private var accumulatedPausedMillis: Long = 0
    private var pausedDistanceOffsetM: Double = 0.0
    private var pausedAscentOffsetM: Double = 0.0
    private var pauseBaselineDistanceM: Double = 0.0
    private var pauseBaselineAscentM: Double = 0.0
    private var lastPersistedSampleAtMillis: Long = 0
    private val bufferedSessionSamples = mutableListOf<SmoothedTreadmillSample>()
    private var latestSmoothedSample: SmoothedTreadmillSample? = null
    private var latestRscSample: FtmsSample? = null
    private var diagnosticPacketCount = 0
    private var lastDiagnosticDistanceSource: DistanceSource? = null
    private var lastDiagnosticDistanceM: Double? = null
    private var lastDiagnosticCadenceValue: Double? = null
    private var lastDiagnosticInclinePct: Double? = null
    private var latestGarminHeartRate: GarminHeartRate? = null
    private var lastGarminHeartRateDiagnosticAtMillis = 0L
    private var sequence = 0
    private val stopCompletionCallbacks = mutableListOf<() -> Unit>()

    val state: StateFlow<BridgeUiState> = _state

    fun initialize(context: Context) {
        if (appContext != null) {
            refreshPermissionState()
            return
        }

        val applicationContext = context.applicationContext
        appContext = applicationContext
        preferences = BridgePreferences(applicationContext)
        sessionHistory = createSessionHistoryRepository(applicationContext)
        logCapture = AndroidLogCaptureService(applicationContext)
        diagnostics = DiagnosticRunLogger(applicationContext)
        treadmillClient = TreadmillBleClient(applicationContext, this)
        garminClient = GarminBridgeClient(applicationContext, this)

        val remembered = preferences?.loadSelection()
        val settings = preferences?.loadSettings() ?: BridgeSettings()
        logCapture?.updateMaxEntries(settings.maxLogEntries)
        _state.update {
            it.copy(
                selectedTreadmillAddress = remembered?.treadmillAddress,
                selectedTreadmillName = remembered?.treadmillName,
                selectedGarminId = remembered?.garminId,
                selectedGarminName = remembered?.garminName,
                settings = settings,
            )
        }

        observeLogs()
        observeDiagnosticLogs()
        if (settings.debugLoggingEnabled) {
            logCapture?.startCapture()
        }
        observeSessions()
        enqueueHistoryWrite {
            sessionHistory?.recoverInterruptedSessions(System.currentTimeMillis())
            _state.update { state ->
                state.copy(lastSummary = sessionHistory?.latestSession()?.toLastSessionSummary())
            }
        }
        refreshPermissionState()
        garminClient?.initialize()
        log("Bridge runtime initialized")
    }

    fun refreshPermissionState() {
        val context = appContext ?: return
        val adapter = context.getSystemService<BluetoothManager>()?.adapter
        _state.update {
            it.copy(
                permissionsGranted = hasRuntimePermissions(context),
                bleAvailable = adapter != null && adapter.isEnabled,
            )
        }
    }

    fun startScan() {
        refreshPermissionState()
        val current = _state.value
        if (!current.permissionsGranted || !current.bleAvailable) {
            recordError("Bluetooth permission or adapter missing")
            return
        }

        treadmillClient?.startScan()
    }

    fun stopScan() {
        treadmillClient?.stopScan()
    }

    fun refreshGarminDevices() {
        garminClient?.refreshDevices()
    }

    fun updateSettings(settings: BridgeSettings) {
        val previous = _state.value.settings
        preferences?.saveSettings(settings)
        logCapture?.updateMaxEntries(settings.maxLogEntries)
        when {
            settings.debugLoggingEnabled && !previous.debugLoggingEnabled -> logCapture?.startCapture()
            !settings.debugLoggingEnabled && previous.debugLoggingEnabled -> logCapture?.stopCapture()
        }
        _state.update { state ->
            state.copy(
                settings = settings,
            )
        }
        refreshBridgeNotification()
        log("Settings updated")
    }

    fun clearRememberedSelections() {
        if (_state.value.running) {
            stopBridge()
        }

        preferences?.clearSelection()
        _state.update {
            it.copy(
                selectedTreadmillAddress = null,
                selectedTreadmillName = null,
                selectedGarminId = null,
                selectedGarminName = null,
                garminReady = false,
            )
        }
        log("Remembered selections cleared")
    }

    fun clearLogs() {
        logCapture?.clearLogs()
        diagnostics?.clearRecent()
    }

    fun toggleLogCapture() {
        val capture = logCapture ?: return
        val enabled = !capture.isCapturing.value
        val settings = _state.value.settings.copy(debugLoggingEnabled = enabled)
        updateSettings(settings)
    }

    fun shareLatestLog(context: Context) {
        scope.launch {
            val latestPath = logCapture?.latestLogFilePath()
            _state.update { it.copy(latestLogFilePath = latestPath) }
            if (latestPath == null) {
                recordError(strings().notification.noDebugLogFile)
                return@launch
            }

            val file = File(latestPath)
            val notificationStrings = strings().notification
            val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, notificationStrings.shareDebugLogSubject)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, notificationStrings.shareDebugLogChooser).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            log("Sharing debug log ${file.name}")
        }
    }

    fun selectHistorySession(sessionId: String?) {
        samplesObserverJob?.cancel()
        samplesObserverJob = null
        _state.update {
            it.copy(
                selectedHistorySessionId = sessionId,
                selectedHistorySamples = emptyList(),
            )
        }

        if (sessionId == null) {
            return
        }

        samplesObserverJob = scope.launch {
            sessionHistory?.observeSamples(sessionId)?.collectLatest { samples ->
                _state.update { it.copy(selectedHistorySamples = samples) }
            }
        }
    }

    fun deleteHistorySession(sessionId: String) {
        scope.launch {
            sessionHistory?.deleteSession(sessionId)
            if (_state.value.selectedHistorySessionId == sessionId) {
                selectHistorySession(null)
            }
            _state.update { state ->
                state.copy(lastSummary = sessionHistory?.latestSession()?.toLastSessionSummary())
            }
            log("Deleted session")
        }
    }

    fun clearHistory() {
        scope.launch {
            sessionHistory?.clearAll()
            selectHistorySession(null)
            _state.update { it.copy(lastSummary = null) }
            log("Session history cleared")
        }
    }

    fun selectTreadmill(address: String) {
        val item = _state.value.treadmills.firstOrNull { it.address == address }
        if (item != null) {
            preferences?.saveTreadmill(item)
        }

        _state.update {
            it.copy(
                selectedTreadmillAddress = address,
                selectedTreadmillName = item?.name ?: it.selectedTreadmillName,
            )
        }
        log("Selected treadmill ${item?.name ?: address}")
    }

    fun selectGarmin(id: Long) {
        val item = _state.value.garmins.firstOrNull { it.id == id }
        if (item != null) {
            preferences?.saveGarmin(item)
        }

        latestGarminHeartRate = null
        _state.update { state ->
            state.copy(
                selectedGarminId = id,
                selectedGarminName = item?.name ?: state.selectedGarminName,
                garminReady = state.garmins.any { it.id == id && it.appInstalled == true },
            )
        }
        log("Selected Garmin ${item?.name ?: id}")
    }

    fun startBridge(context: Context) {
        initialize(context)
        refreshPermissionState()

        val current = _state.value
        if (current.running && current.paused) {
            resumeBridge()
            return
        }
        if (current.running) {
            recordError("Bridge is already running")
            return
        }

        val treadmillAddress = current.selectedTreadmillAddress
        val garminId = current.selectedGarminId
        if (!current.permissionsGranted || treadmillAddress == null || garminId == null) {
            recordError("Select treadmill and Garmin before starting")
            return
        }

        if (!current.garminReady) {
            recordError("Selected Garmin has not confirmed the data field app")
            return
        }

        ContextCompat.startForegroundService(
            context,
            Intent(context, BridgeForegroundService::class.java).setAction(BridgeForegroundService.ACTION_START),
        )

        senderJob?.cancel()
        reconnectJob?.cancel()
        packetWatchdogJob?.cancel()
        backoff.reset()
        smoother.reset()
        stats.start()
        sequence = 0
        latestRscSample = null
        latestSmoothedSample = null
        latestGarminHeartRate = null
        lastGarminHeartRateDiagnosticAtMillis = 0L
        resetDiagnosticState()
        pausedStartedAtMillis = 0
        accumulatedPausedMillis = 0
        pausedDistanceOffsetM = 0.0
        pausedAscentOffsetM = 0.0
        pauseBaselineDistanceM = 0.0
        pauseBaselineAscentM = 0.0
        lastPersistedSampleAtMillis = 0
        bufferedSessionSamples.clear()
        val runStartedAtMillis = stats.startedAtMillis
        activeSessionStartedAtMillis = runStartedAtMillis
        val sessionCreation = CompletableDeferred<WorkoutSessionRecord?>()
        activeSessionCreation = sessionCreation
        activeSessionId = null
        diagnostics?.startRun(
            startedAtMillis = runStartedAtMillis,
            sessionId = null,
            treadmillName = current.selectedTreadmillName ?: treadmillAddress,
            garminName = current.selectedGarminName ?: garminId.toString(),
        )
        _state.update {
            it.copy(
                running = true,
                paused = false,
                activeDurationMillis = 0,
                treadmillConnected = false,
                latest = null,
                liveDashboardSamples = emptyList(),
                lastSendStatus = "starting",
                reconnectStatus = "connecting",
                nextRetryDelayMs = 0,
                packetCount = 0,
                sendSuccessCount = 0,
                sendFailureCount = 0,
                lastError = "",
                latestDiagnosticLogPath = diagnostics?.latestLogPath,
            )
        }
        enqueueHistoryWrite {
            val session = runCatching {
                sessionHistory?.createSession(
                    startedAtMillis = runStartedAtMillis,
                    treadmillName = current.selectedTreadmillName,
                    treadmillAddress = treadmillAddress,
                    garminName = current.selectedGarminName,
                    garminId = garminId,
                )
            }.onFailure { error ->
                BridgeLogger.warn(TAG_RUNTIME, "Failed to create running session: ${error.message ?: error::class.simpleName.orEmpty()}")
                diagnostics?.warn(TAG_RUNTIME, "session_create_failed error=${error.message ?: error::class.simpleName.orEmpty()}")
            }.getOrNull()
            sessionCreation.complete(session)
        }
        scope.launch {
            val session = sessionCreation.await()
            if (activeSessionCreation !== sessionCreation || activeSessionStartedAtMillis != runStartedAtMillis || !_state.value.running) {
                return@launch
            }
            if (session == null) {
                val message = "Failed to create running session"
                recordError(message)
                activeSessionCreation = null
                activeSessionStartedAtMillis = 0
                ensureBridgeStoppedUi()
                stopBridgeForegroundServiceIfIdle()
                diagnostics?.stopRun("failed")
                _state.update { it.copy(latestDiagnosticLogPath = diagnostics?.latestLogPath) }
                return@launch
            }

            activeSessionCreation = null
            activeSessionId = session.sessionId
            activeSessionStartedAtMillis = session.startedAtMillis
            diagnostics?.attachSession(session.sessionId)
            treadmillClient?.connect(treadmillAddress)
            startSender()
            startPacketWatchdog()
        }
    }

    fun pauseBridge() {
        val current = _state.value
        if (!current.running || current.paused) {
            return
        }

        val now = System.currentTimeMillis()
        pausedStartedAtMillis = now
        val baseline = latestSmoothedSample
        pauseBaselineDistanceM = baseline?.distanceM ?: 0.0
        pauseBaselineAscentM = baseline?.ascentM ?: 0.0
        _state.update {
            it.copy(
                paused = true,
                activeDurationMillis = activeDurationAt(now),
                lastSendStatus = "paused",
            )
        }
        sendControlMessage("ftms_pause")
        refreshBridgeNotification()
        log("Bridge paused")
    }

    fun resumeBridge() {
        val current = _state.value
        if (!current.running || !current.paused) {
            return
        }

        val now = System.currentTimeMillis()
        val pauseStarted = pausedStartedAtMillis.takeIf { it > 0 } ?: now
        accumulatedPausedMillis += (now - pauseStarted).coerceAtLeast(0L)
        latestSmoothedSample?.let { smoothed ->
            pausedDistanceOffsetM += (smoothed.distanceM - pauseBaselineDistanceM).coerceAtLeast(0.0)
            pausedAscentOffsetM += (smoothed.ascentM - pauseBaselineAscentM).coerceAtLeast(0.0)
        }
        pausedStartedAtMillis = 0
        _state.update {
            it.copy(
                paused = false,
                activeDurationMillis = activeDurationAt(now),
                lastSendStatus = "resuming",
            )
        }
        sendControlMessage("ftms_resume")
        refreshBridgeNotification()
        log("Bridge resumed")
    }

    fun stopBridge(onStopped: (() -> Unit)? = null) {
        onStopped?.let { stopCompletionCallbacks += it }

        if (activeSessionStartedAtMillis <= 0) {
            ensureBridgeStoppedUi()
            completeStopWhenHistoryIdle()
            return
        }

        senderJob?.cancel()
        senderJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        packetWatchdogJob?.cancel()
        packetWatchdogJob = null
        if (_state.value.paused) {
            val now = System.currentTimeMillis()
            val pauseStarted = pausedStartedAtMillis.takeIf { it > 0 } ?: now
            accumulatedPausedMillis += (now - pauseStarted).coerceAtLeast(0L)
            pausedStartedAtMillis = 0
        }
        sendStopMessage()
        treadmillClient?.disconnect()
        smoother.reset()
        latestRscSample = null
        latestSmoothedSample = null
        latestGarminHeartRate = null
        lastGarminHeartRateDiagnosticAtMillis = 0L
        val startedAtMillis = activeSessionStartedAtMillis
        val sessionId = activeSessionId
        val sessionCreation = activeSessionCreation
        val localSummary = stats.finish("stopped")
        val stoppedAtMillis = stats.stoppedAtMillis
        val snapshot = stats.snapshot()
        val sessionSamples = bufferedSessionSamples.toList()
        val treadmillName = _state.value.selectedTreadmillName
        val treadmillAddress = _state.value.selectedTreadmillAddress
        val garminName = _state.value.selectedGarminName
        val garminId = _state.value.selectedGarminId
        activeSessionCreation = null
        activeSessionId = null
        activeSessionStartedAtMillis = 0
        bufferedSessionSamples.clear()
        _state.update {
            it.copy(
                running = false,
                paused = false,
                activeDurationMillis = 0,
                treadmillConnected = false,
                latest = null,
                liveDashboardSamples = emptyList(),
                lastSendStatus = "stopped",
                reconnectStatus = "idle",
                nextRetryDelayMs = 0,
                lastSummary = localSummary,
            )
        }
        enqueueHistoryWrite {
            val resolvedSessionId = sessionId ?: sessionCreation?.await()?.sessionId
            val result = runCatching {
                val history = sessionHistory ?: error("Session history unavailable")
                if (resolvedSessionId != null) {
                    history.finishSession(
                        sessionId = resolvedSessionId,
                        stoppedAtMillis = stoppedAtMillis,
                        finalStatus = "stopped",
                        stats = snapshot,
                    )
                } else {
                    history.saveFinishedSession(
                        startedAtMillis = startedAtMillis,
                        stoppedAtMillis = stoppedAtMillis,
                        finalStatus = "stopped",
                        treadmillName = treadmillName,
                        treadmillAddress = treadmillAddress,
                        garminName = garminName,
                        garminId = garminId,
                        stats = snapshot,
                        samples = sessionSamples,
                    )
                }
            }

            result.onSuccess { record ->
                _state.update { it.copy(lastSummary = record?.toLastSessionSummary() ?: localSummary) }
            }.onFailure { error ->
                val message = "Failed to finish stopped session: ${error.message ?: error::class.simpleName.orEmpty()}"
                BridgeLogger.warn(TAG_RUNTIME, message)
                diagnostics?.warn(TAG_RUNTIME, "session_finish_failed error=${error.message ?: error::class.simpleName.orEmpty()}")
                _state.update { it.copy(lastError = message, lastSummary = localSummary) }
            }

            stopBridgeForegroundServiceIfIdle()
            drainStopCompletionCallbacks()
        }
        log("Bridge stopped")
        diagnostics?.info(TAG_RUNTIME, "bridge_stopped packets=${stats.packetCount} sendOk=${stats.sendSuccessCount} sendFail=${stats.sendFailureCount}")
        scope.launch {
            delay(DIAGNOSTIC_STOP_GRACE_MS)
            diagnostics?.stopRun("stopped")
            _state.update { it.copy(latestDiagnosticLogPath = diagnostics?.latestLogPath) }
        }
    }

    override fun onTreadmillFound(item: TreadmillDeviceItem) {
        BridgeLogger.debug(
            TAG_BLE,
            "Treadmill scan match name=${item.name} address=${item.address} rssi=${item.rssi} hasFtms=${item.hasFtms}",
        )
        _state.update { state ->
            val filtered = state.treadmills.filterNot { it.address == item.address }
            state.copy(
                treadmills = (filtered + item).sortedByDescending { it.rssi },
                selectedTreadmillName = if (state.selectedTreadmillAddress == item.address) item.name else state.selectedTreadmillName,
            )
        }
    }

    override fun onScanStateChanged(scanning: Boolean) {
        BridgeLogger.debug(TAG_BLE, "Scan state changed scanning=$scanning")
        _state.update { it.copy(scanning = scanning) }
    }

    override fun onConnectionChanged(connected: Boolean) {
        if (connected) {
            backoff.reset()
            reconnectJob?.cancel()
            reconnectJob = null
        }

        _state.update {
            it.copy(
                treadmillConnected = connected,
                reconnectStatus = if (connected) "connected" else if (it.running) "disconnected" else "idle",
                nextRetryDelayMs = 0,
            )
        }
        diagnostics?.info(TAG_BLE, "connection_changed connected=$connected running=${_state.value.running}")
        log(if (connected) "Treadmill connected" else "Treadmill disconnected")
    }

    override fun onPacket(kind: Int, bytes: ByteArray) {
        BridgeLogger.debug(TAG_BLE, "FTMS packet kind=$kind size=${bytes.size} bytes=${bytes.toHexPreview()}")
        val packetIndex = diagnosticPacketCount + 1
        val parsed = FtmsParser.parse(kind, bytes, System.currentTimeMillis())
        val settings = _state.value.settings
        val sample = resolveHeartRateSource(mergeSecondarySensors(parsed), settings.heartRateSource, latestGarminHeartRate)
            .withResolvedPower(settings)
        val smoothed = smoother.update(sample)
        latestSmoothedSample = smoothed
        logGarminHeartRateSourceState(sample)
        logPacketDiagnostic(packetIndex, kind, bytes, parsed, sample, smoothed)
        diagnosticPacketCount = packetIndex
        BridgeLogger.debug(
            TAG_BLE,
            "Parsed sample speedKmh=${smoothed.raw.speedKmh} distanceM=${smoothed.distanceM} ascentM=${smoothed.ascentM} cadStep=${smoothed.raw.cadenceOrStepRate()}",
        )
        if (_state.value.paused) {
            diagnostics?.debug(TAG_RUNTIME, "packet ignored while paused")
            return
        }

        val adjusted = smoothed.adjustForActiveSession()
        stats.recordPacket(adjusted)
        bufferSessionSample(adjusted)
        _state.update {
            it.copy(
                latest = adjusted,
                activeDurationMillis = adjusted.raw.timestampMillis
                    ?.let { timestamp -> (timestamp - activeSessionStartedAtMillis).coerceAtLeast(0L) }
                    ?: it.activeDurationMillis,
                packetCount = stats.packetCount,
                lastError = stats.lastError,
            )
        }
    }

    override fun onRscMeasurement(bytes: ByteArray) {
        val sample = RscMeasurementParser.parse(bytes, System.currentTimeMillis())
        if (sample.truncated) {
            BridgeLogger.warn(TAG_BLE, "RSC parse failed error=${sample.parseError} bytes=${bytes.toHexPreview()}")
            diagnostics?.warn(TAG_BLE, "rsc_parse_error error=${sample.parseError} bytes=${bytes.toHexPreview()}")
            return
        }

        latestRscSample = sample
        diagnostics?.info(TAG_BLE, "rsc_sample speed=${sample.speedKmh} step=${sample.stepRateSpm} dist=${sample.distanceM} flags=${sample.rawFlags}")
        BridgeLogger.debug(
            TAG_BLE,
            "RSC measurement speedKmh=${sample.speedKmh} stepRateSpm=${sample.stepRateSpm} distanceM=${sample.distanceM}",
        )
    }

    override fun onGarminHeartRate(deviceId: Long, bpm: Int) {
        val selectedGarminId = _state.value.selectedGarminId
        if (selectedGarminId != null && deviceId != selectedGarminId) {
            diagnostics?.debug(TAG_GARMIN, "garmin_hr ignored=device_mismatch device=$deviceId selected=$selectedGarminId bpm=$bpm")
            return
        }

        latestGarminHeartRate = GarminHeartRate(
            bpm = bpm,
            timestampMillis = System.currentTimeMillis(),
        )
        _state.update {
            it.copy(garminLastMessage = "Garmin HR $bpm bpm")
        }
        diagnostics?.debug(TAG_GARMIN, "garmin_hr bpm=$bpm device=$deviceId")
    }

    override fun onFailure(message: String) {
        diagnostics?.warn(TAG_RUNTIME, "failure message=$message running=${_state.value.running}")
        recordError(message)
        if (_state.value.running && _state.value.settings.autoReconnect) {
            scheduleReconnect(message)
        }
    }

    override fun onDevicesLoaded(devices: List<GarminDeviceItem>) {
        _state.update { state ->
            state.copy(
                garmins = devices,
                garminKnownDeviceCount = devices.size,
                garminReady = devices.any { it.id == state.selectedGarminId && it.appInstalled == true },
            )
        }
    }

    override fun onDiscoveryStatusChanged(sdkStatus: String, knownDeviceCount: Int, message: String) {
        _state.update {
            it.copy(
                garminSdkStatus = sdkStatus,
                garminKnownDeviceCount = knownDeviceCount,
                garminLastMessage = message,
            )
        }
        diagnostics?.debug(TAG_GARMIN, "discovery status=$sdkStatus knownDevices=$knownDeviceCount message=$message")
        BridgeLogger.debug(TAG_GARMIN, "Discovery status=$sdkStatus knownDevices=$knownDeviceCount message=$message")
    }

    override fun onDeviceUpdated(device: GarminDeviceItem) {
        _state.update { state ->
            val previous = state.garmins.firstOrNull { it.id == device.id }
            val updated = if (device.appInstalled == null && previous != null) {
                device.copy(appInstalled = previous.appInstalled)
            } else {
                device
            }
            val devices = (state.garmins.filterNot { it.id == updated.id } + updated).sortedBy { it.name }
            state.copy(
                garmins = devices,
                garminReady = devices.any { it.id == state.selectedGarminId && it.appInstalled == true },
                selectedGarminName = if (state.selectedGarminId == updated.id) updated.name else state.selectedGarminName,
            )
        }
    }

    override fun onSendStatus(status: String) {
        BridgeLogger.debug(TAG_GARMIN, "Garmin send status=$status")
        val diagnosticEvent = if (_state.value.running) "send_status" else "send_stop_ack"
        diagnostics?.info(TAG_GARMIN, "$diagnosticEvent status=$status running=${_state.value.running} seq=$sequence")
        if (!_state.value.running) {
            _state.update { it.copy(lastSendStatus = status) }
            return
        }

        stats.recordSendStatus(status)
        _state.update {
            it.copy(
                lastSendStatus = status,
                sendSuccessCount = stats.sendSuccessCount,
                sendFailureCount = stats.sendFailureCount,
                lastError = stats.lastError,
            )
        }
    }

    override fun log(message: String) {
        BridgeLogger.info(TAG_RUNTIME, message)
        diagnostics?.info(TAG_RUNTIME, message)
    }

    override fun onBleDiagnostic(message: String) {
        diagnostics?.debug(TAG_BLE, message)
    }

    private fun startSender() {
        senderJob?.cancel()
        senderJob = scope.launch {
            while (true) {
                delay(_state.value.settings.sendIntervalMillis)
                val state = _state.value
                if (!state.running || state.paused) {
                    continue
                }

                val latest = state.latest ?: continue
                val selectedGarminId = state.selectedGarminId ?: continue
                val payload = BridgeMessage(++sequence, latest).toMap()
                diagnostics?.debug(
                    TAG_GARMIN,
                    "send_attempt seq=$sequence device=$selectedGarminId payload=${payload.toSortedDebugText()}",
                )
                BridgeLogger.debug(
                    TAG_GARMIN,
                    "Sending bridge packet sequence=$sequence deviceId=$selectedGarminId payload=$payload",
                )
                garminClient?.send(
                    deviceId = selectedGarminId,
                    payload = payload,
                )
            }
        }
    }

    private fun sendStopMessage() {
        val selectedGarminId = _state.value.selectedGarminId ?: return
        val payload = BridgeMessage.stopMap(++sequence)
        diagnostics?.info(TAG_GARMIN, "send_stop seq=$sequence device=$selectedGarminId payload=${payload.toSortedDebugText()}")
        BridgeLogger.debug(TAG_GARMIN, "Sending bridge stop sequence=$sequence deviceId=$selectedGarminId payload=$payload")
        garminClient?.send(
            deviceId = selectedGarminId,
            payload = payload,
        )
    }

    private fun sendControlMessage(type: String) {
        val selectedGarminId = _state.value.selectedGarminId ?: return
        val payload = BridgeMessage.controlMap(++sequence, type)
        diagnostics?.info(TAG_GARMIN, "send_control type=$type seq=$sequence device=$selectedGarminId payload=${payload.toSortedDebugText()}")
        garminClient?.send(
            deviceId = selectedGarminId,
            payload = payload,
        )
    }

    private fun mergeSecondarySensors(sample: FtmsSample): FtmsSample {
        if (sample.kind != FtmsMachineKind.TREADMILL) {
            return sample
        }

        val rsc = latestRscSample ?: return sample
        val rscTimestamp = rsc.timestampMillis ?: return sample
        if (System.currentTimeMillis() - rscTimestamp > SECONDARY_SENSOR_MAX_AGE_MS) {
            diagnostics?.debug(TAG_BLE, "rsc_merge skipped=stale ageMs=${System.currentTimeMillis() - rscTimestamp}")
            return sample
        }

        val mergedStepRate = sample.stepRateSpm ?: if (sample.cadenceRpm == null) rsc.stepRateSpm else null
        if (mergedStepRate != sample.stepRateSpm) {
            diagnostics?.info(TAG_BLE, "rsc_merge stepRate=$mergedStepRate rscAgeMs=${System.currentTimeMillis() - rscTimestamp}")
        }
        return sample.copy(
            speedKmh = sample.speedKmh ?: rsc.speedKmh,
            distanceM = sample.distanceM ?: rsc.distanceM,
            stepRateSpm = mergedStepRate,
        )
    }

    private fun resolveHeartRateSource(
        sample: FtmsSample,
        source: HeartRateSource,
        garminHeartRate: GarminHeartRate?,
    ): FtmsSample =
        sample.withResolvedHeartRate(
            source = source,
            garminHeartRate = garminHeartRate,
            nowMillis = System.currentTimeMillis(),
        )

    private fun logGarminHeartRateSourceState(sample: FtmsSample) {
        val current = _state.value
        if (current.settings.heartRateSource != HeartRateSource.GARMIN) {
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastGarminHeartRateDiagnosticAtMillis < GARMIN_HEART_RATE_DIAGNOSTIC_INTERVAL_MS) {
            return
        }

        val garminHeartRate = latestGarminHeartRate
        val message = when {
            garminHeartRate == null -> "waiting for Garmin HR"
            now - garminHeartRate.timestampMillis > GARMIN_HEART_RATE_MAX_AGE_MS ->
                "Garmin HR stale ageMs=${now - garminHeartRate.timestampMillis} bpm=${garminHeartRate.bpm}"
            sample.heartRateBpm != null ->
                "Garmin HR ${sample.heartRateBpm} bpm ageMs=${now - garminHeartRate.timestampMillis}"
            else -> "waiting for Garmin HR"
        }

        lastGarminHeartRateDiagnosticAtMillis = now
        diagnostics?.info(TAG_GARMIN, "garmin_hr_source $message")
        BridgeLogger.debug(TAG_GARMIN, "Garmin HR source: $message")
        _state.update { it.copy(garminLastMessage = message) }
    }

    private fun startPacketWatchdog() {
        packetWatchdogJob?.cancel()
        packetWatchdogJob = scope.launch {
            while (true) {
                delay(5_000)
                val current = _state.value
                if (!current.running || current.paused || !current.treadmillConnected || stats.lastPacketAtMillis == 0L) {
                    continue
                }

                val timeoutMs = current.settings.packetTimeoutMillis
                if (System.currentTimeMillis() - stats.lastPacketAtMillis > timeoutMs) {
                    diagnostics?.warn(TAG_RUNTIME, "packet_watchdog_timeout timeoutMs=$timeoutMs lastPacketAt=${stats.lastPacketAtMillis}")
                    recordError("No treadmill packet for ${timeoutMs / 1000}s")
                    treadmillClient?.disconnect()
                    if (_state.value.settings.autoReconnect) {
                        scheduleReconnect("packet timeout")
                    }
                }
            }
        }
    }

    private fun scheduleReconnect(reason: String) {
        val address = _state.value.selectedTreadmillAddress ?: return
        if (!_state.value.running || !_state.value.settings.autoReconnect || reconnectJob?.isActive == true) {
            return
        }

        val delayMs = backoff.nextDelayMillis()
        _state.update {
            it.copy(
                reconnectStatus = "retry in ${delayMs / 1000}s",
                nextRetryDelayMs = delayMs,
            )
        }
        log("Reconnect scheduled: $reason")
        diagnostics?.info(TAG_RUNTIME, "reconnect_scheduled reason=$reason delayMs=$delayMs")
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!_state.value.running) {
                return@launch
            }

            _state.update { it.copy(reconnectStatus = "reconnecting", nextRetryDelayMs = 0) }
            treadmillClient?.connect(address)
            reconnectJob = null
        }
    }

    private fun recordError(message: String) {
        BridgeLogger.warn(TAG_RUNTIME, message)
        diagnostics?.warn(TAG_RUNTIME, "error message=$message")
        stats.recordError(message)
        _state.update { it.copy(lastError = message) }
    }

    private fun observeSessions() {
        sessionsObserverJob?.cancel()
        sessionsObserverJob = scope.launch {
            sessionHistory?.observeRecentSessions()?.collectLatest { sessions ->
                _state.update { state ->
                    state.copy(
                        sessions = sessions,
                        lastSummary = sessions.firstOrNull { it.finalStatus != "running" }?.toLastSessionSummary()
                            ?: state.lastSummary,
                    )
                }
            }
        }
    }

    private fun observeLogs() {
        val capture = logCapture ?: return
        logsObserverJob?.cancel()
        logsObserverJob = scope.launch {
            capture.logs.collectLatest { logs ->
                _state.update { state -> state.copy(logs = logs.take(state.settings.maxLogEntries)) }
            }
        }

        captureStateObserverJob?.cancel()
        captureStateObserverJob = scope.launch {
            capture.isCapturing.collectLatest { capturing ->
                val latestPath = capture.latestLogFilePath()
                _state.update {
                    it.copy(
                        logCaptureEnabled = capturing,
                        latestLogFilePath = latestPath,
                    )
                }
            }
        }
    }

    private fun observeDiagnosticLogs() {
        diagnosticLogsObserverJob?.cancel()
        diagnosticLogsObserverJob = scope.launch {
            diagnostics?.logs?.collectLatest { logs ->
                _state.update {
                    it.copy(
                        diagnosticLogs = logs,
                        latestDiagnosticLogPath = diagnostics?.latestLogPath,
                    )
                }
            }
        }
    }

    private fun enqueueHistoryWrite(block: suspend () -> Unit): Job {
        val previous = historyWriteJob
        lateinit var job: Job
        job = scope.launch {
            previous?.join()
            try {
                block()
            } finally {
                if (historyWriteJob === job) {
                    historyWriteJob = null
                }
            }
        }
        historyWriteJob = job
        return job
    }

    private fun completeStopWhenHistoryIdle() {
        if (historyWriteJob?.isActive == true) {
            enqueueHistoryWrite {
                stopBridgeForegroundServiceIfIdle()
                drainStopCompletionCallbacks()
            }
        } else {
            stopBridgeForegroundServiceIfIdle()
            drainStopCompletionCallbacks()
        }
    }

    private fun stopBridgeForegroundServiceIfIdle() {
        val context = appContext ?: return
        if (shouldStopBridgeForegroundService(_state.value.running, activeSessionStartedAtMillis)) {
            context.stopService(Intent(context, BridgeForegroundService::class.java))
        }
    }

    private fun ensureBridgeStoppedUi() {
        senderJob?.cancel()
        senderJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        packetWatchdogJob?.cancel()
        packetWatchdogJob = null
        _state.update {
            it.copy(
                running = false,
                paused = false,
                activeDurationMillis = 0,
                treadmillConnected = false,
                latest = null,
                liveDashboardSamples = emptyList(),
                lastSendStatus = if (it.lastSendStatus == "idle") it.lastSendStatus else "stopped",
                reconnectStatus = "idle",
                nextRetryDelayMs = 0,
            )
        }
    }

    private fun drainStopCompletionCallbacks() {
        val callbacks = stopCompletionCallbacks.toList()
        stopCompletionCallbacks.clear()
        callbacks.forEach { it() }
    }

    private fun refreshBridgeNotification() {
        val context = appContext ?: return
        if (!_state.value.running) {
            return
        }

        context.startService(Intent(context, BridgeForegroundService::class.java).setAction(BridgeForegroundService.ACTION_REFRESH))
    }

    private fun bufferSessionSample(sample: SmoothedTreadmillSample) {
        val startedAt = activeSessionStartedAtMillis.takeIf { it > 0 } ?: stats.startedAtMillis
        val timestamp = sample.raw.timestampMillis ?: System.currentTimeMillis()
        if (timestamp - lastPersistedSampleAtMillis < 1_000L) {
            diagnostics?.debug(TAG_RUNTIME, "sample_buffer skipped=throttle timestamp=$timestamp last=$lastPersistedSampleAtMillis")
            persistSessionStats()
            return
        }

        lastPersistedSampleAtMillis = timestamp
        bufferedSessionSamples += sample
        diagnostics?.debug(
            TAG_RUNTIME,
            "sample_buffer offsetMs=${(timestamp - startedAt).coerceAtLeast(0L)} dist=${sample.distanceM} ascent=${sample.ascentM}",
        )
        persistSessionSample(sample, startedAt)
        _state.update { it.copy(liveDashboardSamples = bufferedSessionSamples.toList()) }
    }

    private fun persistSessionSample(sample: SmoothedTreadmillSample, startedAtMillis: Long) {
        val sessionId = activeSessionId ?: return
        val snapshot = stats.snapshot()
        val updatedAtMillis = System.currentTimeMillis()
        enqueueHistoryWrite {
            val result = runCatching {
                val history = sessionHistory ?: error("Session history unavailable")
                history.appendSample(sessionId, startedAtMillis, sample)
                history.updateStats(sessionId, updatedAtMillis, snapshot)
            }
            result.onFailure { error ->
                BridgeLogger.warn(TAG_RUNTIME, "Failed to persist sample: ${error.message ?: error::class.simpleName.orEmpty()}")
                diagnostics?.warn(TAG_RUNTIME, "sample_persist_failed error=${error.message ?: error::class.simpleName.orEmpty()}")
            }
        }
    }

    private fun persistSessionStats() {
        val sessionId = activeSessionId ?: return
        val snapshot = stats.snapshot()
        val updatedAtMillis = System.currentTimeMillis()
        enqueueHistoryWrite {
            val result = runCatching {
                val history = sessionHistory ?: error("Session history unavailable")
                history.updateStats(sessionId, updatedAtMillis, snapshot)
            }
            result.onFailure { error ->
                BridgeLogger.warn(TAG_RUNTIME, "Failed to persist stats: ${error.message ?: error::class.simpleName.orEmpty()}")
                diagnostics?.warn(TAG_RUNTIME, "stats_persist_failed error=${error.message ?: error::class.simpleName.orEmpty()}")
            }
        }
    }

    private fun SmoothedTreadmillSample.adjustForActiveSession(): SmoothedTreadmillSample {
        val sourceTimestamp = raw.timestampMillis ?: System.currentTimeMillis()
        val activeTimestamp = activeSessionStartedAtMillis + activeDurationAt(sourceTimestamp)
        return copy(
            raw = raw.copy(timestampMillis = activeTimestamp),
            distanceM = (distanceM - pausedDistanceOffsetM).coerceAtLeast(0.0),
            ascentM = (ascentM - pausedAscentOffsetM).coerceAtLeast(0.0),
        )
    }

    private fun activeDurationAt(timestampMillis: Long): Long {
        val startedAt = activeSessionStartedAtMillis.takeIf { it > 0 } ?: return 0
        val runningPauseMillis = if (_state.value.paused && pausedStartedAtMillis > 0) {
            (timestampMillis - pausedStartedAtMillis).coerceAtLeast(0L)
        } else {
            0L
        }
        return (timestampMillis - startedAt - accumulatedPausedMillis - runningPauseMillis).coerceAtLeast(0L)
    }

    private fun ByteArray.toHexPreview(maxBytes: Int = 32): String {
        val preview = take(maxBytes).joinToString(" ") { byte -> "%02X".format(byte) }
        return if (size > maxBytes) "$preview ..." else preview
    }

    private fun logPacketDiagnostic(
        packetIndex: Int,
        kind: Int,
        bytes: ByteArray,
        parsed: FtmsSample,
        sample: FtmsSample,
        smoothed: SmoothedTreadmillSample,
    ) {
        val cadence = sample.cadenceOrStepRate()
        val sourceChanged = smoothed.distanceSource != lastDiagnosticDistanceSource
        val cadenceChanged = cadence != lastDiagnosticCadenceValue
        val inclineChanged = sample.inclinePct != lastDiagnosticInclinePct
        val previousDistance = lastDiagnosticDistanceM
        val distanceJump = previousDistance != null && kotlin.math.abs(smoothed.distanceM - previousDistance) > DIAGNOSTIC_DISTANCE_JUMP_M
        val shouldLog = packetIndex == 1 ||
            packetIndex % DIAGNOSTIC_PACKET_INTERVAL == 0 ||
            parsed.truncated ||
            sourceChanged ||
            cadenceChanged ||
            inclineChanged ||
            distanceJump

        if (shouldLog) {
            val reason = buildList {
                if (packetIndex == 1) add("first")
                if (packetIndex % DIAGNOSTIC_PACKET_INTERVAL == 0) add("interval")
                if (parsed.truncated) add("parse_error")
                if (sourceChanged) add("source")
                if (cadenceChanged) add("cadence")
                if (inclineChanged) add("incline")
                if (distanceJump) add("distance_jump")
            }.joinToString("|")
            diagnostics?.info(
                TAG_BLE,
                "packet#$packetIndex reason=$reason kind=$kind size=${bytes.size} flags=${parsed.rawFlags} raw=${bytes.toHexPreview()} " +
                    "parseError=${parsed.parseError} speed=${sample.speedKmh} rawDist=${sample.distanceM} smoothDist=${smoothed.distanceM} " +
                    "distSource=${smoothed.distanceSource} incline=${sample.inclinePct} ascent=${smoothed.ascentM} cadStep=$cadence elapsed=${sample.elapsedS}",
            )
        }

        lastDiagnosticDistanceSource = smoothed.distanceSource
        lastDiagnosticCadenceValue = cadence
        lastDiagnosticInclinePct = sample.inclinePct
        lastDiagnosticDistanceM = smoothed.distanceM
    }

    private fun resetDiagnosticState() {
        diagnosticPacketCount = 0
        lastDiagnosticDistanceSource = null
        lastDiagnosticDistanceM = null
        lastDiagnosticCadenceValue = null
        lastDiagnosticInclinePct = null
    }

    private fun Map<String, Any>.toSortedDebugText(): String =
        entries.sortedBy { it.key }.joinToString(prefix = "{", postfix = "}") { "${it.key}=${it.value}" }

    private fun strings() = resolveStrings(_state.value.settings.language)

    private const val TAG_RUNTIME = "BridgeRuntime"
    private const val TAG_BLE = "FtmsBle"
    private const val TAG_GARMIN = "GarminCiq"
    private const val FILE_PROVIDER_AUTHORITY = "de.ma.ftms.bridge.fileprovider"
    private const val SECONDARY_SENSOR_MAX_AGE_MS = 3_000L
    private const val DIAGNOSTIC_PACKET_INTERVAL = 5
    private const val DIAGNOSTIC_DISTANCE_JUMP_M = 5.0
    private const val DIAGNOSTIC_STOP_GRACE_MS = 2_500L
}

internal fun shouldStopBridgeForegroundService(running: Boolean, activeSessionStartedAtMillis: Long): Boolean =
    !running && activeSessionStartedAtMillis <= 0

internal data class GarminHeartRate(
    val bpm: Int,
    val timestampMillis: Long,
)

internal fun FtmsSample.withResolvedHeartRate(
    source: HeartRateSource,
    garminHeartRate: GarminHeartRate?,
    nowMillis: Long,
): FtmsSample =
    when (source) {
        HeartRateSource.MACHINE -> this
        HeartRateSource.GARMIN -> copy(
            heartRateBpm = garminHeartRate
                ?.takeIf { nowMillis - it.timestampMillis <= GARMIN_HEART_RATE_MAX_AGE_MS }
                ?.bpm,
        )
    }

internal fun FtmsSample.withResolvedPower(settings: BridgeSettings): FtmsSample {
    if (!settings.powerCalculationEnabled) {
        return this
    }

    val calculated = treadmillPowerWatts(
        bodyMassKg = settings.powerBodyMassKg,
        speedKmh = speedKmh,
        inclinePercent = inclinePct ?: 0.0,
        flatCost = settings.powerFlatCost,
    )

    return when (settings.powerSource) {
        PowerSource.FTMS_PREFERRED -> if (powerW != null) this else copy(powerW = calculated)
        PowerSource.CALCULATED -> copy(powerW = calculated ?: powerW)
    }
}

internal fun treadmillPowerWatts(
    bodyMassKg: Double?,
    speedKmh: Double?,
    inclinePercent: Double,
    flatCost: Double?,
): Int? {
    val mass = bodyMassKg?.takeIf { it > 0.0 } ?: return null
    val speed = speedKmh?.takeIf { it > 0.0 } ?: return null
    val cost = flatCost?.takeIf { it > 0.0 } ?: return null
    val metersPerSecond = speed / 3.6
    val inclineDecimal = inclinePercent / 100.0
    val watts = mass * metersPerSecond * (cost + 9.81 * inclineDecimal)
    return watts.roundToInt().coerceAtLeast(0)
}
