package de.ma.ftms.bridge

import de.ma.ftms.bridge.runtime.BridgeSettings
import de.ma.ftms.bridge.runtime.DEFAULT_TREADMILL_SPEED_PRESETS
import de.ma.ftms.bridge.runtime.EquipmentType
import de.ma.ftms.bridge.runtime.TreadmillControlAvailability
import de.ma.ftms.bridge.runtime.TreadmillControlState
import de.ma.ftms.bridge.runtime.TreadmillController
import de.ma.ftms.bridge.runtime.TreadmillSetting
import de.ma.ftms.bridge.runtime.TreadmillTargetStatus
import de.ma.ftms.bridge.runtime.isLargeSpeedIncrease
import de.ma.ftms.bridge.runtime.isNewTreadmillPresetAllowed
import de.ma.ftms.bridge.runtime.isTreadmillControlWanted
import de.ma.ftms.bridge.runtime.parseTreadmillValue
import de.ma.ftms.bridge.runtime.sanitizeTreadmillPresets
import de.ma.ftms.core.FtmsControlPoint
import de.ma.ftms.core.FtmsControlResponse
import de.ma.ftms.core.FtmsTargetSettings
import de.ma.ftms.core.FtmsValueRange
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TreadmillControllerTest {
    private val sent = mutableListOf<ByteArray>()
    private val sequences = mutableListOf<Int>()
    private var writesSucceed = true
    private var now = 1_000L
    private val controller = TreadmillController(
        sendCommand = { bytes, sequence ->
            if (writesSucceed) {
                sent += bytes
                sequences += sequence
            }
            writesSucceed
        },
        onStateChanged = {},
    )

    private val speed get() = controller.state.speed
    private val incline get() = controller.state.incline

    private fun answer(opCode: Int, result: Int = FtmsControlPoint.RESULT_SUCCESS) {
        controller.onResponse(FtmsControlResponse(requestOpCode = opCode, resultCode = result), now)
    }

    /** One packet of the treadmill, a second after the last. */
    private fun packet(speedHundredths: Int?, inclineTenths: Int? = null) {
        now += 1_000
        controller.onMeasured(speedHundredths, inclineTenths, now)
    }

    /** The Darwin TM 70 with its belt running at [speedHundredths]. */
    private fun runningTreadmill(speedHundredths: Int = 800) {
        controller.onEquipmentReady(controlPointAvailable = true)
        controller.onTargetSettings(FtmsTargetSettings(speed = true, inclination = true))
        controller.onSupportedRange(TreadmillSetting.SPEED, FtmsValueRange(min = 80, max = 1800, step = 10))
        controller.onSupportedRange(TreadmillSetting.INCLINE, FtmsValueRange(min = 0, max = 150, step = 10))
        packet(speedHundredths)
    }

    /** Asks for [value] and lets the treadmill grant control and confirm it. */
    private fun setAndConfirm(setting: TreadmillSetting, value: Int) {
        val granted = sent.any { it.contentEquals(FtmsControlPoint.requestControl()) }
        assertTrue(controller.request(setting, value, now))
        if (!granted) {
            answer(FtmsControlPoint.OP_REQUEST_CONTROL)
        }
        answer(
            when (setting) {
                TreadmillSetting.SPEED -> FtmsControlPoint.OP_SET_TARGET_SPEED
                TreadmillSetting.INCLINE -> FtmsControlPoint.OP_SET_TARGET_INCLINATION
            },
        )
    }

    @Test
    fun nothingIsSentBeforeTheTreadmillIsConnected() {
        assertFalse(controller.request(TreadmillSetting.SPEED, 1000, now))

        assertTrue(sent.isEmpty())
        assertEquals(TreadmillControlState(), controller.state)
    }

    @Test
    fun aTreadmillWithoutControlPointIsUnsupported() {
        controller.onEquipmentReady(controlPointAvailable = false)
        packet(800)

        assertEquals(TreadmillControlAvailability.UNSUPPORTED, controller.state.availability)
        assertFalse(controller.state.available)
        assertFalse(controller.request(TreadmillSetting.SPEED, 1000, now))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun connectingSendsNothingByItself() {
        runningTreadmill()

        assertTrue(sent.isEmpty())
        assertTrue(controller.state.canSet)
        assertEquals(800, speed.measured)
        assertEquals(0, incline.measured)
        assertNull(speed.target)
    }

    @Test
    fun aStandingBeltIsGivenNothing() {
        runningTreadmill(speedHundredths = 0)

        assertTrue(controller.state.available)
        assertFalse(controller.state.canSet)
        assertFalse(controller.request(TreadmillSetting.SPEED, 600, now))
        assertFalse(controller.step(TreadmillSetting.INCLINE, 10, now))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun aRequestAsksForControlFirstAndThenSetsTheSpeed() {
        runningTreadmill()

        assertTrue(controller.request(TreadmillSetting.SPEED, 1000, now))
        assertContentEquals(FtmsControlPoint.requestControl(), sent.single())
        assertEquals(TreadmillTargetStatus.SENDING, speed.status)
        assertEquals(1000, speed.target)

        answer(FtmsControlPoint.OP_REQUEST_CONTROL)
        assertContentEquals(FtmsControlPoint.setTargetSpeed(1000), sent.last())

        answer(FtmsControlPoint.OP_SET_TARGET_SPEED)
        assertEquals(TreadmillTargetStatus.ACCEPTED, speed.status)
        assertEquals(2, sent.size)
    }

    @Test
    fun controlIsAskedForOnlyOncePerConnection() {
        runningTreadmill()
        setAndConfirm(TreadmillSetting.SPEED, 1000)

        setAndConfirm(TreadmillSetting.INCLINE, 20)

        assertEquals(1, sent.count { it.contentEquals(FtmsControlPoint.requestControl()) })
        assertContentEquals(FtmsControlPoint.setTargetInclination(20), sent.last())
        assertEquals(TreadmillTargetStatus.ACCEPTED, incline.status)
    }

    @Test
    fun aValueIsSnappedToWhatTheTreadmillTakes() {
        runningTreadmill()

        setAndConfirm(TreadmillSetting.SPEED, 2500)
        assertContentEquals(FtmsControlPoint.setTargetSpeed(1800), sent.last())

        setAndConfirm(TreadmillSetting.INCLINE, 25)
        assertContentEquals(FtmsControlPoint.setTargetInclination(30), sent.last())
    }

    @Test
    fun aSpeedOfZeroIsNeverSent() {
        controller.onEquipmentReady(controlPointAvailable = true)
        // A treadmill that names zero as its lowest speed.
        controller.onSupportedRange(TreadmillSetting.SPEED, FtmsValueRange(min = 0, max = 2000, step = 10))
        packet(100)

        setAndConfirm(TreadmillSetting.SPEED, 0)

        assertContentEquals(FtmsControlPoint.setTargetSpeed(50), sent.last())
    }

    @Test
    fun stepsFollowOneAnotherWhileTheBeltIsStillOnItsWay() {
        runningTreadmill(speedHundredths = 800)

        assertTrue(controller.step(TreadmillSetting.SPEED, 50, now))
        answer(FtmsControlPoint.OP_REQUEST_CONTROL)
        // Two more taps before the treadmill answered the first.
        assertTrue(controller.step(TreadmillSetting.SPEED, 50, now))
        assertTrue(controller.step(TreadmillSetting.SPEED, 50, now))
        assertEquals(950, speed.target)

        answer(FtmsControlPoint.OP_SET_TARGET_SPEED)
        // Only the newest value follows the one that was on its way.
        assertContentEquals(FtmsControlPoint.setTargetSpeed(950), sent.last())
        assertEquals(3, sent.size)
        answer(FtmsControlPoint.OP_SET_TARGET_SPEED)
        assertEquals(TreadmillTargetStatus.ACCEPTED, speed.status)
    }

    @Test
    fun aStepStartsFromTheConsoleSpeedOnceTheBeltLeftTheTarget() {
        runningTreadmill(speedHundredths = 800)
        setAndConfirm(TreadmillSetting.SPEED, 1200)
        packet(1000)
        assertEquals(1200, speed.target)
        packet(1200)
        assertEquals(1200, speed.target)

        // Someone slows the belt down on the console.
        packet(600)

        assertNull(speed.target)
        assertEquals(TreadmillTargetStatus.IDLE, speed.status)
        assertTrue(controller.step(TreadmillSetting.SPEED, 10, now))
        assertContentEquals(FtmsControlPoint.setTargetSpeed(610), sent.last())
    }

    @Test
    fun aTargetTheBeltNeverReachesIsGivenUp() {
        runningTreadmill(speedHundredths = 800)
        setAndConfirm(TreadmillSetting.SPEED, 1200)

        repeat(61) { packet(800) }

        assertNull(speed.target)
        assertEquals(800, controller.steppingBase(TreadmillSetting.SPEED))
    }

    @Test
    fun aDisconnectForgetsEverythingAndAReconnectSendsNothing() {
        runningTreadmill(speedHundredths = 1200)
        setAndConfirm(TreadmillSetting.SPEED, 1200)
        packet(1200)
        val sentBefore = sent.size

        controller.onEquipmentDisconnected()
        assertEquals(TreadmillControlState(), controller.state)

        // The runner slowed down on the console while the link was away.
        runningTreadmill(speedHundredths = 600)
        repeat(5) { packet(600) }

        assertEquals(sentBefore, sent.size)
        assertNull(speed.target)
        assertEquals(600, controller.steppingBase(TreadmillSetting.SPEED))
    }

    @Test
    fun aStoppedBeltForgetsWhatWasAskedFor() {
        runningTreadmill(speedHundredths = 800)
        assertTrue(controller.request(TreadmillSetting.SPEED, 1200, now))
        answer(FtmsControlPoint.OP_REQUEST_CONTROL)
        assertTrue(controller.request(TreadmillSetting.INCLINE, 30, now))
        val sentBefore = sent.size

        // The safety key is pulled before the incline could be sent.
        packet(0)
        answer(FtmsControlPoint.OP_SET_TARGET_SPEED)

        assertEquals(sentBefore, sent.size)
        assertNull(speed.target)
        assertNull(incline.target)
        assertFalse(controller.state.canSet)
    }

    @Test
    fun aRequestOnStaleDataIsRefused() {
        runningTreadmill(speedHundredths = 800)
        now += 6_000

        assertFalse(controller.request(TreadmillSetting.SPEED, 1000, now))

        assertTrue(sent.isEmpty())
        assertFalse(controller.state.beltMoving)
    }

    @Test
    fun aRefusedValueIsForgotten() {
        runningTreadmill(speedHundredths = 800)
        assertTrue(controller.request(TreadmillSetting.SPEED, 1000, now))
        answer(FtmsControlPoint.OP_REQUEST_CONTROL)

        answer(FtmsControlPoint.OP_SET_TARGET_SPEED, FtmsControlPoint.RESULT_INVALID_PARAMETER)

        assertNull(speed.target)
        assertEquals(TreadmillTargetStatus.REJECTED, speed.status)
        assertEquals(800, controller.steppingBase(TreadmillSetting.SPEED))
    }

    @Test
    fun aSettingTheTreadmillDoesNotKnowIsNoLongerOffered() {
        runningTreadmill(speedHundredths = 800)
        assertTrue(controller.request(TreadmillSetting.INCLINE, 10, now))
        answer(FtmsControlPoint.OP_REQUEST_CONTROL)

        answer(FtmsControlPoint.OP_SET_TARGET_INCLINATION, FtmsControlPoint.RESULT_NOT_SUPPORTED)

        assertFalse(incline.supported)
        assertNull(incline.target)
        assertFalse(controller.request(TreadmillSetting.INCLINE, 10, now))
        assertTrue(controller.state.available)
    }

    @Test
    fun theFeatureDecidesWhatIsOffered() {
        controller.onEquipmentReady(controlPointAvailable = true)
        controller.onTargetSettings(FtmsTargetSettings(speed = true, inclination = false))
        packet(800)

        assertTrue(speed.supported)
        assertFalse(incline.supported)
        assertFalse(controller.request(TreadmillSetting.INCLINE, 10, now))

        controller.onTargetSettings(FtmsTargetSettings(speed = false, inclination = false))
        assertFalse(controller.state.available)
    }

    @Test
    fun controlIsAskedForOnceMoreWhenTheTreadmillDroppedIt() {
        runningTreadmill(speedHundredths = 800)
        assertTrue(controller.request(TreadmillSetting.SPEED, 1000, now))
        answer(FtmsControlPoint.OP_REQUEST_CONTROL)

        answer(FtmsControlPoint.OP_SET_TARGET_SPEED, FtmsControlPoint.RESULT_CONTROL_NOT_PERMITTED)
        assertContentEquals(FtmsControlPoint.requestControl(), sent.last())
        answer(FtmsControlPoint.OP_REQUEST_CONTROL)
        assertContentEquals(FtmsControlPoint.setTargetSpeed(1000), sent.last())

        // A second refusal is final.
        answer(FtmsControlPoint.OP_SET_TARGET_SPEED, FtmsControlPoint.RESULT_CONTROL_NOT_PERMITTED)
        assertEquals(TreadmillTargetStatus.REJECTED, speed.status)
        assertEquals(4, sent.size)
    }

    @Test
    fun anUnansweredCommandIsGivenUpAndNotSentLater() {
        runningTreadmill(speedHundredths = 800)
        assertTrue(controller.request(TreadmillSetting.SPEED, 1000, now))

        controller.onCommandLost(sequences.last())

        assertNull(speed.target)
        assertEquals(TreadmillTargetStatus.FAILED, speed.status)
        // A late answer changes nothing and triggers nothing.
        answer(FtmsControlPoint.OP_REQUEST_CONTROL)
        assertEquals(1, sent.size)
    }

    @Test
    fun aWriteThatCannotBeStartedFails() {
        runningTreadmill(speedHundredths = 800)
        writesSucceed = false

        assertTrue(controller.request(TreadmillSetting.SPEED, 1000, now))

        assertNull(speed.target)
        assertEquals(TreadmillTargetStatus.FAILED, speed.status)
    }

    @Test
    fun anInclineThatStaysAwayCountsAsLevel() {
        runningTreadmill(speedHundredths = 800)
        packet(800, inclineTenths = 20)
        assertEquals(20, incline.measured)

        // The Darwin TM 70 leaves the incline out of its packets once it is back at zero.
        packet(800)
        assertEquals(20, incline.measured)
        repeat(3) { packet(800) }

        assertEquals(0, incline.measured)
    }

    @Test
    fun anInclineBackAtZeroCountsAsReachedThroughItsAbsence() {
        runningTreadmill(speedHundredths = 800)
        packet(800, inclineTenths = 20)
        setAndConfirm(TreadmillSetting.INCLINE, 0)

        repeat(5) { packet(800) }
        assertEquals(0, incline.measured)
        assertEquals(0, incline.target)

        // The console lifts the deck again.
        packet(800, inclineTenths = 30)
        assertNull(incline.target)
    }

    @Test
    fun quickChoicesAreKeptValidSortedAndLimited() {
        assertEquals(
            listOf(600, 850, 1200),
            sanitizeTreadmillPresets(TreadmillSetting.SPEED, listOf(1200, 600, 850, 600, 0, 3000, 855)),
        )
        assertEquals(
            listOf(0, 5, 20),
            sanitizeTreadmillPresets(TreadmillSetting.INCLINE, listOf(20, 0, 5, 3, 500, -10)),
        )
        assertEquals(8, sanitizeTreadmillPresets(TreadmillSetting.SPEED, (1..20).map { it * 100 }).size)
    }

    @Test
    fun aQuickChoiceCanBeAddedOnlyOnceAndWhileThereIsRoom() {
        val presets = DEFAULT_TREADMILL_SPEED_PRESETS

        assertTrue(isNewTreadmillPresetAllowed(TreadmillSetting.SPEED, 850, presets))
        assertFalse(isNewTreadmillPresetAllowed(TreadmillSetting.SPEED, 800, presets))
        assertFalse(isNewTreadmillPresetAllowed(TreadmillSetting.SPEED, null, presets))
        assertFalse(isNewTreadmillPresetAllowed(TreadmillSetting.SPEED, 3000, presets))
        assertFalse(isNewTreadmillPresetAllowed(TreadmillSetting.SPEED, 855, presets))
        assertFalse(isNewTreadmillPresetAllowed(TreadmillSetting.SPEED, 1500, (1..8).map { it * 100 }))
    }

    @Test
    fun readsATypedValueWithCommaOrPoint() {
        assertEquals(850, parseTreadmillValue(TreadmillSetting.SPEED, "8,5"))
        assertEquals(850, parseTreadmillValue(TreadmillSetting.SPEED, " 8.5 "))
        assertEquals(1200, parseTreadmillValue(TreadmillSetting.SPEED, "12"))
        assertEquals(15, parseTreadmillValue(TreadmillSetting.INCLINE, "1,5"))
        assertEquals(0, parseTreadmillValue(TreadmillSetting.INCLINE, "0"))
        // Finer than a quick choice can be.
        assertNull(parseTreadmillValue(TreadmillSetting.SPEED, "8,55"))
        assertNull(parseTreadmillValue(TreadmillSetting.INCLINE, "1,2"))
        assertNull(parseTreadmillValue(TreadmillSetting.SPEED, ""))
        assertNull(parseTreadmillValue(TreadmillSetting.SPEED, "8,5,1"))
        assertNull(parseTreadmillValue(TreadmillSetting.SPEED, ","))
    }

    @Test
    fun onlyAJumpOfMoreThanThreeKmhCountsAsLarge() {
        assertFalse(isLargeSpeedIncrease(measured = 800, target = 1100))
        assertTrue(isLargeSpeedIncrease(measured = 800, target = 1110))
        assertFalse(isLargeSpeedIncrease(measured = 1400, target = 600))
        assertTrue(isLargeSpeedIncrease(measured = null, target = 600))
    }

    @Test
    fun onlyATreadmillIsSteeredAndOnlyWhenSwitchedOn() {
        val on = BridgeSettings(treadmillControl = true)

        assertTrue(isTreadmillControlWanted(on, EquipmentType.TREADMILL))
        assertFalse(isTreadmillControlWanted(on, EquipmentType.INDOOR_BIKE))
        assertFalse(isTreadmillControlWanted(on, EquipmentType.CROSS_TRAINER))
        assertFalse(isTreadmillControlWanted(BridgeSettings(), EquipmentType.TREADMILL))
    }
}
