package de.ma.ftms.bridge.ui

import de.ma.ftms.bridge.i18n.DeStrings
import de.ma.ftms.bridge.i18n.EnStrings
import de.ma.ftms.bridge.runtime.DashboardMetricKey
import de.ma.ftms.bridge.runtime.EquipmentType
import de.ma.ftms.bridge.runtime.SensorSources
import de.ma.ftms.bridge.runtime.SourcedMetric
import de.ma.ftms.bridge.runtime.ValueSource
import de.ma.ftms.core.DistanceSource
import de.ma.ftms.core.FtmsMachineKind
import de.ma.ftms.core.SensorReadings
import de.ma.ftms.core.storage.SessionAverages
import de.ma.ftms.core.storage.SessionSampleRecord
import de.ma.ftms.core.storage.WorkoutSessionRecord
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class HistoryModelTest {
    private val berlin = TimeZone.getTimeZone("Europe/Berlin")

    /** A moment in Berlin; [month] counts from 1. */
    private fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        Calendar.getInstance(berlin, Locale.ROOT).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    @Test
    fun aPeriodBeginsWithTheRunningWeekMonthOrYear() {
        // A Friday.
        val now = at(2026, 11, 27, 12, 30)

        assertEquals(at(2026, 11, 23), HistoryPeriod.WEEK.startMillis(now, berlin, Locale.GERMANY))
        assertEquals(at(2026, 11, 1), HistoryPeriod.MONTH.startMillis(now, berlin, Locale.GERMANY))
        assertEquals(at(2026, 1, 1), HistoryPeriod.YEAR.startMillis(now, berlin, Locale.GERMANY))
        assertNull(HistoryPeriod.ALL.startMillis(now, berlin, Locale.GERMANY))
    }

    @Test
    fun theWeekBeginsOnTheDayTheRegionBeginsItWith() {
        val friday = at(2026, 11, 27, 12, 30)
        val monday = at(2026, 11, 23, 0, 5)

        // Sunday in the United States, Monday in Germany.
        assertEquals(at(2026, 11, 22), HistoryPeriod.WEEK.startMillis(friday, berlin, Locale.US))
        assertEquals(at(2026, 11, 23), HistoryPeriod.WEEK.startMillis(monday, berlin, Locale.GERMANY))
        // The week reaches back over the turn of a month and of a year.
        assertEquals(at(2025, 12, 29), HistoryPeriod.WEEK.startMillis(at(2026, 1, 2, 9), berlin, Locale.GERMANY))
    }

    @Test
    fun onlyTheSessionsSinceTheStartOfThePeriodAreSummed() {
        val sessions = listOf(
            session("friday", at(2026, 11, 27, 11, 58)),
            session("monday", at(2026, 11, 23)),
            session("sunday", at(2026, 11, 22, 23, 59)),
            session("october", at(2026, 10, 3, 8)),
        )

        val week = sessionsSince(sessions, HistoryPeriod.WEEK.startMillis(at(2026, 11, 27, 12), berlin, Locale.GERMANY))
        val month = sessionsSince(sessions, HistoryPeriod.MONTH.startMillis(at(2026, 11, 27, 12), berlin, Locale.GERMANY))

        assertEquals(listOf("friday", "monday"), week.map { it.sessionId })
        assertEquals(listOf("friday", "monday", "sunday"), month.map { it.sessionId })
        assertSame(sessions, sessionsSince(sessions, null))
    }

    @Test
    fun sessionsAreGroupedByTheMonthTheyStartedInWhereTheUserIs() {
        val sessions = listOf(
            session("late november", at(2026, 11, 27, 11, 58)),
            session("first of november", at(2026, 11, 1, 0, 30)),
            session("last of october", at(2026, 10, 31, 23, 30)),
        )

        val months = groupSessionsByMonth(sessions, berlin)

        assertEquals(listOf(at(2026, 11, 1), at(2026, 10, 1)), months.map { it.startMillis })
        assertEquals(listOf("late november", "first of november"), months[0].sessions.map { it.sessionId })
        assertEquals(listOf("last of october"), months[1].sessions.map { it.sessionId })
        // Half past midnight in Berlin is still October in London.
        assertEquals(2, groupSessionsByMonth(sessions, TimeZone.getTimeZone("Europe/London"))[1].sessions.size)
    }

    @Test
    fun aSessionCountsWithWhatItRecordedWhereThatIsKnown() {
        val sessions = listOf(
            session("known", startedAtMillis = 0L, stoppedAtMillis = 600_000L, finalDistanceM = 1_000.0, finalAscentM = 10.0),
            session("unknown", startedAtMillis = 1_000_000L, stoppedAtMillis = 1_300_000L, finalDistanceM = 500.0, finalAscentM = 5.0),
        )

        val totals = historyTotals(sessions, mapOf("known" to SessionAverages(recordedMillis = 540_000L)))

        assertEquals(HistoryTotals(sessionCount = 2, distanceM = 1_500.0, ascentM = 15.0, durationMillis = 840_000L), totals)
        assertEquals("0:14", formatHoursMinutes(totals.durationMillis))
        assertEquals("9:12", formatHoursMinutes(9 * 3_600_000L + 12 * 60_000L + 59_000L))
    }

    @Test
    fun theListLeadsWithWhatTellsMostOfEachEquipment() {
        assertEquals(SessionFigure.DISTANCE, listFigures(EquipmentType.TREADMILL).first())
        assertEquals(
            listOf(SessionFigure.DURATION, SessionFigure.AVERAGE_POWER, SessionFigure.DISTANCE),
            listFigures(EquipmentType.INDOOR_BIKE),
        )
        EquipmentType.entries.forEach { type ->
            assertEquals(listFigures(type), headlineFigures(type).take(3))
            assertEquals(6, headlineFigures(type).distinct().size)
        }
    }

    @Test
    fun figuresAreWrittenInTheLanguageOfTheAppWithTheirUnitApart() {
        val run = SessionFigures(distanceM = 4_770.0, durationMillis = 2_110_000L, ascentM = 38.4, averageHeartRateBpm = 141.6)

        assertEquals(FigureText("4,77", "km"), SessionFigure.DISTANCE.text(run, EquipmentType.TREADMILL, DeStrings))
        assertEquals(FigureText("4.77", "km"), SessionFigure.DISTANCE.text(run, EquipmentType.TREADMILL, EnStrings))
        assertEquals(FigureText("35:10", ""), SessionFigure.DURATION.text(run, EquipmentType.TREADMILL, DeStrings))
        assertEquals(FigureText("7:22", "/km"), SessionFigure.AVERAGE_PACE.text(run, EquipmentType.TREADMILL, DeStrings))
        assertEquals(FigureText("142", "bpm"), SessionFigure.AVERAGE_HEART_RATE.text(run, EquipmentType.TREADMILL, DeStrings))
        assertEquals(FigureText("38", "m"), SessionFigure.ASCENT.text(run, EquipmentType.TREADMILL, DeStrings))
        assertEquals("7:22 /km", SessionFigure.AVERAGE_PACE.text(run, EquipmentType.TREADMILL, DeStrings).toString())
        assertEquals("35:10", SessionFigure.DURATION.text(run, EquipmentType.TREADMILL, DeStrings).toString())
    }

    @Test
    fun aFigureTheSessionDoesNotHaveIsLeftOut() {
        val ride = SessionFigures(distanceM = 0.0, durationMillis = 600_000L, ascentM = 0.0)

        assertNull(SessionFigure.AVERAGE_POWER.text(ride, EquipmentType.INDOOR_BIKE, DeStrings))
        assertNull(SessionFigure.AVERAGE_HEART_RATE.text(ride, EquipmentType.INDOOR_BIKE, DeStrings))
        assertNull(SessionFigure.AVERAGE_CADENCE.text(ride, EquipmentType.INDOOR_BIKE, DeStrings))
        assertNull(SessionFigure.AVERAGE_SPEED.text(ride, EquipmentType.INDOOR_BIKE, DeStrings))
        assertNull(SessionFigure.AVERAGE_PACE.text(ride, EquipmentType.TREADMILL, DeStrings))
        // A bike does not climb; a treadmill that stayed flat climbed nothing.
        assertNull(SessionFigure.ASCENT.text(ride, EquipmentType.INDOOR_BIKE, DeStrings))
        assertEquals(FigureText("0", "m"), SessionFigure.ASCENT.text(ride, EquipmentType.TREADMILL, DeStrings))
    }

    @Test
    fun theListAndTheSessionAgreeOnItsFigures() {
        val session = session("run", startedAtMillis = 0L, stoppedAtMillis = 700_000L, finalDistanceM = 1_500.0, finalAscentM = 12.0)
        val samples = listOf(
            sample(offsetMillis = 1_000L, distanceM = 2.0, powerW = 100, heartRateBpm = 120, stepRateSpm = 160.0),
            sample(offsetMillis = 600_000L, distanceM = 1_500.0, ascentM = 12.0, powerW = 200, heartRateBpm = 140, stepRateSpm = 170.0),
        )
        val averages = SessionAverages(recordedMillis = 600_000L, powerW = 150.0, heartRateBpm = 130.0, cadence = 165.0)

        assertEquals(sessionFigures(session, samples), sessionFigures(session, averages))
        assertEquals(600_000L, sessionFigures(session, samples).durationMillis)
        // Before the averages are read, the session's own row has to do.
        assertEquals(
            SessionFigures(distanceM = 1_500.0, durationMillis = 700_000L, ascentM = 12.0),
            sessionFigures(session, averages = null),
        )
    }

    @Test
    fun aSessionShowsTheChosenChartsItHasValuesForInTheirOrder() {
        val samples = (1..5).map { second ->
            sample(offsetMillis = second * 1_000L, speedKmh = 8.0 + second, heartRateBpm = 130 + second)
        }

        val charts = historyCharts(
            keys = listOf(DashboardMetricKey.POWER, DashboardMetricKey.HEART_RATE, DashboardMetricKey.SPEED, DashboardMetricKey.MACHINE),
            samples = samples,
            type = EquipmentType.TREADMILL,
            sources = SensorSources(),
            strings = DeStrings,
        )

        assertEquals(listOf(DashboardMetricKey.HEART_RATE, DashboardMetricKey.SPEED), charts.map { it.key })
        assertEquals(listOf("bpm", "km/h"), charts.map { it.unit })
        assertEquals(listOf(ChartPoint(1.0, 9.0), ChartPoint(5.0, 13.0)), charts[1].lines.single().points.let { listOf(it.first(), it.last()) })
        assertEquals(emptyList(), historyCharts(listOf(DashboardMetricKey.SPEED), samples.take(1), EquipmentType.TREADMILL, SensorSources(), DeStrings))
    }

    @Test
    fun aChartOfAValueWithTwoSourcesShowsTheChosenOneAndTheOther() {
        val samples = (1..4).map { second ->
            sample(offsetMillis = second * 1_000L, heartRateBpm = 140).copy(
                equipment = SensorReadings(heartRateBpm = 150 + second),
                watch = SensorReadings(heartRateBpm = 140),
            )
        }

        val chart = historyCharts(
            keys = listOf(DashboardMetricKey.HEART_RATE),
            samples = samples,
            type = EquipmentType.TREADMILL,
            sources = SensorSources(heartRate = ValueSource.EQUIPMENT),
            strings = DeStrings,
        ).single()

        assertEquals(listOf(ChartLineRole.CHOSEN_SOURCE, ChartLineRole.OTHER_SOURCE), chart.lines.map { it.role })
        assertEquals(listOf("Gerät", "Uhr"), chart.lines.map { it.label })
        assertEquals(154.0, chart.lines[0].points.last().y)
        assertEquals(140.0, chart.lines[1].points.last().y)
    }

    @Test
    fun theListOfValuesShowsBothSourcesWhereASessionHoldsBoth() {
        val samples = listOf(
            sample(offsetMillis = 1_000L, powerW = 160, heartRateBpm = 120).copy(
                equipment = SensorReadings(powerW = 160),
                watch = SensorReadings(powerW = 170, heartRateBpm = 120),
            ),
            sample(offsetMillis = 2_000L, powerW = 164, heartRateBpm = 124).copy(
                equipment = SensorReadings(powerW = 164),
                watch = SensorReadings(powerW = 176, heartRateBpm = 124),
            ),
            // The watch alone: counts for its average, as it does for its line in the chart.
            sample(offsetMillis = 3_000L, powerW = null).copy(watch = SensorReadings(powerW = 179)),
        )
        val figures = SessionFigures(distanceM = 0.0, durationMillis = 3_000L, ascentM = 0.0, averagePowerW = 162.0, averageHeartRateBpm = 122.0)
        fun rows(sources: SensorSources) =
            sessionValueRows(figures, EquipmentType.INDOOR_BIKE, samples, sources, DeStrings).associateBy { it.label }

        val byEquipment = rows(SensorSources(power = ValueSource.EQUIPMENT))
        val byWatch = rows(SensorSources(power = ValueSource.WATCH))

        assertEquals(SessionValueRow("Ø Leistung", "162 (175) W", bracketed = "175"), byEquipment["Ø Leistung"])
        assertEquals(SessionValueRow("Ø Leistung", "175 (162) W", bracketed = "162"), byWatch["Ø Leistung"])
        // Only the watch measured the heart rate: the recorded average, with nothing next to it.
        assertEquals(SessionValueRow("Ø HF", "122 bpm"), byEquipment["Ø HF"])
        assertNull(byEquipment["Ø Kadenz"])
        assertNull(sourceAverages(samples, SourcedMetric.HEART_RATE, ValueSource.WATCH))
    }

    @Test
    fun chartsOfTotalsAndRunningAveragesAreWorkedOutFromTheSamples() {
        val samples = listOf(
            sample(offsetMillis = 60_000L, speedKmh = 6.0, distanceM = 100.0, ascentM = 2.0, inclinePct = 2.0),
            // The same moment again, and a total that fell back: neither is a point of a line.
            sample(offsetMillis = 60_000L, speedKmh = 7.0, distanceM = 100.0, ascentM = 2.0, inclinePct = 2.0),
            sample(offsetMillis = 120_000L, speedKmh = 12.0, distanceM = 300.0, ascentM = 1.5, inclinePct = -1.0),
        )

        assertEquals(listOf(ChartPoint(60.0, 10.0), ChartPoint(120.0, 5.0)), samples.chartPoints(DashboardMetricKey.PACE))
        assertEquals(listOf(ChartPoint(60.0, 10.0), ChartPoint(120.0, 2.0 / 0.3)), samples.chartPoints(DashboardMetricKey.AVG_PACE))
        assertEquals(listOf(ChartPoint(60.0, 2.0), ChartPoint(120.0, 2.0)), samples.chartPoints(DashboardMetricKey.ASCENT))
        // 6 km/h is 100 m a minute, of which 2 % go up; downhill nothing is climbed.
        assertEquals(listOf(ChartPoint(60.0, 2.0), ChartPoint(120.0, 0.0)), samples.chartPoints(DashboardMetricKey.ASCENT_RATE))
        // Over the distance covered: 200 m at -1 %, nothing yet at 2 %.
        assertEquals(listOf(ChartPoint(60.0, 2.0), ChartPoint(60.0, 2.0), ChartPoint(120.0, -1.0)), samples.chartPoints(DashboardMetricKey.AVG_INCLINE))
        assertEquals(emptyList(), samples.chartPoints(DashboardMetricKey.MACHINE))
    }

    @Test
    fun aChartMovesUpOrDownByOnePlaceAndStaysInTheList() {
        val charts = listOf("power", "heart rate", "speed")

        assertEquals(listOf("heart rate", "power", "speed"), movedBy(charts, index = 1, delta = -1))
        assertEquals(listOf("power", "speed", "heart rate"), movedBy(charts, index = 1, delta = 1))
        assertSame(charts, movedBy(charts, index = 0, delta = -1))
        assertSame(charts, movedBy(charts, index = 2, delta = 1))
        assertSame(charts, movedBy(charts, index = 5, delta = -1))
    }

    private fun session(
        sessionId: String,
        startedAtMillis: Long,
        stoppedAtMillis: Long = startedAtMillis + 60_000L,
        finalDistanceM: Double = 0.0,
        finalAscentM: Double = 0.0,
    ): WorkoutSessionRecord =
        WorkoutSessionRecord(
            sessionId = sessionId,
            startedAtMillis = startedAtMillis,
            stoppedAtMillis = stoppedAtMillis,
            finalStatus = "stopped",
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
            updatedAtMillis = stoppedAtMillis,
        )

    private fun sample(
        offsetMillis: Long,
        speedKmh: Double? = null,
        distanceM: Double = 0.0,
        ascentM: Double = 0.0,
        inclinePct: Double? = null,
        powerW: Int? = null,
        heartRateBpm: Int? = null,
        stepRateSpm: Double? = null,
    ): SessionSampleRecord =
        SessionSampleRecord(
            sampleId = 0L,
            sessionId = "session",
            timestampMillis = offsetMillis,
            offsetMillis = offsetMillis,
            kind = FtmsMachineKind.TREADMILL,
            rawFlags = 0,
            rawHex = "",
            speedKmh = speedKmh,
            rawDistanceM = null,
            smoothedDistanceM = distanceM,
            ascentM = ascentM,
            inclinePct = inclinePct,
            powerW = powerW,
            heartRateBpm = heartRateBpm,
            cadenceRpm = null,
            stepRateSpm = stepRateSpm,
            resistance = null,
            elapsedS = null,
            remainingS = null,
            parseError = null,
            distanceSource = DistanceSource.NONE,
        )
}
