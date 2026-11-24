package de.ma.ftms.bridge

import de.ma.ftms.bridge.i18n.AppLanguage
import de.ma.ftms.bridge.runtime.BridgeSettings
import de.ma.ftms.bridge.runtime.DashboardChartTimespan
import de.ma.ftms.bridge.runtime.DashboardMetricKey
import de.ma.ftms.bridge.runtime.EquipmentType
import de.ma.ftms.bridge.runtime.PowerSource
import de.ma.ftms.bridge.runtime.SensorSources
import de.ma.ftms.bridge.runtime.toDashboardChartTimespan
import de.ma.ftms.bridge.runtime.toPowerSource
import de.ma.ftms.bridge.runtime.treadmillPowerWatts
import de.ma.ftms.bridge.runtime.withResolvedPower
import de.ma.ftms.core.TreadmillSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BridgeSettingsTest {
    @Test
    fun usesConservativeDefaults() {
        val settings = BridgeSettings()

        assertTrue(settings.autoReconnect)
        assertTrue(!settings.autoStart)
        assertEquals(listOf(100, 150, 200, 250), settings.targetPowerPresets)
        assertEquals(10, settings.targetPowerStepWatts)
        assertEquals(15, settings.packetTimeoutSeconds)
        assertEquals(1_000L, settings.sendIntervalMillis)
        assertEquals(5_000, settings.maxLogEntries)
        assertTrue(!settings.debugLoggingEnabled)
        assertEquals(AppLanguage.SYSTEM_DEFAULT, settings.language)
        assertTrue(settings.showOpenAppNotificationAction)
        assertTrue(DashboardMetricKey.PACE in settings.dashboardLayout(EquipmentType.TREADMILL).metrics)
        assertTrue(DashboardMetricKey.ASCENT_RATE in settings.dashboardLayout(EquipmentType.TREADMILL).metrics)
        assertTrue(DashboardMetricKey.PACE in settings.dashboardLayout(EquipmentType.TREADMILL).graphs)
        assertTrue(DashboardMetricKey.ASCENT_RATE in settings.dashboardLayout(EquipmentType.TREADMILL).graphs)
        assertEquals(DashboardChartTimespan.MINUTES_10, settings.dashboardChartTimespan)
        assertEquals(SensorSources(), settings.sensorSources(EquipmentType.TREADMILL))
        assertTrue(!settings.powerCalculationEnabled)
        assertEquals(PowerSource.FTMS_PREFERRED, settings.powerSource)
        assertNull(settings.powerBodyMassKg)
        assertEquals(1.22, settings.powerFlatCost)
    }

    @Test
    fun exposesPacketTimeoutMillis() {
        assertEquals(30_000L, BridgeSettings(packetTimeoutSeconds = 30).packetTimeoutMillis)
    }

    @Test
    fun parsesDashboardChartTimespanWithDefaultFallback() {
        assertEquals(DashboardChartTimespan.MINUTES_5, "MINUTES_5".toDashboardChartTimespan())
        assertEquals(DashboardChartTimespan.MINUTES_10, null.toDashboardChartTimespan())
        assertEquals(DashboardChartTimespan.MINUTES_10, "not-a-timespan".toDashboardChartTimespan())
    }

    @Test
    fun parsesPowerSourceWithFtmsPreferredDefaultFallback() {
        assertEquals(PowerSource.CALCULATED, "CALCULATED".toPowerSource())
        assertEquals(PowerSource.FTMS_PREFERRED, null.toPowerSource())
        assertEquals(PowerSource.FTMS_PREFERRED, "not-a-source".toPowerSource())
    }

    @Test
    fun calculatesTreadmillPowerFromConfiguredInputs() {
        assertEquals(
            271,
            treadmillPowerWatts(bodyMassKg = 80.0, speedKmh = 10.0, inclinePercent = 0.0, flatCost = 1.22),
        )
        assertEquals(
            297,
            treadmillPowerWatts(bodyMassKg = 80.0, speedKmh = 8.3, inclinePercent = 4.0, flatCost = 1.22),
        )
    }

    @Test
    fun resolvesPowerFromSelectedSource() {
        val ftmsPower = TreadmillSample(speedKmh = 10.0, inclinePct = 0.0, powerW = 220)
        val missingPower = TreadmillSample(speedKmh = 10.0, inclinePct = 0.0, powerW = null)
        val ftmsPreferred = BridgeSettings(
            powerCalculationEnabled = true,
            powerSource = PowerSource.FTMS_PREFERRED,
            powerBodyMassKg = 80.0,
            powerFlatCost = 1.22,
        )
        val calculated = ftmsPreferred.copy(powerSource = PowerSource.CALCULATED)

        assertEquals(220, ftmsPower.withResolvedPower(ftmsPreferred).powerW)
        assertEquals(271, missingPower.withResolvedPower(ftmsPreferred).powerW)
        assertEquals(271, ftmsPower.withResolvedPower(calculated).powerW)
        assertEquals(220, ftmsPower.withResolvedPower(calculated.copy(powerBodyMassKg = null)).powerW)
        assertNull(missingPower.withResolvedPower(ftmsPreferred.copy(powerCalculationEnabled = false)).powerW)
    }
}
