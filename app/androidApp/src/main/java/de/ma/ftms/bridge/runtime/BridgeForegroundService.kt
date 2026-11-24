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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                BridgeRuntime.stopBridge { stopSelf() }
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> BridgeRuntime.pauseBridge()
            ACTION_RESUME -> BridgeRuntime.resumeBridge()
        }

        if (intent?.action != ACTION_START) {
            if (BridgeRuntime.state.value.running) {
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

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), foregroundType)
        } catch (error: RuntimeException) {
            // Android refuses the foreground start when the Bluetooth permission was revoked or
            // the app is not allowed to start foreground services right now.
            BridgeRuntime.onForegroundServiceUnavailable(error.message ?: error::class.simpleName.orEmpty())
            stopSelf()
        }
        // The bridge state lives in this process only, so a restart by the system after the
        // process was killed could not resume the session anyway.
        return START_NOT_STICKY
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

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bridge)
            .setContentTitle(strings.title)
            .setContentText(if (paused) strings.pausedText else strings.text)
            .setContentIntent(openAppIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)

        if (BridgeRuntime.state.value.settings.showOpenAppNotificationAction) {
            builder.addAction(R.drawable.ic_stat_bridge, strings.openAppAction, openAppIntent)
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
        const val ACTION_REFRESH = "de.ma.ftms.bridge.REFRESH"
        const val ACTION_PAUSE = "de.ma.ftms.bridge.PAUSE"
        const val ACTION_RESUME = "de.ma.ftms.bridge.RESUME"
        const val ACTION_STOP = "de.ma.ftms.bridge.STOP"
    }
}
