package de.ma.ftms.bridge.ui

import de.ma.ftms.core.DistanceSource
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
        val totals = allTimeHistoryTotals(emptyList())

        assertEquals(0, totals.sessionCount)
        assertEquals(0.0, totals.totalDistanceM)
        assertEquals(0.0, totals.totalAscentM)
        assertEquals(0L, totals.totalDurationMillis)
    }

    @Test
    fun allTimeTotalsSumStoppedSessions() {
        val totals = allTimeHistoryTotals(
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
        assertEquals(4_600.0, totals.totalDistanceM)
        assertEquals(75.0, totals.totalAscentM)
        assertEquals(210_000L, totals.totalDurationMillis)
    }

    @Test
    fun allTimeTotalsIncludeInterruptedAndRunningSessions() {
        val totals = allTimeHistoryTotals(
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
        assertEquals(1_300.0, totals.totalDistanceM)
        assertEquals(14.0, totals.totalAscentM)
        assertEquals(90_000L, totals.totalDurationMillis)
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
}
