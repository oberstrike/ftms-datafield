package de.ma.ftms.bridge.ui

import de.ma.ftms.bridge.i18n.Strings
import de.ma.ftms.bridge.i18n.fmt
import de.ma.ftms.bridge.i18n.formatNeutral
import de.ma.ftms.bridge.runtime.DashboardChartTimespan
import de.ma.ftms.bridge.runtime.DashboardMetricKey
import de.ma.ftms.bridge.runtime.EquipmentType
import de.ma.ftms.bridge.runtime.SourcedMetric
import de.ma.ftms.core.SmoothedTreadmillSample
import kotlin.math.floor

internal fun DashboardMetricKey.label(strings: Strings, type: EquipmentType): String =
    when (this) {
        DashboardMetricKey.SPEED -> strings.common.speed
        DashboardMetricKey.PACE -> strings.common.pace
        DashboardMetricKey.AVG_PACE -> strings.common.averagePace
        DashboardMetricKey.INCLINE -> strings.common.incline
        DashboardMetricKey.AVG_INCLINE -> strings.common.averageIncline
        DashboardMetricKey.DISTANCE -> strings.common.distance
        DashboardMetricKey.ASCENT -> strings.common.ascent
        DashboardMetricKey.ASCENT_RATE -> strings.common.ascentRate
        DashboardMetricKey.POWER -> strings.common.power
        DashboardMetricKey.HEART_RATE -> strings.common.heartRate
        DashboardMetricKey.CADENCE_STEP -> cadenceLabel(type, strings)
        DashboardMetricKey.ELAPSED -> strings.common.elapsed
        DashboardMetricKey.RESISTANCE -> strings.common.resistance
        DashboardMetricKey.MACHINE -> strings.common.machine
    }

internal fun DashboardMetricKey.unit(type: EquipmentType): String =
    when (this) {
        DashboardMetricKey.SPEED -> "km/h"
        DashboardMetricKey.PACE -> "min/km"
        DashboardMetricKey.AVG_PACE -> "min/km"
        DashboardMetricKey.INCLINE -> "%"
        DashboardMetricKey.AVG_INCLINE -> "%"
        DashboardMetricKey.DISTANCE -> "m"
        DashboardMetricKey.ASCENT -> "m"
        DashboardMetricKey.ASCENT_RATE -> "m/min"
        DashboardMetricKey.POWER -> "W"
        DashboardMetricKey.HEART_RATE -> "bpm"
        DashboardMetricKey.CADENCE_STEP -> cadenceUnit(type)
        DashboardMetricKey.ELAPSED,
        DashboardMetricKey.RESISTANCE,
        DashboardMetricKey.MACHINE,
        -> ""
    }

internal fun DashboardMetricKey.format(
    sample: SmoothedTreadmillSample?,
    strings: Strings,
    samples: List<SmoothedTreadmillSample> = emptyList(),
    activeDurationMillis: Long = 0,
): String {
    val raw = sample?.raw
    val missing = strings.common.missingValue
    return when (this) {
        DashboardMetricKey.SPEED -> raw?.speedKmh?.let { strings.fmt("%.2f km/h", it) } ?: missing
        DashboardMetricKey.PACE -> raw?.speedKmh?.let(::formatPace) ?: missing
        DashboardMetricKey.AVG_PACE -> sample?.distanceM
            ?.let { formatAveragePace(distanceM = it, durationMillis = activeDurationMillis) }
            ?: missing
        DashboardMetricKey.INCLINE -> raw?.inclinePct?.let { strings.fmt("%.1f %%", it) } ?: missing
        DashboardMetricKey.AVG_INCLINE -> averageInclinePct(samples)?.let { strings.fmt("%.1f %%", it) } ?: missing
        DashboardMetricKey.DISTANCE -> sample?.distanceM?.let { strings.fmt("%.2f km", it / 1000.0) } ?: missing
        DashboardMetricKey.ASCENT -> sample?.ascentM?.let { strings.fmt("%.1f m", it) } ?: missing
        DashboardMetricKey.ASCENT_RATE -> sample?.ascentRateMetersPerMinute()?.let { strings.fmt("%.1f m/min", it) } ?: missing
        DashboardMetricKey.POWER -> raw?.powerW?.let { "$it W" } ?: missing
        DashboardMetricKey.HEART_RATE -> raw?.heartRateBpm?.let { "$it bpm" } ?: missing
        DashboardMetricKey.CADENCE_STEP -> raw?.cadenceOrStepRate()?.let { strings.fmt("%.1f", it) } ?: missing
        DashboardMetricKey.ELAPSED -> raw?.elapsedS?.let { formatNeutral("%02d:%02d", it / 60, it % 60) } ?: missing
        DashboardMetricKey.RESISTANCE -> raw?.resistance?.let { strings.fmt("%.1f", it) } ?: missing
        DashboardMetricKey.MACHINE -> raw?.kind?.let { machineLabel(it, strings) } ?: missing
    }
}

