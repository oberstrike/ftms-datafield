package de.ma.ftms.relay

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.PowerManager
import android.os.SystemClock
import de.ma.ftms.core.FootPodModel
import de.ma.ftms.footpod.FootPodUuids
import java.util.UUID

/**
 * Finds the running sensor of the bridge app on another phone, connects to it and hands out its
 * measurements. It keeps looking whenever the sensor is not there, which is the normal state: the
 * bridge offers it only during a treadmill session.
 *
 * The bridge's phone advertises under an address that changes, so it cannot be remembered. Every
 * running sensor in range is a candidate, and the model it reports decides.
 *
 * All public functions must be called on the main thread, and every [Callbacks] function is
 * invoked on the main thread.
 */
internal class FootPodSourceClient(
    private val context: Context,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onSourceStatus(status: SourceStatus)
        fun onSourceMeasurement(bytes: ByteArray)
        fun onSourceDiagnostic(message: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val rejected = RejectedSources()

    /**
     * Keeps the phone awake until a planned new search has begun. In standby a waiting timer does
     * not advance, and a sensor lost during a training would stay lost until something else woke
     * the phone. The search itself needs no such help: Android wakes the phone for what it finds.
     */
    private val retryWakeLock = context.getSystemService(PowerManager::class.java)
        ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FtmsRelay:retry")
        ?.apply { setReferenceCounted(false) }
    private var wanted = false
    private var scanning = false
    private var gatt: BluetoothGatt? = null

    /** True once the measurements of the connected sensor are subscribed. */
    private var setUp = false
    private var status = SourceStatus.OFF

    private val searchAgain = Runnable { search() }
    private val setUpTimeout = Runnable {
        if (gatt != null) {
            callbacks.onSourceDiagnostic("source set_up_timeout afterMs=$SET_UP_TIMEOUT_MS")
            releaseGatt()
            searchLater(RETRY_DELAY_MS)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            mainHandler.post { handleSighting(result) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            mainHandler.post { results.forEach(::handleSighting) }
        }

        override fun onScanFailed(errorCode: Int) {
            mainHandler.post {
                if (!scanning) return@post
                scanning = false
                callbacks.onSourceDiagnostic("source scan_failed code=$errorCode")
                searchLater(RETRY_DELAY_MS)
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
            val characteristic = descriptor.characteristic.uuid
            postForGatt(gatt) { handleSubscribed(characteristic, status) }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            val uuid = characteristic.uuid
            postForGatt(gatt) { handleRead(gatt, uuid, value, status) }
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
            postForGatt(gatt) { handleRead(gatt, uuid, value, status) }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            val uuid = characteristic.uuid
            postForGatt(gatt) { handleValue(uuid, value) }
        }

        @Deprecated("Used by Android versions before API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            // The characteristic's buffer is reused for the next notification, so copy it here.
            @Suppress("DEPRECATION")
            val value = characteristic.value?.copyOf() ?: return
            val uuid = characteristic.uuid
            postForGatt(gatt) { handleValue(uuid, value) }
        }
    }

    /** Begins to look for the bridge's sensor and keeps a connection to it from then on. */
    fun start() {
        if (wanted) {
            return
        }

        wanted = true
        search()
    }

    /** Ends the search and the connection. */
    fun stop() {
        wanted = false
        mainHandler.removeCallbacks(searchAgain)
        runCatching { retryWakeLock?.takeIf { it.isHeld }?.release() }
        stopScan()
        releaseGatt()
        setStatus(SourceStatus.OFF)
    }

    @SuppressLint("MissingPermission")
    private fun search() {
        mainHandler.removeCallbacks(searchAgain)
        if (!wanted || gatt != null) {
            return
        }

        stopScan()
        if (!hasRelayPermissions(context)) {
            setStatus(SourceStatus.PERMISSION_MISSING)
            return
        }

        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            setStatus(SourceStatus.BLUETOOTH_OFF)
            return
        }

        // With a filter Android keeps reporting while the screen is off.
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(FootPodUuids.RSC_SERVICE)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
        val started = runCatching { scanner.startScan(filters, settings, scanCallback) }
            .onFailure { callbacks.onSourceDiagnostic("source scan_refused error=${it.message}") }
            .isSuccess
        setStatus(SourceStatus.SEARCHING)
        if (!started) {
            searchLater(RETRY_DELAY_MS)
            return
        }

        scanning = true
        // Android slows down a search that has been running for long; begin afresh before that.
        mainHandler.postDelayed(searchAgain, SCAN_RENEWAL_MS)
    }

    private fun searchLater(delayMillis: Long) {
        mainHandler.removeCallbacks(searchAgain)
        if (!wanted) {
            return
        }

        setStatus(SourceStatus.SEARCHING)
        runCatching { retryWakeLock?.acquire(delayMillis + WAKE_MARGIN_MS) }
        mainHandler.postDelayed(searchAgain, delayMillis)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!scanning) {
            return
        }

        scanning = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    @SuppressLint("MissingPermission")
    private fun handleSighting(result: ScanResult) {
        if (!wanted || !scanning || gatt != null) {
            return
        }

        val device = result.device
        val serviceUuids = result.scanRecord?.serviceUuids.orEmpty().map { it.uuid }
        if (!mayBeBridgeSensor(serviceUuids) || rejected.isRejected(device.address, SystemClock.elapsedRealtime())) {
            return
        }

        stopScan()
        mainHandler.removeCallbacks(searchAgain)
        callbacks.onSourceDiagnostic("source candidate address=${device.address} rssi=${result.rssi}")
        setStatus(SourceStatus.CONNECTING)
        // The (Context, Boolean, Callback, Int) overload is deprecated since API 37 but is the only
        // one available down to minSdk; callbacks are moved to the main thread by postForGatt.
        @Suppress("DEPRECATION")
        val newGatt = runCatching { device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE) }
            .onFailure { callbacks.onSourceDiagnostic("source connect_refused error=${it.message}") }
            .getOrNull()
        if (newGatt == null) {
            searchLater(RETRY_DELAY_MS)
            return
        }

        gatt = newGatt
        mainHandler.postDelayed(setUpTimeout, SET_UP_TIMEOUT_MS)
    }

    /**
     * Runs [block] on the main thread unless [source] has been closed in the meantime, so callbacks
     * of an abandoned connection cannot disturb the current one.
     */
    private fun postForGatt(source: BluetoothGatt, block: () -> Unit) {
        mainHandler.post {
            if (source === gatt) {
                block()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            if (!runCatching { gatt.discoverServices() }.getOrDefault(false)) {
                giveUp("discovery_refused")
            }
            return
        }

        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            val wasSetUp = setUp
            callbacks.onSourceDiagnostic("source link_down status=$status wasSetUp=$wasSetUp")
            releaseGatt()
            // A sensor that was in use is looked for again at once; a failed attempt waits, because
            // Android refuses to begin more than a few searches in half a minute.
            searchLater(if (wasSetUp) RESUME_DELAY_MS else RETRY_DELAY_MS)
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            giveUp("discovery_failed status=$status")
            return
        }

        // A device may carry a second Device Information service of its system, without a model.
        val model = gatt.services
            .filter { it.uuid == FootPodUuids.DEVICE_INFORMATION_SERVICE }
            .firstNotNullOfOrNull { it.getCharacteristic(FootPodUuids.MODEL_NUMBER) }
        if (model == null) {
            reject(gatt, "no_model")
        } else if (!runCatching { gatt.readCharacteristic(model) }.getOrDefault(false)) {
            giveUp("model_read_refused")
        }
    }

    private fun handleRead(gatt: BluetoothGatt, uuid: UUID, value: ByteArray, status: Int) {
        if (uuid != FootPodUuids.MODEL_NUMBER) {
            return
        }

        if (status != BluetoothGatt.GATT_SUCCESS) {
            giveUp("model_read_failed status=$status")
            return
        }

        val model = value.toString(Charsets.UTF_8)
        if (model != FootPodModel.BRIDGE) {
            // Another running sensor, for example a relay like this one.
            reject(gatt, "model=$model")
            return
        }

        val failure = subscribe(gatt)
        if (failure != null) {
            giveUp("subscription_refused reason=$failure")
        }
    }

    /** Returns null when switching the notifications on was started, otherwise the reason it was not. */
    @SuppressLint("MissingPermission")
    private fun subscribe(gatt: BluetoothGatt): String? {
        val measurement = gatt.getService(FootPodUuids.RSC_SERVICE)?.getCharacteristic(FootPodUuids.RSC_MEASUREMENT)
            ?: return "measurement_missing"
        val descriptor = measurement.getDescriptor(FootPodUuids.CCCD) ?: return "cccd_missing"
        return runCatching {
            if (!gatt.setCharacteristicNotification(measurement, true)) {
                return@runCatching "set_notification_rejected"
            }

            val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
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

    private fun handleSubscribed(characteristic: UUID, status: Int) {
        if (characteristic != FootPodUuids.RSC_MEASUREMENT) {
            return
        }

        if (status != BluetoothGatt.GATT_SUCCESS) {
            giveUp("subscription_failed status=$status")
            return
        }

        mainHandler.removeCallbacks(setUpTimeout)
        setUp = true
        callbacks.onSourceDiagnostic("source subscribed")
        setStatus(SourceStatus.CONNECTED)
    }

    private fun handleValue(uuid: UUID, value: ByteArray) {
        if (setUp && uuid == FootPodUuids.RSC_MEASUREMENT) {
            callbacks.onSourceMeasurement(value)
        }
    }

    /** Leaves a sensor that is not the bridge alone for a while. */
    @SuppressLint("MissingPermission")
    private fun reject(gatt: BluetoothGatt, reason: String) {
        rejected.reject(gatt.device.address, SystemClock.elapsedRealtime())
        callbacks.onSourceDiagnostic("source rejected address=${gatt.device.address} reason=$reason")
        releaseGatt()
        searchLater(RETRY_DELAY_MS)
    }

    /** Ends an attempt that failed for a reason that may not recur. */
    private fun giveUp(reason: String) {
        callbacks.onSourceDiagnostic("source attempt_failed reason=$reason")
        releaseGatt()
        searchLater(RETRY_DELAY_MS)
    }

    /** Closes the client, so repeated attempts do not exhaust the system's GATT interfaces. */
    @SuppressLint("MissingPermission")
    private fun releaseGatt() {
        mainHandler.removeCallbacks(setUpTimeout)
        val current = gatt
        gatt = null
        setUp = false
        if (current != null) {
            runCatching {
                current.disconnect()
                current.close()
            }
        }
    }

    private fun setStatus(next: SourceStatus) {
        if (status == next) {
            return
        }

        status = next
        callbacks.onSourceStatus(next)
    }

    private companion object {
        /** Connecting, reading the model and subscribing take about a second when all is well. */
        const val SET_UP_TIMEOUT_MS = 20_000L

        /** Android ignores a search that is the sixth to begin within 30 seconds. */
        const val RETRY_DELAY_MS = 6_500L
        const val RESUME_DELAY_MS = 1_000L
        const val SCAN_RENEWAL_MS = 10 * 60_000L

        /** How much longer than a planned wait the phone is kept awake, so the search can begin. */
        const val WAKE_MARGIN_MS = 3_000L
    }
}
