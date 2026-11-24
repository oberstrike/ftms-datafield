package de.ma.ftms.bridge.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.getSystemService
import de.ma.ftms.bridge.runtime.CCCD_UUID
import de.ma.ftms.bridge.runtime.FootPodStatus
import de.ma.ftms.bridge.runtime.RSC_MEASUREMENT_UUID
import de.ma.ftms.bridge.runtime.RSC_SERVICE_UUID
import de.ma.ftms.core.RscMeasurement
import de.ma.ftms.core.ScControlPoint
import java.util.ArrayDeque
import java.util.UUID

/**
 * Makes the phone a Bluetooth running sensor (Running Speed and Cadence service), so a watch that
 * is paired with it takes speed and distance from the treadmill instead of estimating them.
 *
 * All public functions must be called on the main thread, and every [Callbacks] function is
 * invoked on the main thread; Android's callbacks arrive on binder threads and are posted over.
 */
internal class VirtualFootPod(
    private val context: Context,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onFootPodStatus(status: FootPodStatus)
        fun onFootPodDiagnostic(message: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothManager = context.getSystemService<BluetoothManager>()
    private var server: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var measurement: BluetoothGattCharacteristic? = null
    private var controlPoint: BluetoothGattCharacteristic? = null
    private val pendingServices = ArrayDeque<BluetoothGattService>()

    /** Devices that asked for the measurements. Other connections of the phone do not count. */
    private val measurementSubscribers = LinkedHashSet<BluetoothDevice>()
    private val controlPointSubscribers = LinkedHashSet<BluetoothDevice>()
    private var status = FootPodStatus.OFF

    val isActive: Boolean
        get() = server != null

    /** Opens the sensor and makes it visible. Does nothing if it is already open. */
    @SuppressLint("MissingPermission")
    fun start() {
        if (server != null) {
            return
        }

        if (!hasAdvertisePermission(context)) {
            setStatus(FootPodStatus.PERMISSION_MISSING)
            return
        }

        val manager = bluetoothManager
        val adapter = manager?.adapter
        if (manager == null || adapter == null || !adapter.isEnabled) {
            callbacks.onFootPodDiagnostic("foot_pod start refused=bluetooth_off")
            setStatus(FootPodStatus.FAILED)
            return
        }

        val leAdvertiser = adapter.bluetoothLeAdvertiser
        if (leAdvertiser == null || !adapter.isMultipleAdvertisementSupported) {
            callbacks.onFootPodDiagnostic("foot_pod start refused=advertising_unsupported")
            setStatus(FootPodStatus.UNSUPPORTED)
            return
        }

        val opened = runCatching { manager.openGattServer(context, serverCallback) }
            .onFailure { callbacks.onFootPodDiagnostic("foot_pod open_failed error=${it.message}") }
            .getOrNull()
        if (opened == null) {
            setStatus(FootPodStatus.FAILED)
            return
        }

        server = opened
        advertiser = leAdvertiser
        pendingServices.clear()
        pendingServices.add(runningSpeedService())
        pendingServices.add(deviceInformationService())
        pendingServices.add(batteryService())
        addNextService(opened)
    }

    /** Hides and closes the sensor. A connected watch loses it. */
    @SuppressLint("MissingPermission")
    fun stop() {
        advertiseCallback?.let { callback ->
            runCatching { advertiser?.stopAdvertising(callback) }
        }
        advertiseCallback = null
        advertiser = null
        runCatching {
            server?.clearServices()
            server?.close()
        }
        server = null
        measurement = null
        controlPoint = null
        pendingServices.clear()
        measurementSubscribers.clear()
        controlPointSubscribers.clear()
        setStatus(FootPodStatus.OFF)
    }

    /** Sends one RSC Measurement to every subscribed watch. Returns how many it was sent to. */
    @SuppressLint("MissingPermission")
    fun publish(bytes: ByteArray): Int {
        val gattServer = server ?: return 0
        val characteristic = measurement ?: return 0
        return measurementSubscribers.count { device -> notify(gattServer, device, characteristic, bytes, confirm = false) }
    }

    private fun runningSpeedService(): BluetoothGattService {
        val service = BluetoothGattService(RSC_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val measurementCharacteristic = BluetoothGattCharacteristic(
            RSC_MEASUREMENT_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0,
        ).apply { addDescriptor(configurationDescriptor()) }
        val feature = BluetoothGattCharacteristic(
            RSC_FEATURE_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        val location = BluetoothGattCharacteristic(
            SENSOR_LOCATION_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        val controlPointCharacteristic = BluetoothGattCharacteristic(
            SC_CONTROL_POINT_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        ).apply { addDescriptor(configurationDescriptor()) }
        service.addCharacteristic(measurementCharacteristic)
        service.addCharacteristic(feature)
        service.addCharacteristic(location)
        service.addCharacteristic(controlPointCharacteristic)
        measurement = measurementCharacteristic
        controlPoint = controlPointCharacteristic
        return service
    }

    private fun deviceInformationService(): BluetoothGattService =
        BluetoothGattService(DEVICE_INFORMATION_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
            addCharacteristic(readOnly(MANUFACTURER_NAME_UUID))
            addCharacteristic(readOnly(MODEL_NUMBER_UUID))
        }

    private fun batteryService(): BluetoothGattService =
        BluetoothGattService(BATTERY_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
            addCharacteristic(readOnly(BATTERY_LEVEL_UUID))
        }

    private fun readOnly(uuid: UUID): BluetoothGattCharacteristic =
        BluetoothGattCharacteristic(
            uuid,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )

    private fun configurationDescriptor(): BluetoothGattDescriptor =
        BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
        )

    /** Android accepts one service at a time; the next one follows in [serverCallback]. */
    @SuppressLint("MissingPermission")
    private fun addNextService(gattServer: BluetoothGattServer) {
        val next = pendingServices.poll()
        if (next == null) {
            startAdvertising(includeName = true)
            return
        }

        if (!gattServer.addService(next)) {
            callbacks.onFootPodDiagnostic("foot_pod add_service_refused uuid=${next.uuid}")
            failAndClose()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising(includeName: Boolean) {
        val leAdvertiser = advertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(RSC_SERVICE_UUID))
            .build()
        val scanResponse = AdvertiseData.Builder().setIncludeDeviceName(includeName).build()
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                mainHandler.post {
                    if (advertiseCallback !== this) return@post
                    callbacks.onFootPodDiagnostic("foot_pod advertising name=$includeName")
                    refreshStatus()
                }
            }

            override fun onStartFailure(errorCode: Int) {
                mainHandler.post {
                    if (advertiseCallback !== this) return@post
                    advertiseCallback = null
                    callbacks.onFootPodDiagnostic("foot_pod advertise_failed code=$errorCode name=$includeName")
                    // A long phone name does not fit into the scan response; the sensor works without it.
                    if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE && includeName) {
                        startAdvertising(includeName = false)
                    } else {
                        failAndClose()
                    }
                }
            }
        }
        advertiseCallback = callback
        runCatching { leAdvertiser.startAdvertising(settings, data, scanResponse, callback) }
            .onFailure {
                callbacks.onFootPodDiagnostic("foot_pod advertise_failed error=${it.message}")
                advertiseCallback = null
                failAndClose()
            }
    }

    private fun failAndClose() {
        stop()
        setStatus(FootPodStatus.FAILED)
    }

    private fun refreshStatus() {
        if (server == null) {
            return
        }

        setStatus(if (measurementSubscribers.isEmpty()) FootPodStatus.ADVERTISING else FootPodStatus.CONNECTED)
    }

    private fun setStatus(next: FootPodStatus) {
        if (status == next) {
            return
        }

        status = next
        callbacks.onFootPodStatus(next)
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            mainHandler.post {
                val gattServer = server ?: return@post
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    callbacks.onFootPodDiagnostic("foot_pod add_service_failed status=$status uuid=${service.uuid}")
                    failAndClose()
                    return@post
                }
                addNextService(gattServer)
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            mainHandler.post {
                if (server == null || newState != BluetoothProfile.STATE_DISCONNECTED) return@post
                val wasSubscribed = measurementSubscribers.remove(device)
                controlPointSubscribers.remove(device)
                if (wasSubscribed) {
                    callbacks.onFootPodDiagnostic("foot_pod watch_disconnected status=$status")
                    refreshStatus()
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            mainHandler.post {
                val value = readableValue(characteristic.uuid)
                if (value == null || offset > value.size) {
                    respond(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                } else {
                    respond(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value.copyOfRange(offset, value.size))
                }
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor,
        ) {
            mainHandler.post {
                val value = when {
                    descriptor.uuid != CCCD_UUID -> null
                    descriptor.characteristic.uuid == RSC_MEASUREMENT_UUID ->
                        if (device in measurementSubscribers) NOTIFICATIONS_ON else NOTIFICATIONS_OFF
                    descriptor.characteristic.uuid == SC_CONTROL_POINT_UUID ->
                        if (device in controlPointSubscribers) INDICATIONS_ON else NOTIFICATIONS_OFF
                    else -> null
                }
                if (value == null || offset > value.size) {
                    respond(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                } else {
                    respond(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value.copyOfRange(offset, value.size))
                }
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            val written = value.copyOf()
            mainHandler.post {
                if (descriptor.uuid != CCCD_UUID || written.isEmpty()) {
                    if (responseNeeded) respond(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
                    return@post
                }

                val enabled = written[0].toInt() != 0
                when (descriptor.characteristic.uuid) {
                    RSC_MEASUREMENT_UUID -> onMeasurementSubscription(device, enabled)
                    SC_CONTROL_POINT_UUID ->
                        if (enabled) controlPointSubscribers.add(device) else controlPointSubscribers.remove(device)
                }
                if (responseNeeded) respond(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            val written = value.copyOf()
            mainHandler.post { onControlPointWrite(device, requestId, characteristic, responseNeeded, offset, written) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun onMeasurementSubscription(device: BluetoothDevice, enabled: Boolean) {
        val changed = if (enabled) measurementSubscribers.add(device) else measurementSubscribers.remove(device)
        if (!changed) {
            return
        }

        callbacks.onFootPodDiagnostic("foot_pod watch_subscribed=$enabled subscribers=${measurementSubscribers.size}")
        if (enabled) {
            // Marks the link as used by this app, so Android keeps it for as long as the sensor is open.
            runCatching { server?.connect(device, false) }
        }
        refreshStatus()
    }

    private fun onControlPointWrite(
        device: BluetoothDevice,
        requestId: Int,
        characteristic: BluetoothGattCharacteristic,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray,
    ) {
        val gattServer = server ?: return
        val target = controlPoint
        val answer = ScControlPoint.respond(value)
        if (characteristic.uuid != SC_CONTROL_POINT_UUID || target == null || answer == null) {
            if (responseNeeded) respond(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
            return
        }

        if (device !in controlPointSubscribers) {
            if (responseNeeded) respond(device, requestId, ATT_ERROR_CCCD_IMPROPERLY_CONFIGURED, offset, null)
            return
        }

        if (responseNeeded) respond(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
        callbacks.onFootPodDiagnostic("foot_pod control_point request=${value.toHex()} answer=${answer.toHex()}")
        notify(gattServer, device, target, answer, confirm = true)
    }

    private fun readableValue(uuid: UUID): ByteArray? =
        when (uuid) {
            RSC_FEATURE_UUID -> RscMeasurement.feature()
            SENSOR_LOCATION_UUID -> byteArrayOf(SENSOR_LOCATION_OTHER)
            BATTERY_LEVEL_UUID -> byteArrayOf(BATTERY_LEVEL_FULL)
            MANUFACTURER_NAME_UUID -> MANUFACTURER_NAME.toByteArray(Charsets.UTF_8)
            MODEL_NUMBER_UUID -> MODEL_NUMBER.toByteArray(Charsets.UTF_8)
            else -> null
        }

    @SuppressLint("MissingPermission")
    private fun respond(device: BluetoothDevice, requestId: Int, status: Int, offset: Int, value: ByteArray?) {
        runCatching { server?.sendResponse(device, requestId, status, offset, value) }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun notify(
        gattServer: BluetoothGattServer,
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        bytes: ByteArray,
        confirm: Boolean,
    ): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gattServer.notifyCharacteristicChanged(device, characteristic, confirm, bytes) == BluetoothStatusCodes.SUCCESS
            } else {
                characteristic.value = bytes
                gattServer.notifyCharacteristicChanged(device, characteristic, confirm)
            }
        }.getOrDefault(false)

    private fun ByteArray.toHex(): String = joinToString(" ") { it.toUByte().toString(16).padStart(2, '0') }

    private companion object {
        val RSC_FEATURE_UUID: UUID = UUID.fromString("00002a54-0000-1000-8000-00805f9b34fb")
        val SC_CONTROL_POINT_UUID: UUID = UUID.fromString("00002a55-0000-1000-8000-00805f9b34fb")
        val SENSOR_LOCATION_UUID: UUID = UUID.fromString("00002a5d-0000-1000-8000-00805f9b34fb")
        val DEVICE_INFORMATION_SERVICE_UUID: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        val MANUFACTURER_NAME_UUID: UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
        val MODEL_NUMBER_UUID: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

        val NOTIFICATIONS_ON = byteArrayOf(0x01, 0x00)
        val INDICATIONS_ON = byteArrayOf(0x02, 0x00)
        val NOTIFICATIONS_OFF = byteArrayOf(0x00, 0x00)

        const val SENSOR_LOCATION_OTHER: Byte = 0
        const val BATTERY_LEVEL_FULL: Byte = 100
        const val MANUFACTURER_NAME = "FTMS Bridge"
        const val MODEL_NUMBER = "Treadmill foot pod"

        /** Bluetooth's common error "Client Characteristic Configuration Descriptor Improperly Configured". */
        const val ATT_ERROR_CCCD_IMPROPERLY_CONFIGURED = 0xFD
        const val ADVERTISE_FAILED_DATA_TOO_LARGE = AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE
    }
}
