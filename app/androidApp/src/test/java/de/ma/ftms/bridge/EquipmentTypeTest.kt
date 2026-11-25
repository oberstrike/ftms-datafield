package de.ma.ftms.bridge

import de.ma.ftms.bridge.runtime.BridgeSettings
import de.ma.ftms.bridge.runtime.DEFAULT_DASHBOARD_GRAPHS
import de.ma.ftms.bridge.runtime.DEFAULT_DASHBOARD_METRICS
import de.ma.ftms.bridge.runtime.DashboardLayout
import de.ma.ftms.bridge.runtime.DashboardMetricKey
import de.ma.ftms.bridge.runtime.EquipmentType
import de.ma.ftms.bridge.runtime.PowerSource
import de.ma.ftms.bridge.runtime.dashboardGraphOptions
import de.ma.ftms.bridge.runtime.dashboardMetricOptions
import de.ma.ftms.bridge.runtime.defaultDashboardLayout
import de.ma.ftms.bridge.runtime.equipmentTypeFromFtmsServiceData
import de.ma.ftms.bridge.runtime.isEquipmentTypeMismatch
import de.ma.ftms.bridge.runtime.withResolvedPower
import de.ma.ftms.core.FtmsMachineKind
import de.ma.ftms.core.TreadmillSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EquipmentTypeTest {
    @Test
    fun mapsStoredKindsAndNamesAndFallsBackToTreadmill() {
        assertEquals(EquipmentType.INDOOR_BIKE, EquipmentType.fromMachineKind(FtmsMachineKind.INDOOR_BIKE))
        assertEquals(EquipmentType.CROSS_TRAINER, EquipmentType.fromMachineKind(FtmsMachineKind.CROSS_TRAINER))
        assertEquals(EquipmentType.TREADMILL, EquipmentType.fromMachineKind(FtmsMachineKind.UNKNOWN))
        assertEquals(EquipmentType.INDOOR_BIKE, EquipmentType.fromName("INDOOR_BIKE"))
        assertEquals(EquipmentType.TREADMILL, EquipmentType.fromName("ROWER"))
        assertEquals(EquipmentType.TREADMILL, EquipmentType.fromName(null))
    }

    @Test
    fun readsTheTypeFromFtmsAdvertisingData() {
        assertEquals(EquipmentType.TREADMILL, equipmentTypeFromFtmsServiceData(byteArrayOf(0x01, 0x01, 0x00)))
        assertEquals(EquipmentType.CROSS_TRAINER, equipmentTypeFromFtmsServiceData(byteArrayOf(0x01, 0x02, 0x00)))
        assertEquals(EquipmentType.INDOOR_BIKE, equipmentTypeFromFtmsServiceData(byteArrayOf(0x01, 0x20, 0x00)))
    }

    @Test
    fun doesNotGuessATypeFromUnclearAdvertisingData() {
        // Treadmill and indoor bike at once, a rower, a truncated value, nothing at all.
        assertNull(equipmentTypeFromFtmsServiceData(byteArrayOf(0x01, 0x21, 0x00)))
        assertNull(equipmentTypeFromFtmsServiceData(byteArrayOf(0x01, 0x10, 0x00)))
        assertNull(equipmentTypeFromFtmsServiceData(byteArrayOf(0x01, 0x20)))
        assertNull(equipmentTypeFromFtmsServiceData(null))
    }

    @Test
    fun treadmillDashboardHasEveryValueButTheResistance() {
        val resistance = DashboardMetricKey.RESISTANCE

        assertEquals(
            DashboardLayout(DEFAULT_DASHBOARD_METRICS - resistance, DEFAULT_DASHBOARD_GRAPHS - resistance),
            defaultDashboardLayout(EquipmentType.TREADMILL),
        )
        assertEquals(DashboardMetricKey.entries - resistance, dashboardMetricOptions(EquipmentType.TREADMILL))
        assertEquals(DEFAULT_DASHBOARD_GRAPHS - resistance, dashboardGraphOptions(EquipmentType.TREADMILL))
    }

    @Test
    fun storedLayoutLosesWhatTheEquipmentTypeDoesNotHave() {
        val stored = DashboardLayout(
            metrics = listOf(DashboardMetricKey.SPEED, DashboardMetricKey.PACE, DashboardMetricKey.RESISTANCE),
            graphs = listOf(DashboardMetricKey.INCLINE, DashboardMetricKey.POWER, DashboardMetricKey.RESISTANCE),
        )

        assertEquals(
            DashboardLayout(
                metrics = listOf(DashboardMetricKey.SPEED, DashboardMetricKey.PACE),
                graphs = listOf(DashboardMetricKey.INCLINE, DashboardMetricKey.POWER),
            ),
            BridgeSettings().withDashboardLayout(EquipmentType.TREADMILL, stored).dashboardLayout(EquipmentType.TREADMILL),
        )
        assertEquals(
            DashboardLayout(
                metrics = listOf(DashboardMetricKey.SPEED, DashboardMetricKey.RESISTANCE),
                graphs = listOf(DashboardMetricKey.POWER, DashboardMetricKey.RESISTANCE),
            ),
            BridgeSettings().withDashboardLayout(EquipmentType.INDOOR_BIKE, stored).dashboardLayout(EquipmentType.INDOOR_BIKE),
        )
    }

    @Test
    fun layoutLeftWithNothingFallsBackToTheDefault() {
        val onlyRunningValues = DashboardLayout(
            metrics = listOf(DashboardMetricKey.PACE),
            graphs = listOf(DashboardMetricKey.AVG_PACE),
        )

        assertEquals(
            defaultDashboardLayout(EquipmentType.INDOOR_BIKE),
            onlyRunningValues.forType(EquipmentType.INDOOR_BIKE),
        )
    }

    @Test
    fun onlyTheTreadmillCountsSteps() {
        assertTrue(EquipmentType.TREADMILL.cadenceIsStepRate)
        assertTrue(!EquipmentType.INDOOR_BIKE.cadenceIsStepRate)
        assertTrue(!EquipmentType.CROSS_TRAINER.cadenceIsStepRate)
    }

    @Test
    fun indoorBikeDashboardLeadsWithPowerAndHasNoRunningValues() {
        val layout = defaultDashboardLayout(EquipmentType.INDOOR_BIKE)
        val runningValues = setOf(
            DashboardMetricKey.PACE,
            DashboardMetricKey.AVG_PACE,
            DashboardMetricKey.INCLINE,
            DashboardMetricKey.AVG_INCLINE,
            DashboardMetricKey.ASCENT,
            DashboardMetricKey.ASCENT_RATE,
        )

        assertEquals(DashboardMetricKey.POWER, layout.metrics.first())
        assertEquals(DashboardMetricKey.POWER, layout.graphs.first())
        assertTrue(layout.metrics.none { it in runningValues })
        assertTrue(layout.graphs.none { it in runningValues })
        assertTrue(dashboardMetricOptions(EquipmentType.INDOOR_BIKE).none { it in runningValues })
        assertTrue(dashboardMetricOptions(EquipmentType.INDOOR_BIKE).containsAll(layout.metrics))
        assertTrue(dashboardGraphOptions(EquipmentType.INDOOR_BIKE).containsAll(layout.graphs))
    }

    @Test
    fun crossTrainerDashboardHasInclineButNoPace() {
        val layout = defaultDashboardLayout(EquipmentType.CROSS_TRAINER)

        assertTrue(DashboardMetricKey.INCLINE in layout.metrics)
        assertTrue(DashboardMetricKey.PACE !in layout.metrics)
        assertTrue(DashboardMetricKey.AVG_PACE !in layout.graphs)
    }

    @Test
    fun settingsKeepOneDashboardLayoutPerType() {
        val bikeLayout = DashboardLayout(
            metrics = listOf(DashboardMetricKey.POWER),
            graphs = listOf(DashboardMetricKey.HEART_RATE),
        )
        val settings = BridgeSettings().withDashboardLayout(EquipmentType.INDOOR_BIKE, bikeLayout)

        assertEquals(bikeLayout, settings.dashboardLayout(EquipmentType.INDOOR_BIKE))
        assertEquals(defaultDashboardLayout(EquipmentType.TREADMILL), settings.dashboardLayout(EquipmentType.TREADMILL))
    }

    @Test
    fun reportsAMismatchOnlyForAnotherSupportedType() {
        assertTrue(isEquipmentTypeMismatch(EquipmentType.TREADMILL, FtmsMachineKind.INDOOR_BIKE))
        assertTrue(!isEquipmentTypeMismatch(EquipmentType.INDOOR_BIKE, FtmsMachineKind.INDOOR_BIKE))
        assertTrue(!isEquipmentTypeMismatch(EquipmentType.INDOOR_BIKE, FtmsMachineKind.UNKNOWN))
    }

    @Test
    fun estimatesRunningPowerOnlyOnATreadmill() {
        val sample = TreadmillSample(speedKmh = 10.0, inclinePct = 0.0, powerW = null)
        val settings = BridgeSettings(
            powerCalculationEnabled = true,
            powerSource = PowerSource.CALCULATED,
            powerBodyMassKg = 80.0,
            powerFlatCost = 1.22,
        )

        assertEquals(271, sample.withResolvedPower(settings, EquipmentType.TREADMILL).powerW)
        assertNull(sample.withResolvedPower(settings, EquipmentType.INDOOR_BIKE).powerW)
        assertEquals(180, sample.copy(powerW = 180).withResolvedPower(settings, EquipmentType.INDOOR_BIKE).powerW)
    }
}
