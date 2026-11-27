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
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.getSystemService
import de.ma.ftms.bridge.debug.BridgeLogger
import de.ma.ftms.bridge.runtime.CCCD_UUID
import de.ma.ftms.bridge.runtime.CROSS_TRAINER_DATA_UUID
import de.ma.ftms.bridge.runtime.FITNESS_MACHINE_CONTROL_POINT_UUID
import de.ma.ftms.bridge.runtime.FITNESS_MACHINE_STATUS_UUID
import de.ma.ftms.bridge.runtime.FTMS_SERVICE_UUID
import de.ma.ftms.bridge.runtime.INDOOR_BIKE_DATA_UUID
import de.ma.ftms.bridge.runtime.RSC_MEASUREMENT_UUID
import de.ma.ftms.bridge.runtime.RSC_SERVICE_UUID
import de.ma.ftms.bridge.runtime.TREADMILL_DATA_UUID
import de.ma.ftms.bridge.runtime.TREADMILL_PREFIX
import de.ma.ftms.bridge.runtime.TreadmillDeviceItem
import de.ma.ftms.bridge.runtime.VENDOR_NOTIFY_UUID
import de.ma.ftms.bridge.runtime.VENDOR_SERVICE_UUID
import de.ma.ftms.bridge.runtime.equipmentTypeFromFtmsServiceData
import de.ma.ftms.core.FtmsMachineKind
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID

/**
 * BLE client for FTMS equipment. It only reads, unless [controlPointWanted] is set: then it also
 * sets up the control point so the owner can write commands with [writeControlPoint].
 *
 * All public functions must be called on the main thread, and every [Callbacks] function is
 * invoked on the main thread. Android delivers GATT callbacks on binder threads; they are posted
 * to the main looper here so the client and its owner never share state across threads.
 */
