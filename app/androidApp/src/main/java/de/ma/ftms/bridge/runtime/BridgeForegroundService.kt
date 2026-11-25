package de.ma.ftms.bridge.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import de.ma.ftms.bridge.MainActivity
import de.ma.ftms.bridge.R
import de.ma.ftms.bridge.i18n.resolveStrings

class BridgeForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        BridgeRuntime.initialize(this)
        ensureNotificationChannel()
    }

    /** True once Android let this service into the foreground. */
    private var inForeground = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_STOP -> {
                if (BridgeRuntime.state.value.running) {
                    // The runtime stops the service itself, unless it goes on waiting for the watch.
                    BridgeRuntime.stopBridge()
                } else {
                    // Without a session the action belongs to the waiting notification.
                    BridgeRuntime.dismissWatchWait()
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> BridgeRuntime.pauseBridge()
            ACTION_RESUME -> BridgeRuntime.resumeBridge()
        }

        if (action != ACTION_START && action != ACTION_WAIT) {
            if (inForeground && BridgeRuntime.isForegroundServiceNeeded()) {
                getSystemService<NotificationManager>()?.notify(NOTIFICATION_ID, notification())
            } else {
                // Nothing to keep alive: the process was restarted without a running bridge, or an
                // action of an outdated notification arrived. Do not leave a notification behind
                // that claims the bridge is active.
                getSystemService<NotificationManager>()?.cancel(NOTIFICATION_ID)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        // A service started for the foreground has to enter it, even if it is no longer needed.
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), foregroundType)
            inForeground = true
            BridgeRuntime.onForegroundServiceStarted()
        } catch (error: RuntimeException) {
            // Android refuses the foreground start when the Bluetooth permission was revoked or
            // the app is not allowed to start foreground services right now.
            val reason = error.message ?: error::class.simpleName.orEmpty()
            if (action == ACTION_WAIT && !BridgeRuntime.state.value.running) {
                BridgeRuntime.onWatchWaitUnavailable(reason)
            } else {
                BridgeRuntime.onForegroundServiceUnavailable(reason)
            }
            stopSelf()
            return START_NOT_STICKY
        }
        if (!BridgeRuntime.isForegroundServiceNeeded()) {
            // What it was started for ended in the meantime.
            stopSelf()
        }
        // The bridge state lives in this process only, so a restart by the system after the
        // process was killed could not resume the session anyway. Waiting for the watch cannot
        // be resumed either: Android refuses the foreground to a service it restarts itself.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        inForeground = false
        BridgeRuntime.onForegroundServiceStopped()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            notificationStrings().channel,
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    private fun notification(): Notification {
        val strings = notificationStrings()
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, BridgeForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, BridgeForegroundService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val resumeIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, BridgeForegroundService::class.java).setAction(ACTION_RESUME),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val paused = BridgeRuntime.state.value.paused
        val waiting = BridgeRuntime.isWaitingForWatch()

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bridge)
            .setContentTitle(if (waiting) strings.waitingTitle else strings.title)
            .setContentText(
                when {
                    waiting -> strings.waitingText
                    paused -> strings.pausedText
                    else -> strings.text
                },
            )
            .setContentIntent(openAppIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)

        if (BridgeRuntime.state.value.settings.showOpenAppNotificationAction) {
            builder.addAction(R.drawable.ic_stat_bridge, strings.openAppAction, openAppIntent)
        }

        if (waiting) {
            return builder
                .addAction(R.drawable.ic_stat_bridge, strings.stopWaitingAction, stopIntent)
                .build()
        }

        builder.addAction(
            R.drawable.ic_stat_bridge,
            if (paused) strings.resumeAction else strings.pauseAction,
            if (paused) resumeIntent else pauseIntent,
        )

        return builder
            .addAction(R.drawable.ic_stat_bridge, strings.stopAction, stopIntent)
            .build()
    }

    private fun notificationStrings() = resolveStrings(BridgeRuntime.state.value.settings.language).notification

    companion object {
        const val ACTION_START = "de.ma.ftms.bridge.START"
        const val ACTION_WAIT = "de.ma.ftms.bridge.WAIT"
        const val ACTION_REFRESH = "de.ma.ftms.bridge.REFRESH"
        const val ACTION_PAUSE = "de.ma.ftms.bridge.PAUSE"
        const val ACTION_RESUME = "de.ma.ftms.bridge.RESUME"
        const val ACTION_STOP = "de.ma.ftms.bridge.STOP"
    }
}
