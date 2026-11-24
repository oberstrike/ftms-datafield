package de.ma.ftms.bridge.runtime

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.io.File
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import de.ma.ftms.bridge.ble.TreadmillBleClient
import de.ma.ftms.bridge.ble.VirtualFootPod
import de.ma.ftms.bridge.ble.hasAdvertisePermission
import de.ma.ftms.bridge.ble.hasRuntimePermissions
import de.ma.ftms.bridge.debug.AndroidLogCaptureService
import de.ma.ftms.bridge.debug.BridgeLogger
import de.ma.ftms.bridge.debug.DiagnosticRunLogger
import de.ma.ftms.bridge.debug.LogCaptureService
import de.ma.ftms.bridge.garmin.GarminBridgeClient
import de.ma.ftms.bridge.i18n.resolveStrings
import de.ma.ftms.core.BridgeMessage
import de.ma.ftms.core.DistanceSource
import de.ma.ftms.core.FitShowStatusParser
import de.ma.ftms.core.FtmsControlPoint
import de.ma.ftms.core.FtmsMachineKind
import de.ma.ftms.core.FtmsParser
import de.ma.ftms.core.FtmsPowerRange
import de.ma.ftms.core.FtmsSample
import de.ma.ftms.core.FtmsSampleMerger
import de.ma.ftms.core.RscMeasurementParser
import de.ma.ftms.core.SensorReadings
import de.ma.ftms.core.SmoothedTreadmillSample
import de.ma.ftms.core.StepRateEstimator
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

private const val GARMIN_HEART_RATE_DIAGNOSTIC_INTERVAL_MS = 10_000L

/**
 * Process-wide bridge state machine.
 *
 * Everything here runs on the main thread: UI calls, coroutines of [scope], Connect IQ callbacks
 * (delivered through a main-thread broadcast receiver) and BLE callbacks, which
 * [TreadmillBleClient] posts to the main looper. The mutable fields below rely on that and are
 * deliberately unsynchronized.
 *
 * The clients held here only ever receive the application context (see [initialize]), which lives
 * as long as the process, so the static references do not leak an activity or service.
 */
