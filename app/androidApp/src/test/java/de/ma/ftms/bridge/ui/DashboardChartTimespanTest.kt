package de.ma.ftms.bridge.ui

import de.ma.ftms.bridge.runtime.DashboardChartTimespan
import de.ma.ftms.core.DistanceSource
import de.ma.ftms.core.FtmsSample
import de.ma.ftms.core.SmoothedFtmsSample
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardChartTimespanTest {
    @Test
    fun fullSessionKeepsAllSamples() {
        val samples = listOf(sample(0L), sample(60_000L), sample(120_000L))

        assertEquals(samples, samples.filterForTimespan(DashboardChartTimespan.FULL_SESSION))
    }

    @Test
    fun timedWindowKeepsRecentSamplesRelativeToLatestTimestamp() {
        val samples = listOf(sample(0L), sample(540_000L), sample(600_000L), sample(660_000L))

        assertEquals(
            listOf(sample(60_000L * 10), sample(60_000L * 11)),
            samples.filterForTimespan(DashboardChartTimespan.MINUTES_1),
        )
    }

    @Test
    fun missingTimestampFallsBackToAllSamples() {
        val samples = listOf(sample(0L), sample(null), sample(120_000L))

        assertEquals(samples, samples.filterForTimespan(DashboardChartTimespan.MINUTES_1))
    }

    private fun sample(timestampMillis: Long?): SmoothedFtmsSample =
        SmoothedFtmsSample(
            raw = FtmsSample(timestampMillis = timestampMillis, speedKmh = 6.0),
            distanceM = timestampMillis?.toDouble() ?: 0.0,
            ascentM = 0.0,
            distanceSource = DistanceSource.SPEED_TIME,
        )
}
