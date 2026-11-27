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

    @Test
    fun encodesTargetSpeedAsHundredthsOfKmh() {
        // 4.00 km/h and 0.80 km/h, as the Darwin TM 70 took them.
        assertContentEquals(hex("02 90 01"), FtmsControlPoint.setTargetSpeed(400))
        assertContentEquals(hex("02 50 00"), FtmsControlPoint.setTargetSpeed(80))
        assertContentEquals(hex("02 00 00"), FtmsControlPoint.setTargetSpeed(-5))
    }

    @Test
    fun encodesTargetInclinationAsSignedTenthsOfPercent() {
        assertContentEquals(hex("03 0A 00"), FtmsControlPoint.setTargetInclination(10))
        assertContentEquals(hex("03 00 00"), FtmsControlPoint.setTargetInclination(0))
        assertContentEquals(hex("03 F6 FF"), FtmsControlPoint.setTargetInclination(-10))
    }

    @Test
    fun readsWhichTargetsTheEquipmentTakes() {
        // The Darwin TM 70: speed and inclination, nothing else.
        assertEquals(
            FtmsTargetSettings(speed = true, inclination = true),
            FtmsTargetSettings.parse(hex("5C 16 00 00 03 00 00 00")),
        )
        assertEquals(
            FtmsTargetSettings(speed = false, inclination = false),
            FtmsTargetSettings.parse(hex("5C 16 00 00 08 00 00 00")),
        )
        assertEquals(
            FtmsTargetSettings(speed = true, inclination = false),
            FtmsTargetSettings.parse(hex("00 00 00 00 01 00 00 00")),
        )
        assertNull(FtmsTargetSettings.parse(hex("5C 16 00 00")))
    }

    @Test
    fun readsTheSpeedAndInclinationRanges() {
        // The Darwin TM 70: 0.80 to 18.00 km/h in 0.10 steps, 0 to 15.0 % in 1.0 steps.
        assertEquals(FtmsValueRange(min = 80, max = 1800, step = 10), FtmsValueRange.parseSpeed(hex("50 00 08 07 0A 00")))
        assertEquals(FtmsValueRange(min = 0, max = 150, step = 10), FtmsValueRange.parseInclination(hex("00 00 96 00 0A 00")))
        // A decline is negative only for the inclination; a speed range has no sign.
        assertEquals(FtmsValueRange(min = -30, max = 150, step = 5), FtmsValueRange.parseInclination(hex("E2 FF 96 00 05 00")))
        assertEquals(FtmsValueRange(min = 0, max = 0xFFE2, step = 1), FtmsValueRange.parseSpeed(hex("00 00 E2 FF 00 00")))
        assertNull(FtmsValueRange.parseSpeed(hex("50 00 08")))
        assertNull(FtmsValueRange.parseSpeed(hex("08 07 50 00 0A 00")))
    }

    @Test
    fun snapsAValueToTheRangeAndItsSteps() {
        val speed = FtmsValueRange(min = 80, max = 1800, step = 10)

        assertEquals(850, speed.nearest(850))
        assertEquals(850, speed.nearest(854))
        assertEquals(860, speed.nearest(855))
        assertEquals(80, speed.nearest(0))
        assertEquals(1800, speed.nearest(2500))
        // The top of a range that does not end on a step stays within the range.
        assertEquals(140, FtmsValueRange(min = 0, max = 145, step = 10).nearest(145))
    }
}
