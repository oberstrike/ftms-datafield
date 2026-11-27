package de.ma.ftms.relay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A relay that was running before the app was closed by the system goes on when it is opened.
        if (RelayPreferences.isEnabled(this) && hasRelayPermissions(this)) {
            startRelay(this)
        }

        setContent {
            RelayTheme {
                RelayScreen()
            }
        }
    }
}

/** Starts the relay; if Android refuses, the reason shows up among the events. */
private fun startRelay(context: Context) {
    runCatching { RelayService.start(context) }
        .onFailure { RelayRuntime.note("start refused error=${it.message}") }
}

@Composable
private fun RelayScreen() {
    val context = LocalContext.current
    val state by RelayRuntime.state.collectAsStateWithLifecycle()
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var batteryUnrestricted by remember { mutableStateOf(isBatteryUnrestricted(context)) }
    LifecycleResumeEffect(Unit) {
        batteryUnrestricted = isBatteryUnrestricted(context)
        onPauseOrDispose {}
    }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionDenied = !hasRelayPermissions(context)
        if (!permissionDenied) {
            startRelay(context)
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.intro), style = MaterialTheme.typography.bodyMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusRow(stringResource(R.string.label_source), stringResource(sourceStatusText(state.source)))
                    StatusRow(stringResource(R.string.label_sensor), stringResource(sensorStatusText(state.sensor)))
                    StatusRow(stringResource(R.string.label_measurements), measurementsText(state))
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    when {
                        state.running -> RelayService.stop(context)
                        hasRelayPermissions(context) -> startRelay(context)
                        else -> permissionRequest.launch(requestableRelayPermissions())
                    }
                },
            ) {
                Text(stringResource(if (state.running) R.string.action_stop else R.string.action_start))
            }

            if (permissionDenied) {
                Text(
                    stringResource(R.string.permission_denied),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (!batteryUnrestricted) {
                Text(stringResource(R.string.battery_text), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { askForBackgroundOperation(context) }) {
                    Text(stringResource(R.string.battery_action))
                }
            }

            if (state.events.isNotEmpty()) {
                Text(stringResource(R.string.label_events), style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    state.events.asReversed().forEach { event ->
                        Text(
                            "${clockTime(event.atMillis)} ${event.message}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun measurementsText(state: RelayState): String {
    val last = state.last
    val lastAtMillis = state.lastAtMillis
    if (last == null || lastAtMillis == null) {
        return stringResource(R.string.measurements_none)
    }

    val counts = stringResource(R.string.measurements_counts, state.received, state.delivered)
    val speed = String.format(LocalLocale.current.platformLocale, "%.1f", last.speedKmh)
    val values = if (last.distanceM != null) {
        stringResource(R.string.measurements_last, clockTime(lastAtMillis), speed, last.stepsPerMinute, last.distanceM)
    } else {
        stringResource(R.string.measurements_last_without_distance, clockTime(lastAtMillis), speed, last.stepsPerMinute)
    }
    return "$counts\n$values"
}

private fun clockTime(atMillis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(atMillis))

private fun isBatteryUnrestricted(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true

/** Opens Android's question whether the app may keep running in standby. */
@SuppressLint("BatteryLife")
private fun askForBackgroundOperation(context: Context) {
    val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
    runCatching { context.startActivity(request) }
        .onFailure { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
}