internal fun DashboardMetricKey.chartValue(sample: SmoothedTreadmillSample): Double? {
    val raw = sample.raw
    return when (this) {
        DashboardMetricKey.SPEED -> raw.speedKmh
        DashboardMetricKey.PACE -> raw.speedKmh?.takeIf { it > 0.0 }?.let { 60.0 / it }
        DashboardMetricKey.AVG_PACE -> null
        DashboardMetricKey.INCLINE -> raw.inclinePct
        DashboardMetricKey.AVG_INCLINE -> null
        DashboardMetricKey.DISTANCE -> sample.distanceM
        DashboardMetricKey.ASCENT -> sample.ascentM
        DashboardMetricKey.ASCENT_RATE -> sample.ascentRateMetersPerMinute()
        DashboardMetricKey.POWER -> raw.powerW?.toDouble()
        DashboardMetricKey.HEART_RATE -> raw.heartRateBpm?.toDouble()
        DashboardMetricKey.CADENCE_STEP -> raw.cadenceOrStepRate()
        DashboardMetricKey.RESISTANCE -> raw.resistance
        DashboardMetricKey.ELAPSED,
        DashboardMetricKey.MACHINE,
        -> null
    }
}

/**
 * Whether [sample] holds this value. The dashboard leaves out what the equipment and the watch
 * never delivered in the running session, instead of showing an empty tile or chart for it.
 */
internal fun DashboardMetricKey.isDeliveredBy(sample: SmoothedTreadmillSample): Boolean {
    val raw = sample.raw
    return when (this) {
        DashboardMetricKey.SPEED,
        DashboardMetricKey.PACE,
        DashboardMetricKey.AVG_PACE,
        -> raw.speedKmh != null
        DashboardMetricKey.INCLINE,
        DashboardMetricKey.AVG_INCLINE,
        DashboardMetricKey.ASCENT,
        -> raw.inclinePct != null
        DashboardMetricKey.ASCENT_RATE -> raw.inclinePct != null && raw.speedKmh != null
        // Counted by the app from what arrives, so there from the first sample.
        DashboardMetricKey.DISTANCE,
        DashboardMetricKey.MACHINE,
        -> true
        DashboardMetricKey.POWER -> raw.powerW != null
        DashboardMetricKey.HEART_RATE -> raw.heartRateBpm != null
        DashboardMetricKey.CADENCE_STEP -> raw.cadenceOrStepRate() != null
        DashboardMetricKey.ELAPSED -> raw.elapsedS != null
        DashboardMetricKey.RESISTANCE -> raw.resistance != null
    }
}

/** The values that at least one of [samples] holds. */
internal fun deliveredDashboardMetrics(samples: List<SmoothedTreadmillSample>): Set<DashboardMetricKey> =
    DashboardMetricKey.entries.filterTo(mutableSetOf()) { key -> samples.any(key::isDeliveredBy) }

/** The values with a source to choose that the equipment or the watch delivered in [samples]. */
internal fun deliveredSourcedMetrics(samples: List<SmoothedTreadmillSample>): Set<SourcedMetric> =
    SourcedMetric.entries.filterTo(mutableSetOf()) { metric ->
        samples.any { sourceValue(metric, it.raw.equipment) != null || sourceValue(metric, it.raw.watch) != null }
    }

