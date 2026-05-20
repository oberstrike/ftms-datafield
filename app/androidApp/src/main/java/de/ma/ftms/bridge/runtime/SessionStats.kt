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
    private var maxDistanceM: Double = 0.0
    private var maxAscentM: Double = 0.0

    fun start() {
        startedAtMillis = nowMillis()
        stoppedAtMillis = 0
        packetCount = 0
        sendSuccessCount = 0
        sendFailureCount = 0
        lastPacketAtMillis = 0
        lastError = ""
        latest = null
        maxDistanceM = 0.0
        maxAscentM = 0.0
    }

    fun recordPacket(sample: SmoothedTreadmillSample) {
        latest = sample
        maxDistanceM = maxOf(maxDistanceM, sample.distanceM)
        maxAscentM = maxOf(maxAscentM, sample.ascentM)
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
            finalDistanceM = maxDistanceM,
            finalAscentM = maxAscentM,
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
            finalDistanceM = maxDistanceM,
            finalAscentM = maxAscentM,
            finalSpeedKmh = latest?.raw?.speedKmh,
        )
}
