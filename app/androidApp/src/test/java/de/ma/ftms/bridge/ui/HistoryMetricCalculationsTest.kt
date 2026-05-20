package de.ma.ftms.bridge.ui

import de.ma.ftms.core.DistanceSource
import de.ma.ftms.core.storage.SessionSampleRecord
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
}
