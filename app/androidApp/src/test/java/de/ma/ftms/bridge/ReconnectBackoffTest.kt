package de.ma.ftms.bridge

import de.ma.ftms.bridge.runtime.ReconnectBackoff
import kotlin.test.Test
import kotlin.test.assertEquals

class ReconnectBackoffTest {
    @Test
    fun usesConservativeDelaySequenceAndCapsAtLastDelay() {
        val backoff = ReconnectBackoff()

        assertEquals(2_000L, backoff.nextDelayMillis())
        assertEquals(5_000L, backoff.nextDelayMillis())
        assertEquals(10_000L, backoff.nextDelayMillis())
        assertEquals(30_000L, backoff.nextDelayMillis())
        assertEquals(30_000L, backoff.nextDelayMillis())
    }

    @Test
    fun resetStartsAtFirstDelayAgain() {
        val backoff = ReconnectBackoff()

        backoff.nextDelayMillis()
        backoff.nextDelayMillis()
        backoff.reset()

        assertEquals(2_000L, backoff.nextDelayMillis())
    }
}
