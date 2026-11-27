package de.ma.ftms.bridge.ui

import de.ma.ftms.bridge.i18n.Strings
import de.ma.ftms.bridge.i18n.fmt
import de.ma.ftms.bridge.i18n.formatNeutral
import de.ma.ftms.bridge.runtime.DashboardMetricKey
import de.ma.ftms.bridge.runtime.EquipmentType
import de.ma.ftms.bridge.runtime.SESSION_STATUS_RUNNING
import de.ma.ftms.bridge.runtime.SensorSources
import de.ma.ftms.bridge.runtime.SourcedMetric
import de.ma.ftms.bridge.runtime.ValueSource
import de.ma.ftms.core.SensorReadings
import de.ma.ftms.core.storage.SessionAverages
import de.ma.ftms.core.storage.SessionSampleRecord
import de.ma.ftms.core.storage.WorkoutSessionRecord
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// What the history screen shows, worked out from the stored sessions and samples. Nothing in this
// file draws; HistoryView.kt does.

internal const val SESSION_STATUS_STOPPED = "stopped"

internal val WorkoutSessionRecord.equipmentType: EquipmentType
    get() = EquipmentType.fromMachineKind(equipmentKind)

/** The equipment types that occur in [sessions], in the order the app lists types. */
internal fun recordedEquipmentTypes(sessions: List<WorkoutSessionRecord>): List<EquipmentType> =
    EquipmentType.entries.filter { type -> sessions.any { it.equipmentType == type } }

internal fun filterSessionsByType(
    sessions: List<WorkoutSessionRecord>,
    type: EquipmentType?,
): List<WorkoutSessionRecord> =
    if (type == null) sessions else sessions.filter { it.equipmentType == type }

// ---- Periods and months ----

/** The stretch of time the sums above the list of sessions cover. */
internal enum class HistoryPeriod {
    WEEK,
    MONTH,
    YEAR,
    ALL,
}

/**
 * The first moment of the week, month or year that [nowMillis] lies in; null for all time. The
 * week begins on the day [locale] begins it with.
 */
internal fun HistoryPeriod.startMillis(nowMillis: Long, zone: TimeZone, locale: Locale): Long? {
    if (this == HistoryPeriod.ALL) {
        return null
    }

    val calendar = Calendar.getInstance(zone, locale).apply {
        timeInMillis = nowMillis
        toStartOfDay()
    }
    when (this) {
        HistoryPeriod.WEEK -> while (calendar.get(Calendar.DAY_OF_WEEK) != calendar.firstDayOfWeek) {
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }
        HistoryPeriod.MONTH -> calendar.set(Calendar.DAY_OF_MONTH, 1)
        HistoryPeriod.YEAR -> calendar.set(Calendar.DAY_OF_YEAR, 1)
        HistoryPeriod.ALL -> Unit
    }
    return calendar.timeInMillis
}

/** The sessions that started at or after [startMillis]; all of them without a start. */
internal fun sessionsSince(sessions: List<WorkoutSessionRecord>, startMillis: Long?): List<WorkoutSessionRecord> =
    if (startMillis == null) sessions else sessions.filter { it.startedAtMillis >= startMillis }

/** The sessions that started in one calendar month. */
internal data class HistoryMonth(
    /** The first moment of the month, to name it by. */
    val startMillis: Long,
    val sessions: List<WorkoutSessionRecord>,
)

/** [sessions] by the month they started in, months and sessions in the order they come in. */
internal fun groupSessionsByMonth(sessions: List<WorkoutSessionRecord>, zone: TimeZone): List<HistoryMonth> {
    val calendar = Calendar.getInstance(zone, Locale.ROOT)
    val months = LinkedHashMap<Long, MutableList<WorkoutSessionRecord>>()
    sessions.forEach { session ->
        calendar.timeInMillis = session.startedAtMillis
        calendar.toStartOfDay()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        months.getOrPut(calendar.timeInMillis) { mutableListOf() } += session
    }
    return months.map { (startMillis, inMonth) -> HistoryMonth(startMillis, inMonth) }
}

