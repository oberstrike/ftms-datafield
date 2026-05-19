package de.ma.ftms.bridge.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.getSystemService
import de.ma.ftms.bridge.runtime.CCCD_UUID
import de.ma.ftms.bridge.runtime.CROSS_TRAINER_DATA_UUID
import de.ma.ftms.bridge.runtime.FTMS_SERVICE_UUID
import de.ma.ftms.bridge.runtime.INDOOR_BIKE_DATA_UUID
import de.ma.ftms.bridge.runtime.RSC_MEASUREMENT_UUID
import de.ma.ftms.bridge.runtime.RSC_SERVICE_UUID
import de.ma.ftms.bridge.runtime.TREADMILL_DATA_UUID
import de.ma.ftms.bridge.runtime.TREADMILL_PREFIX
import de.ma.ftms.bridge.runtime.TreadmillDeviceItem
import de.ma.ftms.bridge.runtime.VENDOR_NOTIFY_UUID
import de.ma.ftms.bridge.runtime.VENDOR_SERVICE_UUID
import de.ma.ftms.bridge.debug.BridgeLogger
import de.ma.ftms.core.FtmsMachineKind
import java.util.ArrayDeque
import java.util.UUID

internal class TreadmillBleClient(
    private val context: Context,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onTreadmillFound(item: TreadmillDeviceItem)
        fun onScanStateChanged(scanning: Boolean)
        fun onConnectionChanged(connected: Boolean)
        fun onPacket(kind: Int, bytes: ByteArray)
        fun onRscMeasurement(bytes: ByteArray)
        fun onBleDiagnostic(message: String)
        fun onFailure(message: String)
        fun log(message: String)
    }

    private val bluetoothManager = context.getSystemService<BluetoothManager>()
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var gatt: BluetoothGatt? = null
    private var disconnectRequested = false
    private val pendingSubscriptions = ArrayDeque<BluetoothGattCharacteristic>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::handleScanResult)
        }

        override fun onScanFailed(errorCode: Int) {
            BridgeLogger.warn(TAG, "BLE scan failed errorCode=$errorCode")
            callbacks.onScanStateChanged(false)
            callbacks.onFailure("BLE scan failed $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            BridgeLogger.debug(TAG, "Connection state changed status=$status newState=$newState")
            callbacks.onBleDiagnostic("connection_state status=$status newState=$newState disconnectRequested=$disconnectRequested")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                callbacks.onConnectionChanged(true)
                runCatching { gatt.discoverServices() }
                    .onFailure {
                        callbacks.onBleDiagnostic("discover_services_failed error=${it.message}")
                        callbacks.onFailure("GATT discovery failed: ${it.message}")
                    }
                return
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                callbacks.onConnectionChanged(false)
                if (!disconnectRequested) {
                    callbacks.onFailure("Treadmill disconnected status=$status")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            BridgeLogger.debug(
                TAG,
                "Services discovered status=$status services=${gatt.services.joinToString { it.uuid.shortName() }}",
            )
            callbacks.onBleDiagnostic(
                "services_discovered status=$status services=${gatt.services.joinToString { it.uuid.toString() }}",
            )
            if (status != BluetoothGatt.GATT_SUCCESS) {
                callbacks.onFailure("GATT services failed $status")
                return
            }

            subscribeData(gatt)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            BridgeLogger.debug(
                TAG,
                "Descriptor write characteristic=${descriptor.characteristic?.uuid?.shortName()} status=$status",
            )
            callbacks.onBleDiagnostic("descriptor_write characteristic=${descriptor.characteristic?.uuid} status=$status")
            if (descriptor.uuid == CCCD_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    callbacks.log("BLE notifications enabled ${descriptor.characteristic?.uuid?.shortName()}")
                    enableNextSubscription(gatt)
                } else {
                    callbacks.onFailure("CCCD write failed $status")
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            BridgeLogger.debug(TAG, "Characteristic changed uuid=${characteristic.uuid.shortName()} size=${value.size}")
            handleCharacteristicValue(characteristic.uuid, value)
        }

        @Deprecated("Used by Android versions before API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            BridgeLogger.debug(TAG, "Characteristic changed uuid=${characteristic.uuid.shortName()} size=${value.size}")
            handleCharacteristicValue(characteristic.uuid, value)
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            callbacks.onFailure("No BLE scanner")
            return
        }

        runCatching {
            scanner.startScan(scanCallback)
            callbacks.onScanStateChanged(true)
            callbacks.log("Scanning for FTMS equipment")
            callbacks.onBleDiagnostic("scan_started")
            BridgeLogger.debug(TAG, "BLE scan started")
        }.onFailure {
            BridgeLogger.warn(TAG, "BLE scan start failed", it)
            callbacks.onFailure("BLE scan start failed: ${it.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        BridgeLogger.debug(TAG, "BLE scan stopped")
        callbacks.onBleDiagnostic("scan_stopped")
        callbacks.onScanStateChanged(false)
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        stopScan()
        close()
        disconnectRequested = false
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            callbacks.onFailure("Cannot resolve FTMS equipment $address")
            return
        }

        callbacks.log("Connecting FTMS equipment ${safeName(device) ?: address}")
        BridgeLogger.debug(TAG, "Connecting address=$address name=${safeName(device)}")
        callbacks.onBleDiagnostic("connect_start address=$address name=${safeName(device) ?: "unknown"}")
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        disconnectRequested = true
        runCatching {
            gatt?.disconnect()
            gatt?.close()
        }
        BridgeLogger.debug(TAG, "Disconnected and closed GATT")
        callbacks.onBleDiagnostic("disconnect_requested")
        gatt = null
    }

    @SuppressLint("MissingPermission")
    private fun close() {
        runCatching { gatt?.close() }
        gatt = null
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val name = result.scanRecord?.deviceName ?: safeName(device) ?: return
        val hasFtms = result.scanRecord?.serviceUuids?.contains(ParcelUuid(FTMS_SERVICE_UUID)) == true
        BridgeLogger.debug(
            TAG,
            "Scan result name=$name address=${device.address} rssi=${result.rssi} services=${result.scanRecord?.serviceUuids.orEmpty()} hasFtms=$hasFtms",
        )
        if (!name.startsWith(TREADMILL_PREFIX) && !hasFtms) {
            return
        }

        callbacks.onTreadmillFound(
            TreadmillDeviceItem(
                address = device.address,
                name = name,
                rssi = result.rssi,
                hasFtms = hasFtms,
            ),
        )
    }

    @SuppressLint("MissingPermission")
    private fun subscribeData(gatt: BluetoothGatt) {
        val ftmsService: BluetoothGattService? = gatt.getService(FTMS_SERVICE_UUID)
        if (ftmsService == null) {
            BridgeLogger.warn(TAG, "FTMS service missing; available=${gatt.services.joinToString { it.uuid.toString() }}")
            callbacks.onBleDiagnostic("ftms_service_missing available=${gatt.services.joinToString { it.uuid.toString() }}")
            callbacks.onFailure("FTMS service missing")
            return
        }

        pendingSubscriptions.clear()
        val primarySubscriptions = listOf(INDOOR_BIKE_DATA_UUID, TREADMILL_DATA_UUID, CROSS_TRAINER_DATA_UUID)
            .mapNotNull { ftmsService.getCharacteristic(it) }
        primarySubscriptions.forEach { pendingSubscriptions.add(it) }

        val rscSubscription = gatt.getService(RSC_SERVICE_UUID)
            ?.getCharacteristic(RSC_MEASUREMENT_UUID)
            ?.also { BridgeLogger.debug(TAG, "RSC measurement characteristic available") }
        rscSubscription?.let { pendingSubscriptions.add(it) }

        gatt.getService(VENDOR_SERVICE_UUID)
            ?.getCharacteristic(VENDOR_NOTIFY_UUID)
            ?.takeIf { it.supportsNotifications() }
            ?.also { callbacks.onBleDiagnostic("vendor_notify_available uuid=${it.uuid}") }
            ?.let { pendingSubscriptions.add(it) }
        callbacks.onBleDiagnostic(
            "subscriptions_queued characteristics=${pendingSubscriptions.joinToString { it.uuid.toString() }}",
        )
        BridgeLogger.debug(TAG, "Data subscriptions queued count=${pendingSubscriptions.size}")

        if (primarySubscriptions.isEmpty() && rscSubscription == null) {
            callbacks.onFailure("No supported FTMS/RSC data characteristic found")
            return
        }

        enableNextSubscription(gatt)
    }

    @SuppressLint("MissingPermission")
    private fun enableNextSubscription(gatt: BluetoothGatt) {
        val characteristic = pendingSubscriptions.poll() ?: return
        BridgeLogger.debug(TAG, "Enable notification characteristic=${characteristic.uuid.shortName()}")
        callbacks.onBleDiagnostic("enable_notification uuid=${characteristic.uuid}")
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        if (descriptor == null) {
            callbacks.onFailure("FTMS ${characteristic.uuid.shortName()} CCCD missing")
            enableNextSubscription(gatt)
            return
        }

        val notificationSet = gatt.setCharacteristicNotification(characteristic, true)
        if (!notificationSet) {
            callbacks.onFailure("setCharacteristicNotification failed ${characteristic.uuid.shortName()}")
            enableNextSubscription(gatt)
            return
        }

        val value = if (characteristic.supportsNotify()) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }

        if (!started) {
            callbacks.onFailure("CCCD write did not start ${characteristic.uuid.shortName()}")
        }
    }

    private fun handleCharacteristicValue(uuid: UUID, value: ByteArray) {
        if (uuid == RSC_MEASUREMENT_UUID) {
            callbacks.onRscMeasurement(value)
            return
        }

        if (uuid == VENDOR_NOTIFY_UUID) {
            callbacks.onBleDiagnostic("vendor_notify uuid=$uuid size=${value.size} bytes=${value.toHexPreview()}")
            return
        }

        kindForCharacteristic(uuid)?.let { callbacks.onPacket(it, value) }
    }
}

private const val TAG = "FtmsBle"

@SuppressLint("MissingPermission")
private fun safeName(device: BluetoothDevice): String? =
    runCatching { device.name }.getOrNull()

private fun kindForCharacteristic(uuid: UUID): Int? =
    when (uuid) {
        INDOOR_BIKE_DATA_UUID -> FtmsMachineKind.INDOOR_BIKE
        TREADMILL_DATA_UUID -> FtmsMachineKind.TREADMILL
        CROSS_TRAINER_DATA_UUID -> FtmsMachineKind.CROSS_TRAINER
        else -> null
    }

private fun UUID.shortName(): String = toString().substring(4, 8)

private fun BluetoothGattCharacteristic.supportsNotifications(): Boolean =
    supportsNotify() || supportsIndicate()

private fun BluetoothGattCharacteristic.supportsNotify(): Boolean =
    properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0

private fun BluetoothGattCharacteristic.supportsIndicate(): Boolean =
    properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0

private fun ByteArray.toHexPreview(maxBytes: Int = 32): String {
    val preview = take(maxBytes).joinToString(" ") { byte -> "%02X".format(byte) }
    return if (size > maxBytes) "$preview ..." else preview
}
