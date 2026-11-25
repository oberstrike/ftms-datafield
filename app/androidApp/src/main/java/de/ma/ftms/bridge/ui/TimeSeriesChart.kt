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

    /** A second measurement of the same quantity, from another source. */
    COMPARISON,

    /** A value that was asked for rather than measured, drawn as a thin stepped line. */
    TARGET,
}

internal class ChartLine(
    val label: String,
    val points: List<ChartPoint>,
    val role: ChartLineRole = ChartLineRole.MAIN,
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
) {
    val visible = remember(lines) { lines.filter { it.points.size >= 2 } }
    val main = visible.firstOrNull() ?: return
    val formatValue = format.format
    val allPoints = visible.flatMap { it.points }
    val minX = allPoints.minOf { it.x }
    val maxX = allPoints.maxOf { it.x }
    val dataMin = allPoints.minOf { it.y }
    val dataMax = allPoints.maxOf { it.y }
    val scale = remember(dataMin, dataMax, format.resolution) {
        niceChartScale(dataMin, dataMax, resolution = format.resolution)
    }
    val drawn = remember(visible) {
        visible.map { line ->
            if (line.role == ChartLineRole.TARGET) line.points else line.points.downsampleForDrawing()
        }
    }

    /** Where the finger is, as a fraction of the plot's width; null while nothing touches it. */
    var touch by remember { mutableStateOf<Float?>(null) }
    val touchSeconds = touch?.let { minX + it * (maxX - minX) }

    val textColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLow
    val colors = visible.map { chartLineColor(it.role) }
    val tickStyle = MaterialTheme.typography.labelSmall.copy(color = mutedColor)
    val textMeasurer = rememberTextMeasurer()
    val tickLabels = scale.ticks.map(formatValue)
    val gutterPx = remember(tickLabels, tickStyle) {
        tickLabels.maxOf { textMeasurer.measure(it, tickStyle).size.width }.toFloat()
    }
    val gutterState = rememberUpdatedState(gutterPx)

    fun valueAt(line: ChartLine, seconds: Double): Double? =
        if (line.role == ChartLineRole.TARGET) steppedValueAt(line.points, seconds) else nearestPoint(line.points, seconds)?.y

    val shownMainValue = touchSeconds?.let { valueAt(main, it) } ?: main.points.last().y
    val withUnit: (Double) -> String = { value -> if (unit.isBlank()) formatValue(value) else "${formatValue(value)} $unit" }
    val description = strings.dashboard.chartSummary(
        title = title,
        latest = formatValue(main.points.last().y),
        min = formatValue(main.points.minOf { it.y }),
        max = formatValue(main.points.maxOf { it.y }),
        start = formatElapsedTime(minX),
        end = formatElapsedTime(maxX),
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Text(
                withUnit(shownMainValue),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                modifier = Modifier.weight(1f),
            )
            val side = when {
                touchSeconds != null -> formatElapsedTime(touchSeconds)
                showStats -> strings.dashboard.chartStats(
                    average = formatValue(main.points.sumOf { it.y } / main.points.size),
                    max = formatValue(main.points.maxOf { it.y }),
                )
                else -> null
            }
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
                        LineKey(color = colors[index], thin = line.role == ChartLineRole.TARGET)
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
                        val fractionOf = { x: Float ->
                            val left = gutterState.value + GUTTER_GAP.toPx()
                            val width = (size.width - left - RIGHT_PADDING.toPx()).coerceAtLeast(1f)
                            ((x - left) / width).coerceIn(0f, 1f)
                        }
                        touch = fractionOf(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                break
                            }
                            touch = fractionOf(change.position.x)
                        }
                        touch = null
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

                if (line.role == ChartLineRole.MAIN && !reverseY) {
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
                        width = (if (line.role == ChartLineRole.TARGET) 1.5.dp else 2.dp).toPx(),
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
                val last = main.points.last()
                marker(Offset(xOf(last.x), yOf(last.y)), colors.first())
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
 * Colours of the lines. Main and comparison are a pair checked for colour-vision deficiencies in
 * both themes; the target is drawn in the text colour, as a line of reference rather than data.
 */
@Composable
private fun chartLineColor(role: ChartLineRole): Color {
    val dark = isSystemInDarkTheme()
    return when (role) {
        ChartLineRole.MAIN -> if (dark) Color(0xFF338CED) else Color(0xFF257DE0)
        ChartLineRole.COMPARISON -> if (dark) Color(0xFFE76624) else Color(0xFFF47230)
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
