package de.ma.ftms.bridge

import de.ma.ftms.bridge.runtime.DEFAULT_TARGET_POWER_PRESETS
import de.ma.ftms.bridge.runtime.PowerControlState
import de.ma.ftms.bridge.runtime.PowerControlStatus
import de.ma.ftms.bridge.runtime.TargetPowerController
import de.ma.ftms.bridge.runtime.isNewTargetPowerPresetAllowed
import de.ma.ftms.bridge.runtime.sanitizeTargetPowerPresets
import de.ma.ftms.bridge.runtime.steppedTargetPower
import de.ma.ftms.core.FtmsControlPoint
import de.ma.ftms.core.FtmsControlResponse
import de.ma.ftms.core.FtmsPowerRange
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TargetPowerControllerTest {
    private val sent = mutableListOf<ByteArray>()
    private val sequences = mutableListOf<Int>()
    private val states = mutableListOf<PowerControlState>()
    private var writesSucceed = true
    private val controller = TargetPowerController(
        sendCommand = { bytes, sequence ->
            if (writesSucceed) {
                sent += bytes
                sequences += sequence
            }
            writesSucceed
        },
        onStateChanged = { states += it },
    )

    private fun answer(opCode: Int, result: Int = FtmsControlPoint.RESULT_SUCCESS) {
        controller.onResponse(FtmsControlResponse(requestOpCode = opCode, resultCode = result))
    }

    private fun connectedBike() {
        controller.onEquipmentReady(controlPointAvailable = true)
    }

    @Test
    fun cannotSetATargetBeforeTheEquipmentIsReady() {
        controller.requestTarget(150)

        assertTrue(sent.isEmpty())
        assertEquals(PowerControlState(), controller.state)
        assertTrue(!controller.state.canSetTarget)
    }

    @Test
    fun equipmentWithoutControlPointIsUnsupported() {
        controller.onEquipmentReady(controlPointAvailable = false)
        controller.requestTarget(150)

        assertEquals(PowerControlStatus.UNSUPPORTED, controller.state.status)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun asksForControlFirstThenSendsTheTarget() {
        connectedBike()
        assertEquals(PowerControlStatus.READY, controller.state.status)

        controller.requestTarget(150)
        assertContentEquals(FtmsControlPoint.requestControl(), sent.single())
        assertEquals(PowerControlStatus.SENDING, controller.state.status)
        assertEquals(150, controller.state.targetWatts)

        answer(FtmsControlPoint.OP_REQUEST_CONTROL)
        assertContentEquals(FtmsControlPoint.setTargetPower(150), sent.last())
        assertEquals(PowerControlStatus.SENDING, controller.state.status)

        answer(FtmsControlPoint.OP_SET_TARGET_POWER)
        assertEquals(PowerControlStatus.ACTIVE, controller.state.status)
        assertEquals(2, sent.size)
    }

    @Test
    fun asksForControlOnlyOncePerConnection() {
        connectedBike()
        controller.requestTarget(150)
        answer(FtmsControlPoint.OP_REQUEST_CONTROL)
        answer(FtmsControlPoint.OP_SET_TARGET_POWER)

        controller.requestTarget(200)

        assertEquals(3, sent.size)
        assertContentEquals(FtmsControlPoint.setTargetPower(200), sent.last())
    }

    @Test
    fun sendsOnlyTheNewestTargetWhenTheUserIsFasterThanTheEquipment() {
        connectedBike()
        controller.requestTarget(150)
        answer(FtmsControlPoint.OP_REQUEST_CONTROL)

        controller.requestTarget(160)
        controller.requestTarget(170)
        controller.requestTarget(180)
        assertEquals(2, sent.size)

        answer(FtmsControlPoint.OP_SET_TARGET_POWER)
        assertContentEquals(FtmsControlPoint.setTargetPower(180), sent.last())
        assertEquals(3, sent.size)
        assertEquals(PowerControlStatus.SENDING, controller.state.status)

        answer(FtmsControlPoint.OP_SET_TARGET_POWER)
        assertEquals(PowerControlStatus.ACTIVE, controller.state.status)
        assertEquals(180, controller.state.targetWatts)
    }

    @Test
    fun snapsTheTargetToTheRangeOfTheEquipment() {
        connectedBike()
        controller.onSupportedRange(FtmsPowerRange(minWatts = 50, maxWatts = 400, stepWatts = 5))

        controller.requestTarget(1000)
        assertEquals(400, controller.state.targetWatts)
        controller.requestTarget(7)
        assertEquals(50, controller.state.targetWatts)
        controller.requestTarget(152)
        assertEquals(150, controller.state.targetWatts)
    }

    @Test
    fun asksForControlAgainOnceWhenTheEquipmentTookItAway() {
        connectedBike()
        controller.requestTarget(150)
        answer(FtmsControlPoint.OP_REQUEST_CONTROL)
        answer(FtmsControlPoint.OP_SET_TARGET_POWER, FtmsControlPoint.RESULT_CONTROL_NOT_PERMITTED)

        assertContentEquals(FtmsControlPoint.requestControl(), sent.last())
        answer(FtmsControlPoint.OP_REQUEST_CONTROL)
        assertContentEquals(FtmsControlPoint.setTargetPower(150), sent.last())

        answer(FtmsControlPoint.OP_SET_TARGET_POWER, FtmsControlPoint.RESULT_CONTROL_NOT_PERMITTED)
        assertEquals(PowerControlStatus.REJECTED, controller.state.status)
        assertEquals(4, sent.size)
    }

    @Test
    fun reportsARefusedTargetAndLetsTheUserTryAnother() {
        connectedBike()
        controller.requestTarget(150)
        answer(FtmsControlPoint.OP_REQUEST_CONTROL)
        answer(FtmsControlPoint.OP_SET_TARGET_POWER, FtmsControlPoint.RESULT_INVALID_PARAMETER)
        assertEquals(PowerControlStatus.REJECTED, controller.state.status)
        assertTrue(controller.state.canSetTarget)

        controller.requestTarget(120)
        answer(FtmsControlPoint.OP_SET_TARGET_POWER)
        assertEquals(PowerControlStatus.ACTIVE, controller.state.status)
        assertEquals(120, controller.state.targetWatts)
    }

    @Test
    fun equipmentThatDoesNotKnowTheCommandBecomesUnsupported() {
        connectedBike()
        controller.requestTarget(150)
        answer(FtmsControlPoint.OP_REQUEST_CONTROL)
        answer(FtmsControlPoint.OP_SET_TARGET_POWER, FtmsControlPoint.RESULT_NOT_SUPPORTED)

        assertEquals(PowerControlStatus.UNSUPPORTED, controller.state.status)
        assertNull(controller.state.targetWatts)
    }

    @Test
    fun reportsRefusedControl() {
        connectedBike()
        controller.requestTarget(150)
        answer(FtmsControlPoint.OP_REQUEST_CONTROL, FtmsControlPoint.RESULT_FAILED)

        assertEquals(PowerControlStatus.REJECTED, controller.state.status)
        assertEquals(1, sent.size)
    }

    @Test
    fun aMissingAnswerFailsOnlyTheCommandItBelongsTo() {
        connectedBike()
        controller.requestTarget(150)
        val firstSequence = sequences.last()
        answer(FtmsControlPoint.OP_REQUEST_CONTROL)

        // The timeout of the answered command fires late and must not disturb the next one.
        controller.onCommandLost(firstSequence)
        assertEquals(PowerControlStatus.SENDING, controller.state.status)

        controller.onCommandLost(sequences.last())
        assertEquals(PowerControlStatus.FAILED, controller.state.status)

        controller.requestTarget(150)
        assertContentEquals(FtmsControlPoint.setTargetPower(150), sent.last())
    }

    @Test
    fun reportsAWriteThatCannotBeStarted() {
        connectedBike()
        writesSucceed = false
        controller.requestTarget(150)

        assertEquals(PowerControlStatus.FAILED, controller.state.status)

        writesSucceed = true
        controller.requestTarget(150)
        assertEquals(PowerControlStatus.SENDING, controller.state.status)
    }

    @Test
    fun reportsAWriteThatFailsWhileItIsBeingStarted() {
        lateinit var failing: TargetPowerController
        failing = TargetPowerController(
            sendCommand = { _, _ ->
                failing.onWriteFailed()
                true
            },
            onStateChanged = {},
        )
        failing.onEquipmentReady(controlPointAvailable = true)
        failing.requestTarget(150)

        assertEquals(PowerControlStatus.FAILED, failing.state.status)
    }

    @Test
    fun ignoresAnswersItDidNotAskFor() {
        connectedBike()
        answer(FtmsControlPoint.OP_SET_TARGET_POWER)
        controller.requestTarget(150)
        answer(FtmsControlPoint.OP_SET_TARGET_POWER)

        assertEquals(PowerControlStatus.SENDING, controller.state.status)
        assertEquals(1, sent.size)
    }

    @Test
    fun setsTheTargetAgainAfterAReconnectWithinASession() {
        connectedBike()
        controller.onSupportedRange(FtmsPowerRange(minWatts = 0, maxWatts = 800, stepWatts = 1))
        controller.requestTarget(150)
        answer(FtmsControlPoint.OP_REQUEST_CONTROL)
        answer(FtmsControlPoint.OP_SET_TARGET_POWER)

        controller.onEquipmentDisconnected(keepTarget = true)
        assertEquals(PowerControlStatus.DISCONNECTED, controller.state.status)
        assertEquals(150, controller.state.targetWatts)
        assertNull(controller.state.range)

        connectedBike()
        assertContentEquals(FtmsControlPoint.requestControl(), sent.last())
        answer(FtmsControlPoint.OP_REQUEST_CONTROL)
        assertContentEquals(FtmsControlPoint.setTargetPower(150), sent.last())
        answer(FtmsControlPoint.OP_SET_TARGET_POWER)
        assertEquals(PowerControlStatus.ACTIVE, controller.state.status)
    }

    @Test
    fun forgetsTheTargetWhenTheConnectionEndsOutsideASession() {
        connectedBike()
        controller.requestTarget(150)
        controller.onEquipmentDisconnected(keepTarget = false)

        assertEquals(PowerControlState(), controller.state)

        connectedBike()
        assertEquals(PowerControlStatus.READY, controller.state.status)
        assertEquals(1, sent.size)
    }

    @Test
    fun clearingTheTargetSendsNothing() {
        connectedBike()
        controller.requestTarget(150)
        answer(FtmsControlPoint.OP_REQUEST_CONTROL)
        answer(FtmsControlPoint.OP_SET_TARGET_POWER)

        controller.clearTarget()

        assertEquals(PowerControlStatus.READY, controller.state.status)
        assertNull(controller.state.targetWatts)
        assertEquals(2, sent.size)
    }

    @Test
    fun reportsEveryStateChangeOnce() {
        connectedBike()
        connectedBike()
        controller.requestTarget(150)

        assertEquals(
            listOf(PowerControlStatus.READY, PowerControlStatus.READY, PowerControlStatus.SENDING),
            states.map { it.status },
        )
        assertEquals(listOf(null, 150, 150), states.map { it.targetWatts })
    }

    @Test
    fun stepsFromTheTargetOrFromTheMeasuredPower() {
        assertEquals(160, steppedTargetPower(currentTarget = 150, measuredWatts = 90, stepWatts = 10, direction = 1))
        assertEquals(140, steppedTargetPower(currentTarget = 150, measuredWatts = 90, stepWatts = 10, direction = -1))
        assertEquals(130, steppedTargetPower(currentTarget = null, measuredWatts = 123, stepWatts = 10, direction = 1))
        assertEquals(100, steppedTargetPower(currentTarget = null, measuredWatts = 123, stepWatts = 25, direction = -1))
        assertEquals(105, steppedTargetPower(currentTarget = null, measuredWatts = null, stepWatts = 5, direction = 1))
        assertEquals(110, steppedTargetPower(currentTarget = null, measuredWatts = 0, stepWatts = 10, direction = 1))
        assertEquals(0, steppedTargetPower(currentTarget = 5, measuredWatts = null, stepWatts = 10, direction = -1))
    }

    @Test
    fun keepsPresetsValidSortedAndLimited() {
        assertEquals(listOf(100, 150, 200), sanitizeTargetPowerPresets(listOf(200, 100, 150, 100, 5, 9000)))
        assertEquals(8, sanitizeTargetPowerPresets((1..20).map { it * 20 }).size)
        assertEquals(DEFAULT_TARGET_POWER_PRESETS, sanitizeTargetPowerPresets(DEFAULT_TARGET_POWER_PRESETS))
    }

    @Test
    fun allowsOnlyNewValidPresetsWhileThereIsRoom() {
        val presets = listOf(100, 150)

        assertTrue(isNewTargetPowerPresetAllowed(180, presets))
        assertTrue(!isNewTargetPowerPresetAllowed(150, presets))
        assertTrue(!isNewTargetPowerPresetAllowed(5, presets))
        assertTrue(!isNewTargetPowerPresetAllowed(2001, presets))
        assertTrue(!isNewTargetPowerPresetAllowed(null, presets))
        assertTrue(!isNewTargetPowerPresetAllowed(999, (1..8).map { it * 20 }))
    }
}
