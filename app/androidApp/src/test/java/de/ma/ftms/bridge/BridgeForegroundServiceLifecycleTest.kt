package de.ma.ftms.bridge

import de.ma.ftms.bridge.runtime.shouldStopBridgeForegroundService
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BridgeForegroundServiceLifecycleTest {
    @Test
    fun stopsForegroundServiceOnlyWhenNoRunIsActive() {
        assertFalse(shouldStopBridgeForegroundService(running = true, activeSessionStartedAtMillis = 10_000L))
        assertFalse(shouldStopBridgeForegroundService(running = true, activeSessionStartedAtMillis = 0L))
        assertFalse(shouldStopBridgeForegroundService(running = false, activeSessionStartedAtMillis = 10_000L))
        assertTrue(shouldStopBridgeForegroundService(running = false, activeSessionStartedAtMillis = 0L))
    }
}
