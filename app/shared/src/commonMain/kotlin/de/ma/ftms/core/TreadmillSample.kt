package de.ma.ftms.core

object FtmsMachineKind {
    const val UNKNOWN = 0
    const val INDOOR_BIKE = 1
    const val TREADMILL = 2
    const val CROSS_TRAINER = 3
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
) {
    fun cadenceOrStepRate(): Double? = cadenceRpm ?: stepRateSpm
}

typealias TreadmillSample = FtmsSample
