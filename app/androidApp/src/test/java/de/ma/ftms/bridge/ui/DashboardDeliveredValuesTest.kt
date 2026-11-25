package de.ma.ftms.bridge.ui

import de.ma.ftms.bridge.i18n.DeStrings
import de.ma.ftms.bridge.i18n.EnStrings
import de.ma.ftms.bridge.runtime.DashboardMetricKey
import de.ma.ftms.bridge.runtime.EquipmentType
import de.ma.ftms.bridge.runtime.SourcedMetric
import de.ma.ftms.core.DistanceSource
import de.ma.ftms.core.FtmsSample
import de.ma.ftms.core.SensorReadings
import de.ma.ftms.core.SmoothedFtmsSample
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardDeliveredValuesTest {
    @Test
    fun treadmillWithoutSensorsDeliversOnlyWhatItMeasures() {
        val samples = listOf(sample(FtmsSample(speedKmh = 5.4, inclinePct = 1.0)))

        assertEquals(
            setOf(
                DashboardMetricKey.SPEED,
                DashboardMetricKey.PACE,
                DashboardMetricKey.AVG_PACE,
                DashboardMetricKey.INCLINE,
                DashboardMetricKey.AVG_INCLINE,
                DashboardMetricKey.DISTANCE,
                DashboardMetricKey.ASCENT,
                DashboardMetricKey.ASCENT_RATE,
                DashboardMetricKey.MACHINE,
            ),
            deliveredDashboardMetrics(samples),
        )
    }

    @Test
    fun bikeDeliversPowerCadenceAndResistance() {
        val samples = listOf(sample(FtmsSample(speedKmh = 25.0, cadenceRpm = 85.0, powerW = 180, resistance = 8.0)))

        assertEquals(
            setOf(
                DashboardMetricKey.SPEED,
                DashboardMetricKey.PACE,
                DashboardMetricKey.AVG_PACE,
                DashboardMetricKey.DISTANCE,
                DashboardMetricKey.POWER,
                DashboardMetricKey.CADENCE_STEP,
                DashboardMetricKey.RESISTANCE,
                DashboardMetricKey.MACHINE,
            ),
            deliveredDashboardMetrics(samples),
        )
    }

    @Test
    fun valueDeliveredOnceInTheSessionStaysDelivered() {
        val samples = listOf(
            sample(FtmsSample(speedKmh = 5.0)),
            sample(FtmsSample(speedKmh = 5.0, heartRateBpm = 120)),
            sample(FtmsSample(speedKmh = 5.0)),
        )

        assertEquals(true, DashboardMetricKey.HEART_RATE in deliveredDashboardMetrics(samples))
        assertEquals(false, DashboardMetricKey.HEART_RATE in deliveredDashboardMetrics(samples.take(1)))
    }

    @Test
    fun sourcesAreOfferedOnlyForValuesOneOfThemDelivers() {
        val samples = listOf(
            sample(FtmsSample(speedKmh = 5.0, watch = SensorReadings(heartRateBpm = 118))),
            sample(FtmsSample(speedKmh = 5.0, equipment = SensorReadings(powerW = 156))),
        )

        assertEquals(setOf(SourcedMetric.HEART_RATE, SourcedMetric.POWER), deliveredSourcedMetrics(samples))
        assertEquals(emptySet(), deliveredSourcedMetrics(listOf(sample(FtmsSample(speedKmh = 5.0)))))
        assertEquals(emptySet(), deliveredSourcedMetrics(emptyList()))
    }

    @Test
    fun cadenceIsNamedAfterTheEquipment() {
        assertEquals("Schrittfrequenz", DashboardMetricKey.CADENCE_STEP.label(DeStrings, EquipmentType.TREADMILL))
        assertEquals("Kadenz", DashboardMetricKey.CADENCE_STEP.label(DeStrings, EquipmentType.INDOOR_BIKE))
        assertEquals("Cadence", DashboardMetricKey.CADENCE_STEP.label(EnStrings, EquipmentType.CROSS_TRAINER))
        assertEquals("spm", DashboardMetricKey.CADENCE_STEP.unit(EquipmentType.TREADMILL))
        assertEquals("rpm", DashboardMetricKey.CADENCE_STEP.unit(EquipmentType.INDOOR_BIKE))
    }

    private fun sample(raw: FtmsSample): SmoothedFtmsSample =
        SmoothedFtmsSample(raw = raw, distanceM = 100.0, ascentM = 0.0, distanceSource = DistanceSource.SPEED_TIME)
}
