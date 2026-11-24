package de.ma.ftms.bridge

import de.ma.ftms.bridge.runtime.BridgeSettings
import de.ma.ftms.bridge.runtime.BridgeUiState
import de.ma.ftms.bridge.runtime.isWatchReadyForStart
import de.ma.ftms.bridge.runtime.EquipmentType
import de.ma.ftms.bridge.runtime.FootPodFeed
import de.ma.ftms.bridge.runtime.isFootPodWanted
import de.ma.ftms.core.DistanceSource
import de.ma.ftms.core.RscMeasurementParser
import de.ma.ftms.core.SensorReadings
import de.ma.ftms.core.SmoothedTreadmillSample
import de.ma.ftms.core.TreadmillSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FootPodFeedTest {
    private fun sample(speedKmh: Double?, distanceM: Double, equipmentStepRate: Double? = null, stepRate: Double? = null) =
        SmoothedTreadmillSample(
            raw = TreadmillSample(
                speedKmh = speedKmh,
                stepRateSpm = stepRate,
                equipment = equipmentStepRate?.let { SensorReadings(cadenceRpm = it) },
            ),
            distanceM = distanceM,
            ascentM = 0.0,
            distanceSource = DistanceSource.SPEED_TIME,
        )

    private fun FootPodFeed.read(sample: SmoothedTreadmillSample, paused: Boolean, nowMillis: Long) =
        next(sample, paused, nowMillis)?.let { RscMeasurementParser.parse(it) }

    @Test
    fun sendsSpeedDistanceAndTheEquipmentsStepRate() {
        val sent = FootPodFeed().read(sample(9.0, 1200.0, equipmentStepRate = 172.0), paused = false, nowMillis = 1_000L)

        assertNotNull(sent)
        assertEquals(9.0, sent!!.speedKmh!!, 0.01)
        assertEquals(1200, sent.distanceM)
        // Half of the steps of both feet: a running sensor counts one foot.
        assertEquals(86.0, sent.stepRateSpm!!, 0.0)
    }

    @Test
    fun doesNotSendAStepRateTheWatchMeasuredBackToIt() {
        // The resolved step rate came from the watch; the treadmill itself reported none.
        val sent = FootPodFeed().read(sample(9.0, 10.0, equipmentStepRate = null, stepRate = 168.0), paused = false, nowMillis = 1_000L)

        assertEquals(0.0, sent!!.stepRateSpm!!, 0.0)
    }

    @Test
    fun sendsAtMostOnceWithinTheInterval() {
        val feed = FootPodFeed(minIntervalMillis = 900L)

        assertNotNull(feed.next(sample(9.0, 10.0), paused = false, nowMillis = 1_000L))
        assertNull(feed.next(sample(9.0, 11.0), paused = false, nowMillis = 1_500L))
        assertNotNull(feed.next(sample(9.0, 12.0), paused = false, nowMillis = 1_900L))
    }

    @Test
    fun sendsNothingForASampleWithoutSpeed() {
        assertNull(FootPodFeed().next(sample(null, 10.0), paused = false, nowMillis = 1_000L))
    }

    @Test
    fun reportsStandstillAndKeepsTheDistanceWhilePaused() {
        val feed = FootPodFeed(minIntervalMillis = 0L)
        feed.next(sample(9.0, 500.0), paused = false, nowMillis = 1_000L)

        val sent = feed.read(sample(9.0, 530.0, equipmentStepRate = 170.0), paused = true, nowMillis = 2_000L)

        assertEquals(0.0, sent!!.speedKmh!!, 0.0)
        assertEquals(500, sent.distanceM)
        assertEquals(0.0, sent.stepRateSpm!!, 0.0)
    }

    @Test
    fun neverLetsTheDistanceRunBackwards() {
        val feed = FootPodFeed(minIntervalMillis = 0L)
        feed.next(sample(9.0, 500.0), paused = false, nowMillis = 1_000L)

        val sent = feed.read(sample(9.0, 480.0), paused = false, nowMillis = 2_000L)

        assertEquals(500, sent!!.distanceM)
    }

    @Test
    fun startsFromZeroAgainAfterAReset() {
        val feed = FootPodFeed(minIntervalMillis = 900L)
        feed.next(sample(9.0, 500.0), paused = false, nowMillis = 1_000L)
        feed.reset()

        val sent = feed.read(sample(9.0, 3.0), paused = false, nowMillis = 1_100L)

        assertEquals(3, sent!!.distanceM)
    }

    @Test
    fun isWantedOnlyForARunningBridgeWithATreadmillAndTheSettingOn() {
        val on = BridgeSettings(virtualFootPod = true)

        assertTrue(isFootPodWanted(running = true, settings = on, equipmentType = EquipmentType.TREADMILL))
        assertFalse(isFootPodWanted(running = false, settings = on, equipmentType = EquipmentType.TREADMILL))
        assertFalse(isFootPodWanted(running = true, settings = on, equipmentType = EquipmentType.INDOOR_BIKE))
        assertFalse(isFootPodWanted(running = true, settings = BridgeSettings(), equipmentType = EquipmentType.TREADMILL))
    }

    @Test
    fun letsTheBridgeStartWithoutAReachableDataFieldOnlyInRunningSensorMode() {
        val sensorOn = BridgeSettings(virtualFootPod = true)

        assertTrue(isWatchReadyForStart(BridgeUiState(garminReady = true)))
        assertFalse(isWatchReadyForStart(BridgeUiState(garminReady = false)))
        assertTrue(isWatchReadyForStart(BridgeUiState(garminReady = false, settings = sensorOn)))
        assertFalse(
            isWatchReadyForStart(
                BridgeUiState(garminReady = false, settings = sensorOn, selectedEquipmentType = EquipmentType.INDOOR_BIKE),
            ),
        )
    }
}
