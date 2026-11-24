package de.ma.ftms.bridge.ui

import de.ma.ftms.bridge.i18n.DeStrings
import de.ma.ftms.bridge.i18n.EnStrings
import de.ma.ftms.bridge.runtime.DashboardMetricKey
import de.ma.ftms.core.DistanceSource
import de.ma.ftms.core.FtmsSample
import de.ma.ftms.core.SmoothedFtmsSample
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DashboardMetricCatalogTest {
    @Test
    fun formatsPaceFromCurrentSpeed() {
        val sample = sample(speedKmh = 12.0, inclinePct = 3.0)

        assertEquals("5:00 min/km", DashboardMetricKey.PACE.format(sample, EnStrings))
        assertEquals(5.0, DashboardMetricKey.PACE.chartValue(sample))
    }

    @Test
    fun calculatesAscentRateFromCurrentSpeedAndPositiveIncline() {
        val sample = sample(speedKmh = 12.0, inclinePct = 3.0)

        assertEquals("6.0 m/min", DashboardMetricKey.ASCENT_RATE.format(sample, EnStrings))
        assertEquals(6.0, DashboardMetricKey.ASCENT_RATE.chartValue(sample))
    }

    @Test
    fun calculatesAveragePaceAndInclineFromActiveSamples() {
        val samples = listOf(
            sample(speedKmh = 10.0, inclinePct = 2.0, distanceM = 0.0),
            sample(speedKmh = 10.0, inclinePct = 4.0, distanceM = 100.0),
            sample(speedKmh = 10.0, inclinePct = 6.0, distanceM = 200.0),
        )

        assertEquals(
            "5:00 min/km",
            DashboardMetricKey.AVG_PACE.format(samples.last(), EnStrings, samples, activeDurationMillis = 60_000L),
        )
        assertEquals(
            "5.0 %",
            DashboardMetricKey.AVG_INCLINE.format(samples.last(), EnStrings, samples, activeDurationMillis = 60_000L),
        )
    }

    @Test
    fun formatsDecimalsInTheDisplayedLanguageNotTheSystemLocale() {
        val sample = sample(speedKmh = 12.5, inclinePct = 3.0)
        val systemLocale = Locale.getDefault()
        try {
            listOf(Locale.GERMANY, Locale.US, Locale.forLanguageTag("ar-EG")).forEach { locale ->
                Locale.setDefault(locale)
                assertEquals("12.50 km/h", DashboardMetricKey.SPEED.format(sample, EnStrings))
                assertEquals("12,50 km/h", DashboardMetricKey.SPEED.format(sample, DeStrings))
                assertEquals("4:48 min/km", DashboardMetricKey.PACE.format(sample, DeStrings))
            }
        } finally {
            Locale.setDefault(systemLocale)
        }
    }

    @Test
    fun reversesPaceChartYAxisOnlyForVisualMapping() {
        assertEquals(0f, chartYFraction(value = 10.0, minY = 10.0, maxY = 20.0, reverseY = true))
        assertEquals(1f, chartYFraction(value = 10.0, minY = 10.0, maxY = 20.0, reverseY = false))
    }

    @Test
    fun omitsRateMetricsWhenInputsAreMissingOrZero() {
        assertEquals("--", DashboardMetricKey.PACE.format(sample(speedKmh = 0.0, inclinePct = 3.0), EnStrings))
        assertNull(DashboardMetricKey.PACE.chartValue(sample(speedKmh = 0.0, inclinePct = 3.0)))
        assertEquals("--", DashboardMetricKey.ASCENT_RATE.format(sample(speedKmh = 10.0, inclinePct = null), EnStrings))
        assertNull(DashboardMetricKey.ASCENT_RATE.chartValue(sample(speedKmh = 10.0, inclinePct = null)))
    }

    private fun sample(speedKmh: Double?, inclinePct: Double?, distanceM: Double = 100.0): SmoothedFtmsSample =
        SmoothedFtmsSample(
            raw = FtmsSample(
                speedKmh = speedKmh,
                inclinePct = inclinePct,
            ),
            distanceM = distanceM,
            ascentM = 3.0,
            distanceSource = DistanceSource.SPEED_TIME,
        )
}
