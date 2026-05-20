package de.ma.ftms.bridge

import de.ma.ftms.bridge.i18n.AppLanguage
import de.ma.ftms.bridge.runtime.BridgeSettings
import de.ma.ftms.bridge.runtime.DashboardChartTimespan
import de.ma.ftms.bridge.runtime.DashboardMetricKey
import de.ma.ftms.bridge.runtime.GarminHeartRate
import de.ma.ftms.bridge.runtime.HeartRateSource
import de.ma.ftms.bridge.runtime.PowerSource
import de.ma.ftms.bridge.runtime.toDashboardChartTimespan
import de.ma.ftms.bridge.runtime.toHeartRateSource
import de.ma.ftms.bridge.runtime.toPowerSource
import de.ma.ftms.bridge.runtime.treadmillPowerWatts
import de.ma.ftms.bridge.runtime.withResolvedHeartRate
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
        assertEquals(15, settings.packetTimeoutSeconds)
        assertEquals(1_000L, settings.sendIntervalMillis)
        assertEquals(5_000, settings.maxLogEntries)
        assertTrue(!settings.debugLoggingEnabled)
        assertEquals(AppLanguage.SYSTEM_DEFAULT, settings.language)
        assertTrue(settings.showOpenAppNotificationAction)
        assertTrue(DashboardMetricKey.PACE in settings.dashboardMetrics)
        assertTrue(DashboardMetricKey.ASCENT_RATE in settings.dashboardMetrics)
        assertTrue(DashboardMetricKey.PACE in settings.dashboardGraphs)
        assertTrue(DashboardMetricKey.ASCENT_RATE in settings.dashboardGraphs)
        assertEquals(DashboardChartTimespan.MINUTES_10, settings.dashboardChartTimespan)
        assertEquals(HeartRateSource.GARMIN, settings.heartRateSource)
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
    fun parsesHeartRateSourceWithGarminDefaultFallback() {
        assertEquals(HeartRateSource.MACHINE, "MACHINE".toHeartRateSource())
        assertEquals(HeartRateSource.GARMIN, null.toHeartRateSource())
        assertEquals(HeartRateSource.GARMIN, "not-a-source".toHeartRateSource())
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

    @Test
    fun resolvesHeartRateFromSelectedSource() {
        val machineSample = TreadmillSample(heartRateBpm = 122)
        val freshGarmin = GarminHeartRate(bpm = 144, timestampMillis = 1_000L)

        assertEquals(
            122,
            machineSample.withResolvedHeartRate(HeartRateSource.MACHINE, freshGarmin, nowMillis = 2_000L).heartRateBpm,
        )
        assertEquals(
            144,
            machineSample.withResolvedHeartRate(HeartRateSource.GARMIN, freshGarmin, nowMillis = 2_000L).heartRateBpm,
        )
        assertNull(
            machineSample.withResolvedHeartRate(HeartRateSource.GARMIN, freshGarmin, nowMillis = 8_000L).heartRateBpm,
        )
    }
}
