package de.ma.ftms.bridge.ui

import de.ma.ftms.bridge.i18n.Strings
import de.ma.ftms.bridge.runtime.DashboardChartTimespan
import de.ma.ftms.bridge.runtime.DashboardMetricKey
import de.ma.ftms.core.SmoothedTreadmillSample
import kotlin.math.floor

internal fun DashboardMetricKey.label(strings: Strings): String =
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
        DashboardMetricKey.CADENCE_STEP -> strings.common.cadenceStep
        DashboardMetricKey.ELAPSED -> strings.common.elapsed
        DashboardMetricKey.RESISTANCE -> strings.common.resistance
        DashboardMetricKey.MACHINE -> strings.common.machine
    }

internal fun DashboardMetricKey.unit(): String =
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
        DashboardMetricKey.CADENCE_STEP -> "rpm/spm"
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
        DashboardMetricKey.SPEED -> raw?.speedKmh?.let { "%.2f km/h".format(it) } ?: missing
        DashboardMetricKey.PACE -> raw?.speedKmh?.let(::formatPace) ?: missing
        DashboardMetricKey.AVG_PACE -> sample?.distanceM
            ?.let { formatAveragePace(distanceM = it, durationMillis = activeDurationMillis) }
            ?: missing
        DashboardMetricKey.INCLINE -> raw?.inclinePct?.let { "%.1f %%".format(it) } ?: missing
        DashboardMetricKey.AVG_INCLINE -> averageInclinePct(samples)?.let { "%.1f %%".format(it) } ?: missing
        DashboardMetricKey.DISTANCE -> sample?.distanceM?.let { "%.2f km".format(it / 1000.0) } ?: missing
        DashboardMetricKey.ASCENT -> sample?.ascentM?.let { "%.1f m".format(it) } ?: missing
        DashboardMetricKey.ASCENT_RATE -> sample?.ascentRateMetersPerMinute()?.let { "%.1f m/min".format(it) } ?: missing
        DashboardMetricKey.POWER -> raw?.powerW?.let { "$it W" } ?: missing
        DashboardMetricKey.HEART_RATE -> raw?.heartRateBpm?.let { "$it bpm" } ?: missing
        DashboardMetricKey.CADENCE_STEP -> raw?.cadenceOrStepRate()?.let { "%.1f".format(it) } ?: missing
        DashboardMetricKey.ELAPSED -> raw?.elapsedS?.let { "%02d:%02d".format(it / 60, it % 60) } ?: missing
        DashboardMetricKey.RESISTANCE -> raw?.resistance?.let { "%.1f".format(it) } ?: missing
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

internal fun List<SmoothedTreadmillSample>.filterForTimespan(timespan: DashboardChartTimespan): List<SmoothedTreadmillSample> {
    val durationMillis = timespan.durationMillis ?: return this
    if (any { it.raw.timestampMillis == null }) {
        return this
    }

    val latestTimestamp = lastOrNull()?.raw?.timestampMillis ?: return this
    val threshold = latestTimestamp - durationMillis
    return filter { sample ->
        sample.raw.timestampMillis?.let { it >= threshold } ?: true
    }
}

private fun formatPace(speedKmh: Double): String {
    if (speedKmh <= 0.0) {
        return "--"
    }
    val secondsPerKm = 3600.0 / speedKmh
    val minutes = floor(secondsPerKm / 60.0).toInt()
    val seconds = (secondsPerKm - minutes * 60).toInt().coerceIn(0, 59)
    return "%d:%02d min/km".format(minutes, seconds)
}

internal fun formatAveragePace(distanceM: Double, durationMillis: Long): String? {
    if (distanceM <= 0.0 || durationMillis <= 0L) {
        return null
    }

    val secondsPerKm = durationMillis.toDouble() / 1000.0 / (distanceM / 1000.0)
    val minutes = floor(secondsPerKm / 60.0).toInt()
    val seconds = (secondsPerKm - minutes * 60).toInt().coerceIn(0, 59)
    return "%d:%02d min/km".format(minutes, seconds)
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
