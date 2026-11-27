package de.ma.ftms.core.storage

import de.ma.ftms.core.DistanceSource
import de.ma.ftms.core.SensorReadings
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
    /** Equipment type selected for the session, as a [de.ma.ftms.core.FtmsMachineKind] value. */
    val equipmentKind: Int = de.ma.ftms.core.FtmsMachineKind.TREADMILL,
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
    /** What the equipment and the watch each reported; null for samples stored without them. */
    val equipment: SensorReadings? = null,
    val watch: SensorReadings? = null,
)

/**
 * What the samples of a session come to, for a list of sessions that does not load the samples.
 * Every value is null where no sample holds one.
 */
data class SessionAverages(
    /** The offset of the last sample: how long the session recorded. */
    val recordedMillis: Long? = null,
    val powerW: Double? = null,
    val heartRateBpm: Double? = null,
    /** Revolutions or steps per minute, whichever the equipment counts. */
    val cadence: Double? = null,
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