/** How the chart of this value writes its numbers. */
internal fun DashboardMetricKey.chartFormat(strings: Strings): ChartValueFormat =
    when (this) {
        DashboardMetricKey.PACE,
        DashboardMetricKey.AVG_PACE,
        -> PACE_CHART_FORMAT
        DashboardMetricKey.DISTANCE,
        DashboardMetricKey.POWER,
        DashboardMetricKey.HEART_RATE,
        DashboardMetricKey.CADENCE_STEP,
        -> decimalChartFormat(strings, decimals = 0)
        else -> decimalChartFormat(strings, decimals = 1)
    }

/**
 * Whether average and maximum over the chart say something: not for totals and running averages,
 * and not for the pace, whose mean over time is not the average pace.
 */
internal fun DashboardMetricKey.chartShowsStats(): Boolean =
    when (this) {
        DashboardMetricKey.SPEED,
        DashboardMetricKey.INCLINE,
        DashboardMetricKey.ASCENT_RATE,
        DashboardMetricKey.POWER,
        DashboardMetricKey.HEART_RATE,
        DashboardMetricKey.CADENCE_STEP,
        DashboardMetricKey.RESISTANCE,
        -> true
        else -> false
    }

internal fun DashboardMetricKey.reverseChartYAxis(): Boolean =
    this == DashboardMetricKey.PACE || this == DashboardMetricKey.AVG_PACE

internal fun chartYFraction(value: Double, minY: Double, maxY: Double, reverseY: Boolean): Float {
    val yRange = (maxY - minY).takeIf { it > 0.0 } ?: 1.0
    val normalized = ((value - minY) / yRange).toFloat().coerceIn(0f, 1f)
    return if (reverseY) normalized else 1f - normalized
}

internal fun DashboardChartTimespan.label(strings: Strings): String =
    durationMillis?.let { strings.settings.chartTimespanMinutes((it / 60_000L).toInt()) }
        ?: strings.settings.chartTimespanFullSession

private fun formatPace(speedKmh: Double): String {
    if (speedKmh <= 0.0) {
        return "--"
    }
    val secondsPerKm = 3600.0 / speedKmh
    val minutes = floor(secondsPerKm / 60.0).toInt()
    val seconds = (secondsPerKm - minutes * 60).toInt().coerceIn(0, 59)
    return formatNeutral("%d:%02d min/km", minutes, seconds)
}

internal fun formatAveragePace(distanceM: Double, durationMillis: Long): String? {
    if (distanceM <= 0.0 || durationMillis <= 0L) {
        return null
    }

    val secondsPerKm = durationMillis.toDouble() / 1000.0 / (distanceM / 1000.0)
    val minutes = floor(secondsPerKm / 60.0).toInt()
    val seconds = (secondsPerKm - minutes * 60).toInt().coerceIn(0, 59)
    return formatNeutral("%d:%02d min/km", minutes, seconds)
}

internal fun averageInclinePct(samples: List<SmoothedTreadmillSample>): Double? {
    if (samples.isEmpty()) {
        return null
    }

    var weightedDistance = 0.0
    var weightedIncline = 0.0
    var previousDistance = samples.first().distanceM
    val simpleValues = mutableListOf<Double>()
    samples.forEach { sample ->
        val incline = sample.raw.inclinePct
        if (incline != null) {
            simpleValues += incline
            val deltaDistance = (sample.distanceM - previousDistance).coerceAtLeast(0.0)
            if (deltaDistance > 0.0) {
                weightedDistance += deltaDistance
                weightedIncline += deltaDistance * incline
            }
        }
        previousDistance = sample.distanceM
    }

    return if (weightedDistance > 0.0) {
        weightedIncline / weightedDistance
    } else {
        simpleValues.takeIf { it.isNotEmpty() }?.average()
    }
}

private fun SmoothedTreadmillSample.ascentRateMetersPerMinute(): Double? {
    val speedKmh = raw.speedKmh?.takeIf { it > 0.0 } ?: return null
    val inclinePct = raw.inclinePct ?: return null
    return speedKmh * 1000.0 / 60.0 * inclinePct.coerceAtLeast(0.0) / 100.0
}
