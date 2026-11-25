package de.ma.ftms.bridge.ui

import de.ma.ftms.bridge.runtime.TargetPowerPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChartScaleTest {
    @Test
    fun axisEndsOnRoundValuesAroundTheData() {
        val scale = niceChartScale(dataMin = 143.0, dataMax = 238.0)

        assertEquals(ChartScale(min = 100.0, max = 250.0, step = 50.0), scale)
        assertEquals(listOf(100.0, 150.0, 200.0, 250.0), scale.ticks)
    }

    @Test
    fun axisNeverHasMoreLabelsThanAsked() {
        val ranges = listOf(0.0 to 1.0, 3.2 to 9.7, 143.0 to 238.0, 96.0 to 104.0, 0.0 to 12_345.0, -4.0 to 3.0)

        ranges.forEach { (low, high) ->
            val scale = niceChartScale(low, high, maxTicks = 5)

            assertTrue(scale.ticks.size <= 5, "$low..$high has ${scale.ticks.size} labels")
            assertTrue(scale.min <= low + 1e-9 && scale.max >= high - 1e-9, "$low..$high is outside ${scale.min}..${scale.max}")
        }
    }

    @Test
    fun stepIsNoFinerThanTheLabelsCanShow() {
        // Whole watts: a step of 2.5 would give two labels that read the same.
        assertEquals(2.5, niceChartScale(dataMin = 190.0, dataMax = 200.0).step)
        assertEquals(5.0, niceChartScale(dataMin = 190.0, dataMax = 200.0, resolution = 1.0).step)
        assertEquals(1.0, niceChartScale(dataMin = 200.0, dataMax = 201.0, resolution = 1.0).step)
        // A pace in steps of three seconds.
        assertEquals(0.05, niceChartScale(dataMin = 5.00, dataMax = 5.04, resolution = 0.05).step, 1e-9)
    }

    @Test
    fun flatLineGetsAnAxisAroundIt() {
        val scale = niceChartScale(dataMin = 200.0, dataMax = 200.0, resolution = 1.0)

        assertTrue(scale.min < 200.0 && scale.max > 200.0, "200 is not inside ${scale.min}..${scale.max}")
    }

    @Test
    fun flatLineAtZeroDoesNotGoBelowZero() {
        val scale = niceChartScale(dataMin = 0.0, dataMax = 0.0, resolution = 1.0)

        assertEquals(0.0, scale.min)
        assertTrue(scale.max >= 1.0)
    }

    @Test
    fun timeLabelsFallOnRoundTimes() {
        assertEquals(listOf(0.0, 300.0, 600.0, 900.0), niceTimeTicks(startSeconds = 0.0, endSeconds = 1_140.0))
        assertEquals(listOf(600.0, 660.0, 720.0), niceTimeTicks(startSeconds = 580.0, endSeconds = 760.0))
        assertEquals(listOf(42.0), niceTimeTicks(startSeconds = 42.0, endSeconds = 42.0))
    }

    @Test
    fun nearestPointIsTheClosestInTime() {
        val points = listOf(ChartPoint(0.0, 1.0), ChartPoint(10.0, 2.0), ChartPoint(20.0, 3.0))

        assertEquals(ChartPoint(10.0, 2.0), nearestPoint(points, 10.0))
        assertEquals(ChartPoint(10.0, 2.0), nearestPoint(points, 14.0))
        assertEquals(ChartPoint(20.0, 3.0), nearestPoint(points, 16.0))
        assertEquals(ChartPoint(0.0, 1.0), nearestPoint(points, -5.0))
        assertEquals(ChartPoint(20.0, 3.0), nearestPoint(points, 99.0))
        assertNull(nearestPoint(emptyList(), 5.0))
    }

    @Test
    fun steppedLineHoldsItsValueUntilTheNextChange() {
        val points = listOf(ChartPoint(10.0, 150.0), ChartPoint(60.0, 200.0))

        assertNull(steppedValueAt(points, 5.0))
        assertEquals(150.0, steppedValueAt(points, 10.0))
        assertEquals(150.0, steppedValueAt(points, 59.0))
        assertEquals(200.0, steppedValueAt(points, 60.0))
        assertEquals(200.0, steppedValueAt(points, 300.0))
    }

    @Test
    fun targetLineReachesFromItsFirstChangeToTheEndOfTheChart() {
        val trace = listOf(TargetPowerPoint(ORIGIN + 30_000L, 150), TargetPowerPoint(ORIGIN + 90_000L, 200))

        assertEquals(
            listOf(ChartPoint(30.0, 150.0), ChartPoint(90.0, 200.0), ChartPoint(120.0, 200.0)),
            targetPowerLine(trace, ORIGIN, fromSeconds = 0.0, toSeconds = 120.0),
        )
    }

    @Test
    fun targetLineBeginsWithTheTargetInEffectWhereTheChartBegins() {
        val trace = listOf(
            TargetPowerPoint(ORIGIN + 10_000L, 100),
            TargetPowerPoint(ORIGIN + 30_000L, 150),
            TargetPowerPoint(ORIGIN + 90_000L, 200),
        )

        assertEquals(
            listOf(ChartPoint(60.0, 150.0), ChartPoint(90.0, 200.0), ChartPoint(120.0, 200.0)),
            targetPowerLine(trace, ORIGIN, fromSeconds = 60.0, toSeconds = 120.0),
        )
    }

    @Test
    fun targetLineCoversOnlyTheStretchSinceTheTargetWasLastUnset() {
        val trace = listOf(
            TargetPowerPoint(ORIGIN + 10_000L, 100),
            TargetPowerPoint(ORIGIN + 40_000L, null),
            TargetPowerPoint(ORIGIN + 70_000L, 180),
        )

        assertEquals(
            listOf(ChartPoint(70.0, 180.0), ChartPoint(120.0, 180.0)),
            targetPowerLine(trace, ORIGIN, fromSeconds = 0.0, toSeconds = 120.0),
        )
    }

    @Test
    fun noTargetLineWithoutATarget() {
        assertEquals(emptyList(), targetPowerLine(emptyList(), ORIGIN, fromSeconds = 0.0, toSeconds = 120.0))
        assertEquals(
            emptyList(),
            targetPowerLine(
                listOf(TargetPowerPoint(ORIGIN, 100), TargetPowerPoint(ORIGIN + 5_000L, null)),
                ORIGIN,
                fromSeconds = 0.0,
                toSeconds = 120.0,
            ),
        )
    }

    @Test
    fun paceIsWrittenInMinutesAndSeconds() {
        assertEquals("5:30", PACE_CHART_FORMAT.format(5.5))
        assertEquals("4:03", PACE_CHART_FORMAT.format(4.05))
        assertEquals("10:00", PACE_CHART_FORMAT.format(9.999))
    }

    private companion object {
        const val ORIGIN = 1_700_000_000_000L
    }
}
