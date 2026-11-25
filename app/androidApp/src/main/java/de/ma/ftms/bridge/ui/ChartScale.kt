package de.ma.ftms.bridge.ui

import de.ma.ftms.bridge.i18n.Strings
import de.ma.ftms.bridge.i18n.fmt
import de.ma.ftms.bridge.i18n.formatNeutral
import de.ma.ftms.bridge.runtime.TargetPowerPoint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** One value of a chart line: [x] in seconds, [y] in the unit of the line. */
internal data class ChartPoint(val x: Double, val y: Double)

/** A value axis with round bounds and the values that get a grid line and a label. */
internal data class ChartScale(val min: Double, val max: Double, val step: Double) {
    val ticks: List<Double>
        get() {
            val count = ((max - min) / step).let { Math.round(it).toInt() }
            return (0..count).map { min + it * step }
        }
}

/**
 * A value axis for data between [dataMin] and [dataMax]: bounds on multiples of a round step
 * (1, 2, 2.5 or 5 times a power of ten) with at most [maxTicks] labelled values.
 *
 * @param resolution the smallest difference the labels can show, e.g. 1.0 for whole watts. The
 * step is a multiple of it, so no two labels read the same. 0 for no such limit.
 */
internal fun niceChartScale(dataMin: Double, dataMax: Double, maxTicks: Int = 5, resolution: Double = 0.0): ChartScale {
    var low = minOf(dataMin, dataMax)
    var high = maxOf(dataMin, dataMax)
    if (high - low < 1e-9) {
        // A flat line gets an axis around it, without going below zero for it.
        val margin = maxOf(1.0, abs(low) * 0.1, resolution)
        val flat = low
        low = if (flat >= 0.0) maxOf(0.0, flat - margin / 2.0) else flat - margin / 2.0
        high = low + margin
    }

    val intervals = (maxTicks - 1).coerceAtLeast(1)
    var step = niceStep((high - low) / intervals)
    while (true) {
        if (isMultipleOf(step, resolution)) {
            // Adding zero turns a negative zero, which would be written as "-0", into zero.
            val min = floor(low / step + 1e-9) * step + 0.0
            val max = ceil(high / step - 1e-9) * step + 0.0
            if (Math.round((max - min) / step) <= intervals) {
                return ChartScale(min = min, max = max, step = step)
            }
        }
        step = niceStep(step * 1.01)
    }
}

private fun isMultipleOf(step: Double, resolution: Double): Boolean {
    if (resolution <= 0.0) {
        return true
    }

    val ratio = step / resolution
    return ratio > 1.0 - 1e-6 && abs(ratio - Math.round(ratio)) < 1e-6
}

/** The smallest round step that is at least [raw]. */
private fun niceStep(raw: Double): Double {
    val magnitude = 10.0.pow(floor(log10(raw)))
    val fraction = raw / magnitude
    val nice = when {
        fraction <= 1.0 + 1e-9 -> 1.0
        fraction <= 2.0 + 1e-9 -> 2.0
        fraction <= 2.5 + 1e-9 -> 2.5
        fraction <= 5.0 + 1e-9 -> 5.0
        else -> 10.0
    }
    return nice * magnitude
}

/**
 * How the values of a chart are written.
 *
 * @param resolution the smallest difference [format] shows; the value axis is divided no finer.
 */
internal class ChartValueFormat(val resolution: Double, val format: (Double) -> String)

/** Values written as a decimal number with [decimals] places in the language of [strings]. */
internal fun decimalChartFormat(strings: Strings, decimals: Int): ChartValueFormat =
    ChartValueFormat(resolution = 10.0.pow(-decimals)) { strings.fmt("%.${decimals}f", it) }

/** A pace in minutes per kilometre, written as `m:ss`. */
internal val PACE_CHART_FORMAT = ChartValueFormat(resolution = 0.05) { minutes ->
    val totalSeconds = Math.round(minutes * 60.0).coerceAtLeast(0L)
    formatNeutral("%d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}

/** The points of the last [durationMillis] before the final one; all of them without a duration. */
internal fun List<ChartPoint>.lastMillis(durationMillis: Long?): List<ChartPoint> {
    val from = (lastOrNull()?.x ?: return this) - (durationMillis ?: return this) / 1000.0
    return if (first().x >= from) this else filter { it.x >= from }
}

private val TIME_STEPS_SECONDS = listOf(
    5.0, 10.0, 15.0, 30.0, 60.0, 120.0, 300.0, 600.0, 900.0, 1_800.0, 3_600.0, 7_200.0, 14_400.0,
)

/** Times on round steps between [startSeconds] and [endSeconds], at most [maxTicks] of them. */
internal fun niceTimeTicks(startSeconds: Double, endSeconds: Double, maxTicks: Int = 4): List<Double> {
    val span = endSeconds - startSeconds
    if (span <= 0.0) {
        return listOf(startSeconds)
    }

    val step = TIME_STEPS_SECONDS.firstOrNull { span / it <= maxTicks } ?: TIME_STEPS_SECONDS.last()
    val first = ceil(startSeconds / step - 1e-9) * step + 0.0
    return generateSequence(first) { it + step }.takeWhile { it <= endSeconds + 1e-9 }.toList()
}

/** The point of [points] (sorted by x) closest to [x], or null without points. */
internal fun nearestPoint(points: List<ChartPoint>, x: Double): ChartPoint? {
    if (points.isEmpty()) {
        return null
    }

    val index = points.binarySearch { it.x.compareTo(x) }
    if (index >= 0) {
        return points[index]
    }

    val after = -index - 1
    val before = after - 1
    return when {
        before < 0 -> points.first()
        after > points.lastIndex -> points.last()
        x - points[before].x <= points[after].x - x -> points[before]
        else -> points[after]
    }
}

/** The value a stepped line holds at [x]: that of the last point at or before it. */
internal fun steppedValueAt(points: List<ChartPoint>, x: Double): Double? =
    points.lastOrNull { it.x <= x + 1e-9 }?.y

/**
 * The target power over time as the points of a stepped line between [fromSeconds] and
 * [toSeconds], both counted from [originMillis] like the samples of the chart.
 *
 * Only the stretch since the target was last unset is drawn. The line begins where the chart
 * begins if the target was set before, and reaches to the chart's end with the current target.
 */
internal fun targetPowerLine(
    trace: List<TargetPowerPoint>,
    originMillis: Long,
    fromSeconds: Double,
    toSeconds: Double,
): List<ChartPoint> {
    val current = trace.takeLastWhile { it.watts != null }
    if (current.isEmpty() || toSeconds <= fromSeconds) {
        return emptyList()
    }

    val points = mutableListOf<ChartPoint>()
    current.forEach { change ->
        val x = ((change.timestampMillis - originMillis) / 1000.0).coerceIn(fromSeconds, toSeconds)
        val y = change.watts!!.toDouble()
        // Several changes before the chart begins collapse into the one still in effect there.
        if (points.isNotEmpty() && points.last().x == x) {
            points[points.lastIndex] = ChartPoint(x, y)
        } else {
            points += ChartPoint(x, y)
        }
    }
    if (points.last().x < toSeconds) {
        points += ChartPoint(toSeconds, points.last().y)
    }
    return points
}