internal class TreadmillBleClient(
    private val context: Context,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onTreadmillFound(item: TreadmillDeviceItem)
        fun onScanStateChanged(scanning: Boolean)

        /** A scan could not be started or was aborted. The equipment connection is unaffected. */
        fun onScanFailed(message: String)

        /** The equipment watched with [startPresenceWatch] is advertising, so it is switched on and in range. */
        fun onWatchedEquipmentSeen(address: String)

        /** The presence watch was aborted by Android and no longer reports sightings. */
        fun onPresenceWatchFailed(message: String)
        fun onConnectionChanged(connected: Boolean)
        fun onPacket(kind: Int, bytes: ByteArray)
        fun onRscMeasurement(bytes: ByteArray)

        /** A packet from the equipment's vendor service, which may carry what FTMS has no field for. */
        fun onVendorPacket(bytes: ByteArray)

        /**
         * The connection is set up and [controlPointWanted] was set; [available] says whether the
         * equipment has a control point that answers.
         */
        fun onControlPointSetUp(available: Boolean)

        /** Value of one of the characteristics named in [controlPointReads], read after the set-up. */
        fun onControlPointValueRead(uuid: UUID, bytes: ByteArray)

        /** The equipment's answer to a command written with [writeControlPoint]. */
        fun onControlPointResponse(bytes: ByteArray)

        /** A command written with [writeControlPoint] did not reach the equipment. */
        fun onControlPointWriteFailed()

        /** The equipment reports a change of its state, such as a target set on its own controls. */
        fun onMachineStatus(bytes: ByteArray)
        fun onBleDiagnostic(message: String)

        /** The equipment connection is unusable or was lost; the owner may reconnect. */
        fun onFailure(message: String)
        fun log(message: String)
    }

    private class Subscription(
        val characteristic: BluetoothGattCharacteristic,
        /** True for characteristics that deliver workout data; false for diagnostic ones. */
        val carriesData: Boolean,
    )

    private sealed interface ControlOperation {
        class Read(val uuid: UUID) : ControlOperation
        class Write(val bytes: ByteArray) : ControlOperation
    }

    /**
     * Whether the next connection also sets up the FTMS control point. Off by default, so equipment
     * that is only read is never written to beyond enabling its notifications.
     */
    var controlPointWanted = false

    /**
     * The characteristics of the FTMS service to read once the control point is set up, such as
     * the range of values the equipment accepts. Those the equipment lacks are left out.
     */
    var controlPointReads: List<UUID> = emptyList()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothManager = context.getSystemService<BluetoothManager>()
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var gatt: BluetoothGatt? = null
    private var connected = false
    private var scanning = false
    private val pendingSubscriptions = ArrayDeque<Subscription>()
    private var activeSubscription: Subscription? = null
    private var enabledDataSubscriptions = 0
    private var controlPoint: BluetoothGattCharacteristic? = null
    private var controlPointReady = false
    private var setUpComplete = false
    private val pendingControlOperations = ArrayDeque<ControlOperation>()
    private var activeControlOperation: ControlOperation? = null
    private val controlOperationTimeout = Runnable {
        val operation = activeControlOperation ?: return@Runnable
        activeControlOperation = null
        callbacks.onBleDiagnostic("control_operation_timeout afterMs=$CONTROL_OPERATION_TIMEOUT_MS")
        if (operation is ControlOperation.Write) {
            callbacks.onControlPointWriteFailed()
        }
        gatt?.let(::runNextControlOperation)
    }
    private val scanTimeout = Runnable {
        if (scanning) {
            callbacks.onBleDiagnostic("scan_timeout afterMs=$SCAN_TIMEOUT_MS")
            stopScan()
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            mainHandler.post { handleScanResult(result) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            val snapshot = results.toList()
            mainHandler.post { snapshot.forEach(::handleScanResult) }
        }

        override fun onScanFailed(errorCode: Int) {
            mainHandler.post {
                BridgeLogger.warn(TAG, "BLE scan failed errorCode=$errorCode")
                mainHandler.removeCallbacks(scanTimeout)
                scanning = false
                callbacks.onScanStateChanged(false)
                callbacks.onScanFailed("BLE scan failed $errorCode")
            }
        }
    }

    private var watchedAddress: String? = null
    private val presenceCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = result.device?.address ?: return
            mainHandler.post { handlePresenceSighting(address) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            val addresses = results.mapNotNull { it.device?.address }
            mainHandler.post { addresses.forEach(::handlePresenceSighting) }
        }

        override fun onScanFailed(errorCode: Int) {
            mainHandler.post {
                if (watchedAddress == null) {
                    return@post
                }
                watchedAddress = null
                BridgeLogger.warn(TAG, "Presence watch failed errorCode=$errorCode")
                callbacks.onPresenceWatchFailed("BLE presence watch failed $errorCode")
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            postForGatt(gatt) { handleConnectionStateChange(gatt, status, newState) }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            postForGatt(gatt) { handleServicesDiscovered(gatt, status) }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            postForGatt(gatt) { handleDescriptorWrite(gatt, descriptor, status) }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            val uuid = characteristic.uuid
            postForGatt(gatt) { handleCharacteristicWrite(gatt, uuid, status) }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            val uuid = characteristic.uuid
            postForGatt(gatt) { handleCharacteristicRead(gatt, uuid, value, status) }
        }

        @Deprecated("Used by Android versions before API 33")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value?.copyOf() ?: ByteArray(0)
            val uuid = characteristic.uuid
            postForGatt(gatt) { handleCharacteristicRead(gatt, uuid, value, status) }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            val uuid = characteristic.uuid
            postForGatt(gatt) { handleCharacteristicValue(uuid, value) }
        }

        @Deprecated("Used by Android versions before API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            // The characteristic's buffer is reused for the next notification, so copy it here.
            @Suppress("DEPRECATION")
            val value = characteristic.value?.copyOf() ?: return
            val uuid = characteristic.uuid
            postForGatt(gatt) { handleCharacteristicValue(uuid, value) }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            callbacks.onScanFailed("No BLE scanner")
            return
        }

        if (scanning) {
            mainHandler.removeCallbacks(scanTimeout)
            mainHandler.postDelayed(scanTimeout, SCAN_TIMEOUT_MS)
            return
        }

        runCatching {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(null, settings, scanCallback)
            scanning = true
            mainHandler.postDelayed(scanTimeout, SCAN_TIMEOUT_MS)
            callbacks.onScanStateChanged(true)
            callbacks.log("Scanning for FTMS equipment")
            callbacks.onBleDiagnostic("scan_started")
            BridgeLogger.debug(TAG, "BLE scan started")
        }.onFailure {
            BridgeLogger.warn(TAG, "BLE scan start failed", it)
            callbacks.onScanFailed("BLE scan start failed: ${it.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        mainHandler.removeCallbacks(scanTimeout)
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        if (!scanning) {
            return
        }

        scanning = false
        BridgeLogger.debug(TAG, "BLE scan stopped")
        callbacks.onBleDiagnostic("scan_stopped")
        callbacks.onScanStateChanged(false)
    }

    /**
     * Watches for advertisements of one piece of equipment without connecting to it, until
     * [stopPresenceWatch] or [connect] is called. Returns false if the watch could not be started.
     *
     * Runs next to a discovery scan started with [startScan]. It scans without a filter and
     * compares the address itself, like the discovery scan that found the equipment.
     */
    @SuppressLint("MissingPermission")
    fun startPresenceWatch(address: String): Boolean {
        stopPresenceWatch()
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return false

        return runCatching {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build()
            scanner.startScan(null, settings, presenceCallback)
            watchedAddress = address
            callbacks.onBleDiagnostic("presence_watch_started address=$address")
            BridgeLogger.debug(TAG, "Presence watch started address=$address")
        }.onFailure {
            BridgeLogger.warn(TAG, "Presence watch start failed", it)
            callbacks.onBleDiagnostic("presence_watch_start_failed error=${it.message}")
        }.isSuccess
    }

    @SuppressLint("MissingPermission")
    fun stopPresenceWatch() {
        if (watchedAddress == null) {
            return
        }

        watchedAddress = null
        runCatching { adapter?.bluetoothLeScanner?.stopScan(presenceCallback) }
        callbacks.onBleDiagnostic("presence_watch_stopped")
        BridgeLogger.debug(TAG, "Presence watch stopped")
    }

    private fun handlePresenceSighting(address: String) {
        val watched = watchedAddress ?: return
        if (address.equals(watched, ignoreCase = true)) {
            callbacks.onWatchedEquipmentSeen(watched)
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        stopScan()
        stopPresenceWatch()
        releaseGatt(reason = "reconnect")

        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            reportFailureAsync("Cannot resolve FTMS equipment $address")
            return
        }

        callbacks.log("Connecting FTMS equipment ${safeName(device) ?: address}")
        BridgeLogger.debug(TAG, "Connecting address=$address name=${safeName(device)}")
        callbacks.onBleDiagnostic("connect_start address=$address name=${safeName(device) ?: "unknown"}")
        // The (Context, Boolean, Callback, Int) overload is deprecated since API 37 but is the only
        // one available down to minSdk; callbacks are moved to the main thread by postForGatt.
        @Suppress("DEPRECATION")
        val newGatt = runCatching {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }.onFailure {
            BridgeLogger.warn(TAG, "connectGatt failed", it)
        }.getOrNull()

        if (newGatt == null) {
            // Happens while Bluetooth is switched off; no GATT callback will follow.
            callbacks.onBleDiagnostic("connect_failed reason=no_gatt")
            reportFailureAsync("Bluetooth connection could not be started")
            return
        }

        gatt = newGatt
    }

    /** True while a link to [address] is up, even if its notifications are not enabled yet. */
    @SuppressLint("MissingPermission")
    fun isConnectedTo(address: String): Boolean =
        connected && gatt?.device?.address.equals(address, ignoreCase = true)

    fun disconnect() {
        callbacks.onBleDiagnostic("disconnect_requested")
        releaseGatt(reason = "requested")
    }

    /**
     * Writes one command to the control point of the connected equipment. The answer arrives through
     * [Callbacks.onControlPointResponse]. Returns false if there is no control point to write to;
     * a write that fails later is reported through [Callbacks.onControlPointWriteFailed].
     */
    fun writeControlPoint(bytes: ByteArray): Boolean {
        val current = gatt
        if (current == null || !setUpComplete || !controlPointReady || controlPoint == null) {
            return false
        }

        pendingControlOperations.add(ControlOperation.Write(bytes.copyOf()))
        runNextControlOperation(current)
        return true
    }

    /** Closes the current GATT client, if any, and reports the link as down exactly once. */
    @SuppressLint("MissingPermission")
    private fun releaseGatt(reason: String) {
        val current = gatt
        gatt = null
        pendingSubscriptions.clear()
        activeSubscription = null
        enabledDataSubscriptions = 0
        mainHandler.removeCallbacks(controlOperationTimeout)
        pendingControlOperations.clear()
        activeControlOperation = null
        controlPoint = null
        controlPointReady = false
        setUpComplete = false
        if (current != null) {
            runCatching {
                current.disconnect()
                current.close()
            }
            BridgeLogger.debug(TAG, "Closed GATT reason=$reason")
        }

        if (connected) {
            connected = false
            callbacks.onConnectionChanged(false)
        }
    }

    /**
     * Runs [block] on the main thread unless [source] has been replaced or closed in the meantime,
     * so callbacks of an abandoned connection cannot disturb the current one.
     */
    private fun postForGatt(source: BluetoothGatt, block: () -> Unit) {
        mainHandler.post {
            if (source === gatt) {
                block()
            }
        }
    }

    /** Reports a failure after the current call returns, so callers are never re-entered. */
    private fun reportFailureAsync(message: String) {
        mainHandler.post { callbacks.onFailure(message) }
    }

    @SuppressLint("MissingPermission")
    private fun handleConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        BridgeLogger.debug(TAG, "Connection state changed status=$status newState=$newState")
        callbacks.onBleDiagnostic("connection_state status=$status newState=$newState")
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            connected = true
            callbacks.onConnectionChanged(true)
            val started = runCatching { gatt.discoverServices() }
                .onFailure { callbacks.onBleDiagnostic("discover_services_failed error=${it.message}") }
                .getOrDefault(false)
            if (!started) {
                callbacks.onFailure("GATT discovery could not be started")
            }
            return
        }

        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            // Close the client so repeated reconnects do not exhaust the system's GATT interfaces.
            releaseGatt(reason = "disconnected status=$status")
            callbacks.onFailure("Treadmill disconnected status=$status")
        }
    }

    private fun handleServicesDiscovered(gatt: BluetoothGatt, status: Int) {
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

    private fun handleDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        val characteristicUuid = descriptor.characteristic?.uuid
        BridgeLogger.debug(TAG, "Descriptor write characteristic=${characteristicUuid?.shortName()} status=$status")
        callbacks.onBleDiagnostic("descriptor_write characteristic=$characteristicUuid status=$status")
        if (descriptor.uuid != CCCD_UUID) {
            return
        }

        val subscription = activeSubscription?.takeIf { it.characteristic.uuid == characteristicUuid }
        activeSubscription = null
        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (subscription?.carriesData == true) {
                enabledDataSubscriptions += 1
            }
            if (subscription != null && subscription.characteristic === controlPoint) {
                controlPointReady = true
            }
            callbacks.log("BLE notifications enabled ${characteristicUuid?.shortName()}")
        } else {
            callbacks.onBleDiagnostic("subscription_failed uuid=$characteristicUuid reason=cccd_status_$status")
        }
        enableNextSubscription(gatt)
    }

    private fun handleScanResult(result: ScanResult) {
        if (!scanning) {
            return
        }

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
                advertisedType = equipmentTypeFromFtmsServiceData(
                    result.scanRecord?.getServiceData(ParcelUuid(FTMS_SERVICE_UUID)),
                ),
            ),
        )
    }

    private fun subscribeData(gatt: BluetoothGatt) {
        val ftmsService: BluetoothGattService? = gatt.getService(FTMS_SERVICE_UUID)
        if (ftmsService == null) {
            BridgeLogger.warn(TAG, "FTMS service missing; available=${gatt.services.joinToString { it.uuid.toString() }}")
            callbacks.onBleDiagnostic("ftms_service_missing available=${gatt.services.joinToString { it.uuid.toString() }}")
            callbacks.onFailure("FTMS service missing")
            return
        }

        pendingSubscriptions.clear()
        activeSubscription = null
        enabledDataSubscriptions = 0
        listOf(INDOOR_BIKE_DATA_UUID, TREADMILL_DATA_UUID, CROSS_TRAINER_DATA_UUID)
            .mapNotNull { ftmsService.getCharacteristic(it) }
            .forEach { pendingSubscriptions.add(Subscription(it, carriesData = true)) }

        gatt.getService(RSC_SERVICE_UUID)
            ?.getCharacteristic(RSC_MEASUREMENT_UUID)
            ?.also { BridgeLogger.debug(TAG, "RSC measurement characteristic available") }
            ?.let { pendingSubscriptions.add(Subscription(it, carriesData = true)) }

        if (pendingSubscriptions.isEmpty()) {
            callbacks.onFailure("No supported FTMS/RSC data characteristic found")
            return
        }

        if (controlPointWanted) {
            // Answers to commands arrive as indications, so those are enabled like the data.
            ftmsService.getCharacteristic(FITNESS_MACHINE_CONTROL_POINT_UUID)
                ?.takeIf { it.supportsIndicate() && it.supportsWrite() }
                ?.let {
                    controlPoint = it
                    pendingSubscriptions.add(Subscription(it, carriesData = false))
                }
            // Tells what was changed on the equipment itself, so the app does not show a stale target.
            ftmsService.getCharacteristic(FITNESS_MACHINE_STATUS_UUID)
                ?.takeIf { controlPoint != null && it.supportsNotifications() }
                ?.let { pendingSubscriptions.add(Subscription(it, carriesData = false)) }
        }

        gatt.getService(VENDOR_SERVICE_UUID)
            ?.getCharacteristic(VENDOR_NOTIFY_UUID)
            ?.takeIf { it.supportsNotifications() }
            ?.also { callbacks.onBleDiagnostic("vendor_notify_available uuid=${it.uuid}") }
            ?.let { pendingSubscriptions.add(Subscription(it, carriesData = false)) }
        callbacks.onBleDiagnostic(
            "subscriptions_queued characteristics=${pendingSubscriptions.joinToString { it.characteristic.uuid.toString() }}",
        )
        BridgeLogger.debug(TAG, "Data subscriptions queued count=${pendingSubscriptions.size}")

        enableNextSubscription(gatt)
    }

    /**
     * Enables notifications one characteristic at a time, as Android allows only one pending GATT
     * operation. A characteristic that cannot be enabled is skipped; the connection only counts as
     * failed when no data characteristic could be enabled at all.
     */
    private fun enableNextSubscription(gatt: BluetoothGatt) {
        while (true) {
            val subscription = pendingSubscriptions.poll()
            if (subscription == null) {
                callbacks.onBleDiagnostic(
                    "subscriptions_complete dataEnabled=$enabledDataSubscriptions controlPoint=$controlPointReady",
                )
                if (enabledDataSubscriptions == 0) {
                    callbacks.onFailure("FTMS notifications could not be enabled")
                    return
                }
                completeSetUp(gatt)
                return
            }

            val failure = startSubscription(gatt, subscription)
            if (failure == null) {
                activeSubscription = subscription
                return
            }

            BridgeLogger.warn(TAG, "Subscription skipped uuid=${subscription.characteristic.uuid.shortName()} reason=$failure")
            callbacks.onBleDiagnostic("subscription_failed uuid=${subscription.characteristic.uuid} reason=$failure")
        }
    }

    /** Returns null when the CCCD write was started, otherwise the reason it was not. */
    @SuppressLint("MissingPermission")
    private fun startSubscription(gatt: BluetoothGatt, subscription: Subscription): String? {
        val characteristic = subscription.characteristic
        BridgeLogger.debug(TAG, "Enable notification characteristic=${characteristic.uuid.shortName()}")
        callbacks.onBleDiagnostic("enable_notification uuid=${characteristic.uuid}")
        val descriptor = characteristic.getDescriptor(CCCD_UUID) ?: return "cccd_missing"

        return runCatching {
            if (!gatt.setCharacteristicNotification(characteristic, true)) {
                return@runCatching "set_notification_rejected"
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
            if (started) null else "cccd_write_rejected"
        }.getOrElse { "exception_${it::class.simpleName}" }
    }

    /** Opens the connection for control point commands once all notifications are enabled. */
    private fun completeSetUp(gatt: BluetoothGatt) {
        setUpComplete = true
        if (!controlPointWanted) {
            return
        }

        if (controlPointReady) {
            val ftmsService = gatt.getService(FTMS_SERVICE_UUID)
            controlPointReads
                .filter { ftmsService?.getCharacteristic(it) != null }
                .forEach { pendingControlOperations.add(ControlOperation.Read(it)) }
        }
        callbacks.onControlPointSetUp(available = controlPointReady)
        // The owner may have queued a command from the callback; run whatever is waiting.
        if (gatt === this.gatt) {
            runNextControlOperation(gatt)
        }
    }

    /** Starts the next read or write, as Android allows only one pending GATT operation. */
    private fun runNextControlOperation(gatt: BluetoothGatt) {
        if (!setUpComplete || activeControlOperation != null) {
            return
        }

        while (true) {
            val operation = pendingControlOperations.poll() ?: return
            if (startControlOperation(gatt, operation)) {
                activeControlOperation = operation
                mainHandler.postDelayed(controlOperationTimeout, CONTROL_OPERATION_TIMEOUT_MS)
                return
            }

            callbacks.onBleDiagnostic("control_operation_rejected type=${operation::class.simpleName}")
            if (operation is ControlOperation.Write) {
                callbacks.onControlPointWriteFailed()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startControlOperation(gatt: BluetoothGatt, operation: ControlOperation): Boolean =
        runCatching {
            when (operation) {
                is ControlOperation.Read -> {
                    val characteristic = gatt.getService(FTMS_SERVICE_UUID)
                        ?.getCharacteristic(operation.uuid)
                        ?: return@runCatching false
                    gatt.readCharacteristic(characteristic)
                }

                is ControlOperation.Write -> {
                    val characteristic = controlPoint ?: return@runCatching false
                    callbacks.onBleDiagnostic("control_point_write bytes=${operation.bytes.toHexPreview()}")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeCharacteristic(
                            characteristic,
                            operation.bytes,
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                        ) == BluetoothStatusCodes.SUCCESS
                    } else {
                        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        @Suppress("DEPRECATION")
                        characteristic.value = operation.bytes
                        @Suppress("DEPRECATION")
                        gatt.writeCharacteristic(characteristic)
                    }
                }
            }
        }.getOrDefault(false)

    private fun finishControlOperation(gatt: BluetoothGatt) {
        mainHandler.removeCallbacks(controlOperationTimeout)
        activeControlOperation = null
        runNextControlOperation(gatt)
    }

    private fun handleCharacteristicWrite(gatt: BluetoothGatt, uuid: UUID, status: Int) {
        callbacks.onBleDiagnostic("characteristic_write uuid=$uuid status=$status")
        if (uuid != FITNESS_MACHINE_CONTROL_POINT_UUID || activeControlOperation !is ControlOperation.Write) {
            return
        }

        if (status != BluetoothGatt.GATT_SUCCESS) {
            callbacks.onControlPointWriteFailed()
        }
        finishControlOperation(gatt)
    }

    private fun handleCharacteristicRead(gatt: BluetoothGatt, uuid: UUID, value: ByteArray, status: Int) {
        callbacks.onBleDiagnostic("characteristic_read uuid=$uuid status=$status bytes=${value.toHexPreview()}")
        if ((activeControlOperation as? ControlOperation.Read)?.uuid != uuid) {
            return
        }

        if (status == BluetoothGatt.GATT_SUCCESS) {
            callbacks.onControlPointValueRead(uuid, value)
        }
        finishControlOperation(gatt)
    }

    private fun handleCharacteristicValue(uuid: UUID, value: ByteArray) {
        BridgeLogger.debug(TAG, "Characteristic changed uuid=${uuid.shortName()} size=${value.size}")
        if (uuid == FITNESS_MACHINE_CONTROL_POINT_UUID) {
            callbacks.onBleDiagnostic("control_point_response bytes=${value.toHexPreview()}")
            callbacks.onControlPointResponse(value)
            return
        }

        if (uuid == FITNESS_MACHINE_STATUS_UUID) {
            callbacks.onBleDiagnostic("machine_status bytes=${value.toHexPreview()}")
            callbacks.onMachineStatus(value)
            return
        }

        if (uuid == RSC_MEASUREMENT_UUID) {
            callbacks.onRscMeasurement(value)
            return
        }

        if (uuid == VENDOR_NOTIFY_UUID) {
            callbacks.onBleDiagnostic("vendor_notify uuid=$uuid size=${value.size} bytes=${value.toHexPreview()}")
            callbacks.onVendorPacket(value)
            return
        }

        kindForCharacteristic(uuid)?.let { callbacks.onPacket(it, value) }
    }

    private companion object {
        const val SCAN_TIMEOUT_MS = 30_000L
        const val CONTROL_OPERATION_TIMEOUT_MS = 5_000L
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

private fun BluetoothGattCharacteristic.supportsWrite(): Boolean =
    properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0

private fun BluetoothGattCharacteristic.supportsIndicate(): Boolean =
    properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0

private fun ByteArray.toHexPreview(maxBytes: Int = 32): String {
    val preview = take(maxBytes).joinToString(" ") { byte -> String.format(Locale.ROOT, "%02X", byte) }
    return if (size > maxBytes) "$preview ..." else preview
}
