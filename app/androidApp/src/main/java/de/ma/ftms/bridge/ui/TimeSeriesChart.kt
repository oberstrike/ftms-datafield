package de.ma.ftms.bridge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.ma.ftms.bridge.i18n.Strings
import de.ma.ftms.bridge.i18n.formatNeutral

private const val MAX_CHART_DRAW_POINTS = 240

/** What a line of a chart stands for, which decides how it is drawn. */
internal enum class ChartLineRole {
    /** The measured values the chart is about. */
    MAIN,

    /**
     * The measured values of a quantity that the equipment and the watch both deliver, from the
     * source that is chosen for it. Drawn in the text colour: black on a light screen.
     */
    CHOSEN_SOURCE,

    /** The same quantity from the source that is not chosen, drawn in red and a little thinner. */
    OTHER_SOURCE,

    /** A value that was asked for rather than measured, drawn as a thin stepped line. */
    TARGET,
}

internal class ChartLine(
    val label: String,
    val points: List<ChartPoint>,
    val role: ChartLineRole = ChartLineRole.MAIN,
)

/** True for the lines a chart is about, as opposed to those drawn next to them for reference. */
private val ChartLineRole.isMeasuredMain: Boolean
    get() = this == ChartLineRole.MAIN || this == ChartLineRole.CHOSEN_SOURCE

/** True for the lines drawn thinner than the main one. */
private val ChartLineRole.isThin: Boolean
    get() = this == ChartLineRole.TARGET || this == ChartLineRole.OTHER_SOURCE

/**
 * The time a finger rests on in a chart, in the seconds of its time axis; null while nothing
 * touches it. Charts that are given the same cursor show their values at that time together.
 */
@Stable
internal class ChartCursor {
    var seconds: Double? by mutableStateOf(null)
}

/** What of the lines of a chart is worked out once rather than with every move of the finger. */
private class ChartExtent(
    val minX: Double,
    val maxX: Double,
    val dataMin: Double,
    val dataMax: Double,
    val mainMin: Double,
    val mainMax: Double,
    val mainAverage: Double,
    /** The average of the source that is not chosen; null where the chart has one source only. */
    val otherAverage: Double?,
)