private fun Calendar.toStartOfDay() {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

// ---- Sums ----

internal data class HistoryTotals(
    val sessionCount: Int = 0,
    val distanceM: Double = 0.0,
    val ascentM: Double = 0.0,
    val durationMillis: Long = 0L,
)

/**
 * How long [session] recorded: up to its last sample where [averages] know it, which is what the
 * session itself shows, and from start to stop otherwise.
 */
internal fun recordedMillis(session: WorkoutSessionRecord, averages: SessionAverages?): Long =
    averages?.recordedMillis ?: session.durationMillis

internal fun historyTotals(
    sessions: List<WorkoutSessionRecord>,
    averages: Map<String, SessionAverages> = emptyMap(),
): HistoryTotals =
    sessions.fold(HistoryTotals()) { totals, session ->
        totals.copy(
            sessionCount = totals.sessionCount + 1,
            distanceM = totals.distanceM + session.finalDistanceM.coerceAtLeast(0.0),
            ascentM = totals.ascentM + session.finalAscentM.coerceAtLeast(0.0),
            durationMillis = totals.durationMillis + recordedMillis(session, averages[session.sessionId]),
        )
    }

// ---- The figures of one session ----

/** What a session comes down to in a few numbers. An average is null where nothing was recorded. */
internal data class SessionFigures(
    val distanceM: Double,
    val durationMillis: Long,
    val ascentM: Double,
    val averagePowerW: Double? = null,
    val averageHeartRateBpm: Double? = null,
    val averageCadence: Double? = null,
)

/** The figures of [session] as far as the list of sessions knows them, without its samples. */
internal fun sessionFigures(session: WorkoutSessionRecord, averages: SessionAverages?): SessionFigures =
    SessionFigures(
        distanceM = session.finalDistanceM,
        durationMillis = recordedMillis(session, averages),
        ascentM = session.finalAscentM,
        averagePowerW = averages?.powerW,
        averageHeartRateBpm = averages?.heartRateBpm,
        averageCadence = averages?.cadence,
    )

/** The figures of [session] from its [samples]. Without samples only what the session itself stores. */
internal fun sessionFigures(session: WorkoutSessionRecord, samples: List<SessionSampleRecord>): SessionFigures =
    SessionFigures(
        distanceM = maxOf(session.finalDistanceM, samples.maxOfOrNull { it.smoothedDistanceM } ?: 0.0),
        durationMillis = samples.lastOrNull()?.offsetMillis ?: session.durationMillis,
        ascentM = maxOf(session.finalAscentM, samples.maxOfOrNull { it.ascentM } ?: 0.0),
        averagePowerW = averagePowerWatts(samples),
        averageHeartRateBpm = averageHeartRateBpm(samples),
        averageCadence = averageCadenceOrStep(samples),
    )

/** One of the numbers a session is summed up in. */
internal enum class SessionFigure {
    DISTANCE,
    DURATION,
    AVERAGE_PACE,
    AVERAGE_SPEED,
    AVERAGE_POWER,
    AVERAGE_HEART_RATE,
    AVERAGE_CADENCE,
    ASCENT,
}

/** The three figures a row of the list shows for a session of [type], the most telling one first. */
internal fun listFigures(type: EquipmentType): List<SessionFigure> =
    when (type) {
        EquipmentType.TREADMILL -> listOf(SessionFigure.DISTANCE, SessionFigure.DURATION, SessionFigure.AVERAGE_PACE)
        EquipmentType.INDOOR_BIKE -> listOf(SessionFigure.DURATION, SessionFigure.AVERAGE_POWER, SessionFigure.DISTANCE)
        EquipmentType.CROSS_TRAINER -> listOf(SessionFigure.DISTANCE, SessionFigure.DURATION, SessionFigure.AVERAGE_SPEED)
    }

/** The figures at the top of a session of [type]: those of the list, then what else sums it up. */
internal fun headlineFigures(type: EquipmentType): List<SessionFigure> =
    listFigures(type) + when (type) {
        EquipmentType.TREADMILL -> listOf(SessionFigure.AVERAGE_HEART_RATE, SessionFigure.AVERAGE_CADENCE, SessionFigure.ASCENT)
        EquipmentType.INDOOR_BIKE -> listOf(SessionFigure.AVERAGE_HEART_RATE, SessionFigure.AVERAGE_CADENCE, SessionFigure.AVERAGE_SPEED)
        EquipmentType.CROSS_TRAINER -> listOf(SessionFigure.AVERAGE_HEART_RATE, SessionFigure.AVERAGE_CADENCE, SessionFigure.ASCENT)
    }

/** A figure as it is written: the number and, apart from it, its unit (empty for a duration). */
internal data class FigureText(val value: String, val unit: String) {
    override fun toString(): String = if (unit.isEmpty()) value else "$value $unit"
}

/** This figure of a session of [type], or null if the session has no such value. */
internal fun SessionFigure.text(figures: SessionFigures, type: EquipmentType, strings: Strings): FigureText? =
    when (this) {
        SessionFigure.DISTANCE -> FigureText(strings.fmt("%.2f", figures.distanceM / 1000.0), "km")
        SessionFigure.DURATION -> FigureText(formatDuration(figures.durationMillis), "")
        SessionFigure.AVERAGE_PACE -> averagePaceClock(figures.distanceM, figures.durationMillis)?.let { FigureText(it, "/km") }
        SessionFigure.AVERAGE_SPEED -> averageSpeedKmh(figures.distanceM, figures.durationMillis)
            ?.let { FigureText(strings.fmt("%.1f", it), "km/h") }
        SessionFigure.AVERAGE_POWER -> figures.averagePowerW?.let { FigureText(strings.fmt("%.0f", it), "W") }
        SessionFigure.AVERAGE_HEART_RATE -> figures.averageHeartRateBpm?.let { FigureText(strings.fmt("%.0f", it), "bpm") }
        SessionFigure.AVERAGE_CADENCE -> figures.averageCadence?.let { FigureText(strings.fmt("%.0f", it), cadenceUnit(type)) }
        SessionFigure.ASCENT -> if (type.showsClimb) FigureText(strings.fmt("%.0f", figures.ascentM), "m") else null
    }

internal fun SessionFigure.label(type: EquipmentType, strings: Strings): String =
    when (this) {
        SessionFigure.DISTANCE -> strings.common.distance
        SessionFigure.DURATION -> strings.common.duration
        SessionFigure.AVERAGE_PACE -> strings.common.averagePace
        SessionFigure.AVERAGE_SPEED -> strings.common.averageSpeed
        SessionFigure.AVERAGE_POWER -> strings.common.averagePower
        SessionFigure.AVERAGE_HEART_RATE -> strings.common.averageHeartRate
        SessionFigure.AVERAGE_CADENCE -> averageCadenceLabel(type, strings)
        SessionFigure.ASCENT -> strings.common.ascent
    }

/**
 * One line of the full list of what a session recorded.
 *
 * @param bracketed the number that [value] holds in brackets, if any: the average of the source
 * that is not chosen. It is drawn in the colour of that source's line.
 */
internal data class SessionValueRow(val label: String, val value: String, val bracketed: String? = null)

/**
 * Every figure of a session beyond its headline: the full list of what was recorded.
 *
 * Heart rate, power and cadence come from the equipment or the watch. Where a session holds both,
 * their line reads like the chart of that value: the average of the source chosen now in [sources],
 * with that of the other one in brackets behind it. Otherwise it is the average that was recorded.
 */
internal fun sessionValueRows(
    figures: SessionFigures,
    type: EquipmentType,
    samples: List<SessionSampleRecord>,
    sources: SensorSources,
    strings: Strings,
): List<SessionValueRow> {
    val missing = strings.common.missingValue
    val rows = mutableListOf(
        SessionValueRow(strings.common.distance, strings.fmt("%.2f km", figures.distanceM / 1000.0)),
        SessionValueRow(strings.common.duration, formatDuration(figures.durationMillis)),
        SessionValueRow(
            strings.common.averageSpeed,
            averageSpeedKmh(figures.distanceM, figures.durationMillis)?.let { strings.fmt("%.2f km/h", it) } ?: missing,
        ),
    )
    if (type.showsPace) {
        rows += SessionValueRow(strings.common.averagePace, formatAveragePace(figures.distanceM, figures.durationMillis) ?: missing)
    }
    if (type.showsClimb) {
        rows += SessionValueRow(strings.common.ascent, strings.fmt("%.1f m", figures.ascentM))
        rows += SessionValueRow(
            strings.common.averageIncline,
            averageInclinePct(samples)?.let { strings.fmt("%.1f %%", it) } ?: missing,
        )
        rows += SessionValueRow(
            strings.common.averageAscentRate,
            averageAscentRateMetersPerMinute(figures.ascentM, figures.durationMillis)?.let { strings.fmt("%.1f m/min", it) } ?: missing,
        )
    }

    fun sourced(metric: SourcedMetric, label: String, recorded: Double?, pattern: String) {
        val unit = sourcedMetricUnit(metric, type)
        val both = sourceAverages(samples, metric, sources.of(metric))
        if (both != null) {
            val other = strings.fmt(pattern, both.other)
            rows += SessionValueRow(label, "${averageWithOther(strings.fmt(pattern, both.chosen), other)} $unit", other)
        } else if (recorded != null) {
            rows += SessionValueRow(label, "${strings.fmt(pattern, recorded)} $unit")
        }
    }
    sourced(SourcedMetric.POWER, strings.common.averagePower, figures.averagePowerW, "%.0f")
    sourced(SourcedMetric.HEART_RATE, strings.common.averageHeartRate, figures.averageHeartRateBpm, "%.0f")
    sourced(SourcedMetric.CADENCE, averageCadenceLabel(type, strings), figures.averageCadence, "%.1f")
    averageResistance(samples)?.let { rows += SessionValueRow(strings.common.averageResistance, strings.fmt("%.1f", it)) }
    return rows
}

/** The averages of one value from the source that is chosen and from the other one. */
internal data class SourceAverages(val chosen: Double, val other: Double)

/**
 * What the [chosen] source of [metric] and the other one each averaged, each over the samples it
 * delivered a value for, as the lines of the chart do. Null unless both delivered one.
 */
internal fun sourceAverages(samples: List<SessionSampleRecord>, metric: SourcedMetric, chosen: ValueSource): SourceAverages? {
    fun average(source: ValueSource): Double? =
        samples.mapNotNull { sourceValue(metric, if (source == ValueSource.EQUIPMENT) it.equipment else it.watch) }
            .takeIf { it.isNotEmpty() }
            ?.average()
    return SourceAverages(chosen = average(chosen) ?: return null, other = average(chosen.other()) ?: return null)
}

internal fun averageSpeedKmh(distanceM: Double, durationMillis: Long): Double? {
    if (distanceM <= 0.0 || durationMillis <= 0L) {
        return null
    }
    return distanceM / 1000.0 / (durationMillis.toDouble() / 3_600_000.0)
}

/** The average pace per kilometre as `m:ss`, or null without a distance or a duration. */
internal fun averagePaceClock(distanceM: Double, durationMillis: Long): String? {
    if (distanceM <= 0.0 || durationMillis <= 0L) {
        return null
    }

    val secondsPerKm = (durationMillis / 1000.0 / (distanceM / 1000.0)).toLong()
    return formatNeutral("%d:%02d", secondsPerKm / 60L, secondsPerKm % 60L)
}

internal fun averageAscentRateMetersPerMinute(ascentM: Double, durationMillis: Long): Double? {
    if (ascentM <= 0.0 || durationMillis <= 0L) {
        return null
    }
    return ascentM / (durationMillis.toDouble() / 60_000.0)
}

internal fun averagePowerWatts(samples: List<SessionSampleRecord>): Double? =
    samples.averageOfNonNull { powerW?.toDouble() }

internal fun averageHeartRateBpm(samples: List<SessionSampleRecord>): Double? =
    samples.averageOfNonNull { heartRateBpm?.toDouble() }

internal fun averageCadenceOrStep(samples: List<SessionSampleRecord>): Double? =
    samples.averageOfNonNull { cadenceRpm ?: stepRateSpm }

internal fun averageResistance(samples: List<SessionSampleRecord>): Double? =
    samples.averageOfNonNull { resistance }

private fun List<SessionSampleRecord>.averageOfNonNull(value: SessionSampleRecord.() -> Double?): Double? {
    val values = mapNotNull { it.value() }
    return values.takeIf { it.isNotEmpty() }?.average()
}

private fun averageInclinePct(samples: List<SessionSampleRecord>): Double? =
    samples.averageInclinePoints().lastOrNull()?.y

/** A duration as `mm:ss`, or `h:mm:ss` from one hour. */
internal fun formatDuration(durationMillis: Long): String =
    formatElapsedTime(durationMillis / 1000.0)

/** A long duration as hours and minutes, `h:mm`, as the sum of many sessions is written. */
internal fun formatHoursMinutes(durationMillis: Long): String {
    val totalMinutes = durationMillis.coerceAtLeast(0L) / 60_000L
    return formatNeutral("%d:%02d", totalMinutes / 60L, totalMinutes % 60L)
}

// ---- Equipment and watch ----

/** The averages of one value from both sources, over the samples where both delivered it. */
internal data class SourceComparison(
    val equipmentAverage: Double,
    val watchAverage: Double,
) {
    /** How far the watch is above (positive) or below the equipment, in percent. */
    val differencePercent: Double?
        get() = if (equipmentAverage == 0.0) null else (watchAverage - equipmentAverage) / equipmentAverage * 100.0
}

internal fun sourceValue(metric: SourcedMetric, readings: SensorReadings?): Double? =
    when (metric) {
        SourcedMetric.HEART_RATE -> readings?.heartRateBpm?.toDouble()
        SourcedMetric.POWER -> readings?.powerW?.toDouble()
        SourcedMetric.CADENCE -> readings?.cadenceRpm
    }

/**
 * Compares the two sources of [metric]. Only samples where both have a value count, so a source
 * that joined late or dropped out does not shift its average. Null if there is no such sample.
 */
internal fun compareSources(samples: List<SessionSampleRecord>, metric: SourcedMetric): SourceComparison? {
    val pairs = samples.mapNotNull { sample ->
        val equipment = sourceValue(metric, sample.equipment) ?: return@mapNotNull null
        val watch = sourceValue(metric, sample.watch) ?: return@mapNotNull null
        equipment to watch
    }
    if (pairs.isEmpty()) {
        return null
    }

    return SourceComparison(
        equipmentAverage = pairs.map { it.first }.average(),
        watchAverage = pairs.map { it.second }.average(),
    )
}

internal fun sourcedMetricUnit(metric: SourcedMetric, type: EquipmentType): String =
    when (metric) {
        SourcedMetric.HEART_RATE -> "bpm"
        SourcedMetric.POWER -> "W"
        SourcedMetric.CADENCE -> cadenceUnit(type)
    }

// ---- Charts ----

/** One chart of a recorded session, ready to draw. */
internal class HistoryChart(
    val key: DashboardMetricKey,
    val title: String,
    val unit: String,
    val lines: List<ChartLine>,
)

/**
 * The charts of a recorded session: those of [keys], in that order, that the session has at least
 * two values for. What was never delivered gets no chart rather than an empty one.
 *
 * Heart rate, power and cadence come from the equipment or the watch. Their charts show the
 * source that is chosen now in [sources] and, next to it, what the other one delivered.
 */
internal fun historyCharts(
    keys: List<DashboardMetricKey>,
    samples: List<SessionSampleRecord>,
    type: EquipmentType,
    sources: SensorSources,
    strings: Strings,
): List<HistoryChart> =
    keys.mapNotNull { key ->
        val title = key.label(strings, type)
        val recorded = samples.chartPoints(key)
        val metric = key.sourcedMetric()
        val lines = when {
            recorded.size < 2 -> emptyList()
            metric == null -> listOf(ChartLine(title, recorded))
            else -> {
                val chosen = sources.of(metric)
                historySourceChartLines(
                    title = title,
                    recorded = recorded,
                    chosen = samples.points { sourceValue(metric, if (chosen == ValueSource.EQUIPMENT) equipment else watch) },
                    other = samples.points { sourceValue(metric, if (chosen == ValueSource.EQUIPMENT) watch else equipment) },
                    chosenLabel = valueSourceLabel(chosen, strings),
                    otherLabel = valueSourceLabel(chosen.other(), strings),
                )
            }
        }
        if (lines.isEmpty()) null else HistoryChart(key, title, key.unit(type), lines)
    }

/** The values of [key] over the session, as a session stores them. Empty for what is no line. */
internal fun List<SessionSampleRecord>.chartPoints(key: DashboardMetricKey): List<ChartPoint> =
    when (key) {
        DashboardMetricKey.SPEED -> points { speedKmh }
        DashboardMetricKey.PACE -> points { speedKmh?.takeIf { it > 0.0 }?.let { 60.0 / it } }
        DashboardMetricKey.AVG_PACE -> points {
            val distanceKm = smoothedDistanceM / 1000.0
            val minutes = offsetMillis.toDouble() / 60_000.0
            if (distanceKm > 0.0 && minutes > 0.0) minutes / distanceKm else null
        }
        DashboardMetricKey.INCLINE -> points { inclinePct }
        DashboardMetricKey.AVG_INCLINE -> averageInclinePoints()
        DashboardMetricKey.DISTANCE -> points(monotonic = true) { smoothedDistanceM }
        DashboardMetricKey.ASCENT -> points(monotonic = true) { ascentM }
        DashboardMetricKey.ASCENT_RATE -> points {
            val speed = speedKmh?.takeIf { it > 0.0 }
            val incline = inclinePct
            if (speed == null || incline == null) null else speed * 1000.0 / 60.0 * incline.coerceAtLeast(0.0) / 100.0
        }
        DashboardMetricKey.POWER -> points { powerW?.toDouble() }
        DashboardMetricKey.HEART_RATE -> points { heartRateBpm?.toDouble() }
        DashboardMetricKey.CADENCE_STEP -> points { cadenceRpm ?: stepRateSpm }
        DashboardMetricKey.RESISTANCE -> points { resistance }
        DashboardMetricKey.ELAPSED,
        DashboardMetricKey.MACHINE,
        -> emptyList()
    }

/** One point per sample that has a [value], at the sample's time; samples that do not advance in time are skipped. */
private fun List<SessionSampleRecord>.points(
    monotonic: Boolean = false,
    value: SessionSampleRecord.() -> Double?,
): List<ChartPoint> {
    var lastX: Double? = null
    var lastY: Double? = null
    return mapNotNull { sample ->
        val y = sample.value() ?: return@mapNotNull null
        val x = sample.offsetMillis.toDouble() / 1000.0
        val previousX = lastX
        if (previousX != null && x <= previousX) {
            return@mapNotNull null
        }

        val chartY = if (monotonic) y.coerceAtLeast(lastY ?: y) else y
        lastX = x
        lastY = chartY
        ChartPoint(x, chartY)
    }
}

/** The incline averaged over the distance covered so far; over the samples while nothing was covered. */
private fun List<SessionSampleRecord>.averageInclinePoints(): List<ChartPoint> {
    var previousDistance = firstOrNull()?.smoothedDistanceM ?: 0.0
    var weightedDistance = 0.0
    var weightedIncline = 0.0
    val simpleInclines = mutableListOf<Double>()
    return mapNotNull { sample ->
        val x = sample.offsetMillis.toDouble() / 1000.0
        val incline = sample.inclinePct
        if (incline != null) {
            simpleInclines += incline
            val deltaDistance = (sample.smoothedDistanceM - previousDistance).coerceAtLeast(0.0)
            if (deltaDistance > 0.0) {
                weightedDistance += deltaDistance
                weightedIncline += deltaDistance * incline
            }
        }
        previousDistance = sample.smoothedDistanceM
        val average = if (weightedDistance > 0.0) {
            weightedIncline / weightedDistance
        } else {
            simpleInclines.takeIf { it.isNotEmpty() }?.average()
        }
        average?.let { ChartPoint(x, it) }
    }
}

// ---- Choosing and ordering charts in the settings ----

/** [items] with the one at [index] moved by [delta] places; unchanged if that leaves the list. */
internal fun <T> movedBy(items: List<T>, index: Int, delta: Int): List<T> {
    val target = index + delta
    if (index !in items.indices || target !in items.indices) {
        return items
    }

    return items.toMutableList().apply { add(target, removeAt(index)) }
}