@SuppressLint("StaticFieldLeak")
object BridgeRuntime : TreadmillBleClient.Callbacks, GarminBridgeClient.Callbacks, VirtualFootPod.Callbacks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val smoother = TreadmillSampleSmoother()
    private val merger = FtmsSampleMerger()
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
    private var footPod: VirtualFootPod? = null
    private val footPodFeed = FootPodFeed()
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
    private val odometer = SessionOdometer()
    private val autoStopPolicy = AutoStopPolicy()
    private var autoStopJob: Job? = null

    /** Newest timer state the watch reported, and the state the bridge last acted on. */
    private var watchTimer: WatchTimerReport? = null
    private var followedWatchTimer: WatchTimerState? = null

    /** Counts diagnostic runs, so a run's delayed closing does not close its successor. */
    private var diagnosticRunGeneration = 0
    private var lastPersistedSampleAtMillis: Long = 0
    private val bufferedSessionSamples = mutableListOf<SmoothedTreadmillSample>()
    private var latestRscSample: FtmsSample? = null
    private val vendorStepRate = StepRateEstimator()

    /** Step rate from the equipment's own step count, with the time it was worked out at. */
    private var latestVendorStepRate: Pair<Double, Long>? = null
    private var diagnosticPacketCount = 0
    private var lastDiagnosticDistanceSource: DistanceSource? = null
    private var lastDiagnosticDistanceM: Double? = null
    private var lastDiagnosticCadenceValue: Double? = null
    private var lastDiagnosticInclinePct: Double? = null
    private var latestWatchReadings: WatchReadings? = null
    private var lastGarminHeartRateDiagnosticAtMillis = 0L
    private var treadmillConnectedAtMillis = 0L
    private var lastFtmsPacketAtMillis = 0L
    private var awaitingFirstPacketAfterConnect = false
    private var lastPauseMessageAtMillis = 0L
    private var lastWaitMessageAtMillis = 0L
    private val autoStartPolicy = AutoStartPolicy()
    private val appVisible = MutableStateFlow(false)
    private var presenceWatchFailed = false
    private var equipmentSeen: CompletableDeferred<Unit>? = null

    /**
     * True while auto-start holds the equipment connection without a session: packets only feed
     * the start decision and a lost link is no error. A starting session takes the connection over.
     */
    private var standbyActive = false
    private var standbyEnded: CompletableDeferred<Unit>? = null
    private var standbyLastPacketAtMillis = 0L
    private val targetPower = TargetPowerController(
        sendCommand = ::sendTargetPowerCommand,
        onStateChanged = { powerControl -> _state.update { it.copy(powerControl = powerControl) } },
    )
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
        footPod = VirtualFootPod(applicationContext, this)

        val remembered = preferences?.loadSelection()
        val settings = preferences?.loadSettings() ?: BridgeSettings()
        logCapture?.updateMaxEntries(settings.maxLogEntries)
        _state.update {
            it.copy(
                selectedTreadmillAddress = remembered?.treadmillAddress,
                selectedTreadmillName = remembered?.treadmillName,
                selectedEquipmentType = remembered?.equipmentType ?: EquipmentType.TREADMILL,
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
        observeEquipmentType()
        observeFootPod()
        observeAutoStart()
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
        // The permission may have been granted since the sensor was refused for lack of it.
        if (_state.value.footPodStatus == FootPodStatus.PERMISSION_MISSING && hasAdvertisePermission(context)) {
            footPod?.start()
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

    /** Re-checks everything the user may have changed outside the app (permissions, Garmin Connect). */
    fun onAppResumed() {
        refreshPermissionState()
        garminClient?.retryInitializationIfFailed()
    }

    /** Auto-start only watches for the equipment while the app is on screen. */
    fun onAppVisibilityChanged(visible: Boolean) {
        appVisible.value = visible
    }

    fun updateSettings(settings: BridgeSettings) {
        val previous = _state.value.settings
        if (settings.autoStart != previous.autoStart) {
            autoStartPolicy.reset()
        }
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
        autoStartPolicy.reset()
        _state.update {
            it.copy(
                selectedTreadmillAddress = null,
                selectedTreadmillName = null,
                selectedEquipmentType = EquipmentType.TREADMILL,
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
        val newEquipment = address != _state.value.selectedTreadmillAddress
        if (newEquipment) {
            autoStartPolicy.reset()
        }
        // Equipment that says what it is saves the user a step; the type stays changeable.
        val advertisedType = item?.advertisedType?.takeIf { newEquipment }
        if (advertisedType != null) {
            preferences?.saveEquipmentType(advertisedType)
        }

        _state.update {
            it.copy(
                selectedTreadmillAddress = address,
                selectedTreadmillName = item?.name ?: it.selectedTreadmillName,
                selectedEquipmentType = advertisedType ?: it.selectedEquipmentType,
            )
        }
        log("Selected treadmill ${item?.name ?: address}")
    }

    fun selectEquipmentType(type: EquipmentType) {
        if (_state.value.running) {
            // The running session was recorded as the type it started with.
            return
        }

        preferences?.saveEquipmentType(type)
        _state.update { it.copy(selectedEquipmentType = type) }
        log("Selected equipment type ${type.name}")
    }

    fun selectGarmin(id: Long) {
        val item = _state.value.garmins.firstOrNull { it.id == id }
        if (item != null) {
            preferences?.saveGarmin(item)
        }

        latestWatchReadings = null
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

        if (!isWatchReadyForStart(current)) {
            recordError("Selected Garmin has not confirmed the data field app")
            return
        }

        val serviceStart = runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BridgeForegroundService::class.java).setAction(BridgeForegroundService.ACTION_START),
            )
        }
        if (serviceStart.isFailure) {
            // Without the foreground service Android would stop the bridge once the app is hidden.
            val error = serviceStart.exceptionOrNull()
            recordError("Bridge service could not be started: ${error?.message ?: error?.let { it::class.simpleName }}")
            return
        }

        senderJob?.cancel()
        reconnectJob?.cancel()
        packetWatchdogJob?.cancel()
        backoff.reset()
        smoother.reset()
        merger.reset()
        treadmillConnectedAtMillis = 0L
        lastFtmsPacketAtMillis = 0L
        awaitingFirstPacketAfterConnect = false
        stats.start()
        sequence = 0
        latestRscSample = null
        vendorStepRate.reset()
        latestVendorStepRate = null
        latestWatchReadings = null
        lastGarminHeartRateDiagnosticAtMillis = 0L
        resetDiagnosticState()
        pausedStartedAtMillis = 0
        accumulatedPausedMillis = 0
        odometer.reset()
        autoStopPolicy.reset()
        lastPersistedSampleAtMillis = 0
        bufferedSessionSamples.clear()
        val runStartedAtMillis = stats.startedAtMillis
        activeSessionStartedAtMillis = runStartedAtMillis
        val sessionCreation = CompletableDeferred<WorkoutSessionRecord?>()
        activeSessionCreation = sessionCreation
        activeSessionId = null
        diagnosticRunGeneration += 1
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
                    equipmentKind = current.selectedEquipmentType.machineKind,
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
                autoStartPolicy.holdUntilStandstill(SystemClock.elapsedRealtime())
                standbyActive = false
                treadmillClient?.disconnect()
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
            val adoptStandbyConnection = standbyActive && treadmillClient?.isConnectedTo(treadmillAddress) == true
            standbyActive = false
            if (adoptStandbyConnection) {
                onStandbyConnectionAdopted()
            } else {
                treadmillClient?.connect(treadmillAddress)
            }
            // At once, before the equipment is connected: a data field that has connected to the
            // equipment itself has to let go of it, or this session never gets the connection.
            sendWaitMessage(System.currentTimeMillis())
            startSender()
            startPacketWatchdog()
            startAutoStopWatch()
        }
    }

    fun pauseBridge() {
        val current = _state.value
        if (!current.running || current.paused) {
            return
        }

        val now = System.currentTimeMillis()
        pausedStartedAtMillis = now
        _state.update {
            it.copy(
                paused = true,
                activeDurationMillis = activeDurationAt(now),
                lastSendStatus = "paused",
            )
        }
        sendControlMessage("ftms_pause")
        lastPauseMessageAtMillis = now
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
        endSession(endedAtMillis = null, onStopped = onStopped)
    }

    /**
     * Ends the session. With [endedAtMillis] it counts as over since that moment, which is how a
     * session that ended by itself leaves out the wait for the equipment to be surely at rest.
     */
    private fun endSession(endedAtMillis: Long?, onStopped: (() -> Unit)?) {
        onStopped?.let { stopCompletionCallbacks += it }

        if (activeSessionStartedAtMillis <= 0) {
            ensureBridgeStoppedUi()
            completeStopWhenHistoryIdle()
            return
        }

        // The equipment may still be moving; without this auto-start would restart at once.
        autoStartPolicy.holdUntilStandstill(SystemClock.elapsedRealtime())
        standbyActive = false
        senderJob?.cancel()
        senderJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        packetWatchdogJob?.cancel()
        packetWatchdogJob = null
        autoStopJob?.cancel()
        autoStopJob = null
        if (_state.value.paused) {
            val now = System.currentTimeMillis()
            val pauseStarted = pausedStartedAtMillis.takeIf { it > 0 } ?: now
            accumulatedPausedMillis += (now - pauseStarted).coerceAtLeast(0L)
            pausedStartedAtMillis = 0
        }
        sendStopMessage()
        treadmillClient?.disconnect()
        targetPower.clearTarget()
        smoother.reset()
        merger.reset()
        treadmillConnectedAtMillis = 0L
        lastFtmsPacketAtMillis = 0L
        awaitingFirstPacketAfterConnect = false
        latestRscSample = null
        vendorStepRate.reset()
        latestVendorStepRate = null
        latestWatchReadings = null
        lastGarminHeartRateDiagnosticAtMillis = 0L
        val startedAtMillis = activeSessionStartedAtMillis
        val sessionId = activeSessionId
        val sessionCreation = activeSessionCreation
        val localSummary = stats.finish("stopped", endedAtMillis)
        val stoppedAtMillis = stats.stoppedAtMillis
        val snapshot = stats.snapshot()
        val sessionSamples = bufferedSessionSamples.toList()
        val treadmillName = _state.value.selectedTreadmillName
        val treadmillAddress = _state.value.selectedTreadmillAddress
        val garminName = _state.value.selectedGarminName
        val garminId = _state.value.selectedGarminId
        val equipmentKind = _state.value.selectedEquipmentType.machineKind
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
                        equipmentKind = equipmentKind,
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
        val stoppedRun = diagnosticRunGeneration
        scope.launch {
            delay(DIAGNOSTIC_STOP_GRACE_MS)
            // A session started in the meantime writes its own run, which must stay open.
            if (diagnosticRunGeneration != stoppedRun) {
                return@launch
            }
            diagnostics?.stopRun("stopped")
            _state.update { it.copy(latestDiagnosticLogPath = diagnostics?.latestLogPath) }
        }
    }

    /** Called by the foreground service when Android refused to run it in the foreground. */
    internal fun onForegroundServiceUnavailable(reason: String) {
        diagnostics?.warn(TAG_RUNTIME, "foreground_service_unavailable reason=$reason")
        stopBridge()
        recordError("Bridge stopped: foreground service unavailable ($reason)")
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

    override fun onScanFailed(message: String) {
        // A failed scan says nothing about an established equipment connection, so no reconnect.
        diagnostics?.warn(TAG_BLE, "scan_failed message=$message")
        recordError(message)
    }

    override fun onWatchedEquipmentSeen(address: String) {
        if (address != autoStartWatchAddress(_state.value, appVisible.value)) {
            return
        }

        autoStartPolicy.onEquipmentSeen(SystemClock.elapsedRealtime())
        equipmentSeen?.complete(Unit)
    }

    override fun onPresenceWatchFailed(message: String) {
        diagnostics?.warn(TAG_BLE, "presence_watch_failed message=$message")
        presenceWatchFailed = true
    }

    override fun onConnectionChanged(connected: Boolean) {
        if (connected) {
            // The reconnect backoff is only reset once data flows again (see onPacket); a link that
            // connects but never delivers data must keep backing off.
            reconnectJob?.cancel()
            reconnectJob = null
            treadmillConnectedAtMillis = System.currentTimeMillis()
            awaitingFirstPacketAfterConnect = true
            merger.reset()
        } else {
            treadmillConnectedAtMillis = 0L
            // Within a session the target is set again once the equipment is back.
            targetPower.onEquipmentDisconnected(keepTarget = _state.value.running)
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
        if (standbyActive) {
            onStandbyPacket(kind, bytes)
            return
        }

        val packetIndex = diagnosticPacketCount + 1
        val receivedAtMillis = System.currentTimeMillis()
        lastFtmsPacketAtMillis = receivedAtMillis
        if (awaitingFirstPacketAfterConnect) {
            awaitingFirstPacketAfterConnect = false
            backoff.reset()
        }
        val parsed = FtmsParser.parse(kind, bytes, receivedAtMillis)
        val settings = _state.value.settings
        val sample = mergeSecondarySensors(merger.merge(parsed)).withResolvedSources(
            settings = settings,
            equipmentType = _state.value.selectedEquipmentType,
            watch = latestWatchReadings,
            nowMillis = receivedAtMillis,
        )
        val smoothed = smoother.update(sample)
        val paused = _state.value.paused
        odometer.advance(smoothed, counted = !paused)
        // The packet as the equipment sent it: a cadence taken from the watch says nothing about
        // whether the equipment moves.
        autoStopPolicy.onMovementReported(isEquipmentMoving(parsed), receivedAtMillis)
        logWatchSourceState(sample, receivedAtMillis)
        logPacketDiagnostic(packetIndex, kind, bytes, parsed, sample, smoothed)
        diagnosticPacketCount = packetIndex
        BridgeLogger.debug(
            TAG_BLE,
            "Parsed sample speedKmh=${smoothed.raw.speedKmh} distanceM=${smoothed.distanceM} ascentM=${smoothed.ascentM} cadStep=${smoothed.raw.cadenceOrStepRate()}",
        )
        if (paused) {
            publishToFootPod(smoothed, paused = true, nowMillis = receivedAtMillis)
            diagnostics?.debug(TAG_RUNTIME, "packet ignored while paused")
            return
        }

        val adjusted = smoothed.forActiveSession()
        publishToFootPod(adjusted, paused = false, nowMillis = receivedAtMillis)
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

    override fun onVendorPacket(bytes: ByteArray) {
        val status = FitShowStatusParser.parse(bytes) ?: return
        val now = System.currentTimeMillis()
        val stepCount = status.stepCount
        if (stepCount == null) {
            // The belt does not run, so nobody walks on it.
            vendorStepRate.reset()
            latestVendorStepRate = null
            return
        }

        val rate = vendorStepRate.update(stepCount, now)
        val previous = latestVendorStepRate?.first
        latestVendorStepRate = rate?.let { it to now }
        if ((rate == null) != (previous == null) || (rate == 0.0) != (previous == 0.0)) {
            diagnostics?.info(TAG_BLE, "vendor_step_rate rate=$rate steps=$stepCount")
        }
    }

    override fun onWatchSensors(deviceId: Long, readings: SensorReadings) {
        val selectedGarminId = _state.value.selectedGarminId
        if (selectedGarminId != null && deviceId != selectedGarminId) {
            diagnostics?.debug(TAG_GARMIN, "watch_sensors ignored=device_mismatch device=$deviceId selected=$selectedGarminId")
            return
        }

        latestWatchReadings = WatchReadings(readings = readings, timestampMillis = System.currentTimeMillis())
        _state.update {
            it.copy(garminLastMessage = "Watch ${describeReadings(readings)}")
        }
        diagnostics?.debug(TAG_GARMIN, "watch_sensors ${describeReadings(readings)} device=$deviceId")
    }

    override fun onWatchTimer(deviceId: Long, state: WatchTimerState) {
        val current = _state.value
        val selectedGarminId = current.selectedGarminId
        if (selectedGarminId != null && deviceId != selectedGarminId) {
            return
        }

        val previous = watchTimer?.state
        watchTimer = WatchTimerReport(state, System.currentTimeMillis())
        if (previous != state) {
            log("Watch activity: $state")
            diagnostics?.info(TAG_GARMIN, "watch_timer state=$state previous=$previous device=$deviceId")
        }
        if (!current.settings.followWatchTimer) {
            followedWatchTimer = state
            return
        }

        when (watchTimerAction(followedWatchTimer, state, current.running, current.paused)) {
            WatchTimerAction.START -> {
                val context = appContext
                if (context == null || !isBridgeStartPossible(current)) {
                    // Not followed yet, so the watch's next report tries again.
                    BridgeLogger.debug(TAG_RUNTIME, "Watch activity is recording, but the bridge cannot start yet")
                    return
                }
                log("Starting with the activity on the watch")
                startBridge(context)
            }
            WatchTimerAction.PAUSE -> {
                log("Pausing with the activity on the watch")
                pauseBridge()
            }
            WatchTimerAction.RESUME -> {
                log("Resuming with the activity on the watch")
                resumeBridge()
            }
            WatchTimerAction.STOP -> {
                log("Stopping with the activity on the watch")
                stopBridge()
            }
            WatchTimerAction.NONE -> Unit
        }
        followedWatchTimer = state
    }

    override fun onFailure(message: String) {
        if (standbyActive) {
            // Equipment that is switched off while auto-start waits for it is no error.
            diagnostics?.info(TAG_RUNTIME, "standby_ended message=$message")
            autoStartPolicy.onConnectionLost()
            standbyEnded?.complete(Unit)
            return
        }

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

    override fun onGarminConnectProblem(problem: GarminConnectProblem?) {
        _state.update { state ->
            if (problem == null) {
                state.copy(garminConnectProblem = null)
            } else {
                // Without Garmin Connect no watch is reachable, so nothing listed before still counts.
                state.copy(garminConnectProblem = problem, garmins = emptyList(), garminReady = false)
            }
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

    override fun onControlPointSetUp(available: Boolean) {
        diagnostics?.info(TAG_BLE, "control_point_set_up available=$available")
        targetPower.onEquipmentReady(controlPointAvailable = available)
    }

    override fun onSupportedPowerRange(bytes: ByteArray) {
        val range = FtmsPowerRange.parse(bytes)
        diagnostics?.info(TAG_BLE, "supported_power_range range=$range")
        targetPower.onSupportedRange(range)
    }

    override fun onControlPointResponse(bytes: ByteArray) {
        val response = FtmsControlPoint.parseResponse(bytes) ?: return
        diagnostics?.info(
            TAG_BLE,
            "control_point_response opCode=${response.requestOpCode} result=${response.resultCode}",
        )
        targetPower.onResponse(response)
    }

    override fun onControlPointWriteFailed() {
        targetPower.onWriteFailed()
    }

    /** Chooses where [metric] is taken from for the selected equipment type. */
    fun selectSensorSource(metric: SourcedMetric, source: ValueSource) {
        val current = _state.value
        if (current.settings.sensorSources(current.selectedEquipmentType).of(metric) == source) {
            return
        }

        updateSettings(current.settings.withSensorSource(current.selectedEquipmentType, metric, source))
        diagnostics?.info(TAG_RUNTIME, "sensor_source metric=${metric.name} source=${source.name} type=${current.selectedEquipmentType.name}")
    }

    /** Sets the target power of the connected indoor bike; ignored while that is not possible. */
    fun setTargetPower(watts: Int) {
        if (!_state.value.powerControl.canSetTarget) {
            return
        }

        targetPower.requestTarget(watts)
        val target = _state.value.powerControl.targetWatts
        diagnostics?.info(TAG_BLE, "target_power_requested watts=$watts target=$target")
        log("Target power $target W")
    }

    /** Moves the target power one configured step up ([direction] 1) or down (-1). */
    fun stepTargetPower(direction: Int) {
        val current = _state.value
        setTargetPower(
            steppedTargetPower(
                currentTarget = current.powerControl.targetWatts,
                // The target is held by the equipment, so its own measurement is the starting point.
                measuredWatts = current.latest?.raw?.let { it.equipment?.powerW ?: it.powerW },
                stepWatts = current.settings.targetPowerStepWatts,
                direction = direction,
            ),
        )
    }

    private fun sendTargetPowerCommand(bytes: ByteArray, sequence: Int): Boolean {
        if (treadmillClient?.writeControlPoint(bytes) != true) {
            return false
        }

        scope.launch {
            delay(TARGET_POWER_ANSWER_TIMEOUT_MS)
            targetPower.onCommandLost(sequence)
        }
        return true
    }

    private fun startSender() {
        senderJob?.cancel()
        senderJob = scope.launch {
            while (true) {
                delay(_state.value.settings.sendIntervalMillis)
                val state = _state.value
                if (!state.running) {
                    continue
                }
                if (state.paused) {
                    // Repeat the pause message so the watch can tell a paused bridge from a phone
                    // that went away, and keeps its own BLE scan off meanwhile.
                    val now = System.currentTimeMillis()
                    if (now - lastPauseMessageAtMillis >= PAUSE_HEARTBEAT_INTERVAL_MS) {
                        lastPauseMessageAtMillis = now
                        sendControlMessage("ftms_pause", droppable = true)
                    }
                    continue
                }

                val selectedGarminId = state.selectedGarminId ?: continue
                val now = System.currentTimeMillis()
                val latest = state.latest
                if (latest == null) {
                    // No data yet: the equipment is still being connected.
                    repeatWaitMessage(now)
                    continue
                }
                val sampleAgeMillis = now - lastFtmsPacketAtMillis
                if (isSampleTooOldToSend(sampleAgeMillis)) {
                    // Do not present the last known values as live data while the equipment is
                    // silent. The watch is told that the bridge is still there, so it shows that
                    // it waits and does not take the equipment's connection for itself.
                    if (state.lastSendStatus != SEND_STATUS_NO_FRESH_DATA) {
                        diagnostics?.info(TAG_GARMIN, "send_suspended reason=stale_sample ageMs=$sampleAgeMillis")
                        _state.update { it.copy(lastSendStatus = SEND_STATUS_NO_FRESH_DATA) }
                    }
                    repeatWaitMessage(now)
                    continue
                }
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
                    droppable = true,
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

    /** Tells the watch that the bridge runs but has no data from the equipment at the moment. */
    private fun sendWaitMessage(nowMillis: Long, droppable: Boolean = false) {
        lastWaitMessageAtMillis = nowMillis
        sendControlMessage(BridgeMessage.TYPE_WAIT, droppable)
    }

    private fun repeatWaitMessage(nowMillis: Long) {
        if (nowMillis - lastWaitMessageAtMillis >= PAUSE_HEARTBEAT_INTERVAL_MS) {
            sendWaitMessage(nowMillis, droppable = true)
        }
    }

    /** A [droppable] message is a repeated one; see [GarminBridgeClient.send]. */
    private fun sendControlMessage(type: String, droppable: Boolean = false) {
        val selectedGarminId = _state.value.selectedGarminId ?: return
        val payload = BridgeMessage.controlMap(++sequence, type)
        diagnostics?.info(TAG_GARMIN, "send_control type=$type seq=$sequence device=$selectedGarminId payload=${payload.toSortedDebugText()}")
        garminClient?.send(
            deviceId = selectedGarminId,
            payload = payload,
            droppable = droppable,
        )
    }

    private fun mergeSecondarySensors(sample: FtmsSample): FtmsSample {
        if (sample.kind != FtmsMachineKind.TREADMILL) {
            return sample
        }

        return sample.withRscValues().withVendorStepRate()
    }

    /** Fills speed, distance and step rate from the treadmill's Running Speed and Cadence service. */
    private fun FtmsSample.withRscValues(): FtmsSample {
        val rsc = latestRscSample ?: return this
        val rscTimestamp = rsc.timestampMillis ?: return this
        if (System.currentTimeMillis() - rscTimestamp > SECONDARY_SENSOR_MAX_AGE_MS) {
            diagnostics?.debug(TAG_BLE, "rsc_merge skipped=stale ageMs=${System.currentTimeMillis() - rscTimestamp}")
            return this
        }

        val mergedStepRate = stepRateSpm ?: if (cadenceRpm == null) rsc.stepRateSpm else null
        if (mergedStepRate != stepRateSpm) {
            diagnostics?.info(TAG_BLE, "rsc_merge stepRate=$mergedStepRate rscAgeMs=${System.currentTimeMillis() - rscTimestamp}")
        }
        return copy(
            speedKmh = speedKmh ?: rsc.speedKmh,
            distanceM = distanceM ?: rsc.distanceM,
            stepRateSpm = mergedStepRate,
        )
    }

    /** Fills a missing step rate with the one worked out from the treadmill's own step count. */
    private fun FtmsSample.withVendorStepRate(): FtmsSample {
        if (stepRateSpm != null || cadenceRpm != null) {
            return this
        }
        val (rate, workedOutAt) = latestVendorStepRate ?: return this
        if (System.currentTimeMillis() - workedOutAt > SECONDARY_SENSOR_MAX_AGE_MS) {
            return this
        }
        return copy(stepRateSpm = rate)
    }

    /** Notes every few seconds whether the values chosen from the watch actually arrive. */
    private fun logWatchSourceState(sample: FtmsSample, nowMillis: Long) {
        val current = _state.value
        val sources = current.settings.sensorSources(current.selectedEquipmentType)
        val fromWatch = SourcedMetric.entries.filter { sources.of(it) == ValueSource.WATCH }
        if (fromWatch.isEmpty() || nowMillis - lastGarminHeartRateDiagnosticAtMillis < GARMIN_HEART_RATE_DIAGNOSTIC_INTERVAL_MS) {
            return
        }

        val watch = latestWatchReadings
        val message = when {
            watch == null -> "waiting for watch values"
            watch.freshAt(nowMillis) == null -> "watch values stale ageMs=${nowMillis - watch.timestampMillis}"
            else -> "watch ${describeReadings(sample.watch)} ageMs=${nowMillis - watch.timestampMillis}"
        }

        lastGarminHeartRateDiagnosticAtMillis = nowMillis
        diagnostics?.info(TAG_GARMIN, "watch_source used=${fromWatch.joinToString("+") { it.name }} $message")
        BridgeLogger.debug(TAG_GARMIN, "Watch source: $message")
        _state.update { it.copy(garminLastMessage = message) }
    }

    private fun describeReadings(readings: SensorReadings?): String =
        "hr=${readings?.heartRateBpm} power=${readings?.powerW} cadence=${readings?.cadenceRpm}"

    private fun startPacketWatchdog() {
        packetWatchdogJob?.cancel()
        packetWatchdogJob = scope.launch {
            while (true) {
                delay(5_000)
                val current = _state.value
                if (!current.running || current.paused || !current.treadmillConnected) {
                    continue
                }

                val timeoutMs = current.settings.packetTimeoutMillis
                val expired = isPacketWatchdogExpired(
                    nowMillis = System.currentTimeMillis(),
                    connectedAtMillis = treadmillConnectedAtMillis,
                    lastPacketAtMillis = lastFtmsPacketAtMillis,
                    timeoutMillis = timeoutMs,
                )
                if (expired) {
                    diagnostics?.warn(
                        TAG_RUNTIME,
                        "packet_watchdog_timeout timeoutMs=$timeoutMs connectedAt=$treadmillConnectedAtMillis lastPacketAt=$lastFtmsPacketAtMillis",
                    )
                    recordError("No treadmill packet for ${timeoutMs / 1000}s")
                    // disconnect() reports the link as down, which keeps this check from firing
                    // again until the next connection.
                    treadmillClient?.disconnect()
                    if (_state.value.settings.autoReconnect) {
                        scheduleReconnect("packet timeout")
                    }
                }
            }
        }
    }

    /**
     * Ends the session once the equipment has come to a stop. While the watch reports an activity
     * of its own, the watch decides instead (see [onWatchTimer]).
     */
    private fun startAutoStopWatch() {
        autoStopJob?.cancel()
        autoStopJob = scope.launch {
            while (true) {
                delay(AUTO_STOP_CHECK_INTERVAL_MS)
                val current = _state.value
                if (!current.running || !isAutoStopWanted(current.settings, current.selectedEquipmentType)) {
                    continue
                }

                val now = System.currentTimeMillis()
                if (current.settings.followWatchTimer && isWatchActivityOngoing(watchTimer, now)) {
                    continue
                }

                val stoppedSinceMillis = autoStopPolicy.stopDueSince(now) ?: continue
                log("Auto-stop: ${current.selectedTreadmillName ?: current.selectedTreadmillAddress} has stopped")
                diagnostics?.info(TAG_RUNTIME, "auto_stop stoppedSince=$stoppedSinceMillis waitedMs=${now - stoppedSinceMillis}")
                // Ending the session cancels this job, so nothing may follow.
                endSession(endedAtMillis = stoppedSinceMillis, onStopped = null)
                return@launch
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
            // Clear the job first: a connect attempt that fails right away must be able to
            // schedule the next retry.
            reconnectJob = null
            treadmillClient?.connect(address)
        }
    }

    private fun observeAutoStart() {
        scope.launch {
            // The equipment type is part of the key: a standby connection is set up for the type,
            // so changing it has to connect anew.
            combine(_state, appVisible) { state, visible ->
                autoStartWatchAddress(state, visible)?.let { address -> address to state.selectedEquipmentType }
            }
                .distinctUntilChanged()
                .collectLatest { watch -> runAutoStart(watch?.first) }
        }
    }

    /** Opens the running sensor for the watch while it is wanted, and closes it otherwise. */
    private fun observeFootPod() {
        scope.launch {
            _state.map { isFootPodWanted(it.running, it.settings, it.selectedEquipmentType) }
                .distinctUntilChanged()
                .collect { wanted ->
                    if (wanted) {
                        footPodFeed.reset()
                        footPod?.start()
                    } else {
                        footPod?.stop()
                    }
                }
        }
    }

    private fun publishToFootPod(sample: SmoothedTreadmillSample, paused: Boolean, nowMillis: Long) {
        val pod = footPod?.takeIf { it.isActive } ?: return
        val measurement = footPodFeed.next(sample, paused, nowMillis) ?: return
        pod.publish(measurement)
    }

    override fun onFootPodStatus(status: FootPodStatus) {
        _state.update { it.copy(footPodStatus = status) }
        log("Running sensor for the watch: $status")
        diagnostics?.info(TAG_BLE, "foot_pod status=$status")
    }

    override fun onFootPodDiagnostic(message: String) {
        BridgeLogger.debug(TAG_BLE, message)
        diagnostics?.info(TAG_BLE, message)
    }

    /** Only an indoor bike is given commands; other equipment is read and nothing else. */
    private fun observeEquipmentType() {
        scope.launch {
            _state.map { it.selectedEquipmentType }
                .distinctUntilChanged()
                .collect { type -> treadmillClient?.controlPointWanted = type == EquipmentType.INDOOR_BIKE }
        }
    }

    /**
     * Auto-start for the equipment at [address], until cancelled: look for it, connect once it is
     * switched on, and wait there for movement. With null there is nothing to do.
     */
    private suspend fun runAutoStart(address: String?) {
        val client = treadmillClient
        if (address == null || client == null) {
            setAutoStartStatus(AutoStartStatus.OFF)
            return
        }

        while (true) {
            awaitEquipmentSighting(client, address)
            standByForMovement(client, address)
            setAutoStartStatus(AutoStartStatus.WAITING)
            delay(AUTO_START_RECONNECT_DELAY_MS)
        }
    }

    /** Scans until the equipment at [address] advertises, which it only does while switched on. */
    private suspend fun awaitEquipmentSighting(client: TreadmillBleClient, address: String) {
        while (true) {
            val seen = CompletableDeferred<Unit>()
            equipmentSeen = seen
            presenceWatchFailed = false
            val watching = client.startPresenceWatch(address)
            if (watching) {
                autoStartPolicy.onWatchStarted(SystemClock.elapsedRealtime())
            }
            setAutoStartStatus(if (watching) AutoStartStatus.WAITING else AutoStartStatus.OFF)
            try {
                // Android turns a scan into a passive one after 30 minutes, so a running watch is
                // restarted well before that. A watch that could not start is retried sooner, but
                // not so soon that Android's limit on scan starts is hit.
                val roundEndsAtMillis = SystemClock.elapsedRealtime() +
                    if (watching) AUTO_START_WATCH_RESTART_MS else AUTO_START_WATCH_RETRY_MS
                while (SystemClock.elapsedRealtime() < roundEndsAtMillis && !(watching && presenceWatchFailed)) {
                    if (withTimeoutOrNull(AUTO_START_STATUS_INTERVAL_MS) { seen.await() } != null) {
                        return
                    }
                }
            } finally {
                equipmentSeen = null
                client.stopPresenceWatch()
            }
            if (watching && presenceWatchFailed) {
                setAutoStartStatus(AutoStartStatus.OFF)
                delay(AUTO_START_WATCH_RETRY_MS)
            }
        }
    }

    /**
     * Connects to the equipment without a session and returns when that connection ends. Movement
     * reported meanwhile starts the bridge (see [onStandbyPacket]), which takes the connection over.
     */
    private suspend fun standByForMovement(client: TreadmillBleClient, address: String) {
        val ended = CompletableDeferred<Unit>()
        standbyEnded = ended
        standbyActive = true
        standbyLastPacketAtMillis = 0L
        var connectedSinceMillis: Long? = null
        diagnostics?.info(TAG_RUNTIME, "standby_started address=$address")
        client.connect(address)
        try {
            while (!ended.isCompleted) {
                val now = SystemClock.elapsedRealtime()
                val connected = client.isConnectedTo(address)
                if (!connected) {
                    connectedSinceMillis = null
                } else if (connectedSinceMillis == null) {
                    connectedSinceMillis = now
                }
                val quietSinceMillis = connectedSinceMillis?.let { maxOf(it, standbyLastPacketAtMillis) }
                if (quietSinceMillis != null && now - quietSinceMillis >= AUTO_START_SILENCE_MEANS_STANDSTILL_MS) {
                    // Equipment that sends nothing while connected is not being used.
                    autoStartPolicy.onMovementReported(moving = false, nowMillis = now)
                }
                refreshStandbyStatus(connected)
                withTimeoutOrNull(AUTO_START_STATUS_INTERVAL_MS) { ended.await() }
            }
        } finally {
            standbyEnded = null
            // A session that is starting keeps the connection; in every other case it is given up.
            if (!_state.value.running) {
                standbyActive = false
                client.disconnect()
            }
        }
    }

    private fun onStandbyPacket(kind: Int, bytes: ByteArray) {
        val now = SystemClock.elapsedRealtime()
        standbyLastPacketAtMillis = now
        if (_state.value.running) {
            // A session is being created and takes the connection over in a moment.
            return
        }

        val sample = FtmsParser.parse(kind, bytes, System.currentTimeMillis())
        val startDue = autoStartPolicy.onMovementReported(isEquipmentMoving(sample), now)
        refreshStandbyStatus(connected = true)
        if (!startDue) {
            return
        }

        val context = appContext ?: return
        val state = _state.value
        if (autoStartWatchAddress(state, appVisible.value) == null || !isBridgeStartPossible(state)) {
            return
        }

        // One attempt per movement, whether or not the start succeeds.
        autoStartPolicy.holdUntilStandstill(now)
        log("Auto-start: ${state.selectedTreadmillName ?: state.selectedTreadmillAddress} is moving")
        startBridge(context)
    }

    /** The session that just started continues on the connection auto-start already had. */
    private fun onStandbyConnectionAdopted() {
        treadmillConnectedAtMillis = System.currentTimeMillis()
        awaitingFirstPacketAfterConnect = true
        merger.reset()
        _state.update {
            it.copy(treadmillConnected = true, reconnectStatus = "connected", nextRetryDelayMs = 0)
        }
        diagnostics?.info(TAG_BLE, "connection_adopted_from_standby")
    }

    private fun refreshStandbyStatus(connected: Boolean) {
        if (_state.value.running) {
            return
        }

        setAutoStartStatus(
            when {
                !connected -> AutoStartStatus.WAITING
                autoStartPolicy.isHolding(SystemClock.elapsedRealtime()) -> AutoStartStatus.HOLDING
                else -> AutoStartStatus.READY
            },
        )
    }

    private fun setAutoStartStatus(status: AutoStartStatus) {
        if (_state.value.autoStartStatus != status) {
            _state.update { it.copy(autoStartStatus = status) }
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

    /** The sample with the session's own distance, ascent and time in place of the equipment's totals. */
    private fun SmoothedTreadmillSample.forActiveSession(): SmoothedTreadmillSample {
        val sourceTimestamp = raw.timestampMillis ?: System.currentTimeMillis()
        val activeTimestamp = activeSessionStartedAtMillis + activeDurationAt(sourceTimestamp)
        return copy(
            raw = raw.copy(timestampMillis = activeTimestamp),
            distanceM = odometer.distanceM,
            ascentM = odometer.ascentM,
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
        val preview = take(maxBytes).joinToString(" ") { byte -> String.format(java.util.Locale.ROOT, "%02X", byte) }
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
    private const val SEND_STATUS_NO_FRESH_DATA = "no fresh data"
    private const val PAUSE_HEARTBEAT_INTERVAL_MS = 2_000L
}

internal const val MAX_SENDABLE_SAMPLE_AGE_MS = 5_000L

/** True when the newest equipment sample is too old to be forwarded to the watch as live data. */
private const val AUTO_START_WATCH_RESTART_MS = 15 * 60_000L
private const val AUTO_START_WATCH_RETRY_MS = 30_000L
private const val AUTO_START_STATUS_INTERVAL_MS = 5_000L
// Long enough that repeated short-lived connections stay below Android's limit on scan starts.
private const val AUTO_START_RECONNECT_DELAY_MS = 8_000L
private const val AUTO_START_SILENCE_MEANS_STANDSTILL_MS = 15_000L
private const val TARGET_POWER_ANSWER_TIMEOUT_MS = 6_000L

internal fun isSampleTooOldToSend(sampleAgeMillis: Long): Boolean =
    sampleAgeMillis > MAX_SENDABLE_SAMPLE_AGE_MS

/**
 * True when a connected machine has delivered no FTMS packet for longer than [timeoutMillis].
 *
 * The silence is measured from the later of the connection time and the last packet, so a link
 * that never delivers its first packet is detected as well, and a timestamp left over from an
 * earlier connection cannot trigger the check right after a reconnect.
 */
internal fun isPacketWatchdogExpired(
    nowMillis: Long,
    connectedAtMillis: Long,
    lastPacketAtMillis: Long,
    timeoutMillis: Long,
): Boolean {
    val silentSinceMillis = maxOf(connectedAtMillis, lastPacketAtMillis)
    if (silentSinceMillis <= 0L) {
        return false
    }

    return nowMillis - silentSinceMillis > timeoutMillis
}

internal fun shouldStopBridgeForegroundService(running: Boolean, activeSessionStartedAtMillis: Long): Boolean =
    !running && activeSessionStartedAtMillis <= 0

internal fun FtmsSample.withResolvedPower(
    settings: BridgeSettings,
    equipmentType: EquipmentType = EquipmentType.TREADMILL,
): FtmsSample {
    // The estimate models running on a belt; other equipment keeps the power it reports.
    if (!settings.powerCalculationEnabled || equipmentType != EquipmentType.TREADMILL) {
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
