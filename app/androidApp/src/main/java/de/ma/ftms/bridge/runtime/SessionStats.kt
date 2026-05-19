package de.ma.ftms.bridge.runtime

import de.ma.ftms.core.SmoothedTreadmillSample
import de.ma.ftms.core.storage.SessionStatsSnapshot

class SessionStats(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    var startedAtMillis: Long = 0
        private set
    var stoppedAtMillis: Long = 0
        private set
    var packetCount: Int = 0
        private set
    var sendSuccessCount: Int = 0
        private set
    var sendFailureCount: Int = 0
        private set
    var lastPacketAtMillis: Long = 0
        private set
    var lastError: String = ""
        private set
    var latest: SmoothedTreadmillSample? = null
        private set

    fun start() {
        startedAtMillis = nowMillis()
        stoppedAtMillis = 0
        packetCount = 0
        sendSuccessCount = 0
        sendFailureCount = 0
        lastPacketAtMillis = 0
        lastError = ""
        latest = null
    }

    fun recordPacket(sample: SmoothedTreadmillSample) {
        latest = sample
        packetCount += 1
        lastPacketAtMillis = nowMillis()
    }

    fun recordSendStatus(status: String) {
        if (status.contains("SUCCESS", ignoreCase = true)) {
            sendSuccessCount += 1
        } else {
            sendFailureCount += 1
            lastError = "Garmin send $status"
        }
    }

    fun recordError(message: String) {
        lastError = message
    }

    fun finish(finalStatus: String): LastSessionSummary {
        stoppedAtMillis = nowMillis()
        val finalSample = latest
        return LastSessionSummary(
            startedAtMillis = startedAtMillis,
            stoppedAtMillis = stoppedAtMillis,
            finalStatus = finalStatus,
            packetCount = packetCount,
            sendSuccessCount = sendSuccessCount,
            sendFailureCount = sendFailureCount,
            lastError = lastError,
            finalDistanceM = finalSample?.distanceM ?: 0.0,
            finalAscentM = finalSample?.ascentM ?: 0.0,
            finalSpeedKmh = finalSample?.raw?.speedKmh ?: 0.0,
        )
    }

    fun snapshot(): SessionStatsSnapshot =
        SessionStatsSnapshot(
            packetCount = packetCount,
            sendSuccessCount = sendSuccessCount,
            sendFailureCount = sendFailureCount,
            lastError = lastError,
            latest = latest,
        )
}
