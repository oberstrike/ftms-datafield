package de.ma.ftms.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FtmsGenericParserTest {
    @Test
    fun parsesIndoorBikePacket() {
        val sample = FtmsIndoorBikeParser.parse(
            hex("74 0e 58 02 b4 00 34 12 00 05 00 d2 00 96 03 21 00"),
        )

        assertFalse(sample.truncated)
        assertEquals(FtmsMachineKind.INDOOR_BIKE, sample.kind)
        assertEquals(6.0, sample.speedKmh)
        assertEquals(90.0, sample.cadenceRpm)
        assertEquals(0x1234, sample.distanceM)
        assertEquals(5.0, sample.resistance)
        assertEquals(210, sample.powerW)
        assertEquals(150, sample.heartRateBpm)
        assertEquals(33, sample.elapsedS)
    }

    @Test
    fun parsesCrossTrainerPacket() {
        val sample = FtmsCrossTrainerParser.parse(
            hex("fc 29 00 58 02 34 12 00 78 00 6c 00 20 03 32 00 00 00 00 00 00 00 f4 01 96 00 46 d9 01"),
        )

        assertFalse(sample.truncated)
        assertEquals(FtmsMachineKind.CROSS_TRAINER, sample.kind)
        assertEquals(6.0, sample.speedKmh)
        assertEquals(0x1234, sample.distanceM)
        assertEquals(120.0, sample.stepRateSpm)
        assertEquals(80.0, sample.strideCount)
        assertEquals(50.0, sample.resistance)
        assertEquals(150, sample.powerW)
        assertEquals(70, sample.heartRateBpm)
        assertEquals(473, sample.elapsedS)
    }

    @Test
    fun bridgeMessageCarriesOptionalGenericMetrics() {
        val smoothed = SmoothedFtmsSample(
            raw = FtmsSample(
                kind = FtmsMachineKind.INDOOR_BIKE,
                rawFlags = 0x0e74,
                cadenceRpm = 90.0,
                resistance = 5.0,
                heartRateBpm = 150,
                remainingS = 120,
            ),
            distanceM = 42.0,
            ascentM = 0.0,
            distanceSource = DistanceSource.FTMS,
        )

        val map = BridgeMessage(sequence = 8, sample = smoothed).toMap()

        assertEquals(2, map["v"])
        assertEquals(FtmsMachineKind.INDOOR_BIKE, map["kind"])
        assertEquals(900, map["cad10"])
        assertEquals(50, map["res10"])
        assertEquals(150, map["hr"])
        assertEquals(120, map["remaining"])
    }

    @Test
    fun parsesRunningSpeedCadenceMeasurement() {
        val sample = RscMeasurementParser.parse(hex("02 00 01 a4 e8 03 00 00"))

        assertFalse(sample.truncated)
        assertEquals(FtmsMachineKind.TREADMILL, sample.kind)
        assertEquals(3.6, sample.speedKmh)
        assertEquals(164.0, sample.stepRateSpm)
        assertEquals(1000, sample.distanceM)
    }
}
