package de.ma.ftms.bridge

import de.ma.ftms.bridge.runtime.AutoStartPolicy
import de.ma.ftms.bridge.runtime.BridgeSettings
import de.ma.ftms.bridge.runtime.BridgeUiState
import de.ma.ftms.bridge.runtime.EquipmentType
import de.ma.ftms.bridge.runtime.GarminConnectProblem
import de.ma.ftms.bridge.runtime.autoStartWatchAddress
import de.ma.ftms.bridge.runtime.isBridgeStartPossible
import de.ma.ftms.bridge.runtime.isEquipmentMoving
import de.ma.ftms.bridge.runtime.standbyWatchAddress
import de.ma.ftms.core.FtmsSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoStartPolicyTest {
    private val policy = AutoStartPolicy(
        rearmAfterUnseenMillis = 60_000,
        maxHoldMillis = 1_800_000,
        movingSamplesToStart = 2,
    )

    @Test
    fun doesNotStartWhileTheEquipmentStandsStill() {
        repeat(10) { second ->
            assertFalse(policy.onMovementReported(moving = false, nowMillis = second * 1_000L))
        }
    }

    @Test
    fun startsOnTheSecondMovingSampleInARow() {
        assertFalse(policy.onMovementReported(moving = true, nowMillis = 1_000))
        assertTrue(policy.onMovementReported(moving = true, nowMillis = 2_000))
    }

    @Test
    fun aSingleMovingSampleBetweenStandstillDoesNotStart() {
        assertFalse(policy.onMovementReported(moving = true, nowMillis = 1_000))
        assertFalse(policy.onMovementReported(moving = false, nowMillis = 2_000))
        assertFalse(policy.onMovementReported(moving = true, nowMillis = 3_000))
    }

    @Test
    fun samplesThatSayNothingAboutMovementAreSkipped() {
        assertFalse(policy.onMovementReported(moving = true, nowMillis = 1_000))
        assertFalse(policy.onMovementReported(moving = null, nowMillis = 1_500))
        assertTrue(policy.onMovementReported(moving = true, nowMillis = 2_000))
    }

    @Test
    fun keepsAskingForAStartUntilTheCallerHolds() {
        policy.onMovementReported(moving = true, nowMillis = 1_000)

        // The caller cannot start yet (for example the watch is not ready) and does not hold.
        assertTrue(policy.onMovementReported(moving = true, nowMillis = 2_000))
        assertTrue(policy.onMovementReported(moving = true, nowMillis = 3_000))
    }

    @Test
    fun aLostConnectionRequiresMovementToBeConfirmedAgain() {
        policy.onMovementReported(moving = true, nowMillis = 1_000)

        policy.onConnectionLost()

        assertFalse(policy.onMovementReported(moving = true, nowMillis = 2_000))
        assertTrue(policy.onMovementReported(moving = true, nowMillis = 3_000))
    }

    @Test
    fun doesNotStartAgainWhileTheEquipmentKeepsMovingAfterASession() {
        policy.holdUntilStandstill(100_000)

        var now = 100_000L
        repeat(100) {
            now += 1_000
            assertFalse(policy.onMovementReported(moving = true, nowMillis = now))
        }
        assertTrue(policy.isHolding(now))
    }

    @Test
    fun startsAgainOnceTheEquipmentStoodStill() {
        policy.holdUntilStandstill(100_000)
        policy.onMovementReported(moving = true, nowMillis = 101_000)

        assertFalse(policy.onMovementReported(moving = false, nowMillis = 102_000))
        assertFalse(policy.isHolding(102_000))

        assertFalse(policy.onMovementReported(moving = true, nowMillis = 103_000))
        assertTrue(policy.onMovementReported(moving = true, nowMillis = 104_000))
    }

    @Test
    fun startsAgainAfterTheEquipmentWasWatchedAndUnseenForAMinute() {
        // Switched off while still moving: no sample ever reports the standstill.
        policy.holdUntilStandstill(100_000)
        policy.onWatchStarted(100_000)
        policy.onEquipmentSeen(110_000)

        assertTrue(policy.isHolding(169_999))
        assertFalse(policy.isHolding(170_000))
    }

    @Test
    fun timeWithoutWatchingDoesNotCountAsUnseen() {
        policy.holdUntilStandstill(100_000)
        // The app was hidden for five minutes, so nobody watched whether the equipment went away.
        policy.onWatchStarted(400_000)
        policy.onEquipmentSeen(400_500)

        assertTrue(policy.isHolding(400_500))
        assertFalse(policy.onMovementReported(moving = true, nowMillis = 401_000))
        assertFalse(policy.onMovementReported(moving = true, nowMillis = 402_000))
    }

    @Test
    fun theHoldEndsAfterItsMaximumEvenIfNobodyWatchedTheEquipmentStop() {
        policy.holdUntilStandstill(100_000)
        policy.onWatchStarted(1_900_000)

        assertFalse(policy.onMovementReported(moving = true, nowMillis = 1_900_500))
        assertTrue(policy.onMovementReported(moving = true, nowMillis = 1_901_500))
    }

    @Test
    fun resetForgetsHoldsAndCountedMovement() {
        policy.onMovementReported(moving = true, nowMillis = 1_000)
        policy.holdUntilStandstill(2_000)

        policy.reset()

        assertFalse(policy.isHolding(2_000))
        assertFalse(policy.onMovementReported(moving = true, nowMillis = 3_000))
        assertTrue(policy.onMovementReported(moving = true, nowMillis = 4_000))
    }

    @Test
    fun readsMovementFromSpeedCadenceOrStepRate() {
        assertEquals(true, isEquipmentMoving(FtmsSample(speedKmh = 0.5)))
        assertEquals(true, isEquipmentMoving(FtmsSample(speedKmh = 8.5)))
        assertEquals(false, isEquipmentMoving(FtmsSample(speedKmh = 0.0)))
        assertEquals(false, isEquipmentMoving(FtmsSample(speedKmh = 0.4)))
        assertEquals(true, isEquipmentMoving(FtmsSample(speedKmh = 0.0, cadenceRpm = 60.0)))
        assertEquals(true, isEquipmentMoving(FtmsSample(stepRateSpm = 150.0)))
        assertEquals(false, isEquipmentMoving(FtmsSample(cadenceRpm = 0.0)))
    }

    @Test
    fun aSampleWithoutMovementFieldsOrWithAParseErrorSaysNothing() {
        assertNull(isEquipmentMoving(FtmsSample(inclinePct = 2.0, heartRateBpm = 120)))
        assertNull(isEquipmentMoving(FtmsSample(speedKmh = 9.0, truncated = true)))
        assertNull(isEquipmentMoving(FtmsSample(speedKmh = 9.0, parseError = "short packet")))
    }

    @Test
    fun watchesOnlyWhileEverythingNeededIsInPlace() {
        val ready = readyState()

        assertEquals("AA:BB", autoStartWatchAddress(ready, appVisible = true))
        assertNull(autoStartWatchAddress(ready, appVisible = false))
        assertNull(autoStartWatchAddress(ready.copy(settings = BridgeSettings(autoStart = false)), appVisible = true))
        assertNull(autoStartWatchAddress(ready.copy(running = true), appVisible = true))
        assertNull(autoStartWatchAddress(ready.copy(scanning = true), appVisible = true))
        assertNull(autoStartWatchAddress(ready.copy(permissionsGranted = false), appVisible = true))
        assertNull(autoStartWatchAddress(ready.copy(bleAvailable = false), appVisible = true))
        assertNull(autoStartWatchAddress(ready.copy(selectedTreadmillAddress = null), appVisible = true))
    }

    @Test
    fun aTreadmillToBeSteeredIsHeldWithoutAutoStartButDoesNotStartASession() {
        val steered = readyState().copy(settings = BridgeSettings(autoStart = false, treadmillControl = true))

        assertEquals("AA:BB", standbyWatchAddress(steered, appVisible = true))
        assertNull(autoStartWatchAddress(steered, appVisible = true))
        assertNull(standbyWatchAddress(steered, appVisible = false))
        assertNull(standbyWatchAddress(steered.copy(running = true), appVisible = true))
        // The switch is about treadmills; a bike is held only for auto-start.
        assertNull(standbyWatchAddress(steered.copy(selectedEquipmentType = EquipmentType.INDOOR_BIKE), appVisible = true))
        assertNull(standbyWatchAddress(readyState().copy(settings = BridgeSettings()), appVisible = true))
        assertEquals("AA:BB", standbyWatchAddress(readyState(), appVisible = true))
    }

    @Test
    fun watchesEvenWhileTheGarminWatchIsNotReadyYet() {
        val garminPending = readyState().copy(garminReady = false)

        assertEquals("AA:BB", autoStartWatchAddress(garminPending, appVisible = true))
        assertFalse(isBridgeStartPossible(garminPending))
    }

    @Test
    fun aStartNeedsTheSameThingsAsAManualStart() {
        val ready = readyState()

        assertTrue(isBridgeStartPossible(ready))
        assertFalse(isBridgeStartPossible(ready.copy(running = true)))
        assertFalse(isBridgeStartPossible(ready.copy(permissionsGranted = false)))
        assertFalse(isBridgeStartPossible(ready.copy(bleAvailable = false)))
        assertFalse(isBridgeStartPossible(ready.copy(selectedTreadmillAddress = null)))
        assertFalse(isBridgeStartPossible(ready.copy(selectedGarminId = null)))
        assertFalse(isBridgeStartPossible(ready.copy(garminReady = false)))
        // With the running sensor the data field is out of reach by design.
        assertTrue(
            isBridgeStartPossible(
                ready.copy(garminReady = false, settings = ready.settings.copy(virtualFootPod = true)),
            ),
        )
        assertFalse(isBridgeStartPossible(ready.copy(garminConnectProblem = GarminConnectProblem.NOT_INSTALLED)))
    }

    private fun readyState() = BridgeUiState(
        permissionsGranted = true,
        bleAvailable = true,
        selectedTreadmillAddress = "AA:BB",
        selectedGarminId = 7,
        garminReady = true,
        settings = BridgeSettings(autoStart = true),
    )
}
