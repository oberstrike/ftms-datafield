package de.ma.ftms.bridge

import de.ma.ftms.bridge.runtime.MAX_SENDABLE_SAMPLE_AGE_MS
import de.ma.ftms.bridge.runtime.isPacketWatchdogExpired
import de.ma.ftms.bridge.runtime.isSampleTooOldToSend
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BridgeWatchdogTest {
    private val timeout = 15_000L

    @Test
    fun staysQuietWhilePacketsKeepArriving() {
        assertFalse(isPacketWatchdogExpired(nowMillis = 100_000, connectedAtMillis = 50_000, lastPacketAtMillis = 99_000, timeoutMillis = timeout))
        assertFalse(isPacketWatchdogExpired(nowMillis = 100_000, connectedAtMillis = 50_000, lastPacketAtMillis = 85_000, timeoutMillis = timeout))
    }

    @Test
    fun firesWhenTheMachineFallsSilent() {
        assertTrue(isPacketWatchdogExpired(nowMillis = 100_001, connectedAtMillis = 50_000, lastPacketAtMillis = 85_000, timeoutMillis = timeout))
    }

    @Test
    fun firesWhenAConnectionNeverDeliversItsFirstPacket() {
        assertFalse(isPacketWatchdogExpired(nowMillis = 60_000, connectedAtMillis = 50_000, lastPacketAtMillis = 0, timeoutMillis = timeout))
        assertTrue(isPacketWatchdogExpired(nowMillis = 65_001, connectedAtMillis = 50_000, lastPacketAtMillis = 0, timeoutMillis = timeout))
    }

    @Test
    fun givesAFreshConnectionTheFullTimeoutDespiteAnOldPacketTimestamp() {
        // Last packet is from before the reconnect at 90 s and must not count against the new link.
        assertFalse(isPacketWatchdogExpired(nowMillis = 95_000, connectedAtMillis = 90_000, lastPacketAtMillis = 40_000, timeoutMillis = timeout))
        assertTrue(isPacketWatchdogExpired(nowMillis = 105_001, connectedAtMillis = 90_000, lastPacketAtMillis = 40_000, timeoutMillis = timeout))
    }

    @Test
    fun neverFiresWithoutAConnection() {
        assertFalse(isPacketWatchdogExpired(nowMillis = 1_000_000, connectedAtMillis = 0, lastPacketAtMillis = 0, timeoutMillis = timeout))
    }

    @Test
    fun forwardsOnlyRecentSamplesToTheWatch() {
        assertFalse(isSampleTooOldToSend(sampleAgeMillis = 0))
        assertFalse(isSampleTooOldToSend(sampleAgeMillis = MAX_SENDABLE_SAMPLE_AGE_MS))
        assertTrue(isSampleTooOldToSend(sampleAgeMillis = MAX_SENDABLE_SAMPLE_AGE_MS + 1))
    }
}
