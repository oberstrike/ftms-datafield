package de.ma.ftms.bridge

import de.ma.ftms.bridge.runtime.SessionStats
import de.ma.ftms.core.DistanceSource
import de.ma.ftms.core.SmoothedTreadmillSample
import de.ma.ftms.core.TreadmillSample
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionStatsTest {
    @Test
    fun buildsLastSessionSummaryFromRecordedSamplesAndSends() {
        var now = 100L
        val stats = SessionStats { now }

        stats.start()
        now = 250L
        stats.recordPacket(
            SmoothedTreadmillSample(
                raw = TreadmillSample(speedKmh = 6.0),
                distanceM = 123.4,
                ascentM = 5.6,
                distanceSource = DistanceSource.SPEED_TIME,
            ),
        )
        stats.recordSendStatus("SUCCESS")
        stats.recordSendStatus("FAILURE_DURING_TRANSFER")
        now = 500L

        val summary = stats.finish("stopped")

        assertEquals(100L, summary.startedAtMillis)
        assertEquals(500L, summary.stoppedAtMillis)
        assertEquals("stopped", summary.finalStatus)
        assertEquals(1, summary.packetCount)
        assertEquals(1, summary.sendSuccessCount)
        assertEquals(1, summary.sendFailureCount)
        assertEquals("Garmin send FAILURE_DURING_TRANSFER", summary.lastError)
        assertEquals(123.4, summary.finalDistanceM)
        assertEquals(5.6, summary.finalAscentM)
        assertEquals(6.0, summary.finalSpeedKmh)
    }
}
