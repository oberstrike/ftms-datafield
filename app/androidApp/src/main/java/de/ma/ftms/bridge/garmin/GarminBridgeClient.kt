package de.ma.ftms.bridge.garmin

import android.content.Context
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import de.ma.ftms.bridge.debug.BridgeLogger
import de.ma.ftms.bridge.runtime.GarminDeviceItem
import de.ma.ftms.bridge.runtime.WATCH_APP_ID
import de.ma.ftms.bridge.runtime.WATCH_VARIANT_APP_IDS

internal class GarminBridgeClient(
    private val context: Context,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onDevicesLoaded(devices: List<GarminDeviceItem>)
        fun onDeviceUpdated(device: GarminDeviceItem)
        fun onDiscoveryStatusChanged(sdkStatus: String, knownDeviceCount: Int, message: String)
        fun onSendStatus(status: String)
        fun log(message: String)
    }

    private var connectIQ: ConnectIQ? = null
    private var initialized = false
    private var knownDeviceCount = 0
    private val devicesById = mutableMapOf<Long, IQDevice>()
    private val installedAppIdsByDevice = mutableMapOf<Long, MutableSet<String>>()

    fun initialize() {
        if (connectIQ != null) {
            return
        }

        connectIQ = ConnectIQ.getInstance(context, ConnectIQ.IQConnectType.WIRELESS)
        BridgeLogger.debug(TAG, "Initializing Connect IQ companion SDK")
        publishDiscoveryStatus("initializing", message = "Initializing Connect IQ companion SDK")
        connectIQ?.initialize(
            context,
            true,
            object : ConnectIQ.ConnectIQListener {
                override fun onSdkReady() {
                    initialized = true
                    BridgeLogger.info(TAG, "Connect IQ SDK ready")
                    publishDiscoveryStatus("ready", message = "Connect IQ SDK ready")
                    callbacks.log("Connect IQ SDK ready")
                    refreshDevices()
                }

                override fun onInitializeError(errStatus: ConnectIQ.IQSdkErrorStatus) {
                    initialized = false
                    knownDeviceCount = 0
                    BridgeLogger.warn(TAG, "Connect IQ init error $errStatus")
                    publishDiscoveryStatus("init error", knownDeviceCount = 0, message = "Connect IQ init error $errStatus")
                    callbacks.log("Connect IQ init error $errStatus")
                }

                override fun onSdkShutDown() {
                    initialized = false
                    knownDeviceCount = 0
                    BridgeLogger.info(TAG, "Connect IQ SDK shut down")
                    publishDiscoveryStatus("shut down", knownDeviceCount = 0, message = "Connect IQ SDK shut down")
                    callbacks.log("Connect IQ SDK shut down")
                }
            },
        )
    }

    fun refreshDevices() {
        val sdk = connectIQ
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

    fun send(deviceId: Long, payload: Map<String, Any>, sendToAllInstalledVariants: Boolean) {
        val sdk = connectIQ
        val device = devicesById[deviceId]
        if (!initialized || sdk == null || device == null) {
            BridgeLogger.warn(TAG, "Cannot send: initialized=$initialized sdk=${sdk != null} device=${device != null}")
            callbacks.onSendStatus("garmin unavailable")
            return
        }

        val appIds = if (sendToAllInstalledVariants) {
            installedAppIdsByDevice[deviceId]?.takeIf { it.isNotEmpty() } ?: WATCH_VARIANT_APP_IDS.toSet()
        } else {
            setOf(WATCH_APP_ID)
        }
        val payloadType = payload["type"]?.toString().orEmpty()
        val sendKind = if (payloadType == "ftms_stop") "stop" else "sample"

        appIds.forEach { appId ->
            try {
                BridgeLogger.debug(TAG, "Sending message deviceId=$deviceId appId=${appId.take(8)} payload=$payload")
                sdk.sendMessage(device, IQApp(appId), payload) { _, _, status ->
                    callbacks.onSendStatus(status.name)
                    BridgeLogger.debug(TAG, "Send callback appId=${appId.take(8)} status=$status")
                    callbacks.log("Garmin $sendKind ack ${appId.take(8)} $status")
                }
            } catch (_: InvalidStateException) {
                BridgeLogger.warn(TAG, "Send failed invalid state appId=${appId.take(8)}")
                callbacks.onSendStatus("invalid state")
            } catch (_: ServiceUnavailableException) {
                BridgeLogger.warn(TAG, "Send failed service unavailable appId=${appId.take(8)}")
                callbacks.onSendStatus("service unavailable")
            }
        }
    }

    private fun verifyAppsInstalled(sdk: ConnectIQ, device: IQDevice) {
        installedAppIdsByDevice[device.deviceIdentifier] = mutableSetOf()
        callbacks.onDeviceUpdated(device.toUiItem(appInstalled = false))

        WATCH_VARIANT_APP_IDS.forEach { appId ->
            try {
                BridgeLogger.debug(TAG, "Checking app install deviceId=${device.deviceIdentifier} appId=${appId.take(8)}")
                sdk.getApplicationInfo(
                    appId,
                    device,
                    object : ConnectIQ.IQApplicationInfoListener {
                        override fun onApplicationInfoReceived(app: IQApp) {
                            BridgeLogger.debug(TAG, "App info appId=${appId.take(8)} status=${app.status}")
                            if (app.status == IQApp.IQAppStatus.INSTALLED) {
                                installedAppIdsByDevice.getOrPut(device.deviceIdentifier) { mutableSetOf() }.add(appId)
                                callbacks.onDeviceUpdated(device.toUiItem(appInstalled = true))
                            }
                        }

                        override fun onApplicationNotInstalled(applicationId: String) {
                            BridgeLogger.debug(TAG, "App not installed appId=${applicationId.take(8)}")
                            val hasAny = installedAppIdsByDevice[device.deviceIdentifier]?.isNotEmpty() == true
                            callbacks.onDeviceUpdated(device.toUiItem(appInstalled = hasAny))
                        }
                    },
                )
            } catch (_: InvalidStateException) {
                BridgeLogger.warn(TAG, "App install check invalid state appId=${appId.take(8)}")
                callbacks.onDeviceUpdated(device.toUiItem(appInstalled = false))
            } catch (_: ServiceUnavailableException) {
                BridgeLogger.warn(TAG, "App install check service unavailable appId=${appId.take(8)}")
                callbacks.onDeviceUpdated(device.toUiItem(appInstalled = false))
            }
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

private const val TAG = "GarminCiq"