/**
 * A line chart over time with a value axis on round numbers, a readout of the latest value and,
 * while a finger rests on it, of the values at that time.
 *
 * [lines] holds the main line first; every line with fewer than two points is left out. The
 * caller makes sure the main line has at least two, and remembers the list between recompositions.
 *
 * @param showStats whether average and maximum of the main line mean something (not for totals
 * such as distance, and not for a pace, whose mean over time is not the average pace).
 * @param cursor where the finger is. Charts of the same stretch of time share one, so that a
 * finger on one of them reads all of them at that time.
 * @param timeRange the stretch of time the chart spans, in seconds; that of its lines if null.
 * Charts sharing a cursor take the same range, so the same time is at the same place in each.
 * @param widestAxisLabel a label of the value axis to leave room for even if this chart has none
 * as wide. Given the same one, charts below each other begin their plots at the same place.
 * @param recorded whether the chart shows a finished recording rather than a session that is
 * running. The last value of a recording says little, so at rest the chart then reads the average
 * of its main line, and marks no point. A line that ends in its result, such as a total, is read
 * by its last value all the same; that is one whose average means nothing ([showStats] false).
 * @param average the average of the main line of a recording where that is not the mean of its
 * points, as with a pace.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimeSeriesChart(
    title: String,
    lines: List<ChartLine>,
    unit: String,
    strings: Strings,
    format: ChartValueFormat,
    modifier: Modifier = Modifier,
    reverseY: Boolean = false,
    showStats: Boolean = true,
    chartHeight: Dp = 180.dp,
    cursor: ChartCursor = remember { ChartCursor() },
    timeRange: ClosedFloatingPointRange<Double>? = null,
    widestAxisLabel: String? = null,
    recorded: Boolean = false,
    average: Double? = null,
) {
    val visible = remember(lines) { lines.filter { it.points.size >= 2 } }
    val main = visible.firstOrNull() ?: return
    val formatValue = format.format
    val extent = remember(visible, timeRange) {
        val allPoints = visible.flatMap { it.points }
        ChartExtent(
            minX = timeRange?.start ?: allPoints.minOf { it.x },
            maxX = timeRange?.endInclusive ?: allPoints.maxOf { it.x },
            dataMin = allPoints.minOf { it.y },
            dataMax = allPoints.maxOf { it.y },
            mainMin = main.points.minOf { it.y },
            mainMax = main.points.maxOf { it.y },
            mainAverage = main.points.sumOf { it.y } / main.points.size,
            otherAverage = visible.firstOrNull { it.role == ChartLineRole.OTHER_SOURCE }
                ?.points?.let { points -> points.sumOf { it.y } / points.size },
        )
    }
    val minX = extent.minX
    val maxX = extent.maxX
    val scale = remember(extent, format.resolution) {
        niceChartScale(extent.dataMin, extent.dataMax, resolution = format.resolution)
    }
    val drawn = remember(visible) {
        visible.map { line ->
            if (line.role == ChartLineRole.TARGET) line.points else line.points.downsampleForDrawing()
        }
    }

    val touchSeconds = cursor.seconds?.coerceIn(minX, maxX)
    val cursorState = rememberUpdatedState(cursor)
    val timeSpan = rememberUpdatedState(minX to maxX)

    val textColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLow
    val colors = visible.map { chartLineColor(it.role) }
    val tickStyle = MaterialTheme.typography.labelSmall.copy(color = mutedColor)
    val textMeasurer = rememberTextMeasurer()
    val tickLabels = scale.ticks.map(formatValue)
    val gutterPx = remember(tickLabels, tickStyle, widestAxisLabel) {
        (tickLabels + listOfNotNull(widestAxisLabel)).maxOf { textMeasurer.measure(it, tickStyle).size.width }.toFloat()
    }
    val gutterState = rememberUpdatedState(gutterPx)

    fun valueAt(line: ChartLine, seconds: Double): Double? =
        if (line.role == ChartLineRole.TARGET) steppedValueAt(line.points, seconds) else nearestPoint(line.points, seconds)?.y

    val withUnit: (Double) -> String = { value -> if (unit.isBlank()) formatValue(value) else "${formatValue(value)} $unit" }
    val restingAverage = if (recorded) average ?: extent.mainAverage.takeIf { showStats } else null
    // Where both sources are drawn, the average of the other one stands in brackets behind that of
    // the chosen one, in the colour of its line.
    val otherAverageText = extent.otherAverage?.takeIf { average == null }?.let(formatValue)
    val otherColor = chartLineColor(ChartLineRole.OTHER_SOURCE)
    val averages = averageWithOther(formatValue(restingAverage ?: extent.mainAverage), otherAverageText)
    val readout = when {
        touchSeconds != null -> AnnotatedString(withUnit(valueAt(main, touchSeconds) ?: main.points.last().y))
        restingAverage != null -> strings.dashboard
            .chartAverage(if (unit.isBlank()) averages else "$averages $unit")
            .withBracketsIn(otherColor, otherAverageText)
        else -> AnnotatedString(withUnit(main.points.last().y))
    }
    val description = remember(title, main, extent, strings) {
        strings.dashboard.chartSummary(
            title = title,
            latest = formatValue(main.points.last().y),
            min = formatValue(extent.mainMin),
            max = formatValue(extent.mainMax),
            start = formatElapsedTime(minX),
            end = formatElapsedTime(maxX),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Text(
                readout,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                modifier = Modifier.weight(1f),
            )
            val side = when {
                touchSeconds != null -> formatElapsedTime(touchSeconds)
                restingAverage != null -> strings.dashboard.chartMax(formatValue(extent.mainMax)).takeIf { showStats }
                showStats -> strings.dashboard.chartStats(average = averages, max = formatValue(extent.mainMax))
                else -> null
            }?.withBracketsIn(otherColor, otherAverageText)
            if (side != null) {
                Text(side, style = MaterialTheme.typography.bodySmall, color = mutedColor)
            }
        }

        if (visible.size > 1) {
            // Names every line, so the lines are never told apart by colour alone. While a finger
            // rests on the chart it also gives each line's value at that time.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                visible.forEachIndexed { index, line ->
                    val value = when {
                        touchSeconds != null -> valueAt(line, touchSeconds)
                        line.role == ChartLineRole.TARGET -> line.points.last().y
                        else -> null
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LineKey(color = colors[index], thin = line.role.isThin)
                        Text(
                            if (value == null) line.label else "${line.label} ${withUnit(value)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedColor,
                        )
                    }
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .semantics { contentDescription = description }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        // Not consumed: the list keeps scrolling while the readout follows the finger.
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val secondsAt = { x: Float ->
                            val left = gutterState.value + GUTTER_GAP.toPx()
                            val width = (size.width - left - RIGHT_PADDING.toPx()).coerceAtLeast(1f)
                            val (from, to) = timeSpan.value
                            from + ((x - left) / width).coerceIn(0f, 1f) * (to - from)
                        }
                        val touched = cursorState.value
                        try {
                            touched.seconds = secondsAt(down.position.x)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) {
                                    break
                                }
                                touched.seconds = secondsAt(change.position.x)
                            }
                        } finally {
                            // Also when the chart leaves the screen under the finger: a cursor shared
                            // with other charts must not stay where it was.
                            touched.seconds = null
                        }
                    }
                },
        ) {
            val tickHeight = textMeasurer.measure("0", tickStyle).size.height.toFloat()
            val left = gutterPx + GUTTER_GAP.toPx()
            val top = (tickHeight / 2f).coerceAtLeast(6.dp.toPx())
            val bottom = size.height - tickHeight - 6.dp.toPx()
            val plotWidth = (size.width - left - RIGHT_PADDING.toPx()).coerceAtLeast(1f)
            val plotHeight = (bottom - top).coerceAtLeast(1f)
            val xRange = (maxX - minX).takeIf { it > 0.0 } ?: 1.0

            fun xOf(seconds: Double) = left + ((seconds - minX) / xRange).toFloat() * plotWidth
            fun yOf(value: Double) = top + chartYFraction(value, scale.min, scale.max, reverseY) * plotHeight

            // Value axis: a hairline and a label per round value.
            scale.ticks.forEachIndexed { index, tick ->
                val y = yOf(tick)
                drawLine(gridColor, Offset(left, y), Offset(left + plotWidth, y), strokeWidth = 1.dp.toPx())
                val label = textMeasurer.measure(tickLabels[index], tickStyle)
                drawText(label, topLeft = Offset(gutterPx - label.size.width, y - label.size.height / 2f))
            }

            // Time axis: labels on round times, kept inside the plot.
            val timeTicks = niceTimeTicks(minX, maxX).ifEmpty { listOf(minX, maxX) }
            var lastLabelEnd = Float.NEGATIVE_INFINITY
            timeTicks.forEach { tick ->
                val label = textMeasurer.measure(formatElapsedTime(tick), tickStyle)
                val x = (xOf(tick) - label.size.width / 2f).coerceIn(left, left + plotWidth - label.size.width)
                if (x >= lastLabelEnd + 8.dp.toPx()) {
                    drawText(label, topLeft = Offset(x, bottom + 6.dp.toPx()))
                    lastLabelEnd = x + label.size.width
                }
            }

            drawn.forEachIndexed { index, points ->
                val line = visible[index]
                val color = colors[index]
                val path = Path()
                points.forEachIndexed { pointIndex, point ->
                    val x = xOf(point.x)
                    val y = yOf(point.y)
                    when {
                        pointIndex == 0 -> path.moveTo(x, y)
                        line.role == ChartLineRole.TARGET -> {
                            // Holds its value until the next change, then steps.
                            path.lineTo(x, yOf(points[pointIndex - 1].y))
                            path.lineTo(x, y)
                        }
                        else -> path.lineTo(x, y)
                    }
                }

                if (line.role.isMeasuredMain && !reverseY) {
                    // A light wash below the main line; it carries no value of its own.
                    val area = Path().apply {
                        addPath(path)
                        lineTo(xOf(points.last().x), bottom)
                        lineTo(xOf(points.first().x), bottom)
                        close()
                    }
                    drawPath(area, color.copy(alpha = 0.10f))
                }
                drawPath(
                    path,
                    color,
                    style = Stroke(
                        width = (if (line.role.isThin) 1.5.dp else 2.dp).toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }

            // The marker sits on the latest value, or on the values under the finger.
            fun marker(center: Offset, color: Color) {
                drawCircle(surfaceColor, radius = 6.dp.toPx(), center = center)
                drawCircle(color, radius = 4.dp.toPx(), center = center)
            }
            if (touchSeconds == null) {
                if (!recorded) {
                    val last = main.points.last()
                    marker(Offset(xOf(last.x), yOf(last.y)), colors.first())
                }
            } else {
                val x = xOf(touchSeconds)
                drawLine(mutedColor, Offset(x, top), Offset(x, bottom), strokeWidth = 1.dp.toPx())
                visible.forEachIndexed { index, line ->
                    if (line.role == ChartLineRole.TARGET) {
                        steppedValueAt(line.points, touchSeconds)?.let { marker(Offset(x, yOf(it)), colors[index]) }
                    } else {
                        nearestPoint(line.points, touchSeconds)?.let { marker(Offset(xOf(it.x), yOf(it.y)), colors[index]) }
                    }
                }
            }
        }
    }
}

/**
 * The average of the chosen source with that of the other source in brackets behind it, as in
 * `148 (150)`; the average alone where there is no other source.
 */
