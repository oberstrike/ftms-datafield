package de.ma.ftms.bridge.ui

import de.ma.ftms.bridge.runtime.EquipmentType
import de.ma.ftms.bridge.runtime.SourcedMetric
import de.ma.ftms.core.DistanceSource
import de.ma.ftms.core.FtmsMachineKind
import de.ma.ftms.core.SensorReadings
import de.ma.ftms.core.storage.SessionSampleRecord
import de.ma.ftms.core.storage.WorkoutSessionRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryMetricCalculationsTest {
    @Test
    fun calculatesAverageSpeedFromDistanceAndActiveDuration() {
        assertEquals(10.0, averageSpeedKmh(distanceM = 5_000.0, durationMillis = 30 * 60_000L))
        assertNull(averageSpeedKmh(distanceM = 0.0, durationMillis = 30 * 60_000L))
        assertNull(averageSpeedKmh(distanceM = 5_000.0, durationMillis = 0L))
    }

    @Test
    fun calculatesAverageAscentRateFromAscentAndActiveDuration() {
        assertEquals(4.0, averageAscentRateMetersPerMinute(ascentM = 120.0, durationMillis = 30 * 60_000L))
        assertNull(averageAscentRateMetersPerMinute(ascentM = 0.0, durationMillis = 30 * 60_000L))
        assertNull(averageAscentRateMetersPerMinute(ascentM = 120.0, durationMillis = 0L))
    }

    @Test
    fun averagesOptionalHistorySampleValuesIgnoringMissingSamples() {
        val samples = listOf(
            sample(powerW = 200, heartRateBpm = 130, cadenceRpm = 80.0, resistance = 2.0),
            sample(powerW = null, heartRateBpm = null, stepRateSpm = 90.0, resistance = null),
            sample(powerW = 300, heartRateBpm = 150, cadenceRpm = null, resistance = 4.0),
        )

        assertEquals(250.0, averagePowerWatts(samples))
        assertEquals(140.0, averageHeartRateBpm(samples))
        assertEquals(85.0, averageCadenceOrStep(samples))
        assertEquals(3.0, averageResistance(samples))
    }

    @Test
    fun optionalHistoryAveragesReturnNullWhenNoSamplesHaveValues() {
        val samples = listOf(sample(), sample())

        assertNull(averagePowerWatts(samples))
        assertNull(averageHeartRateBpm(samples))
        assertNull(averageCadenceOrStep(samples))
        assertNull(averageResistance(samples))
    }

    @Test
    fun allTimeTotalsAreZeroForEmptyHistory() {
        val totals = historyTotals(emptyList())

        assertEquals(0, totals.sessionCount)
        assertEquals(0.0, totals.distanceM)
        assertEquals(0.0, totals.ascentM)
        assertEquals(0L, totals.durationMillis)
    }

    @Test
    fun allTimeTotalsSumStoppedSessions() {
        val totals = historyTotals(
            listOf(
                session(
                    sessionId = "first",
                    startedAtMillis = 1_000L,
                    stoppedAtMillis = 61_000L,
                    finalDistanceM = 1_200.0,
                    finalAscentM = 24.0,
                ),
                session(
                    sessionId = "second",
                    startedAtMillis = 100_000L,
                    stoppedAtMillis = 250_000L,
                    finalDistanceM = 3_400.0,
                    finalAscentM = 51.0,
                ),
            ),
        )

        assertEquals(2, totals.sessionCount)
        assertEquals(4_600.0, totals.distanceM)
        assertEquals(75.0, totals.ascentM)
        assertEquals(210_000L, totals.durationMillis)
    }

    @Test
    fun allTimeTotalsIncludeInterruptedAndRunningSessions() {
        val totals = historyTotals(
            listOf(
                session(
                    sessionId = "interrupted",
                    finalStatus = "interrupted",
                    startedAtMillis = 1_000L,
                    stoppedAtMillis = 31_000L,
                    finalDistanceM = 500.0,
                    finalAscentM = 5.0,
                ),
                session(
                    sessionId = "running",
                    finalStatus = "running",
                    startedAtMillis = 40_000L,
                    stoppedAtMillis = null,
                    updatedAtMillis = 100_000L,
                    finalDistanceM = 800.0,
                    finalAscentM = 9.0,
                ),
            ),
        )

        assertEquals(2, totals.sessionCount)
        assertEquals(1_300.0, totals.distanceM)
        assertEquals(14.0, totals.ascentM)
        assertEquals(90_000L, totals.durationMillis)
    }

    private fun sample(
        powerW: Int? = null,
        heartRateBpm: Int? = null,
        cadenceRpm: Double? = null,
        stepRateSpm: Double? = null,
        resistance: Double? = null,
    ): SessionSampleRecord =
        SessionSampleRecord(
            sampleId = 0L,
            sessionId = "session",
            timestampMillis = 0L,
            offsetMillis = 0L,
            kind = 2,
            rawFlags = 0,
            rawHex = "",
            speedKmh = null,
            rawDistanceM = null,
            smoothedDistanceM = 0.0,
            ascentM = 0.0,
            inclinePct = null,
            powerW = powerW,
            heartRateBpm = heartRateBpm,
            cadenceRpm = cadenceRpm,
            stepRateSpm = stepRateSpm,
            resistance = resistance,
            elapsedS = null,
            remainingS = null,
            parseError = null,
            distanceSource = DistanceSource.NONE,
        )

    private fun session(
        sessionId: String,
        finalStatus: String = "stopped",
        startedAtMillis: Long,
        stoppedAtMillis: Long?,
        updatedAtMillis: Long = stoppedAtMillis ?: startedAtMillis,
        finalDistanceM: Double,
        finalAscentM: Double,
    ): WorkoutSessionRecord =
        WorkoutSessionRecord(
            sessionId = sessionId,
            startedAtMillis = startedAtMillis,
            stoppedAtMillis = stoppedAtMillis,
            finalStatus = finalStatus,
            treadmillName = null,
            treadmillAddress = null,
            garminName = null,
            garminId = null,
            packetCount = 0,
            sendSuccessCount = 0,
            sendFailureCount = 0,
            lastError = "",
            finalDistanceM = finalDistanceM,
            finalAscentM = finalAscentM,
            finalSpeedKmh = 0.0,
            updatedAtMillis = updatedAtMillis,
        )

    @Test
    fun comparesEquipmentAndWatchOverTheSamplesBothDelivered() {
        val samples = listOf(
            sample().copy(equipment = SensorReadings(powerW = 200), watch = SensorReadings(powerW = 210)),
            sample().copy(equipment = SensorReadings(powerW = 100), watch = SensorReadings(powerW = 105)),
            // Only one source each: must not shift either average.
            sample().copy(equipment = SensorReadings(powerW = 900)),
            sample().copy(watch = SensorReadings(powerW = 5, heartRateBpm = 150)),
        )

        val power = compareSources(samples, SourcedMetric.POWER)

        assertEquals(150.0, power?.equipmentAverage)
        assertEquals(157.5, power?.watchAverage)
        assertEquals(5.0, power?.differencePercent)
        assertNull(compareSources(samples, SourcedMetric.HEART_RATE))
        assertNull(compareSources(listOf(sample(powerW = 180)), SourcedMetric.POWER))
    }

    @Test
    fun givesNoPercentageAgainstAnEquipmentAverageOfZero() {
        val samples = listOf(sample().copy(equipment = SensorReadings(powerW = 0), watch = SensorReadings(powerW = 12)))

        assertNull(compareSources(samples, SourcedMetric.POWER)?.differencePercent)
    }

    @Test
    fun listsAndFiltersSessionsByEquipmentType() {
        val run = WorkoutSessionRecord(
            sessionId = "run",
            startedAtMillis = 0L,
            stoppedAtMillis = 60_000L,
            finalStatus = "stopped",
            treadmillName = null,
            treadmillAddress = null,
            garminName = null,
            garminId = null,
            packetCount = 0,
            sendSuccessCount = 0,
            sendFailureCount = 0,
            lastError = "",
            finalDistanceM = 1_000.0,
            finalAscentM = 10.0,
            finalSpeedKmh = 0.0,
            updatedAtMillis = 60_000L,
        )
        val ride = run.copy(sessionId = "ride", equipmentKind = FtmsMachineKind.INDOOR_BIKE, finalAscentM = 0.0)
        val unknown = run.copy(sessionId = "old", equipmentKind = FtmsMachineKind.UNKNOWN)
        val sessions = listOf(run, ride, unknown)

        assertEquals(EquipmentType.INDOOR_BIKE, ride.equipmentType)
        assertEquals(EquipmentType.TREADMILL, unknown.equipmentType)
        assertEquals(listOf(EquipmentType.TREADMILL, EquipmentType.INDOOR_BIKE), recordedEquipmentTypes(sessions))
        assertEquals(listOf(ride), filterSessionsByType(sessions, EquipmentType.INDOOR_BIKE))
        assertEquals(listOf(run, unknown), filterSessionsByType(sessions, EquipmentType.TREADMILL))
        assertEquals(sessions, filterSessionsByType(sessions, null))
        assertEquals(1, historyTotals(filterSessionsByType(sessions, EquipmentType.INDOOR_BIKE)).sessionCount)
    }
}
