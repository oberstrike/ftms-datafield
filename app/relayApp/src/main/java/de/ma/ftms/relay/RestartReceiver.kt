package de.ma.ftms.relay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Brings the relay back after the phone was restarted or the app was updated, if it was running. */
class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val expected = intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!expected || !RelayPreferences.isEnabled(context)) {
            return
        }

        runCatching { RelayService.start(context) }
            .onFailure { RelayRuntime.note("restart refused action=${intent.action} error=${it.message}") }
    }
}
