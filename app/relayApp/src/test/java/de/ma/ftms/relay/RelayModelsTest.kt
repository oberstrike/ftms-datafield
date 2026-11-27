package de.ma.ftms.relay

import de.ma.ftms.core.RscMeasurement
import de.ma.ftms.footpod.FootPodUuids
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelayModelsTest {
    @Test
    fun aRunningSensorAloneMayBeTheBridge() {
        assertTrue(mayBeBridgeSensor(listOf(FootPodUuids.RSC_SERVICE)))
    }

    @Test
    fun aChestStrapThatAlsoCountsStepsIsNotTheBridge() {
        assertFalse(mayBeBridgeSensor(listOf(FootPodUuids.HEART_RATE_SERVICE, FootPodUuids.RSC_SERVICE)))
        assertFalse(mayBeBridgeSensor(listOf(FootPodUuids.HEART_RATE_SERVICE)))
        assertFalse(mayBeBridgeSensor(emptyList()))
    }

    @Test
    fun readsAMeasurementOfTheBridgeWithTheStepsOfBothFeet() {
        // The bridge sends the falls of one foot: 81 a minute are 162 steps.
        val summary = summarize(RscMeasurement.encode(speedKmh = 9.2, stepRateSpm = 81.0, totalDistanceM = 1234.5))!!

        assertEquals(9.2, summary.speedKmh, 0.02)
        assertEquals(162, summary.stepsPerMinute)
        assertEquals(1234, summary.distanceM)
    }

    @Test
    fun whatIsNoMeasurementHasNoSummary() {
        assertNull(summarize(byteArrayOf(0x02, 0x00)))
    }

    @Test
    fun aRejectedSensorIsLeftAloneUntilTheRejectionExpires() {
        val rejected = RejectedSources(forMillis = 1_000L)

        rejected.reject("AA:BB", nowMillis = 5_000L)

        assertTrue(rejected.isRejected("AA:BB", nowMillis = 5_999L))
        assertFalse(rejected.isRejected("AA:BB", nowMillis = 6_000L))
        assertFalse(rejected.isRejected("CC:DD", nowMillis = 5_500L))
    }

    @Test
    fun rejectingAnotherSensorKeepsTheOnesStillInForce() {
        val rejected = RejectedSources(forMillis = 1_000L)
        rejected.reject("AA:BB", nowMillis = 0L)
        rejected.reject("CC:DD", nowMillis = 600L)

        rejected.reject("EE:FF", nowMillis = 1_200L)

        assertFalse(rejected.isRejected("AA:BB", nowMillis = 1_200L))
        assertTrue(rejected.isRejected("CC:DD", nowMillis = 1_200L))
        assertTrue(rejected.isRejected("EE:FF", nowMillis = 1_200L))
    }
}
