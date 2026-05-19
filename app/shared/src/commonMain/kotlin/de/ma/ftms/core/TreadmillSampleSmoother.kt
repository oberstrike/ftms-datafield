package de.ma.ftms.core

data class SmoothedFtmsSample(
    val raw: FtmsSample,
    val distanceM: Double,
    val ascentM: Double,
    val distanceSource: DistanceSource,
)

typealias SmoothedTreadmillSample = SmoothedFtmsSample

enum class DistanceSource {
    NONE,
    FTMS,
    SPEED_TIME,
}

class TreadmillSampleSmoother {
    private var lastElapsedS: Int? = null
    private var lastTimestampMillis: Long? = null
    private var lastDistanceM: Double? = null
    private var lastRawDistanceM: Double? = null
    private var lastInclinePct: Double? = null
    private var pendingInclinePct: Double? = null
    private var pendingInclineStartDistanceM: Double? = null

    var distanceM: Double = 0.0
        private set

    var ascentM: Double = 0.0
        private set

    fun reset() {
        lastElapsedS = null
        lastTimestampMillis = null
        lastDistanceM = null
        lastRawDistanceM = null
        lastInclinePct = null
        pendingInclinePct = null
        pendingInclineStartDistanceM = null
        distanceM = 0.0
        ascentM = 0.0
    }

    fun update(sample: FtmsSample): SmoothedFtmsSample {
        val hasBaseline = lastDistanceM != null
        val previousDistance = distanceM
        var source = DistanceSource.NONE

        val rawDistance = sample.distanceM?.takeIf { it >= 0 }?.toDouble()
        val previousRawDistance = lastRawDistanceM

        if (lastDistanceM == null && rawDistance != null) {
            distanceM = rawDistance
            source = DistanceSource.FTMS
        }

        if (rawDistance != null && previousRawDistance != null && rawDistance > previousRawDistance) {
            distanceM = rawDistance
            source = DistanceSource.FTMS
        } else if (rawDistance != null && rawDistance < distanceM - RESET_DISTANCE_DROP_M) {
            distanceM = rawDistance
            ascentM = 0.0
            pendingInclinePct = null
            pendingInclineStartDistanceM = null
            source = DistanceSource.FTMS
        } else {
            val deltaSeconds = elapsedDeltaSeconds(sample)
            if (deltaSeconds != null && sample.speedKmh != null) {
                distanceM = integrateDistance(sample, deltaSeconds, rawDistance)
                source = DistanceSource.SPEED_TIME
            }
        }

        if (rawDistance != null && distanceM > rawDistance + MAX_INTERPOLATED_LEAD_M) {
            distanceM = rawDistance + MAX_INTERPOLATED_LEAD_M
            source = DistanceSource.FTMS
        }

        val distanceDelta = if (hasBaseline) distanceM - previousDistance else 0.0
        val incline = activeIncline(sample.inclinePct)
        if (distanceDelta > 0.0 && incline != null && incline > 0.0) {
            ascentM += distanceDelta * incline / 100.0
        }

        updateIncline(sample.inclinePct)

        lastElapsedS = sample.elapsedS ?: lastElapsedS
        lastTimestampMillis = sample.timestampMillis ?: lastTimestampMillis
        lastDistanceM = distanceM
        lastRawDistanceM = rawDistance ?: lastRawDistanceM

        return SmoothedFtmsSample(
            raw = sample,
            distanceM = distanceM,
            ascentM = ascentM,
            distanceSource = source,
        )
    }

    private fun integrateDistance(sample: FtmsSample, deltaSeconds: Double, rawDistance: Double?): Double {
        val speedKmh = sample.speedKmh ?: return distanceM
        val previousDistance = distanceM
        val speedMetersPerSecond = speedKmh * 1000.0 / 3600.0
        val integratedDistance = speedMetersPerSecond * deltaSeconds
        if (integratedDistance < 0.0 || integratedDistance >= 1000.0) {
            return previousDistance
        }

        val candidate = previousDistance + integratedDistance
        return if (rawDistance != null) {
            candidate.coerceAtMost(rawDistance + MAX_INTERPOLATED_LEAD_M)
        } else {
            candidate
        }
    }

    private fun elapsedDeltaSeconds(sample: FtmsSample): Double? {
        val elapsed = sample.elapsedS
        if (elapsed != null) {
            val previous = lastElapsedS
            if (previous != null && elapsed > previous) {
                return (elapsed - previous).coerceAtMost(10).toDouble()
            }
            return null
        }

        val timestamp = sample.timestampMillis
        val previousTimestamp = lastTimestampMillis
        if (timestamp != null && previousTimestamp != null && timestamp > previousTimestamp) {
            return ((timestamp - previousTimestamp).coerceAtMost(10_000L)).toDouble() / 1000.0
        }

        return null
    }

    private fun activeIncline(sampleInclinePct: Double?): Double? {
        val current = lastInclinePct ?: sampleInclinePct
        val pending = pendingInclinePct ?: return current
        val pendingStart = pendingInclineStartDistanceM ?: distanceM
        return if (distanceM - pendingStart >= INCLINE_CHANGE_BUFFER_M) {
            pending
        } else {
            current
        }
    }

    private fun updateIncline(sampleInclinePct: Double?) {
        val sampleIncline = sampleInclinePct ?: return
        val current = lastInclinePct
        if (current == null) {
            lastInclinePct = sampleIncline
            return
        }

        val pending = pendingInclinePct
        if (pending != null && closeEnough(sampleIncline, pending)) {
            val pendingStart = pendingInclineStartDistanceM ?: distanceM
            if (distanceM - pendingStart >= INCLINE_CHANGE_BUFFER_M) {
                lastInclinePct = pending
                pendingInclinePct = null
                pendingInclineStartDistanceM = null
            }
            return
        }
        if (pending != null && closeEnough(sampleIncline, current)) {
            pendingInclinePct = null
            pendingInclineStartDistanceM = null
            return
        }

        if (!closeEnough(sampleIncline, current)) {
            pendingInclinePct = sampleIncline
            pendingInclineStartDistanceM = distanceM
        }
    }

    private fun closeEnough(left: Double, right: Double): Boolean =
        kotlin.math.abs(left - right) < 0.0001

    private companion object {
        const val MAX_INTERPOLATED_LEAD_M = 3.0
        const val RESET_DISTANCE_DROP_M = 20.0
        const val INCLINE_CHANGE_BUFFER_M = 2.0
    }
}
