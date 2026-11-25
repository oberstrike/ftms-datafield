package de.ma.ftms.bridge

import de.ma.ftms.bridge.runtime.BridgeSettings
import de.ma.ftms.bridge.runtime.BridgeUiState
import de.ma.ftms.bridge.runtime.isWatchWaitWanted
import de.ma.ftms.bridge.runtime.shouldStopBridgeForegroundService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchWaitTest {
    private fun readyState(settings: BridgeSettings = BridgeSettings(waitForWatch = true)) = BridgeUiState(
        permissionsGranted = true,
        selectedTreadmillAddress = "AA:BB:CC:DD:EE:FF",
        selectedGarminId = 42L,
        settings = settings,
    )

    @Test
    fun waitsOnceEverythingForAStartByTheWatchIsChosen() {
        assertTrue(isWatchWaitWanted(readyState(), dismissed = false))
    }

    @Test
    fun isOffUnlessSwitchedOn() {
        assertFalse(BridgeSettings().waitForWatch)
        assertFalse(isWatchWaitWanted(readyState(BridgeSettings()), dismissed = false))
    }

    @Test
    fun doesNotWaitWhenTheSessionDoesNotFollowTheWatch() {
        val settings = BridgeSettings(waitForWatch = true, followWatchTimer = false)

        assertFalse(isWatchWaitWanted(readyState(settings), dismissed = false))
    }

    @Test
    fun doesNotWaitOnceTheUserEndedIt() {
        assertFalse(isWatchWaitWanted(readyState(), dismissed = true))
    }

    @Test
    fun doesNotWaitWithoutPermissionsOrDevices() {
        assertFalse(isWatchWaitWanted(readyState().copy(permissionsGranted = false), dismissed = false))
        assertFalse(isWatchWaitWanted(readyState().copy(selectedTreadmillAddress = null), dismissed = false))
        assertFalse(isWatchWaitWanted(readyState().copy(selectedGarminId = null), dismissed = false))
    }

    @Test
    fun keepsWaitingWhileASessionRuns() {
        // The service stays for the session anyway, and for the next activity after it.
        assertTrue(isWatchWaitWanted(readyState().copy(running = true), dismissed = false))
    }

    @Test
    fun theServiceStaysWhileTheBridgeWaits() {
        assertFalse(shouldStopBridgeForegroundService(running = false, activeSessionStartedAtMillis = 0L, waiting = true))
        assertTrue(shouldStopBridgeForegroundService(running = false, activeSessionStartedAtMillis = 0L, waiting = false))
        assertFalse(shouldStopBridgeForegroundService(running = true, activeSessionStartedAtMillis = 10_000L, waiting = false))
    }
}
