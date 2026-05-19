package de.ma.ftms.bridge.runtime

import de.ma.ftms.core.storage.StoredLastSessionSummary
import de.ma.ftms.core.storage.WorkoutSessionRecord
import de.ma.ftms.core.storage.toStoredLastSessionSummary

internal fun WorkoutSessionRecord.toLastSessionSummary(): LastSessionSummary =
    toStoredLastSessionSummary().toLastSessionSummary()

private fun StoredLastSessionSummary.toLastSessionSummary(): LastSessionSummary =
    LastSessionSummary(
        startedAtMillis = startedAtMillis,
        stoppedAtMillis = stoppedAtMillis,
        finalStatus = finalStatus,
        packetCount = packetCount,
        sendSuccessCount = sendSuccessCount,
        sendFailureCount = sendFailureCount,
        lastError = lastError,
        finalDistanceM = finalDistanceM,
        finalAscentM = finalAscentM,
        finalSpeedKmh = finalSpeedKmh,
    )
