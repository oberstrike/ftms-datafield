package de.ma.ftms.bridge.ui

import de.ma.ftms.bridge.runtime.DashboardChartTimespan
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardChartTimespanTest {
    @Test
    fun fullSessionKeepsAllPoints() {
        val points = listOf(point(0.0), point(60.0), point(120.0))

        assertEquals(points, points.lastMillis(DashboardChartTimespan.FULL_SESSION.durationMillis))
    }

    @Test
    fun timedWindowKeepsThePointsBeforeTheLatest() {
        val points = listOf(point(0.0), point(540.0), point(600.0), point(660.0))

        assertEquals(
            listOf(point(600.0), point(660.0)),
            points.lastMillis(DashboardChartTimespan.MINUTES_1.durationMillis),
        )
    }

    @Test
    fun aSessionShorterThanTheWindowIsKeptWhole() {
        val points = listOf(point(0.0), point(30.0))

        assertEquals(points, points.lastMillis(DashboardChartTimespan.MINUTES_1.durationMillis))
    }

    @Test
    fun noPointsStayNoPoints() {
        assertEquals(emptyList(), emptyList<ChartPoint>().lastMillis(60_000L))
    }

    private fun point(seconds: Double) = ChartPoint(x = seconds, y = seconds / 10.0)
}