internal fun averageWithOther(average: String, other: String?): String =
    if (other == null) average else "$average ($other)"

/** This text with the bracketed [other] average in [color]; unchanged where there is none. */
internal fun String.withBracketsIn(color: Color, other: String?): AnnotatedString {
    val bracketed = other?.let { "($it)" }
    val start = bracketed?.let { indexOf(it) } ?: -1
    if (bracketed == null || start < 0) {
        return AnnotatedString(this)
    }

    return buildAnnotatedString {
        append(this@withBracketsIn)
        addStyle(SpanStyle(color = color), start, start + bracketed.length)
    }
}

private val GUTTER_GAP = 8.dp
private val RIGHT_PADDING = 8.dp

/** A short stroke in the colour of a line, to name it in the legend. */
@Composable
private fun LineKey(color: Color, thin: Boolean) {
    Canvas(modifier = Modifier.size(width = 16.dp, height = 8.dp)) {
        drawLine(
            color,
            Offset(0f, size.height / 2f),
            Offset(size.width, size.height / 2f),
            strokeWidth = (if (thin) 1.5.dp else 2.5.dp).toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Colours of the lines. Of the two sources of a value the chosen one is drawn in the text colour,
 * black on a light screen and white on a dark one, and the other one in red. Red and black are hard
 * to tell apart for some eyes, so the other source is also thinner and the legend names both. The
 * target is drawn in the muted text colour, as a line of reference rather than data.
 */
@Composable
internal fun chartLineColor(role: ChartLineRole): Color {
    val dark = isSystemInDarkTheme()
    return when (role) {
        ChartLineRole.MAIN -> if (dark) Color(0xFF338CED) else Color(0xFF257DE0)
        ChartLineRole.CHOSEN_SOURCE -> MaterialTheme.colorScheme.onSurface
        ChartLineRole.OTHER_SOURCE -> if (dark) Color(0xFFFF5A52) else Color(0xFFD32F2F)
        ChartLineRole.TARGET -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** Seconds as `mm:ss`, or `h:mm:ss` from one hour. */
internal fun formatElapsedTime(seconds: Double): String {
    val totalSeconds = seconds.toLong().coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val remainingSeconds = totalSeconds % 60L
    return if (hours > 0) {
        formatNeutral("%d:%02d:%02d", hours, minutes, remainingSeconds)
    } else {
        formatNeutral("%02d:%02d", minutes, remainingSeconds)
    }
}

/**
 * Thins a long line for drawing without flattening it: of every stretch that falls onto about one
 * pixel column, the lowest and the highest value stay.
 */
internal fun List<ChartPoint>.downsampleForDrawing(maxPoints: Int = MAX_CHART_DRAW_POINTS): List<ChartPoint> {
    if (size <= maxPoints || maxPoints < 4) {
        return this
    }

    val bucketCount = ((maxPoints - 2) / 2).coerceAtLeast(1)
    val interiorStart = 1
    val interiorEndExclusive = lastIndex
    val interiorCount = interiorEndExclusive - interiorStart
    val bucketSize = interiorCount.toDouble() / bucketCount
    val result = mutableListOf(first())
    repeat(bucketCount) { bucket ->
        val startIndex = (interiorStart + (bucket * bucketSize).toInt()).coerceIn(interiorStart, interiorEndExclusive - 1)
        val endIndex = (interiorStart + ((bucket + 1) * bucketSize).toInt())
            .coerceIn(startIndex + 1, interiorEndExclusive)
        val bucketPoints = subList(startIndex, endIndex)
        val minPoint = bucketPoints.minBy { it.y }
        val maxPoint = bucketPoints.maxBy { it.y }
        if (minPoint.x <= maxPoint.x) {
            result.addIfNew(minPoint)
            result.addIfNew(maxPoint)
        } else {
            result.addIfNew(maxPoint)
            result.addIfNew(minPoint)
        }
    }
    result.addIfNew(last())
    return result
}

private fun MutableList<ChartPoint>.addIfNew(point: ChartPoint) {
    if (lastOrNull() != point) {
        add(point)
    }
}
