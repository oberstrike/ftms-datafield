package de.ma.ftms.core.storage

data class StoredLastSessionSummary(
    val startedAtMillis: Long = 0,
    val stoppedAtMillis: Long = 0,
    val finalStatus: String = "none",
    val packetCount: Int = 0,
    val sendSuccessCount: Int = 0,
    val sendFailureCount: Int = 0,
    val lastError: String = "",
    val finalDistanceM: Double = 0.0,
    val finalAscentM: Double = 0.0,
    val finalSpeedKmh: Double = 0.0,
)

fun WorkoutSessionRecord.toStoredLastSessionSummary(): StoredLastSessionSummary =
    StoredLastSessionSummary(
        startedAtMillis = startedAtMillis,
        stoppedAtMillis = stoppedAtMillis ?: updatedAtMillis,
        finalStatus = finalStatus,
        packetCount = packetCount,
        sendSuccessCount = sendSuccessCount,
        sendFailureCount = sendFailureCount,
        lastError = lastError,
        finalDistanceM = finalDistanceM,
        finalAscentM = finalAscentM,
        finalSpeedKmh = finalSpeedKmh,
    )
