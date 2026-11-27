package de.ma.ftms.relay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Permissions without which the relay can neither find the bridge nor be a sensor. */
internal fun requiredRelayPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

/**
 * Permissions to ask for. Notifications are asked for so the running relay is visible; declining
 * them does not stop it.
 */
internal fun requestableRelayPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        requiredRelayPermissions() + Manifest.permission.POST_NOTIFICATIONS
    } else {
        requiredRelayPermissions()
    }

internal fun hasRelayPermissions(context: Context): Boolean =
    requiredRelayPermissions().all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
