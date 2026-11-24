package de.ma.ftms.bridge.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** True when everything the bridge cannot work without has been granted. */
fun hasRuntimePermissions(context: Context): Boolean =
    requiredRuntimePermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

/** Permissions without which scanning and connecting are impossible. */
fun requiredRuntimePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

/**
 * Permissions to ask the user for. Notifications are requested so the running bridge is visible,
 * but declining them does not block the bridge: the foreground service keeps working without a
 * visible notification.
 */
fun requestableRuntimePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        requiredRuntimePermissions() + advertisePermissions() + Manifest.permission.POST_NOTIFICATIONS
    } else {
        requiredRuntimePermissions() + advertisePermissions()
    }

/**
 * Needed only for the running sensor the app can provide for the watch. Android shows it in the
 * same "Nearby devices" group as scanning and connecting, so it adds no separate question there.
 */
fun advertisePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE)
    } else {
        emptyArray()
    }

/** True when the phone may be made visible as a Bluetooth sensor. */
fun hasAdvertisePermission(context: Context): Boolean =
    advertisePermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
