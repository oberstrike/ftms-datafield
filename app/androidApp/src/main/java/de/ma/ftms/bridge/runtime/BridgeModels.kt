package de.ma.ftms.bridge.runtime

import de.ma.ftms.bridge.debug.LogEntry
import de.ma.ftms.bridge.i18n.AppLanguage
import de.ma.ftms.core.FootPodStatus
import de.ma.ftms.core.SmoothedTreadmillSample
import de.ma.ftms.core.storage.SessionAverages
import de.ma.ftms.core.storage.SessionSampleRecord
import de.ma.ftms.core.storage.WorkoutSessionRecord
import java.util.UUID

internal const val WATCH_APP_ID = "8eb0b6152ef04aa7a1687c67ce46bfdf"
internal val WATCH_APP_IDS = listOf(
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
internal val FITNESS_MACHINE_CONTROL_POINT_UUID: UUID = UUID.fromString("00002ad9-0000-1000-8000-00805f9b34fb")
internal val SUPPORTED_POWER_RANGE_UUID: UUID = UUID.fromString("00002ad8-0000-1000-8000-00805f9b34fb")
internal val FITNESS_MACHINE_FEATURE_UUID: UUID = UUID.fromString("00002acc-0000-1000-8000-00805f9b34fb")
internal val SUPPORTED_SPEED_RANGE_UUID: UUID = UUID.fromString("00002ad4-0000-1000-8000-00805f9b34fb")
internal val SUPPORTED_INCLINATION_RANGE_UUID: UUID = UUID.fromString("00002ad5-0000-1000-8000-00805f9b34fb")
internal val FITNESS_MACHINE_STATUS_UUID: UUID = UUID.fromString("00002ada-0000-1000-8000-00805f9b34fb")
internal val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

data class TreadmillDeviceItem(
    val address: String,
    val name: String,
    val rssi: Int,
    val hasFtms: Boolean,
    /** Equipment type the advertisement names, if it names exactly one. */
    val advertisedType: EquipmentType? = null,
)

data class GarminDeviceItem(
    val id: Long,
    val name: String,
    val status: String,
    val appInstalled: Boolean?,
)

/** Why the Connect IQ SDK cannot reach the Garmin Connect app on this phone. */
enum class GarminConnectProblem {
    NOT_INSTALLED,
    UPGRADE_NEEDED,
    SERVICE_ERROR,
}

/** What auto-start is doing while the bridge is idle. */
enum class AutoStartStatus {
    /** Not active: switched off, the app is hidden, or something it needs is missing. */
    OFF,

    /** Looking for the selected equipment, which is switched off or out of range. */
    WAITING,

    /** Connected to the equipment; the bridge starts when it moves. */
    READY,

    /** Connected, but the equipment has to stop before the next start. */
    HOLDING,
}

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

enum class DashboardMetricKey {
    SPEED,
    PACE,
    AVG_PACE,
    INCLINE,
    AVG_INCLINE,
    DISTANCE,
    ASCENT,
    ASCENT_RATE,
    POWER,
    HEART_RATE,
    CADENCE_STEP,
    ELAPSED,
    RESISTANCE,
    MACHINE,
}

val DEFAULT_DASHBOARD_METRICS: List<DashboardMetricKey> = listOf(
    DashboardMetricKey.SPEED,
    DashboardMetricKey.PACE,
    DashboardMetricKey.AVG_PACE,
    DashboardMetricKey.INCLINE,
    DashboardMetricKey.AVG_INCLINE,
    DashboardMetricKey.DISTANCE,
    DashboardMetricKey.ASCENT,
    DashboardMetricKey.ASCENT_RATE,
    DashboardMetricKey.POWER,
    DashboardMetricKey.HEART_RATE,
    DashboardMetricKey.CADENCE_STEP,
    DashboardMetricKey.ELAPSED,
    DashboardMetricKey.MACHINE,
    DashboardMetricKey.RESISTANCE,
)

/**
 * The distance is not among the charts: its line only ever rises, at the slope of the speed, and
 * says nothing the speed chart and the distance tile do not say better.
 */
val DEFAULT_DASHBOARD_GRAPHS: List<DashboardMetricKey> = listOf(
    DashboardMetricKey.SPEED,
    DashboardMetricKey.PACE,
    DashboardMetricKey.AVG_PACE,
    DashboardMetricKey.INCLINE,
    DashboardMetricKey.AVG_INCLINE,
    DashboardMetricKey.ASCENT,
    DashboardMetricKey.ASCENT_RATE,
    DashboardMetricKey.POWER,
    DashboardMetricKey.HEART_RATE,
    DashboardMetricKey.CADENCE_STEP,
    DashboardMetricKey.RESISTANCE,
)

enum class DashboardChartTimespan(val durationMillis: Long?) {
    MINUTES_1(60_000L),
    MINUTES_5(5 * 60_000L),
    MINUTES_10(10 * 60_000L),
    MINUTES_30(30 * 60_000L),
    FULL_SESSION(null),
}

enum class PowerSource {
    FTMS_PREFERRED,
    CALCULATED,
}

data class BridgeSettings(
    val autoReconnect: Boolean = true,
    val autoStart: Boolean = false,
    /** End the session by itself once the equipment has come to a stop. */
    val autoStop: Boolean = true,
    /** Start, pause and stop the session together with the activity on the watch. */
    val followWatchTimer: Boolean = true,
    /** Keep waiting for the activity on the watch after the app has left the screen. */
    val waitForWatch: Boolean = false,
    /** Offer the treadmill's speed and distance to the watch as a Bluetooth running sensor. */
    val virtualFootPod: Boolean = false,
    val packetTimeoutSeconds: Int = 15,
    val sendIntervalMillis: Long = DEFAULT_SEND_INTERVAL_MS,
    val maxLogEntries: Int = 5_000,
    val debugLoggingEnabled: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM_DEFAULT,
    val showOpenAppNotificationAction: Boolean = true,
    /** Chosen source of heart rate, power and cadence per equipment type; default if absent. */
    val sensorSources: Map<EquipmentType, SensorSources> = emptyMap(),
    val powerCalculationEnabled: Boolean = false,
    val powerSource: PowerSource = PowerSource.FTMS_PREFERRED,
    val powerBodyMassKg: Double? = null,
    val powerFlatCost: Double? = 1.22,
    /** Dashboard contents per equipment type; types without an entry use their default. */
    val dashboardLayouts: Map<EquipmentType, DashboardLayout> = emptyMap(),
    val dashboardChartTimespan: DashboardChartTimespan = DashboardChartTimespan.MINUTES_10,
    /** The charts of a recorded session per equipment type, in their order; default if absent. */
    val historyCharts: Map<EquipmentType, List<DashboardMetricKey>> = emptyMap(),
    /** Quick choices for the target power of an indoor bike, in watts. */
    val targetPowerPresets: List<Int> = DEFAULT_TARGET_POWER_PRESETS,
    /** How far one tap on plus or minus moves the target power. */
    val targetPowerStepWatts: Int = DEFAULT_TARGET_POWER_STEP_WATTS,
    /**
     * Offer speed and incline of a treadmill on the dashboard and hold a connection to it for that
     * while the app is open. Off by default: without it a treadmill is only read.
     */
    val treadmillControl: Boolean = false,
    /** Quick choices for the speed of a treadmill, in hundredths of a km/h. */
    val treadmillSpeedPresets: List<Int> = DEFAULT_TREADMILL_SPEED_PRESETS,
    /** Quick choices for the incline of a treadmill, in tenths of a percent. */
    val treadmillInclinePresets: List<Int> = DEFAULT_TREADMILL_INCLINE_PRESETS,
    /** The larger of the two steps for the speed, in hundredths of a km/h. */
    val treadmillSpeedStep: Int = DEFAULT_TREADMILL_SPEED_STEP,
    /** Ask before a quick choice makes the belt much faster. */
    val treadmillConfirmLargeIncrease: Boolean = true,
    /** How the app takes the treadmill to a speed that is some way off. */
    val treadmillRamp: TreadmillRampProfile = TreadmillRampProfile.GENTLE,
    /**
     * Set the speed of the treadmill to what the workout on the watch asks for, step by step. Off
     * by default: with it the app sends speeds nobody tapped.
     */
    val treadmillFollowWorkout: Boolean = false,
    /** How the belt gets from the speed of one step to that of the next. */
    val treadmillFollowMode: WorkoutTransitionMode = WorkoutTransitionMode.SMART,
    /** How long a soft change between steps takes, in seconds; 0 for the pace of [treadmillRamp]. */
    val treadmillFollowTransitionSeconds: Int = 0,
    /** A step shorter than this many seconds counts as short for [WorkoutTransitionMode.SMART]; 0 for none. */
    val treadmillFollowShortStepSeconds: Int = DEFAULT_TREADMILL_FOLLOW_SHORT_STEP_SECONDS,
    /** The most a workout may ask of the belt, in hundredths of a km/h. */
    val treadmillFollowMaxSpeed: Int = DEFAULT_TREADMILL_FOLLOW_MAX_SPEED,
    /** A short tone on the phone before a workout makes the belt faster. */
    val treadmillFollowTone: Boolean = true,
    /** The four values the workout screen shows, in the order of its fields. */
    val workoutScreenFields: List<DashboardMetricKey> = DEFAULT_WORKOUT_SCREEN_FIELDS,
) {
    fun treadmillPresets(setting: TreadmillSetting): List<Int> =
        when (setting) {
            TreadmillSetting.SPEED -> treadmillSpeedPresets
            TreadmillSetting.INCLINE -> treadmillInclinePresets
        }

    fun withTreadmillPresets(setting: TreadmillSetting, presets: List<Int>): BridgeSettings {
        val valid = sanitizeTreadmillPresets(setting, presets)
        return when (setting) {
            TreadmillSetting.SPEED -> copy(treadmillSpeedPresets = valid)
            TreadmillSetting.INCLINE -> copy(treadmillInclinePresets = valid)
        }
    }

    fun dashboardLayout(type: EquipmentType): DashboardLayout =
        dashboardLayouts[type]?.forType(type) ?: defaultDashboardLayout(type)

    fun withDashboardLayout(type: EquipmentType, layout: DashboardLayout): BridgeSettings =
        copy(dashboardLayouts = dashboardLayouts + (type to layout))

    /**
     * The charts a recorded session of [type] shows. A stored choice can hold charts from an earlier
     * version that [type] no longer offers; those are left out, and a choice left empty is the default.
     */
    fun historyCharts(type: EquipmentType): List<DashboardMetricKey> {
        val offered = historyChartOptions(type)
        return historyCharts[type]?.filter { it in offered }?.distinct()?.ifEmpty { null } ?: defaultHistoryCharts(type)
    }

    fun withHistoryCharts(type: EquipmentType, charts: List<DashboardMetricKey>): BridgeSettings =
        copy(historyCharts = historyCharts + (type to charts))

    fun sensorSources(type: EquipmentType): SensorSources =
        sensorSources[type] ?: SensorSources()

    fun withSensorSource(type: EquipmentType, metric: SourcedMetric, source: ValueSource): BridgeSettings =
        copy(sensorSources = sensorSources + (type to sensorSources(type).with(metric, source)))

    val packetTimeoutMillis: Long
        get() = packetTimeoutSeconds * 1_000L
}

data class BridgeUiState(
    val permissionsGranted: Boolean = false,
    val bleAvailable: Boolean = false,
    val scanning: Boolean = false,
    val running: Boolean = false,
    val paused: Boolean = false,
    /** The watch leads the session: pause and end are decided there, not in the app. */
    val watchLed: Boolean = false,
    /** The running session began with the activity on the watch and not with a tap in the app. */
    val startedByWatch: Boolean = false,
    val activeDurationMillis: Long = 0,
    val treadmillConnected: Boolean = false,
    val garminReady: Boolean = false,
    val selectedTreadmillAddress: String? = null,
    val selectedTreadmillName: String? = null,
    val selectedEquipmentType: EquipmentType = EquipmentType.TREADMILL,
    val selectedGarminId: Long? = null,
    val selectedGarminName: String? = null,
    val treadmills: List<TreadmillDeviceItem> = emptyList(),
    val garmins: List<GarminDeviceItem> = emptyList(),
    val garminSdkStatus: String = "not initialized",
    val garminKnownDeviceCount: Int = 0,
    val garminLastMessage: String = "",
    val garminConnectProblem: GarminConnectProblem? = null,
    val autoStartStatus: AutoStartStatus = AutoStartStatus.OFF,
    val footPodStatus: FootPodStatus = FootPodStatus.OFF,
    val powerControl: PowerControlState = PowerControlState(),
    /** What the app knows about steering the connected treadmill. */
    val treadmillControl: TreadmillControlState = TreadmillControlState(),
    /** What the app knows about the workout on the watch that the treadmill follows. */
    val workoutFollow: WorkoutFollowState = WorkoutFollowState(),
    /** The changes of the target power during the running session, oldest first. */
    val targetPowerTrace: List<TargetPowerPoint> = emptyList(),
    /** The target last set in the app, offered when one is typed on the same bike. Never sent by itself. */
    val lastOwnTargetPower: RememberedTargetPower? = null,
    val latest: SmoothedTreadmillSample? = null,
    val liveDashboardSamples: List<SmoothedTreadmillSample> = emptyList(),
    val lastSendStatus: String = "idle",
    val reconnectStatus: String = "idle",
    val nextRetryDelayMs: Long = 0,
    val packetCount: Int = 0,
    val sendSuccessCount: Int = 0,
    val sendFailureCount: Int = 0,
    val lastError: String = "",
    val lastSummary: LastSessionSummary? = null,
    val sessions: List<WorkoutSessionRecord> = emptyList(),
    /** Averages over the samples of the finished sessions, by session id, as far as they are read. */
    val sessionAverages: Map<String, SessionAverages> = emptyMap(),
    val selectedHistorySessionId: String? = null,
    /** The sessions marked in the list of the history, for an action on all of them. */
    val markedHistorySessionIds: Set<String> = emptySet(),
    val selectedHistorySamples: List<SessionSampleRecord> = emptyList(),
    /** False from selecting a session until its samples have been read. */
    val selectedHistorySamplesLoaded: Boolean = false,
    val logs: List<LogEntry> = emptyList(),
    val diagnosticLogs: List<LogEntry> = emptyList(),
    val logCaptureEnabled: Boolean = false,
    val latestLogFilePath: String? = null,
    val latestDiagnosticLogPath: String? = null,
    val settings: BridgeSettings = BridgeSettings(),
)
