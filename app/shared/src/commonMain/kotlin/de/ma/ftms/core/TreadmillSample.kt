package de.ma.ftms.core

object FtmsMachineKind {
    const val UNKNOWN = 0
    const val INDOOR_BIKE = 1
    const val TREADMILL = 2
    const val CROSS_TRAINER = 3
}

/**
 * Heart rate, power and cadence as one source reported them. Cadence is in revolutions per minute
 * on a bike and whatever the source calls cadence elsewhere (steps on a treadmill).
 */
data class SensorReadings(
    val powerW: Int? = null,
    val heartRateBpm: Int? = null,
    val cadenceRpm: Double? = null,
) {
    val isEmpty: Boolean
        get() = powerW == null && heartRateBpm == null && cadenceRpm == null

    companion object {
        /** Readings for the given values, or null when there is none. */
        fun ofNotEmpty(powerW: Int?, heartRateBpm: Int?, cadenceRpm: Double?): SensorReadings? =
            SensorReadings(powerW, heartRateBpm, cadenceRpm).takeUnless { it.isEmpty }
    }
}

data class FtmsSample(
    val kind: Int = FtmsMachineKind.TREADMILL,
    val timestampMillis: Long? = null,
    val rawFlags: Int = 0,
    val rawHex: String = "",
    val speedKmh: Double? = null,
    val distanceM: Int? = null,
    val cadenceRpm: Double? = null,
    val resistance: Double? = null,
    val inclinePct: Double? = null,
    val rampDeg: Double? = null,
    val positiveElevationM: Double? = null,
    val negativeElevationM: Double? = null,
    val powerW: Int? = null,
    val heartRateBpm: Int? = null,
    val elapsedS: Int? = null,
    val remainingS: Int? = null,
    val stepRateSpm: Double? = null,
    val strideCount: Double? = null,
    val movementDirection: Int? = null,
    val truncated: Boolean = false,
    val parseError: String? = null,
    /**
     * What the equipment and the watch each reported, kept next to [powerW], [heartRateBpm] and
     * the cadence fields, which hold the value of the source that was chosen. Null until the
     * sources have been resolved.
     */
    val equipment: SensorReadings? = null,
    val watch: SensorReadings? = null,
) {
    fun cadenceOrStepRate(): Double? = cadenceRpm ?: stepRateSpm
}

typealias TreadmillSample = FtmsSample
