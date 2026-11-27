package de.ma.ftms.relay

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import de.ma.ftms.core.FootPodModel
import de.ma.ftms.core.FootPodStatus
import de.ma.ftms.footpod.VirtualFootPod
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The relay itself: every measurement of the bridge's running sensor goes out unchanged through
 * the running sensor this phone offers to the watch.
 *
 * Lives as long as the process; [RelayService] keeps the process alive while the relay runs. It
 * holds the application context only, so the static references leak no activity or service. All
 * functions must be called on the main thread.
 */
@SuppressLint("StaticFieldLeak")
internal object RelayRuntime : VirtualFootPod.Callbacks, FootPodSourceClient.Callbacks {
    private val _state = MutableStateFlow(RelayState())
    val state: StateFlow<RelayState> = _state.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var log: RelayLog? = null
    private var footPod: VirtualFootPod? = null
    private var source: FootPodSourceClient? = null
    private var bluetoothReceiver: BroadcastReceiver? = null
    private var summarizedReceived = 0L

    /** Writes into the log now and then how much went through, without filling the screen with it. */
    private val summary = object : Runnable {
        override fun run() {
            val current = _state.value
            if (current.received != summarizedReceived) {
                summarizedReceived = current.received
                record("forwarded received=${current.received} delivered=${current.delivered} last=${describe(current.last)}")
            }
            mainHandler.postDelayed(this, SUMMARY_INTERVAL_MS)
        }
    }

    /** Starts the sensor for the watch and the search for the bridge. Does nothing if already running. */
    fun start(context: Context) {
        if (_state.value.running) {
            return
        }

        val app = context.applicationContext
        appContext = app
        log = log ?: RelayLog(File(app.filesDir, LOG_DIRECTORY))
        footPod = VirtualFootPod(app, FootPodModel.RELAY, this)
        source = FootPodSourceClient(app, this)
        summarizedReceived = 0L
        _state.update { RelayState(running = true, events = it.events) }
        note("relay started")

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val bluetooth = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (bluetooth == BluetoothAdapter.STATE_ON || bluetooth == BluetoothAdapter.STATE_OFF) {
                    note("bluetooth ${if (bluetooth == BluetoothAdapter.STATE_ON) "on" else "off"}")
                    restartRadio()
                }
            }
        }
        ContextCompat.registerReceiver(
            app,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        bluetoothReceiver = receiver

        footPod?.start()
        source?.start()
        mainHandler.postDelayed(summary, SUMMARY_INTERVAL_MS)
    }

    /** Ends everything. A connected watch loses the sensor. */
    fun stop() {
        if (!_state.value.running) {
            return
        }

        mainHandler.removeCallbacks(summary)
        bluetoothReceiver?.let { receiver -> runCatching { appContext?.unregisterReceiver(receiver) } }
        bluetoothReceiver = null
        source?.stop()
        footPod?.stop()
        source = null
        footPod = null
        _state.update { it.copy(running = false, source = SourceStatus.OFF, sensor = FootPodStatus.OFF) }
        note("relay stopped received=${_state.value.received} delivered=${_state.value.delivered}")
    }

    /** Sets both sides up again, after Bluetooth was switched or a permission was granted. */
    fun restartRadio() {
        if (!_state.value.running) {
            return
        }

        source?.stop()
        footPod?.stop()
        footPod?.start()
        source?.start()
    }

    /** Puts [message] into the event list and the log, also while the relay is not running. */
    fun note(message: String) {
        val event = RelayEvent(System.currentTimeMillis(), message)
        _state.update { it.copy(events = (it.events + event).takeLast(MAX_EVENTS)) }
        record(message)
    }

    private fun record(message: String) {
        Log.i(TAG, message)
        log?.append(System.currentTimeMillis(), message)
    }

    override fun onFootPodStatus(status: FootPodStatus) {
        _state.update { it.copy(sensor = status) }
        note("sensor status=$status")
    }

    override fun onFootPodDiagnostic(message: String) {
        note(message)
    }

    override fun onSourceStatus(status: SourceStatus) {
        _state.update { it.copy(source = status) }
        note("source status=$status")
    }

    override fun onSourceDiagnostic(message: String) {
        note(message)
    }

    override fun onSourceMeasurement(bytes: ByteArray) {
        val watches = footPod?.publish(bytes) ?: 0
        _state.update {
            it.copy(
                received = it.received + 1,
                delivered = it.delivered + if (watches > 0) 1 else 0,
                last = summarize(bytes),
                lastAtMillis = System.currentTimeMillis(),
            )
        }
    }

    private fun describe(measurement: MeasurementSummary?): String =
        if (measurement == null) {
            "none"
        } else {
            String.format(
                Locale.US,
                "%.2fkm/h,%dspm,%sm",
                measurement.speedKmh,
                measurement.stepsPerMinute,
                measurement.distanceM ?: "-",
            )
        }

    private const val TAG = "FtmsRelay"
    private const val LOG_DIRECTORY = "logs"
    private const val SUMMARY_INTERVAL_MS = 60_000L
}
