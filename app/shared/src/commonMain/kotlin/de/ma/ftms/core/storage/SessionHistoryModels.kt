package de.ma.ftms.core.storage

import de.ma.ftms.core.DistanceSource
import de.ma.ftms.core.SmoothedFtmsSample

data class WorkoutSessionRecord(
    val sessionId: String,
    val startedAtMillis: Long,
    val stoppedAtMillis: Long?,
    val finalStatus: String,
    val treadmillName: String?,
    val treadmillAddress: String?,
    val garminName: String?,
    val garminId: Long?,
    val packetCount: Int,
    val sendSuccessCount: Int,
    val sendFailureCount: Int,
    val lastError: String,
    val finalDistanceM: Double,
    val finalAscentM: Double,
    val finalSpeedKmh: Double,
    val updatedAtMillis: Long,
) {
    val durationMillis: Long
        get() = ((stoppedAtMillis ?: updatedAtMillis) - startedAtMillis).coerceAtLeast(0L)
}

data class SessionSampleRecord(
    val sampleId: Long,
    val sessionId: String,
    val timestampMillis: Long,
    val offsetMillis: Long,
    val kind: Int,
    val rawFlags: Int,
    val rawHex: String,
    val speedKmh: Double?,
    val rawDistanceM: Int?,
    val smoothedDistanceM: Double,
    val ascentM: Double,
    val inclinePct: Double?,
    val powerW: Int?,
    val heartRateBpm: Int?,
    val cadenceRpm: Double?,
    val stepRateSpm: Double?,
    val resistance: Double?,
    val elapsedS: Int?,
    val remainingS: Int?,
    val parseError: String?,
    val distanceSource: DistanceSource,
)

data class SessionStatsSnapshot(
    val packetCount: Int,
    val sendSuccessCount: Int,
    val sendFailureCount: Int,
    val lastError: String,
    val latest: SmoothedFtmsSample?,
    val finalDistanceM: Double? = null,
    val finalAscentM: Double? = null,
    val finalSpeedKmh: Double? = null,
)
