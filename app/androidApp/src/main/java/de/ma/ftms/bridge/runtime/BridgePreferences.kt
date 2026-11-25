package de.ma.ftms.bridge.runtime

import android.content.Context
import androidx.core.content.edit
import de.ma.ftms.bridge.i18n.toAppLanguage

internal class BridgePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("ftms_bridge_prefs", Context.MODE_PRIVATE)

    fun loadSelection(): RememberedSelection =
        RememberedSelection(
            treadmillAddress = preferences.getString(KEY_TREADMILL_ADDRESS, null),
            treadmillName = preferences.getString(KEY_TREADMILL_NAME, null),
            equipmentType = EquipmentType.fromName(preferences.getString(KEY_EQUIPMENT_TYPE, null)),
            garminId = if (preferences.contains(KEY_GARMIN_ID)) preferences.getLong(KEY_GARMIN_ID, 0) else null,
            garminName = preferences.getString(KEY_GARMIN_NAME, null),
        )

    fun saveTreadmill(item: TreadmillDeviceItem) {
        preferences.edit {
            putString(KEY_TREADMILL_ADDRESS, item.address)
            putString(KEY_TREADMILL_NAME, item.name)
        }
    }

    fun saveEquipmentType(type: EquipmentType) {
        preferences.edit { putString(KEY_EQUIPMENT_TYPE, type.name) }
    }

    fun saveGarmin(item: GarminDeviceItem) {
        preferences.edit {
            putLong(KEY_GARMIN_ID, item.id)
            putString(KEY_GARMIN_NAME, item.name)
        }
    }

    fun loadTargetPower(): RememberedTargetPower? {
        val address = preferences.getString(KEY_TARGET_POWER_ADDRESS, null) ?: return null
        val watts = preferences.getInt(KEY_TARGET_POWER_WATTS, 0).takeIf { it > 0 } ?: return null
        return RememberedTargetPower(address, watts)
    }

    fun saveTargetPower(target: RememberedTargetPower?) {
        preferences.edit {
            if (target == null) {
                remove(KEY_TARGET_POWER_ADDRESS)
                remove(KEY_TARGET_POWER_WATTS)
            } else {
                putString(KEY_TARGET_POWER_ADDRESS, target.equipmentAddress)
                putInt(KEY_TARGET_POWER_WATTS, target.watts)
            }
        }
    }

    fun clearSelection() {
        preferences.edit {
            remove(KEY_TREADMILL_ADDRESS)
            remove(KEY_TREADMILL_NAME)
            remove(KEY_EQUIPMENT_TYPE)
            remove(KEY_GARMIN_ID)
            remove(KEY_GARMIN_NAME)
        }
    }

    fun loadSettings(): BridgeSettings =
        BridgeSettings(
            autoReconnect = preferences.getBoolean(
                KEY_SETTINGS_AUTO_RECONNECT,
                BridgeSettings().autoReconnect,
            ),
            autoStart = preferences.getBoolean(KEY_SETTINGS_AUTO_START, BridgeSettings().autoStart),
            autoStop = preferences.getBoolean(KEY_SETTINGS_AUTO_STOP, BridgeSettings().autoStop),
            followWatchTimer = preferences.getBoolean(KEY_SETTINGS_FOLLOW_WATCH_TIMER, BridgeSettings().followWatchTimer),
            waitForWatch = preferences.getBoolean(KEY_SETTINGS_WAIT_FOR_WATCH, BridgeSettings().waitForWatch),
            virtualFootPod = preferences.getBoolean(KEY_SETTINGS_VIRTUAL_FOOT_POD, BridgeSettings().virtualFootPod),
            packetTimeoutSeconds = preferences.getInt(
                KEY_SETTINGS_PACKET_TIMEOUT_SECONDS,
                BridgeSettings().packetTimeoutSeconds,
            ).coerceIn(ALLOWED_PACKET_TIMEOUT_SECONDS),
            sendIntervalMillis = preferences.getLong(
                KEY_SETTINGS_SEND_INTERVAL_MS,
                BridgeSettings().sendIntervalMillis,
            ).coerceIn(ALLOWED_SEND_INTERVAL_MS),
            maxLogEntries = preferences.getInt(
                KEY_SETTINGS_MAX_LOG_ENTRIES,
                BridgeSettings().maxLogEntries,
            ).coerceIn(ALLOWED_MAX_LOG_ENTRIES),
            debugLoggingEnabled = preferences.getBoolean(
                KEY_SETTINGS_DEBUG_LOGGING_ENABLED,
                BridgeSettings().debugLoggingEnabled,
            ),
            language = preferences.getString(
                KEY_SETTINGS_LANGUAGE,
                BridgeSettings().language.name,
            ).toAppLanguage(),
            showOpenAppNotificationAction = preferences.getBoolean(
                KEY_SETTINGS_SHOW_OPEN_APP_NOTIFICATION_ACTION,
                BridgeSettings().showOpenAppNotificationAction,
            ),
            sensorSources = loadSensorSources(),
            powerCalculationEnabled = preferences.getBoolean(
                KEY_SETTINGS_POWER_CALCULATION_ENABLED,
                BridgeSettings().powerCalculationEnabled,
            ),
            powerSource = preferences.getString(
                KEY_SETTINGS_POWER_SOURCE,
                null,
            ).toPowerSource(),
            powerBodyMassKg = preferences.getString(
                KEY_SETTINGS_POWER_BODY_MASS_KG,
                null,
            ).toPositiveDoubleOrNull(),
            powerFlatCost = preferences.getString(
                KEY_SETTINGS_POWER_FLAT_COST,
                null,
            ).toPositiveDoubleOrNull() ?: BridgeSettings().powerFlatCost,
            dashboardLayouts = EquipmentType.entries.associateWith { type ->
                val default = defaultDashboardLayout(type)
                DashboardLayout(
                    metrics = loadMetricList(dashboardMetricsKey(type), default.metrics),
                    graphs = loadMetricList(dashboardGraphsKey(type), default.graphs),
                )
            },
            dashboardChartTimespan = preferences.getString(
                KEY_SETTINGS_DASHBOARD_CHART_TIMESPAN,
                null,
            ).toDashboardChartTimespan(),
            targetPowerPresets = preferences.getString(KEY_SETTINGS_TARGET_POWER_PRESETS, null)
                ?.let { raw -> sanitizeTargetPowerPresets(raw.split(',').mapNotNull { it.trim().toIntOrNull() }) }
                ?: DEFAULT_TARGET_POWER_PRESETS,
            targetPowerStepWatts = preferences.getInt(
                KEY_SETTINGS_TARGET_POWER_STEP,
                DEFAULT_TARGET_POWER_STEP_WATTS,
            ).takeIf { it in TARGET_POWER_STEP_OPTIONS } ?: DEFAULT_TARGET_POWER_STEP_WATTS,
        )

    fun saveSettings(settings: BridgeSettings) {
        preferences.edit {
            putBoolean(KEY_SETTINGS_AUTO_RECONNECT, settings.autoReconnect)
            putBoolean(KEY_SETTINGS_AUTO_START, settings.autoStart)
            putBoolean(KEY_SETTINGS_AUTO_STOP, settings.autoStop)
            putBoolean(KEY_SETTINGS_FOLLOW_WATCH_TIMER, settings.followWatchTimer)
            putBoolean(KEY_SETTINGS_WAIT_FOR_WATCH, settings.waitForWatch)
            putBoolean(KEY_SETTINGS_VIRTUAL_FOOT_POD, settings.virtualFootPod)
            putInt(KEY_SETTINGS_PACKET_TIMEOUT_SECONDS, settings.packetTimeoutSeconds)
            putLong(KEY_SETTINGS_SEND_INTERVAL_MS, settings.sendIntervalMillis)
            putInt(KEY_SETTINGS_MAX_LOG_ENTRIES, settings.maxLogEntries)
            putBoolean(KEY_SETTINGS_DEBUG_LOGGING_ENABLED, settings.debugLoggingEnabled)
            putString(KEY_SETTINGS_LANGUAGE, settings.language.name)
            putBoolean(KEY_SETTINGS_SHOW_OPEN_APP_NOTIFICATION_ACTION, settings.showOpenAppNotificationAction)
            EquipmentType.entries.forEach { type ->
                val sources = settings.sensorSources(type)
                SourcedMetric.entries.forEach { metric ->
                    putString(sensorSourceKey(type, metric), sources.of(metric).name)
                }
            }
            putBoolean(KEY_SETTINGS_POWER_CALCULATION_ENABLED, settings.powerCalculationEnabled)
            putString(KEY_SETTINGS_POWER_SOURCE, settings.powerSource.name)
            putString(KEY_SETTINGS_POWER_BODY_MASS_KG, settings.powerBodyMassKg?.toString())
            putString(KEY_SETTINGS_POWER_FLAT_COST, settings.powerFlatCost?.toString())
            EquipmentType.entries.forEach { type ->
                val layout = settings.dashboardLayout(type)
                putString(dashboardMetricsKey(type), layout.metrics.joinToString(",") { it.name })
                putString(dashboardGraphsKey(type), layout.graphs.joinToString(",") { it.name })
            }
            putString(KEY_SETTINGS_DASHBOARD_CHART_TIMESPAN, settings.dashboardChartTimespan.name)
            putString(KEY_SETTINGS_TARGET_POWER_PRESETS, settings.targetPowerPresets.joinToString(","))
            putInt(KEY_SETTINGS_TARGET_POWER_STEP, settings.targetPowerStepWatts)
        }
    }

    private fun loadSensorSources(): Map<EquipmentType, SensorSources> {
        // Versions before sources existed per type and value stored one choice for the heart rate.
        val defaults = SensorSources(
            heartRate = legacyHeartRateSource(preferences.getString(KEY_SETTINGS_HEART_RATE_SOURCE, null)),
        )
        return EquipmentType.entries.associateWith { type ->
            SourcedMetric.entries.fold(defaults) { sources, metric ->
                val stored = preferences.getString(sensorSourceKey(type, metric), null)
                sources.with(metric, stored.toValueSource(default = defaults.of(metric)))
            }
        }
    }

    private fun sensorSourceKey(type: EquipmentType, metric: SourcedMetric): String =
        "settings_source_${metric.name.lowercase()}_${type.name.lowercase()}"

    private fun loadMetricList(key: String, default: List<DashboardMetricKey>): List<DashboardMetricKey> {
        val raw = preferences.getString(key, null) ?: return default
        val parsed = raw.split(',')
            .mapNotNull { value -> runCatching { DashboardMetricKey.valueOf(value.trim()) }.getOrNull() }
            .distinct()
        return parsed.ifEmpty { default }
    }

    // The treadmill keeps the keys from before layouts existed per type, so saved choices survive.
    private fun dashboardMetricsKey(type: EquipmentType): String =
        if (type == EquipmentType.TREADMILL) KEY_SETTINGS_DASHBOARD_METRICS else "${KEY_SETTINGS_DASHBOARD_METRICS}_${type.name.lowercase()}"

    private fun dashboardGraphsKey(type: EquipmentType): String =
        if (type == EquipmentType.TREADMILL) KEY_SETTINGS_DASHBOARD_GRAPHS else "${KEY_SETTINGS_DASHBOARD_GRAPHS}_${type.name.lowercase()}"

    companion object {
        private const val KEY_TREADMILL_ADDRESS = "selected_treadmill_address"
        private const val KEY_EQUIPMENT_TYPE = "selected_equipment_type"
        private const val KEY_TREADMILL_NAME = "selected_treadmill_name"
        private const val KEY_GARMIN_ID = "selected_garmin_id"
        private const val KEY_GARMIN_NAME = "selected_garmin_name"
        private const val KEY_TARGET_POWER_ADDRESS = "target_power_last_address"
        private const val KEY_TARGET_POWER_WATTS = "target_power_last_watts"
        private const val KEY_SETTINGS_AUTO_RECONNECT = "settings_auto_reconnect"
        private const val KEY_SETTINGS_AUTO_START = "settings_auto_start"
        private const val KEY_SETTINGS_AUTO_STOP = "settings_auto_stop"
        private const val KEY_SETTINGS_FOLLOW_WATCH_TIMER = "settings_follow_watch_timer"
        private const val KEY_SETTINGS_WAIT_FOR_WATCH = "settings_wait_for_watch"
        private const val KEY_SETTINGS_VIRTUAL_FOOT_POD = "settings_virtual_foot_pod"
        private const val KEY_SETTINGS_PACKET_TIMEOUT_SECONDS = "settings_packet_timeout_seconds"
        private const val KEY_SETTINGS_SEND_INTERVAL_MS = "settings_send_interval_ms"
        private const val KEY_SETTINGS_MAX_LOG_ENTRIES = "settings_max_log_entries"
        private const val KEY_SETTINGS_DEBUG_LOGGING_ENABLED = "settings_debug_logging_enabled"
        private const val KEY_SETTINGS_LANGUAGE = "settings_language"
        private const val KEY_SETTINGS_SHOW_OPEN_APP_NOTIFICATION_ACTION = "settings_show_open_app_notification_action"
        private const val KEY_SETTINGS_HEART_RATE_SOURCE = "settings_heart_rate_source"
        private const val KEY_SETTINGS_POWER_CALCULATION_ENABLED = "settings_power_calculation_enabled"
        private const val KEY_SETTINGS_POWER_SOURCE = "settings_power_source"
        private const val KEY_SETTINGS_POWER_BODY_MASS_KG = "settings_power_body_mass_kg"
        private const val KEY_SETTINGS_POWER_FLAT_COST = "settings_power_flat_cost"
        private const val KEY_SETTINGS_DASHBOARD_METRICS = "settings_dashboard_metrics"
        private const val KEY_SETTINGS_DASHBOARD_GRAPHS = "settings_dashboard_graphs"
        private const val KEY_SETTINGS_DASHBOARD_CHART_TIMESPAN = "settings_dashboard_chart_timespan"
        private const val KEY_SETTINGS_TARGET_POWER_PRESETS = "settings_target_power_presets"
        private const val KEY_SETTINGS_TARGET_POWER_STEP = "settings_target_power_step"

        private val ALLOWED_PACKET_TIMEOUT_SECONDS = 10..60
        private val ALLOWED_SEND_INTERVAL_MS = 500L..2_000L
        private val ALLOWED_MAX_LOG_ENTRIES = 500..5_000
    }
}

internal fun String?.toDashboardChartTimespan(): DashboardChartTimespan =
    this?.let { value ->
        runCatching { DashboardChartTimespan.valueOf(value) }.getOrNull()
    } ?: DashboardChartTimespan.MINUTES_10

internal fun String?.toPowerSource(): PowerSource =
    this?.let { value ->
        runCatching { PowerSource.valueOf(value) }.getOrNull()
    } ?: PowerSource.FTMS_PREFERRED

private fun String?.toPositiveDoubleOrNull(): Double? =
    this?.toDoubleOrNull()?.takeIf { it > 0.0 }

internal data class RememberedSelection(
    val treadmillAddress: String?,
    val treadmillName: String?,
    val equipmentType: EquipmentType,
    val garminId: Long?,
    val garminName: String?,
)
