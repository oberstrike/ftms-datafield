package de.ma.ftms.bridge.ui

import de.ma.ftms.bridge.runtime.DashboardMetricKey
import de.ma.ftms.bridge.runtime.SourcedMetric
import de.ma.ftms.bridge.runtime.ValueSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceChartLinesTest {
    @Test
    fun theAverageOfTheOtherSourceStandsInBracketsBehindTheChosenOne() {
        assertEquals("148 (150)", averageWithOther("148", "150"))
        assertEquals("148", averageWithOther("148", null))
    }

    private fun line(vararg values: Double) = values.mapIndexed { index, value -> ChartPoint(index.toDouble(), value) }

    @Test
    fun theChosenSourceComesFirstAndTheOtherOneNextToIt() {
        val equipment = line(150.0, 152.0, 151.0)
        val watch = line(149.0, 155.0, 150.0)

        val lines = sourceChartLines("Power", chosen = equipment, other = watch, chosenLabel = "Equipment", otherLabel = "Watch")

        assertEquals(listOf("Equipment", "Watch"), lines.map { it.label })
        assertEquals(listOf(ChartLineRole.CHOSEN_SOURCE, ChartLineRole.OTHER_SOURCE), lines.map { it.role })
        assertSame(equipment, lines[0].points)
        assertSame(watch, lines[1].points)
    }

    @Test
    fun aSourceThatDeliveredNothingLeavesTheChosenOneAloneUnderTheTitle() {
        val lines = sourceChartLines("Power", chosen = line(150.0, 152.0), other = line(149.0), chosenLabel = "Equipment", otherLabel = "Watch")

        assertEquals(listOf("Power"), lines.map { it.label })
        assertEquals(ChartLineRole.CHOSEN_SOURCE, lines.single().role)
    }

    @Test
    fun withoutValuesOfTheChosenSourceThereIsNoChart() {
        assertTrue(sourceChartLines("Power", chosen = line(150.0), other = line(149.0, 150.0), chosenLabel = "Equipment", otherLabel = "Watch").isEmpty())
    }

    @Test
    fun aRecordedSessionShowsTheSourceChosenNowWithTheOtherOne() {
        val recorded = line(60.0, 61.0, 62.0)
        val watch = line(120.0, 122.0, 124.0)

        val lines = historySourceChartLines("Cadence", recorded, chosen = watch, other = recorded, chosenLabel = "Watch", otherLabel = "Equipment")

        assertEquals(listOf("Watch", "Equipment"), lines.map { it.label })
        assertSame(watch, lines[0].points)
    }

    @Test
    fun aSessionWithoutTheSourceChosenNowShowsWhatWasRecordedOnce() {
        val recorded = line(60.0, 61.0, 62.0)

        // The watch is chosen now, but this session had the equipment's values only.
        val lines = historySourceChartLines("Cadence", recorded, chosen = emptyList(), other = recorded, chosenLabel = "Watch", otherLabel = "Equipment")

        assertEquals(listOf("Cadence"), lines.map { it.label })
        assertSame(recorded, lines.single().points)
    }

    @Test
    fun onlyHeartRatePowerAndCadenceHaveTwoSources() {
        assertEquals(SourcedMetric.POWER, DashboardMetricKey.POWER.sourcedMetric())
        assertEquals(SourcedMetric.HEART_RATE, DashboardMetricKey.HEART_RATE.sourcedMetric())
        assertEquals(SourcedMetric.CADENCE, DashboardMetricKey.CADENCE_STEP.sourcedMetric())
        assertNull(DashboardMetricKey.SPEED.sourcedMetric())
        assertEquals(ValueSource.WATCH, ValueSource.EQUIPMENT.other())
        assertEquals(ValueSource.EQUIPMENT, ValueSource.WATCH.other())
    }
}
