package de.ma.ftms.bridge.runtime

import de.ma.ftms.bridge.debug.LogEntry
import de.ma.ftms.bridge.i18n.AppLanguage
import de.ma.ftms.core.SmoothedTreadmillSample
import de.ma.ftms.core.storage.SessionSampleRecord
import de.ma.ftms.core.storage.WorkoutSessionRecord
import java.util.UUID

internal const val WATCH_APP_ID = "8eb0b6152ef04aa7a1687c67ce46bfdf"
internal val WATCH_VARIANT_APP_IDS = listOf(
    WATCH_APP_ID,
    "bc58d0ad4f40470f8a5303b891839681",
    "f87c0efb71f44bf79f965d95f4261f89",
    "29ac77102480444d947df8e8b3703b65",
    "a2f6ed06f31c4598be28a8a37dd054db",
    "cbcb54002f7949deacbf25ef925977fe",
    "dfb711bb7b1648a1890688b85c7aa39f",
    "ed07928d924147c38ce6ddcb0fe41937",
    "9ef0c46cf2224a9982f3c6b2b4c987df",
)
internal const val TREADMILL_PREFIX = "FS-BC11B"
internal const val NOTIFICATION_ID = 1826
internal const val NOTIFICATION_CHANNEL_ID = "ftms_bridge"
internal const val PACKET_TIMEOUT_MS = 15_000L
internal const val DEFAULT_SEND_INTERVAL_MS = 1_000L

internal val FTMS_SERVICE_UUID: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
internal val INDOOR_BIKE_DATA_UUID: UUID = UUID.fromString("00002ad2-0000-1000-8000-00805f9b34fb")
internal val TREADMILL_DATA_UUID: UUID = UUID.fromString("00002acd-0000-1000-8000-00805f9b34fb")
internal val CROSS_TRAINER_DATA_UUID: UUID = UUID.fromString("00002ace-0000-1000-8000-00805f9b34fb")
internal val RSC_SERVICE_UUID: UUID = UUID.fromString("00001814-0000-1000-8000-00805f9b34fb")
internal val RSC_MEASUREMENT_UUID: UUID = UUID.fromString("00002a53-0000-1000-8000-00805f9b34fb")
internal val VENDOR_SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
internal val VENDOR_NOTIFY_UUID: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
internal val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

data class TreadmillDeviceItem(
    val address: String,
    val name: String,
    val rssi: Int,
    val hasFtms: Boolean,
)

data class GarminDeviceItem(
    val id: Long,
    val name: String,
    val status: String,
    val appInstalled: Boolean?,
)

data class LastSessionSummary(
    val startedAtMillis: Long = 0,
    val stoppedAtMillis: Long = 0,
    val finalStatus: String = "none",
    val packetCount: Int = 0,
    val sendSuccessCount: Int = 0,
    val sendFailureCount: Int = 0,
    val lastError: String = "",
    val finalDistanceM: Double = 0.0,
    val finalAscentM: Double = 0.0,
    val finalSpeedKmh: Double = 0.0,
)

data class BridgeSettings(
    val sendToAllInstalledVariants: Boolean = true,
    val autoReconnect: Boolean = true,
    val packetTimeoutSeconds: Int = 15,
    val sendIntervalMillis: Long = DEFAULT_SEND_INTERVAL_MS,
    val maxLogEntries: Int = 5_000,
    val debugLoggingEnabled: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM_DEFAULT,
) {
    val packetTimeoutMillis: Long
        get() = packetTimeoutSeconds * 1_000L
}

data class BridgeUiState(
    val permissionsGranted: Boolean = false,
    val bleAvailable: Boolean = false,
    val scanning: Boolean = false,
    val running: Boolean = false,
    val treadmillConnected: Boolean = false,
    val garminReady: Boolean = false,
    val selectedTreadmillAddress: String? = null,
    val selectedTreadmillName: String? = null,
    val selectedGarminId: Long? = null,
    val selectedGarminName: String? = null,
    val treadmills: List<TreadmillDeviceItem> = emptyList(),
    val garmins: List<GarminDeviceItem> = emptyList(),
    val garminSdkStatus: String = "not initialized",
    val garminKnownDeviceCount: Int = 0,
    val garminLastMessage: String = "",
    val latest: SmoothedTreadmillSample? = null,
    val lastSendStatus: String = "idle",
    val reconnectStatus: String = "idle",
    val nextRetryDelayMs: Long = 0,
    val packetCount: Int = 0,
    val sendSuccessCount: Int = 0,
    val sendFailureCount: Int = 0,
    val lastError: String = "",
    val lastSummary: LastSessionSummary? = null,
    val sessions: List<WorkoutSessionRecord> = emptyList(),
    val selectedHistorySessionId: String? = null,
    val selectedHistorySamples: List<SessionSampleRecord> = emptyList(),
    val logs: List<LogEntry> = emptyList(),
    val diagnosticLogs: List<LogEntry> = emptyList(),
    val logCaptureEnabled: Boolean = false,
    val latestLogFilePath: String? = null,
    val latestDiagnosticLogPath: String? = null,
    val settings: BridgeSettings = BridgeSettings(),
)
