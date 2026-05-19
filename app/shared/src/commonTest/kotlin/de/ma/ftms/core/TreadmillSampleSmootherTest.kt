package de.ma.ftms.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TreadmillSampleSmootherTest {
    @Test
    fun smoothsDistanceBetweenCoarseFtmsUpdates() {
        val smoother = TreadmillSampleSmoother()

        val first = smoother.update(sample(distanceM = 160, elapsedS = 473))
        assertEquals(160.0, first.distanceM)
        assertEquals(DistanceSource.FTMS, first.distanceSource)

        val second = smoother.update(sample(distanceM = 160, elapsedS = 476))
        assertClose(163.0, second.distanceM)
        assertEquals(DistanceSource.SPEED_TIME, second.distanceSource)

        val third = smoother.update(sample(distanceM = 170, elapsedS = 477))
        assertClose(170.0, third.distanceM)
        assertEquals(DistanceSource.FTMS, third.distanceSource)
    }

    @Test
    fun duplicateCoarseFtmsPacketDoesNotSnapSmoothedDistanceBackward() {
        val smoother = TreadmillSampleSmoother()

        smoother.update(sample(distanceM = 10, elapsedS = 20))
        val interpolated = smoother.update(sample(distanceM = 10, elapsedS = 21))
        val duplicate = smoother.update(sample(distanceM = 10, elapsedS = 21))

        assertClose(11.666, interpolated.distanceM)
        assertClose(interpolated.distanceM, duplicate.distanceM)
        assertEquals(DistanceSource.NONE, duplicate.distanceSource)
    }

    @Test
    fun duplicateCoarseFtmsPacketThenLaterElapsedKeepsDistanceMonotonic() {
        val smoother = TreadmillSampleSmoother()

        smoother.update(sample(distanceM = 20, elapsedS = 26))
        val interpolated = smoother.update(sample(distanceM = 20, elapsedS = 27))
        smoother.update(sample(distanceM = 20, elapsedS = 27))
        val later = smoother.update(sample(distanceM = 20, elapsedS = 28))

        assertClose(21.666, interpolated.distanceM)
        assertTrue(later.distanceM >= interpolated.distanceM)
    }

    @Test
    fun rawDistanceIncreaseAnchorsToFtmsValue() {
        val smoother = TreadmillSampleSmoother()

        smoother.update(sample(distanceM = 10, elapsedS = 20))
        smoother.update(sample(distanceM = 10, elapsedS = 21))
        val anchored = smoother.update(sample(distanceM = 20, elapsedS = 22))

        assertEquals(20.0, anchored.distanceM)
        assertEquals(DistanceSource.FTMS, anchored.distanceSource)
    }

    @Test
    fun largeRawDistanceDropResetsDistanceAndAscent() {
        val smoother = TreadmillSampleSmoother()

        smoother.update(sample(distanceM = 100, elapsedS = 1, inclinePct = 3.0))
        smoother.update(sample(distanceM = 110, elapsedS = 2, inclinePct = 3.0))
        val reset = smoother.update(sample(distanceM = 80, elapsedS = 3, inclinePct = 3.0))

        assertEquals(80.0, reset.distanceM)
        assertEquals(0.0, reset.ascentM)
        assertEquals(DistanceSource.FTMS, reset.distanceSource)
    }

    @Test
    fun interpolationIsCappedNearCoarseFtmsDistance() {
        val smoother = TreadmillSampleSmoother()

        smoother.update(sample(distanceM = 0, elapsedS = 0))
        val capped = smoother.update(sample(distanceM = 0, elapsedS = 10))

        assertEquals(3.0, capped.distanceM)
    }

    @Test
    fun calculatesAscentFromPreviousInclineForInterval() {
        val smoother = TreadmillSampleSmoother()

        smoother.update(sample(distanceM = 160, elapsedS = 473, inclinePct = 1.0))
        val result = smoother.update(sample(distanceM = 170, elapsedS = 483, inclinePct = 5.0))

        assertClose(170.0, result.distanceM)
        assertClose(0.1, result.ascentM)
    }

    @Test
    fun buffersInclineChangesBeforeApplyingToAscent() {
        val smoother = TreadmillSampleSmoother()

        smoother.update(sample(distanceM = 100, elapsedS = 1, inclinePct = 1.0))
        val firstAfterChange = smoother.update(sample(distanceM = 110, elapsedS = 2, inclinePct = 5.0))
        val buffered = smoother.update(sample(distanceM = 111, elapsedS = 3, inclinePct = 5.0))
        val applied = smoother.update(sample(distanceM = 112, elapsedS = 4, inclinePct = 5.0))

        assertClose(0.1, firstAfterChange.ascentM)
        assertClose(0.11, buffered.ascentM)
        assertClose(0.16, applied.ascentM)
    }

    @Test
    fun bridgeMessageUsesScaledIntegers() {
        val smoother = TreadmillSampleSmoother()
        val smoothed = smoother.update(sample(distanceM = 160, elapsedS = 473))

        val map = BridgeMessage(sequence = 7, sample = smoothed).toMap()

        assertEquals(2, map["v"])
        assertEquals("ftms", map["type"])
        assertEquals(2, map["kind"])
        assertEquals(7, map["seq"])
        assertEquals(600, map["speed100"])
        assertEquals(1600, map["dist10"])
        assertEquals(160, map["ftmsDist"])
        assertEquals(30, map["incl10"])
    }

    private fun sample(distanceM: Int, elapsedS: Int, inclinePct: Double = 3.0): TreadmillSample =
        TreadmillSample(
            rawFlags = 0x048c,
            speedKmh = 6.0,
            distanceM = distanceM,
            inclinePct = inclinePct,
            elapsedS = elapsedS,
        )

    private fun assertClose(expected: Double, actual: Double) {
        assertTrue(abs(expected - actual) < 0.01, "expected <$expected>, actual <$actual>")
    }
}
