package de.ma.ftms.bridge.garmin

import android.content.Context
import android.os.SystemClock
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import de.ma.ftms.bridge.debug.BridgeLogger
import de.ma.ftms.bridge.runtime.GarminConnectProblem
import de.ma.ftms.bridge.runtime.GarminDeviceItem
import de.ma.ftms.bridge.runtime.WATCH_APP_ID
import de.ma.ftms.bridge.runtime.WATCH_APP_IDS
import de.ma.ftms.bridge.runtime.WatchTimerState
import de.ma.ftms.core.SensorReadings

internal class GarminBridgeClient(
    private val context: Context,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onDevicesLoaded(devices: List<GarminDeviceItem>)
        fun onDeviceUpdated(device: GarminDeviceItem)
        fun onDiscoveryStatusChanged(sdkStatus: String, knownDeviceCount: Int, message: String)

        /** Reports why the SDK cannot reach Garmin Connect, or null once it can. */
        fun onGarminConnectProblem(problem: GarminConnectProblem?)
        /** Heart rate, power and cadence as the watch measures them, sent by the data field. */
        fun onWatchSensors(deviceId: Long, readings: SensorReadings)

        /** State of the activity timer on the watch, sent by the data field. */
        fun onWatchTimer(deviceId: Long, state: WatchTimerState)
        fun onSendStatus(status: String)
        fun log(message: String)
    }

    private var connectIQ: ConnectIQ? = null
    private var initialized = false
    private var initializationFailed = false
    private var knownDeviceCount = 0
    private val devicesById = mutableMapOf<Long, IQDevice>()
    private val installedAppIdsByDeviceId = mutableMapOf<Long, Set<String>>()
    private val registeredAppEventIdsByDeviceId = mutableMapOf<Long, Set<String>>()
    private val sendGate = SendGate()

    fun initialize() {
        if (connectIQ != null) {
            return
        }

        val sdk = ConnectIQ.getInstance(context, ConnectIQ.IQConnectType.WIRELESS)
        connectIQ = sdk
        startSdk(sdk)
    }

    /**
     * Initializes the SDK again after a failed attempt, for example once the user has installed or
     * updated Garmin Connect. Does nothing while the SDK is ready or was never started.
     */
    fun retryInitializationIfFailed() {
        val sdk = connectIQ ?: return
        if (initialized || !initializationFailed) {
            return
        }

        BridgeLogger.debug(TAG, "Retrying Connect IQ initialization")
        // A failed attempt leaves the SDK's broadcast receiver registered; shut down to release it
        // before the next attempt registers a new one.
        runCatching { sdk.shutdown(context) }
            .onFailure { BridgeLogger.warn(TAG, "Connect IQ shutdown before retry failed: $it") }
        startSdk(sdk)
    }

    private fun startSdk(sdk: ConnectIQ) {
        initialized = false
        initializationFailed = false
        devicesById.clear()
        installedAppIdsByDeviceId.clear()
        registeredAppEventIdsByDeviceId.clear()
        sendGate.clear()
        BridgeLogger.debug(TAG, "Initializing Connect IQ companion SDK")
        publishDiscoveryStatus("initializing", knownDeviceCount = 0, message = "Initializing Connect IQ companion SDK")

        val listener = object : ConnectIQ.ConnectIQListener {
            override fun onSdkReady() {
                initialized = true
                initializationFailed = false
                BridgeLogger.info(TAG, "Connect IQ SDK ready")
                callbacks.onGarminConnectProblem(null)
                publishDiscoveryStatus("ready", message = "Connect IQ SDK ready")
                callbacks.log("Connect IQ SDK ready")
                refreshDevices()
            }

            override fun onInitializeError(errStatus: ConnectIQ.IQSdkErrorStatus) {
                reportInitializationFailure(garminConnectProblemFor(errStatus), "Connect IQ init error $errStatus")
            }

            override fun onSdkShutDown() {
                initialized = false
                BridgeLogger.info(TAG, "Connect IQ SDK shut down")
                publishDiscoveryStatus("shut down", knownDeviceCount = 0, message = "Connect IQ SDK shut down")
                callbacks.log("Connect IQ SDK shut down")
            }
        }

        try {
            // autoUI stays off: the SDK's own "Garmin Connect missing" dialog leaves an invisible
            // activity on top of the app after "Cancel", which blocks all input until Back is
            // pressed. The app shows the problem itself instead.
            sdk.initialize(context, false, listener)
        } catch (error: RuntimeException) {
            reportInitializationFailure(GarminConnectProblem.SERVICE_ERROR, "Connect IQ init failed: $error")
        }
    }

    private fun reportInitializationFailure(problem: GarminConnectProblem, message: String) {
        initialized = false
        initializationFailed = true
        BridgeLogger.warn(TAG, message)
        callbacks.onGarminConnectProblem(problem)
        publishDiscoveryStatus("init error", knownDeviceCount = 0, message = message)
        callbacks.log(message)
    }

    fun refreshDevices() {
        val sdk = connectIQ
        if (sdk != null && !initialized && initializationFailed) {
            // Refreshing is how the user asks to try again after fixing Garmin Connect.
            retryInitializationIfFailed()
            return
        }
        if (!initialized || sdk == null) {
            BridgeLogger.debug(TAG, "Refresh requested before SDK ready")
            publishDiscoveryStatus("not ready", knownDeviceCount = 0, message = "Refresh requested before SDK ready")
            callbacks.log("Connect IQ SDK not ready")
            return
        }

        publishDiscoveryStatus("loading", message = "Loading Garmin knownDevices")
        val devices = try {
            sdk.knownDevices ?: emptyList()
        } catch (_: InvalidStateException) {
            BridgeLogger.warn(TAG, "Connect IQ invalid state while loading devices")
            publishDiscoveryStatus("invalid state", knownDeviceCount = 0, message = "Connect IQ invalid state while loading devices")
            callbacks.log("Connect IQ invalid state")
            emptyList()
        } catch (_: ServiceUnavailableException) {
            BridgeLogger.warn(TAG, "Garmin Connect service unavailable while loading devices")
            publishDiscoveryStatus(
                "service unavailable",
                knownDeviceCount = 0,
                message = "Garmin Connect service unavailable while loading devices",
            )
            callbacks.log("Garmin Connect service unavailable")
            emptyList()
        }

        knownDeviceCount = devices.size
        devicesById.clear()
        installedAppIdsByDeviceId.clear()
        devices.forEach { device ->
            devicesById[device.deviceIdentifier] = device
            runCatching {
                device.status = sdk.getDeviceStatus(device)
                sdk.unregisterForDeviceEvents(device)
                sdk.registerForDeviceEvents(device) { changedDevice, status ->
                    BridgeLogger.debug(TAG, "Device event id=${changedDevice.deviceIdentifier} status=$status")
                    changedDevice.status = status
                    callbacks.onDeviceUpdated(changedDevice.toUiItem(appInstalled = null))
                }
            }
        }

        callbacks.onDevicesLoaded(devices.map { it.toUiItem(appInstalled = null) })
        devices.forEach { verifyAppsInstalled(sdk, it) }
        BridgeLogger.debug(TAG, "Loaded ${devices.size} Garmin device(s)")
        publishDiscoveryStatus("loaded", knownDeviceCount = devices.size, message = "Loaded ${devices.size} Garmin device(s)")
        callbacks.log("Loaded ${devices.size} Garmin device(s)")
    }

    /**
     * Sends [payload] to the data field on the watch. A [droppable] message, one that a newer one
     * follows soon anyway, is left out for a variant of the data field that has not answered the
     * previous message yet or has just failed (see [SendGate]).
     */
    fun send(deviceId: Long, payload: Map<String, Any>, droppable: Boolean = false) {
        val sdk = connectIQ
        val device = devicesById[deviceId]
        if (!initialized || sdk == null || device == null) {
            BridgeLogger.warn(TAG, "Cannot send: initialized=$initialized sdk=${sdk != null} device=${device != null}")
            callbacks.onSendStatus("garmin unavailable")
            return
        }

        val payloadType = payload["type"]?.toString().orEmpty()
        val sendKind = if (payloadType == "ftms_stop") "stop" else "sample"

        val now = SystemClock.elapsedRealtime()
        val appIdsByTarget = compatibleAppIdsForSend(installedAppIdsByDeviceId[deviceId])
            .associateBy { sendTarget(deviceId, it) }
        // A message that must arrive goes to every variant; a sample only to the one that runs.
        val targets = appIdsByTarget.keys.toList().let { if (droppable) sendGate.liveAmong(it, now) else it }
        targets.forEach { target ->
            val appId = appIdsByTarget.getValue(target)
            if (droppable) {
                if (!sendGate.tryAcquire(target, now)) {
                    return@forEach
                }
            } else {
                sendGate.onSentRegardless(target, now)
            }
            try {
                BridgeLogger.debug(TAG, "Sending message deviceId=$deviceId appId=${appId.take(8)} payload=$payload")
                sdk.sendMessage(device, IQApp(appId), payload) { _, _, status ->
                    sendGate.onResult(
                        target,
                        delivered = status == ConnectIQ.IQMessageStatus.SUCCESS,
                        nowMillis = SystemClock.elapsedRealtime(),
                    )
                    callbacks.onSendStatus(status.name)
                    BridgeLogger.debug(TAG, "Send callback appId=${appId.take(8)} status=$status")
                    callbacks.log("Garmin $sendKind ack ${appId.take(8)} $status")
                }
            } catch (_: InvalidStateException) {
                sendGate.onResult(target, delivered = false, nowMillis = now)
                BridgeLogger.warn(TAG, "Send failed invalid state appId=${appId.take(8)}")
                callbacks.onSendStatus("invalid state")
            } catch (_: ServiceUnavailableException) {
                sendGate.onResult(target, delivered = false, nowMillis = now)
                BridgeLogger.warn(TAG, "Send failed service unavailable appId=${appId.take(8)}")
                callbacks.onSendStatus("service unavailable")
            }
        }
    }

    private fun verifyAppsInstalled(sdk: ConnectIQ, device: IQDevice) {
        installedAppIdsByDeviceId[device.deviceIdentifier] = emptySet()
        callbacks.onDeviceUpdated(device.toUiItem(appInstalled = false))

        WATCH_APP_IDS.forEach { appId ->
            try {
                BridgeLogger.debug(TAG, "Checking app install deviceId=${device.deviceIdentifier} appId=${appId.take(8)}")
                sdk.getApplicationInfo(
                    appId,
                    device,
                    object : ConnectIQ.IQApplicationInfoListener {
                        override fun onApplicationInfoReceived(app: IQApp) {
                            BridgeLogger.debug(TAG, "App info appId=${appId.take(8)} status=${app.status}")
                            if (app.status == IQApp.IQAppStatus.INSTALLED) {
                                installedAppIdsByDeviceId[device.deviceIdentifier] =
                                    installedAppIdsByDeviceId.orEmptySet(device.deviceIdentifier) + appId
                                registerForAppEvents(sdk, device, appId)
                                callbacks.onDeviceUpdated(device.toUiItem(appInstalled = true))
                            }
                        }

                        override fun onApplicationNotInstalled(applicationId: String) {
                            BridgeLogger.debug(TAG, "App not installed appId=${applicationId.take(8)}")
                            if (installedAppIdsByDeviceId.orEmptySet(device.deviceIdentifier).isEmpty()) {
                                callbacks.onDeviceUpdated(device.toUiItem(appInstalled = false))
                            }
                        }
                    },
                )
            } catch (_: InvalidStateException) {
                BridgeLogger.warn(TAG, "App install check invalid state appId=${appId.take(8)}")
                if (installedAppIdsByDeviceId.orEmptySet(device.deviceIdentifier).isEmpty()) {
                    callbacks.onDeviceUpdated(device.toUiItem(appInstalled = false))
                }
            } catch (_: ServiceUnavailableException) {
                BridgeLogger.warn(TAG, "App install check service unavailable appId=${appId.take(8)}")
                if (installedAppIdsByDeviceId.orEmptySet(device.deviceIdentifier).isEmpty()) {
                    callbacks.onDeviceUpdated(device.toUiItem(appInstalled = false))
                }
            }
        }
    }

    private fun Map<Long, Set<String>>.orEmptySet(deviceId: Long): Set<String> =
        this[deviceId] ?: emptySet()

    private fun registerForAppEvents(sdk: ConnectIQ, device: IQDevice, appId: String) {
        val deviceId = device.deviceIdentifier
        if (appId in registeredAppEventIdsByDeviceId.orEmptySet(deviceId)) {
            return
        }

        try {
            sdk.registerForAppEvents(
                device,
                IQApp(appId),
                object : ConnectIQ.IQApplicationEventListener {
                    override fun onMessageReceived(
                        device: IQDevice,
                        app: IQApp,
                        message: List<Any>,
                        status: ConnectIQ.IQMessageStatus,
                    ) {
                        if (status != ConnectIQ.IQMessageStatus.SUCCESS) {
                            BridgeLogger.debug(TAG, "App message status=$status appId=${app.applicationId.take(8)}")
                            return
                        }

                        // A variant that sends runs in the activity, so it is written to at once.
                        sendGate.onHeardFrom(
                            sendTarget(device.deviceIdentifier, app.applicationId),
                            SystemClock.elapsedRealtime(),
                        )
                        // The readings go first: a timer state may start a session, which then
                        // already knows what the watch measures.
                        val readings = parseWatchSensorMessage(message)
                        if (readings != null) {
                            callbacks.onWatchSensors(device.deviceIdentifier, readings)
                        }
                        val timer = parseWatchTimerState(message)
                        if (timer != null) {
                            callbacks.onWatchTimer(device.deviceIdentifier, timer)
                        }
                        BridgeLogger.debug(
                            TAG,
                            "Watch message deviceId=${device.deviceIdentifier} readings=$readings timer=$timer",
                        )
                    }
                },
            )
            registeredAppEventIdsByDeviceId[deviceId] = registeredAppEventIdsByDeviceId.orEmptySet(deviceId) + appId
            BridgeLogger.debug(TAG, "Registered app event listener deviceId=$deviceId appId=${appId.take(8)}")
        } catch (_: InvalidStateException) {
            BridgeLogger.warn(TAG, "App event registration invalid state appId=${appId.take(8)}")
        } catch (_: ServiceUnavailableException) {
            BridgeLogger.warn(TAG, "App event registration service unavailable appId=${appId.take(8)}")
        }
    }

    private fun IQDevice.toUiItem(appInstalled: Boolean?): GarminDeviceItem =
        GarminDeviceItem(
            id = deviceIdentifier,
            name = friendlyName ?: "Garmin $deviceIdentifier",
            status = status?.name ?: "UNKNOWN",
            appInstalled = appInstalled,
        )

    private fun publishDiscoveryStatus(
        sdkStatus: String,
        knownDeviceCount: Int = this.knownDeviceCount,
        message: String,
    ) {
        this.knownDeviceCount = knownDeviceCount
        callbacks.onDiscoveryStatusChanged(sdkStatus, knownDeviceCount, message)
    }
}

