package de.ma.ftms.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FtmsControlPointTest {
    @Test
    fun encodesRequestControl() {
        assertContentEquals(byteArrayOf(0x00), FtmsControlPoint.requestControl())
    }

    @Test
    fun encodesTargetPowerAsLittleEndianSignedWatts() {
        assertContentEquals(hex("05 96 00"), FtmsControlPoint.setTargetPower(150))
        assertContentEquals(hex("05 2C 01"), FtmsControlPoint.setTargetPower(300))
        assertContentEquals(hex("05 FF 7F"), FtmsControlPoint.setTargetPower(100_000))
    }

    @Test
    fun readsTheTargetPowerFromAMachineStatus() {
        assertEquals(150, FtmsMachineStatus.targetPowerWatts(hex("08 96 00")))
        assertEquals(300, FtmsMachineStatus.targetPowerWatts(hex("08 2C 01")))
        assertEquals(-10, FtmsMachineStatus.targetPowerWatts(hex("08 F6 FF")))
    }

    @Test
    fun otherMachineStatusesNameNoTargetPower() {
        // Target resistance changed, stopped by the user, too short, empty.
        assertNull(FtmsMachineStatus.targetPowerWatts(hex("07 32")))
        assertNull(FtmsMachineStatus.targetPowerWatts(hex("02 01")))
        assertNull(FtmsMachineStatus.targetPowerWatts(hex("08 96")))
        assertNull(FtmsMachineStatus.targetPowerWatts(byteArrayOf()))
    }

    @Test
    fun readsAResponseIndication() {
        val accepted = FtmsControlPoint.parseResponse(hex("80 05 01"))
        assertEquals(FtmsControlResponse(requestOpCode = 0x05, resultCode = 0x01), accepted)
        assertTrue(accepted!!.succeeded)

        val refused = FtmsControlPoint.parseResponse(hex("80 05 05"))
        assertEquals(FtmsControlPoint.RESULT_CONTROL_NOT_PERMITTED, refused!!.resultCode)
        assertTrue(!refused.succeeded)
    }

    @Test
    fun ignoresValuesThatAreNotAResponse() {
        assertNull(FtmsControlPoint.parseResponse(hex("80 05")))
        assertNull(FtmsControlPoint.parseResponse(hex("05 96 00")))
        assertNull(FtmsControlPoint.parseResponse(byteArrayOf()))
    }

    @Test
    fun readsTheSupportedPowerRange() {
        assertEquals(
            FtmsPowerRange(minWatts = 25, maxWatts = 2000, stepWatts = 5),
            FtmsPowerRange.parse(hex("19 00 D0 07 05 00")),
        )
        assertEquals(
            FtmsPowerRange(minWatts = -100, maxWatts = 100, stepWatts = 1),
            FtmsPowerRange.parse(hex("9C FF 64 00 00 00")),
        )
    }

    @Test
    fun rejectsATruncatedOrEmptyPowerRange() {
        assertNull(FtmsPowerRange.parse(hex("19 00 D0 07 05")))
        assertNull(FtmsPowerRange.parse(hex("D0 07 19 00 05 00")))
    }

    @Test
    fun snapsATargetToWhatTheEquipmentAccepts() {
        val range = FtmsPowerRange(minWatts = 25, maxWatts = 400, stepWatts = 10)
        assertEquals(25, range.nearest(0))
        assertEquals(395, range.nearest(1000))
        assertEquals(145, range.nearest(147))
        assertEquals(155, range.nearest(150))
        assertEquals(133, FtmsPowerRange(minWatts = 0, maxWatts = 400, stepWatts = 1).nearest(133))
    }
}
