package de.ma.ftms.relay

import android.content.Context
import androidx.core.content.edit

/** Remembers whether the relay is meant to run, so it comes back after a restart of the phone. */
internal object RelayPreferences {
    fun isEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit { putBoolean(KEY_ENABLED, enabled) }
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences("relay", Context.MODE_PRIVATE)

    private const val KEY_ENABLED = "enabled"
}
