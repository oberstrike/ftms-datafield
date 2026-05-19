package de.ma.ftms.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FtmsTreadmillParserTest {
    @Test
    fun parsesIdlePacket() {
        val sample = FtmsTreadmillParser.parse(hex("84 04 00 00 00 00 00 00 00 00 00 00 00 00"))

        assertFalse(sample.truncated)
        assertEquals(0x0484, sample.rawFlags)
        assertEquals(0.0, sample.speedKmh)
        assertEquals(0, sample.distanceM)
        assertEquals(0, sample.elapsedS)
    }

    @Test
    fun parsesMovingPacket() {
        val sample = FtmsTreadmillParser.parse(
            hex("8c 04 58 02 a0 00 00 1e 00 00 00 0b 00 00 00 00 d9 01"),
        )

        assertFalse(sample.truncated)
        assertEquals(0x048c, sample.rawFlags)
        assertEquals(6.0, sample.speedKmh)
        assertEquals(160, sample.distanceM)
        assertEquals(3.0, sample.inclinePct)
        assertEquals(0.0, sample.rampDeg)
        assertEquals(473, sample.elapsedS)
    }

    @Test
    fun marksTruncatedPackets() {
        val sample = FtmsTreadmillParser.parse(hex("8c 04 58"))

        assertTrue(sample.truncated)
        assertTrue(sample.parseError?.contains("truncated") == true)
    }
}
