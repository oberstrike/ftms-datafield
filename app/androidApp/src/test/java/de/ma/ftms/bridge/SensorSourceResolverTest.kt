package de.ma.ftms.bridge

import de.ma.ftms.bridge.runtime.BridgeSettings
import de.ma.ftms.bridge.runtime.EquipmentType
import de.ma.ftms.bridge.runtime.PowerSource
import de.ma.ftms.bridge.runtime.SensorSources
import de.ma.ftms.bridge.runtime.SourcedMetric
import de.ma.ftms.bridge.runtime.ValueSource
import de.ma.ftms.bridge.runtime.WATCH_READINGS_MAX_AGE_MS
import de.ma.ftms.bridge.runtime.WatchReadings
import de.ma.ftms.bridge.runtime.legacyHeartRateSource
import de.ma.ftms.bridge.runtime.toValueSource
import de.ma.ftms.bridge.runtime.withResolvedSources
import de.ma.ftms.core.FtmsMachineKind
import de.ma.ftms.core.FtmsSample
import de.ma.ftms.core.SensorReadings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SensorSourceResolverTest {
    private val bikeSample = FtmsSample(
        kind = FtmsMachineKind.INDOOR_BIKE,
        speedKmh = 30.0,
        powerW = 182,
        heartRateBpm = 120,
        cadenceRpm = 88.0,
    )
    private val watch = WatchReadings(
        readings = SensorReadings(powerW = 176, heartRateBpm = 141, cadenceRpm = 90.0),
        timestampMillis = 10_000L,
    )

    private fun settings(type: EquipmentType, sources: SensorSources): BridgeSettings =
        BridgeSettings(sensorSources = mapOf(type to sources))

    private fun bike(sources: SensorSources, watch: WatchReadings? = this.watch, now: Long = 11_000L): FtmsSample =
        bikeSample.withResolvedSources(settings(EquipmentType.INDOOR_BIKE, sources), EquipmentType.INDOOR_BIKE, watch, now)

    @Test
    fun byDefaultTakesTheHeartRateFromTheWatchAndTheRestFromTheEquipment() {
        val resolved = bikeSample.withResolvedSources(BridgeSettings(), EquipmentType.INDOOR_BIKE, watch, 11_000L)

        assertEquals(141, resolved.heartRateBpm)
        assertEquals(182, resolved.powerW)
        assertEquals(88.0, resolved.cadenceRpm)
    }

    @Test
    fun takesEachValueFromItsChosenSource() {
        val allWatch = bike(SensorSources(ValueSource.WATCH, ValueSource.WATCH, ValueSource.WATCH))
        assertEquals(141, allWatch.heartRateBpm)
        assertEquals(176, allWatch.powerW)
        assertEquals(90.0, allWatch.cadenceRpm)

        val allEquipment = bike(SensorSources(ValueSource.EQUIPMENT, ValueSource.EQUIPMENT, ValueSource.EQUIPMENT))
        assertEquals(120, allEquipment.heartRateBpm)
        assertEquals(182, allEquipment.powerW)
        assertEquals(88.0, allEquipment.cadenceRpm)
    }

    @Test
    fun keepsBothSourcesNextToTheChosenValue() {
        val resolved = bike(SensorSources(power = ValueSource.WATCH))

        assertEquals(SensorReadings(powerW = 182, heartRateBpm = 120, cadenceRpm = 88.0), resolved.equipment)
        assertEquals(SensorReadings(powerW = 176, heartRateBpm = 141, cadenceRpm = 90.0), resolved.watch)
    }

    @Test
    fun leavesAValueEmptyRatherThanSwitchingToTheOtherSource() {
        val noWatch = bike(SensorSources(power = ValueSource.WATCH), watch = null)
        assertNull(noWatch.powerW)
        assertNull(noWatch.heartRateBpm)
        assertEquals(182, noWatch.equipment?.powerW)
        assertNull(noWatch.watch)

        val watchWithoutPower = watch.copy(readings = SensorReadings(heartRateBpm = 141))
        assertNull(bike(SensorSources(power = ValueSource.WATCH), watch = watchWithoutPower).powerW)

        val equipmentWithoutHeartRate = bikeSample.copy(heartRateBpm = null).withResolvedSources(
            settings(EquipmentType.INDOOR_BIKE, SensorSources(heartRate = ValueSource.EQUIPMENT)),
            EquipmentType.INDOOR_BIKE,
            watch,
            11_000L,
        )
        assertNull(equipmentWithoutHeartRate.heartRateBpm)
        assertEquals(141, equipmentWithoutHeartRate.watch?.heartRateBpm)
    }

    @Test
    fun ignoresWatchValuesThatAreTooOld() {
        val justFresh = bike(SensorSources(), now = watch.timestampMillis + WATCH_READINGS_MAX_AGE_MS)
        assertEquals(141, justFresh.heartRateBpm)

        val stale = bike(SensorSources(), now = watch.timestampMillis + WATCH_READINGS_MAX_AGE_MS + 1)
        assertNull(stale.heartRateBpm)
        assertNull(stale.watch)
        assertEquals(182, stale.powerW)
    }

    @Test
    fun usesTheSourcesOfTheSelectedEquipmentTypeOnly() {
        val settings = settings(EquipmentType.TREADMILL, SensorSources(power = ValueSource.WATCH))

        val onBike = bikeSample.withResolvedSources(settings, EquipmentType.INDOOR_BIKE, watch, 11_000L)

        assertEquals(182, onBike.powerW)
    }

    @Test
    fun countsTheTreadmillEstimateAsEquipmentPower() {
        val treadmill = FtmsSample(kind = FtmsMachineKind.TREADMILL, speedKmh = 10.0, inclinePct = 0.0, stepRateSpm = 168.0)
        val estimating = BridgeSettings(
            powerCalculationEnabled = true,
            powerSource = PowerSource.CALCULATED,
            powerBodyMassKg = 80.0,
            powerFlatCost = 1.22,
        )

        val fromEquipment = treadmill.withResolvedSources(estimating, EquipmentType.TREADMILL, watch, 11_000L)
        assertEquals(271, fromEquipment.powerW)
        assertEquals(271, fromEquipment.equipment?.powerW)

        val fromWatch = treadmill.withResolvedSources(
            estimating.withSensorSource(EquipmentType.TREADMILL, SourcedMetric.POWER, ValueSource.WATCH),
            EquipmentType.TREADMILL,
            watch,
            11_000L,
        )
        assertEquals(176, fromWatch.powerW)
        assertEquals(271, fromWatch.equipment?.powerW)
    }

    @Test
    fun treatsTheWatchCadenceAsStepRateOnATreadmill() {
        val treadmill = FtmsSample(kind = FtmsMachineKind.TREADMILL, speedKmh = 10.0, stepRateSpm = 168.0)
        val runningWatch = watch.copy(readings = SensorReadings(cadenceRpm = 172.0))
        val settings = settings(EquipmentType.TREADMILL, SensorSources(cadence = ValueSource.WATCH))

        val resolved = treadmill.withResolvedSources(settings, EquipmentType.TREADMILL, runningWatch, 11_000L)

        assertEquals(172.0, resolved.stepRateSpm)
        assertNull(resolved.cadenceRpm)
        assertEquals(168.0, resolved.equipment?.cadenceRpm)
    }

    @Test
    fun leavesTheSourceNotesEmptyWhenNothingIsReported() {
        val bare = FtmsSample(kind = FtmsMachineKind.TREADMILL, speedKmh = 8.0)

        val resolved = bare.withResolvedSources(BridgeSettings(), EquipmentType.TREADMILL, null, 11_000L)

        assertNull(resolved.equipment)
        assertNull(resolved.watch)
    }

    @Test
    fun changesOneSourceAndKeepsTheOthers() {
        val settings = BridgeSettings()
            .withSensorSource(EquipmentType.INDOOR_BIKE, SourcedMetric.POWER, ValueSource.WATCH)
            .withSensorSource(EquipmentType.INDOOR_BIKE, SourcedMetric.CADENCE, ValueSource.WATCH)

        assertEquals(
            SensorSources(heartRate = ValueSource.WATCH, power = ValueSource.WATCH, cadence = ValueSource.WATCH),
            settings.sensorSources(EquipmentType.INDOOR_BIKE),
        )
        assertEquals(SensorSources(), settings.sensorSources(EquipmentType.TREADMILL))
        assertEquals(ValueSource.WATCH, settings.sensorSources(EquipmentType.INDOOR_BIKE).of(SourcedMetric.POWER))
    }

    @Test
    fun readsStoredSourcesIncludingTheOldHeartRateSetting() {
        assertEquals(ValueSource.EQUIPMENT, legacyHeartRateSource("MACHINE"))
        assertEquals(ValueSource.WATCH, legacyHeartRateSource("GARMIN"))
        assertEquals(ValueSource.WATCH, legacyHeartRateSource(null))
        assertEquals(ValueSource.WATCH, "WATCH".toValueSource(default = ValueSource.EQUIPMENT))
        assertEquals(ValueSource.EQUIPMENT, null.toValueSource(default = ValueSource.EQUIPMENT))
        assertEquals(ValueSource.WATCH, "nonsense".toValueSource(default = ValueSource.WATCH))
    }
}