/**
 * Reads the data field's sensor message: heart rate ("hr"), power ("pwr") and cadence ("cad"),
 * each optional. Returns null for other messages and for one without a usable value.
 */
internal fun parseWatchSensorMessage(message: List<Any?>): SensorReadings? {
    val payload = watchMessagePayload(message) ?: return null
    return SensorReadings.ofNotEmpty(
        powerW = payload["pwr"].toIntIn(0..3000),
        heartRateBpm = payload["hr"].toIntIn(1..255),
        cadenceRpm = payload["cad"].toIntIn(0..300)?.toDouble(),
    )
}

/**
 * Reads the state of the watch's activity timer ("timer") from the data field's message. Returns
 * null for other messages and for data fields from before the timer was reported.
 */
internal fun parseWatchTimerState(message: List<Any?>): WatchTimerState? {
    val payload = watchMessagePayload(message) ?: return null
    return WatchTimerState.fromWire(payload["timer"].toIntIn(0..3))
}

/** The content of a message of the data field's own type, or null for anything else. */
private fun watchMessagePayload(message: List<Any?>): Map<*, *>? =
    message.asSequence()
        .mapNotNull { it as? Map<*, *> }
        .firstOrNull()
        ?.takeIf { it["type"] == WATCH_SENSOR_MESSAGE_TYPE }

private fun Any?.toIntIn(range: IntRange): Int? {
    val value = when (this) {
        is Number -> toInt()
        is String -> toIntOrNull()
        else -> null
    }
    return value?.takeIf { it in range }
}

internal fun garminConnectProblemFor(status: ConnectIQ.IQSdkErrorStatus): GarminConnectProblem =
    when (status) {
        ConnectIQ.IQSdkErrorStatus.GCM_NOT_INSTALLED -> GarminConnectProblem.NOT_INSTALLED
        ConnectIQ.IQSdkErrorStatus.GCM_UPGRADE_NEEDED -> GarminConnectProblem.UPGRADE_NEEDED
        ConnectIQ.IQSdkErrorStatus.SERVICE_ERROR -> GarminConnectProblem.SERVICE_ERROR
    }

internal fun compatibleAppIdsForSend(installedAppIds: Set<String>?): List<String> {
    val installed = installedAppIds.orEmpty()
    return WATCH_APP_IDS.filter { it in installed }.ifEmpty { listOf(WATCH_APP_ID) }
}

// Named after its first content; kept so data fields and apps of different versions understand each other.
private const val WATCH_SENSOR_MESSAGE_TYPE = "garmin_hr"
private const val TAG = "GarminCiq"
