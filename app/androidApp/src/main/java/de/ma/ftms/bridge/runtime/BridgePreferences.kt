package de.ma.ftms.bridge.runtime

import android.content.Context
import de.ma.ftms.bridge.i18n.toAppLanguage

internal class BridgePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("ftms_bridge_prefs", Context.MODE_PRIVATE)

    fun loadSelection(): RememberedSelection =
        RememberedSelection(
            treadmillAddress = preferences.getString(KEY_TREADMILL_ADDRESS, null),
            treadmillName = preferences.getString(KEY_TREADMILL_NAME, null),
            garminId = if (preferences.contains(KEY_GARMIN_ID)) preferences.getLong(KEY_GARMIN_ID, 0) else null,
            garminName = preferences.getString(KEY_GARMIN_NAME, null),
        )

    fun saveTreadmill(item: TreadmillDeviceItem) {
        preferences.edit()
            .putString(KEY_TREADMILL_ADDRESS, item.address)
            .putString(KEY_TREADMILL_NAME, item.name)
            .apply()
    }

    fun saveGarmin(item: GarminDeviceItem) {
        preferences.edit()
            .putLong(KEY_GARMIN_ID, item.id)
            .putString(KEY_GARMIN_NAME, item.name)
            .apply()
    }

    fun clearSelection() {
        preferences.edit()
            .remove(KEY_TREADMILL_ADDRESS)
            .remove(KEY_TREADMILL_NAME)
            .remove(KEY_GARMIN_ID)
            .remove(KEY_GARMIN_NAME)
            .apply()
    }

    fun loadSettings(): BridgeSettings =
        BridgeSettings(
            autoReconnect = preferences.getBoolean(
                KEY_SETTINGS_AUTO_RECONNECT,
                BridgeSettings().autoReconnect,
            ),
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
            heartRateSource = preferences.getString(
                KEY_SETTINGS_HEART_RATE_SOURCE,
                null,
            ).toHeartRateSource(),
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
            dashboardMetrics = loadMetricList(KEY_SETTINGS_DASHBOARD_METRICS, DEFAULT_DASHBOARD_METRICS),
            dashboardGraphs = loadMetricList(KEY_SETTINGS_DASHBOARD_GRAPHS, DEFAULT_DASHBOARD_GRAPHS),
            dashboardChartTimespan = preferences.getString(
                KEY_SETTINGS_DASHBOARD_CHART_TIMESPAN,
                null,
            ).toDashboardChartTimespan(),
        )

    fun saveSettings(settings: BridgeSettings) {
        preferences.edit()
            .putBoolean(KEY_SETTINGS_AUTO_RECONNECT, settings.autoReconnect)
            .putInt(KEY_SETTINGS_PACKET_TIMEOUT_SECONDS, settings.packetTimeoutSeconds)
            .putLong(KEY_SETTINGS_SEND_INTERVAL_MS, settings.sendIntervalMillis)
            .putInt(KEY_SETTINGS_MAX_LOG_ENTRIES, settings.maxLogEntries)
            .putBoolean(KEY_SETTINGS_DEBUG_LOGGING_ENABLED, settings.debugLoggingEnabled)
            .putString(KEY_SETTINGS_LANGUAGE, settings.language.name)
            .putBoolean(KEY_SETTINGS_SHOW_OPEN_APP_NOTIFICATION_ACTION, settings.showOpenAppNotificationAction)
            .putString(KEY_SETTINGS_HEART_RATE_SOURCE, settings.heartRateSource.name)
            .putBoolean(KEY_SETTINGS_POWER_CALCULATION_ENABLED, settings.powerCalculationEnabled)
            .putString(KEY_SETTINGS_POWER_SOURCE, settings.powerSource.name)
            .putString(KEY_SETTINGS_POWER_BODY_MASS_KG, settings.powerBodyMassKg?.toString())
            .putString(KEY_SETTINGS_POWER_FLAT_COST, settings.powerFlatCost?.toString())
            .putString(KEY_SETTINGS_DASHBOARD_METRICS, settings.dashboardMetrics.joinToString(",") { it.name })
            .putString(KEY_SETTINGS_DASHBOARD_GRAPHS, settings.dashboardGraphs.joinToString(",") { it.name })
            .putString(KEY_SETTINGS_DASHBOARD_CHART_TIMESPAN, settings.dashboardChartTimespan.name)
            .apply()
    }

    private fun loadMetricList(key: String, default: List<DashboardMetricKey>): List<DashboardMetricKey> {
        val raw = preferences.getString(key, null) ?: return default
        val parsed = raw.split(',')
            .mapNotNull { value -> runCatching { DashboardMetricKey.valueOf(value.trim()) }.getOrNull() }
            .distinct()
        return parsed.ifEmpty { default }
    }

    companion object {
        private const val KEY_TREADMILL_ADDRESS = "selected_treadmill_address"
        private const val KEY_TREADMILL_NAME = "selected_treadmill_name"
        private const val KEY_GARMIN_ID = "selected_garmin_id"
        private const val KEY_GARMIN_NAME = "selected_garmin_name"
        private const val KEY_SETTINGS_AUTO_RECONNECT = "settings_auto_reconnect"
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

        private val ALLOWED_PACKET_TIMEOUT_SECONDS = 10..60
        private val ALLOWED_SEND_INTERVAL_MS = 500L..2_000L
        private val ALLOWED_MAX_LOG_ENTRIES = 500..5_000
    }
}

internal fun String?.toDashboardChartTimespan(): DashboardChartTimespan =
    this?.let { value ->
        runCatching { DashboardChartTimespan.valueOf(value) }.getOrNull()
    } ?: DashboardChartTimespan.MINUTES_10

internal fun String?.toHeartRateSource(): HeartRateSource =
    this?.let { value ->
        runCatching { HeartRateSource.valueOf(value) }.getOrNull()
    } ?: HeartRateSource.GARMIN

internal fun String?.toPowerSource(): PowerSource =
    this?.let { value ->
        runCatching { PowerSource.valueOf(value) }.getOrNull()
    } ?: PowerSource.FTMS_PREFERRED

private fun String?.toPositiveDoubleOrNull(): Double? =
    this?.toDoubleOrNull()?.takeIf { it > 0.0 }

internal data class RememberedSelection(
    val treadmillAddress: String?,
    val treadmillName: String?,
    val garminId: Long?,
    val garminName: String?,
)
