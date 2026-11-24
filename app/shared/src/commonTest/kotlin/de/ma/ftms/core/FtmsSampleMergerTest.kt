package de.ma.ftms.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class FtmsSampleMergerTest {
    @Test
    fun passesCompleteSamplesThroughUnchanged() {
        val merger = FtmsSampleMerger()
        val sample = FtmsSample(timestampMillis = 1_000, speedKmh = 8.0, distanceM = 120, inclinePct = 2.0)

        assertEquals(sample, merger.merge(sample))
    }

    @Test
    fun fillsFieldsMissingFromASplitRecord() {
        val merger = FtmsSampleMerger()
        merger.merge(FtmsSample(timestampMillis = 1_000, speedKmh = 8.0, distanceM = 120))

        val merged = merger.merge(FtmsSample(timestampMillis = 1_200, rawFlags = 0x0501, heartRateBpm = 141, elapsedS = 60))

        assertEquals(8.0, merged.speedKmh)
        assertEquals(120, merged.distanceM)
        assertEquals(141, merged.heartRateBpm)
        assertEquals(60, merged.elapsedS)
        assertEquals(0x0501, merged.rawFlags)
    }

    @Test
    fun prefersFreshValuesOverCarriedOnes() {
        val merger = FtmsSampleMerger()
        merger.merge(FtmsSample(timestampMillis = 1_000, speedKmh = 8.0, heartRateBpm = 120))

        val merged = merger.merge(FtmsSample(timestampMillis = 2_000, speedKmh = 9.5))

        assertEquals(9.5, merged.speedKmh)
        assertEquals(120, merged.heartRateBpm)
    }

    @Test
    fun dropsCarriedValuesThatAreTooOld() {
        val merger = FtmsSampleMerger(maxFieldAgeMillis = 3_000)
        merger.merge(FtmsSample(timestampMillis = 1_000, speedKmh = 8.0, heartRateBpm = 120))

        val stillFresh = merger.merge(FtmsSample(timestampMillis = 4_000, speedKmh = 8.0))
        val expired = merger.merge(FtmsSample(timestampMillis = 4_001, speedKmh = 8.0))

        assertEquals(120, stillFresh.heartRateBpm)
        assertNull(expired.heartRateBpm)
    }

    @Test
    fun doesNotCarryValuesAcrossMachineKindsOrReset() {
        val merger = FtmsSampleMerger()
        merger.merge(FtmsSample(kind = FtmsMachineKind.TREADMILL, timestampMillis = 1_000, inclinePct = 4.0))

        val otherKind = merger.merge(FtmsSample(kind = FtmsMachineKind.INDOOR_BIKE, timestampMillis = 1_100, cadenceRpm = 80.0))
        assertNull(otherKind.inclinePct)

        merger.reset()
        val afterReset = merger.merge(FtmsSample(kind = FtmsMachineKind.INDOOR_BIKE, timestampMillis = 1_200, powerW = 150))
        assertNull(afterReset.cadenceRpm)
    }

    @Test
    fun keepsLastValuesWhenAPacketCannotBeParsed() {
        val merger = FtmsSampleMerger()
        merger.merge(FtmsSample(timestampMillis = 1_000, speedKmh = 8.0, distanceM = 120))

        val merged = merger.merge(FtmsSample(timestampMillis = 2_000, truncated = true, parseError = "truncated"))

        assertEquals(8.0, merged.speedKmh)
        assertEquals(true, merged.truncated)
    }

    @Test
    fun leavesSamplesWithoutTimestampUntouched() {
        val merger = FtmsSampleMerger()
        merger.merge(FtmsSample(timestampMillis = 1_000, speedKmh = 8.0))
        val sample = FtmsSample(heartRateBpm = 100)

        assertSame(sample, merger.merge(sample))
    }
}
