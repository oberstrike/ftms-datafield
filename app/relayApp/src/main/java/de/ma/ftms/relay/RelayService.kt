package de.ma.ftms.relay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.ma.ftms.core.FootPodStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Keeps the relay running while the app is not on screen, and shows that it runs. */
class RelayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var notificationUpdates: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            RelayPreferences.setEnabled(this, false)
            RelayRuntime.stop()
            stopSelf()
            return START_NOT_STICKY
        }

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(RelayRuntime.state.value), foregroundType)
        } catch (error: RuntimeException) {
            // Android refuses the foreground when the Bluetooth permission is missing, or when it
            // restarts the service itself and the app may not run in the background.
            RelayRuntime.note("foreground refused error=${error.message ?: error::class.simpleName}")
            stopSelf()
            return START_NOT_STICKY
        }

        RelayPreferences.setEnabled(this, true)
        RelayRuntime.start(this)
        if (notificationUpdates == null) {
            notificationUpdates = scope.launch {
                RelayRuntime.state
                    .map { it.source to it.sensor }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect {
                        getSystemService(NotificationManager::class.java)
                            ?.notify(NOTIFICATION_ID, notification(RelayRuntime.state.value))
                    }
            }
        }
        // The relay holds nothing that a restart would lose, so Android may bring it back.
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        RelayRuntime.stop()
        super.onDestroy()
    }

    private fun notification(state: RelayState): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, RelayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = getString(
            R.string.notification_text,
            getString(sourceStatusText(state.source)),
            getString(sensorStatusText(state.sensor)),
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_relay)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(R.drawable.ic_stat_relay, getString(R.string.notification_stop), stop)
            .build()
    }

    companion object {
        private const val ACTION_STOP = "de.ma.ftms.relay.STOP"
        private const val NOTIFICATION_ID = 1814
        private const val NOTIFICATION_CHANNEL_ID = "ftms_relay"

        /** Starts the relay and remembers that it is meant to run. Throws if Android refuses. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, RelayService::class.java))
        }

        /** Stops the relay and remembers that it is meant to stay off. */
        fun stop(context: Context) {
            RelayPreferences.setEnabled(context, false)
            context.stopService(Intent(context, RelayService::class.java))
        }
    }
}

/** The text resource for [status], shared by the screen and the notification. */
internal fun sourceStatusText(status: SourceStatus): Int =
    when (status) {
        SourceStatus.OFF -> R.string.source_off
        SourceStatus.PERMISSION_MISSING -> R.string.source_permission_missing
        SourceStatus.BLUETOOTH_OFF -> R.string.source_bluetooth_off
        SourceStatus.SEARCHING -> R.string.source_searching
        SourceStatus.CONNECTING -> R.string.source_connecting
        SourceStatus.CONNECTED -> R.string.source_connected
    }

internal fun sensorStatusText(status: FootPodStatus): Int =
    when (status) {
        FootPodStatus.OFF -> R.string.sensor_off
        FootPodStatus.UNSUPPORTED -> R.string.sensor_unsupported
        FootPodStatus.PERMISSION_MISSING -> R.string.sensor_permission_missing
        FootPodStatus.ADVERTISING -> R.string.sensor_advertising
        FootPodStatus.CONNECTED -> R.string.sensor_connected
        FootPodStatus.FAILED -> R.string.sensor_failed
    }
