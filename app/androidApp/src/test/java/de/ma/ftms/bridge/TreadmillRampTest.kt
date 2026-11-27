package de.ma.ftms.bridge

import de.ma.ftms.bridge.runtime.TreadmillController
import de.ma.ftms.bridge.runtime.TreadmillRampLimits
import de.ma.ftms.bridge.runtime.TreadmillRampProfile
import de.ma.ftms.bridge.runtime.TreadmillSetting
import de.ma.ftms.bridge.runtime.TreadmillTargetStatus
import de.ma.ftms.bridge.runtime.TreadmillTransition
import de.ma.ftms.bridge.runtime.advanceTreadmillRamp
import de.ma.ftms.bridge.runtime.limits
import de.ma.ftms.bridge.runtime.toTreadmillRampProfile
import de.ma.ftms.bridge.runtime.treadmillTransitionSeconds
import de.ma.ftms.core.FtmsControlPoint
import de.ma.ftms.core.FtmsControlResponse
import de.ma.ftms.core.FtmsTargetSettings
import de.ma.ftms.core.FtmsValueRange
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The transition to a speed that is some way off, tried on a belt that behaves like the Darwin
 * TM 70: it reports twice a second and goes to what it is told at 0.64 km/h per second.
 */
class TreadmillRampTest {
    private var profile = TreadmillRampProfile.GENTLE
    private var now = 10_000L

    /** Every speed the treadmill was told, with the time. */
    private val told = mutableListOf<Pair<Long, Int>>()
    private val unanswered = ArrayDeque<Int>()
    private var lastSequence = 0
    private var answers = true
    private var result = FtmsControlPoint.RESULT_SUCCESS

    private var belt = 600.0
    private var beltTarget = 600
    private var beltFollows = true

    private val controller = TreadmillController(
        sendCommand = { bytes, sequence ->
            lastSequence = sequence
            unanswered += bytes[0].toInt()
            if (bytes[0].toInt() == FtmsControlPoint.OP_SET_TARGET_SPEED) {
                val value = (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)
                told += now to value
                beltTarget = value
            }
            true
        },
        onStateChanged = {},
        rampProfile = { profile },
    )

    private val speed get() = controller.state.speed
    private val values get() = told.map { it.second }

    private fun answerAll() {
        while (answers && unanswered.isNotEmpty()) {
            controller.onResponse(FtmsControlResponse(requestOpCode = unanswered.removeFirst(), resultCode = result), now)
        }
    }

    /** Half a second passes: the belt moves, reports, and the treadmill answers what it was told. */
    private fun tick(count: Int = 1) {
        repeat(count) {
            now += 500
            if (beltFollows) {
                val step = (beltTarget - belt).coerceIn(-32.0, 32.0)
                belt += step
            }
            controller.onMeasured(belt.roundToInt(), null, now)
            answerAll()
        }
    }

    private fun connect() {
        controller.onEquipmentReady(controlPointAvailable = true)
        controller.onTargetSettings(FtmsTargetSettings(speed = true, inclination = true))
        controller.onSupportedRange(TreadmillSetting.SPEED, FtmsValueRange(min = 80, max = 1800, step = 10))
        controller.onSupportedRange(TreadmillSetting.INCLINE, FtmsValueRange(min = 0, max = 150, step = 10))
    }

    private fun runningTreadmill(speedHundredths: Int = 600) {
        belt = speedHundredths.toDouble()
        beltTarget = speedHundredths
        connect()
        tick()
    }

    private fun ask(value: Int) {
        assertTrue(controller.request(TreadmillSetting.SPEED, value, now))
        answerAll()
    }

    /** Lets time pass until the transition is over; returns how long that took. */
    private fun untilLed(): Long {
        val start = now
        var ticks = 0
        while (speed.ramping) {
            tick()
            ticks += 1
            assertTrue(ticks < 200, "the transition does not end")
        }
        return now - start
    }

    @Test
    fun aSpeedSomeWayOffIsLedToInSmallValues() {
        runningTreadmill(600)

        ask(920)

        // The belt answers the tap at once, with the smallest step.
        assertEquals(listOf(610), values)
        assertEquals(920, speed.target)
        assertTrue(speed.ramping)

        val millis = untilLed()

        assertEquals(920, values.last())
        assertEquals(920, speed.target)
        assertEquals(TreadmillTargetStatus.ACCEPTED, speed.status)
        val changes = values.zipWithNext { a, b -> b - a }
        assertTrue(changes.all { it > 0 }, "rises all the way: $values")
        assertTrue(values.all { it % 10 == 0 }, "only values the treadmill takes: $values")
        // 0.4 km/h per second at most, sent twice a second.
        assertTrue(changes.all { it <= 30 }, "no jump: $changes")
        // It starts softly and lands softly.
        assertTrue(changes.take(2).all { it <= 10 }, "soft start: $changes")
        assertTrue(changes.takeLast(2).all { it <= 10 }, "soft landing: $changes")
        assertTrue(changes.max() >= 20, "picks up pace in between: $changes")
        assertTrue(millis in 8_000..13_000, "took $millis ms")
    }

    @Test
    fun theBeltIsNeverToldMoreThanItCanFollow() {
        runningTreadmill(600)
        ask(1200)

        while (speed.ramping) {
            tick()
            assertTrue(abs(values.last() - belt) <= 90, "told ${values.last()} at $belt")
        }

        assertEquals(1200, values.last())
    }

    @Test
    fun slowingDownIsQuickerThanSpeedingUp() {
        runningTreadmill(600)
        ask(920)
        val up = untilLed()
        tick(10)
        assertEquals(920, belt.roundToInt())

        ask(600)
        val down = untilLed()

        assertEquals(600, values.last())
        assertTrue(down < up, "down $down ms, up $up ms")
        val changes = values.dropWhile { it != 920 }.zipWithNext { a, b -> b - a }
        assertTrue(changes.all { it < 0 }, "falls all the way: $changes")
        assertTrue(changes.takeLast(2).all { it >= -10 }, "soft landing: $changes")
    }

    @Test
    fun veryGentleTakesLonger() {
        runningTreadmill(600)
        ask(920)
        val gentle = untilLed()
        tick(10)
        ask(600)
        untilLed()
        tick(10)

        profile = TreadmillRampProfile.VERY_GENTLE
        ask(920)
        val veryGentle = untilLed()

        assertTrue(veryGentle > gentle + 2_000, "gentle $gentle ms, very gentle $veryGentle ms")
    }

    @Test
    fun aSmallStepIsSentAsItIs() {
        runningTreadmill(600)

        ask(650)

        assertEquals(listOf(650), values)
        assertFalse(speed.ramping)
        tick(10)
        assertEquals(listOf(650), values)
    }

    @Test
    fun directSendsTheTargetInOnePiece() {
        profile = TreadmillRampProfile.DIRECT
        runningTreadmill(600)

        ask(920)

        assertEquals(listOf(920), values)
        assertFalse(speed.ramping)
    }

    @Test
    fun theInclineIsNeverLed() {
        runningTreadmill(600)

        assertTrue(controller.request(TreadmillSetting.INCLINE, 50, now))

        assertFalse(controller.state.incline.ramping)
        assertFalse(speed.ramping)
    }

    @Test
    fun aBeltThatDoesNotFollowIsWaitedForAndThenGivenUp() {
        runningTreadmill(600)
        beltFollows = false
        ask(920)

        tick(10)

        // It stops where the belt would be left 0.6 km/h behind.
        val waitingAt = values.last()
        assertTrue(waitingAt <= 680, "told $values")
        assertEquals(920, speed.target)
        tick(6)
        assertEquals(waitingAt, values.last())

        tick(20)

        assertNull(speed.target)
        assertFalse(speed.ramping)
        assertEquals(TreadmillTargetStatus.IDLE, speed.status)
        val count = told.size
        beltFollows = true
        tick(20)
        assertEquals(count, told.size)
    }

    @Test
    fun aBeltThatCatchesUpIsLedOn() {
        runningTreadmill(600)
        beltFollows = false
        ask(920)
        tick(10)
        val waitingAt = values.last()

        beltFollows = true
        untilLed()

        assertTrue(values.last() > waitingAt)
        assertEquals(920, values.last())
    }

    @Test
    fun aBeltMovedOnTheConsoleEndsTheTransition() {
        runningTreadmill(600)
        ask(920)
        tick(6)
        assertTrue(speed.ramping)
        val count = told.size

        // Someone slows the belt down on the console, to below where the transition began.
        beltFollows = false
        belt = 450.0
        tick()

        assertNull(speed.target)
        assertFalse(speed.ramping)
        tick(20)
        assertEquals(count, told.size)
        // The next step starts from what the console set.
        assertTrue(controller.step(TreadmillSetting.SPEED, 10, now))
        assertEquals(460, values.last())
    }

    @Test
    fun aNewTargetCarriesTheTransitionOn() {
        runningTreadmill(600)
        ask(920)
        tick(8)
        val at = values.last()
        assertTrue(at in 650..900, "at $at")

        ask(1000)

        // No jump and no new start from the belt's speed: it goes on from the value last sent.
        assertEquals(at + 10, values.last())
        assertEquals(1000, speed.target)
        untilLed()
        assertEquals(1000, values.last())
        assertTrue(values.zipWithNext { a, b -> b - a }.all { it in 1..30 }, "$values")
    }

    @Test
    fun aLowerTargetTurnsTheTransitionAround() {
        runningTreadmill(600)
        ask(920)
        tick(8)
        val at = values.last()

        ask(500)

        assertEquals(at - 10, values.last())
        untilLed()
        assertEquals(500, values.last())
        assertTrue(values.dropWhile { it != at }.zipWithNext { a, b -> b - a }.all { it in -40..-1 }, "$values")
    }

    @Test
    fun aDisconnectEndsTheTransitionForGood() {
        runningTreadmill(600)
        ask(920)
        tick(6)
        val count = told.size

        controller.onEquipmentDisconnected()
        unanswered.clear()
        connect()
        tick(30)

        assertEquals(count, told.size)
        assertNull(speed.target)
        assertFalse(speed.ramping)
    }

    @Test
    fun aStoppedBeltEndsTheTransition() {
        runningTreadmill(600)
        ask(920)
        tick(6)
        val count = told.size

        beltFollows = false
        belt = 0.0
        tick()
        belt = 300.0
        tick(20)

        assertEquals(count, told.size)
        assertNull(speed.target)
        assertFalse(speed.ramping)
    }

    @Test
    fun aSilentTreadmillEndsTheTransition() {
        runningTreadmill(600)
        ask(920)
        tick(6)
        val count = told.size

        // Six seconds without a packet, then the treadmill is back.
        now += 6_000
        tick(20)

        assertEquals(count, told.size)
        assertNull(speed.target)
        assertFalse(speed.ramping)
    }

    @Test
    fun aRefusedValueEndsTheTransition() {
        runningTreadmill(600)
        ask(920)
        tick(4)
        assertTrue(speed.ramping)

        result = FtmsControlPoint.RESULT_FAILED
        tick(4)
        val count = told.size

        assertNull(speed.target)
        assertFalse(speed.ramping)
        assertEquals(TreadmillTargetStatus.REJECTED, speed.status)
        result = FtmsControlPoint.RESULT_SUCCESS
        tick(20)
        assertEquals(count, told.size)
    }

    @Test
    fun anUnansweredValueEndsTheTransition() {
        runningTreadmill(600)
        ask(920)
        tick(4)

        answers = false
        tick(4)
        val count = told.size
        // Nothing follows a value that is still on its way.
        tick(4)
        assertEquals(count, told.size)
        controller.onCommandLost(lastSequence)

        assertNull(speed.target)
        assertFalse(speed.ramping)
        assertEquals(TreadmillTargetStatus.FAILED, speed.status)
        answers = true
        unanswered.clear()
        tick(20)
        assertEquals(count, told.size)
    }

    @Test
    fun theTargetIsWatchedLikeAnyOtherOnceTheTransitionIsOver() {
        runningTreadmill(600)
        ask(920)
        untilLed()
        tick(6)
        assertEquals(920, belt.roundToInt())
        assertEquals(920, speed.target)

        beltFollows = false
        belt = 700.0
        tick()

        assertNull(speed.target)
    }

    @Test
    fun aRequestCanNameItsOwnTransition() {
        runningTreadmill(600)

        assertTrue(controller.request(TreadmillSetting.SPEED, 920, now, TreadmillTransition.Direct))
        answerAll()

        assertEquals(listOf(920), values)
        assertFalse(speed.ramping)
    }

    @Test
    fun aTransitionWithinAGivenTimeTakesAboutThatLong() {
        runningTreadmill(600)

        assertTrue(controller.request(TreadmillSetting.SPEED, 920, now, TreadmillTransition.Seconds(20)))
        answerAll()
        val millis = untilLed()

        assertEquals(920, values.last())
        assertTrue(millis in 17_000..23_000, "took $millis ms")
        assertTrue(values.zipWithNext { a, b -> b - a }.all { it in 1..20 }, "$values")
    }

    @Test
    fun theTimeATransitionTakesIsKnownBeforehand() {
        val gentle = TreadmillTransition.Profile(TreadmillRampProfile.GENTLE)
        runningTreadmill(600)
        ask(920)
        val millis = untilLed()

        val estimate = treadmillTransitionSeconds(600, 920, gentle)

        assertTrue(abs(estimate * 1000 - millis) <= 2_000, "estimated $estimate s, took $millis ms")
        // In one piece the belt needs what it needs: 3.2 km/h at 0.6 km/h per second.
        assertEquals(320 / 60.0, treadmillTransitionSeconds(600, 920, TreadmillTransition.Direct))
        assertEquals(0.5, treadmillTransitionSeconds(600, 630, gentle))
        // A time shorter than the belt manages is not believed.
        assertEquals(320 / 60.0, treadmillTransitionSeconds(600, 920, TreadmillTransition.Seconds(2)))
        assertTrue(treadmillTransitionSeconds(920, 600, gentle) < estimate)
    }

    @Test
    fun thePaceBuildsUpHoldsAndFadesOut() {
        val limits = TreadmillRampLimits(maxRate = 40.0, acceleration = 20.0)
        var position = 600.0
        var rate = 0.0
        val rates = mutableListOf<Double>()
        var steps = 0
        while (position < 920.0) {
            val motion = advanceTreadmillRamp(position, rate, target = 920, limits = limits, seconds = 0.5)
            assertTrue(motion.position > position && motion.position <= 920.0)
            assertTrue(motion.rate <= limits.maxRate)
            assertTrue(motion.rate - rate <= limits.acceleration * 0.5 + 1e-9, "builds up no faster than allowed")
            position = motion.position
            rate = motion.rate
            rates += rate
            steps += 1
            assertTrue(steps < 100)
        }

        assertEquals(920.0, position)
        assertEquals(0.0, rate)
        assertEquals(10.0, rates.first())
        assertEquals(40.0, rates.max())
        // Before it arrives it has slowed to a quarter of its pace.
        assertTrue(rates[rates.size - 2] <= 10.0, "$rates")
    }

    @Test
    fun aTransitionAtItsTargetStaysThere() {
        val limits = TreadmillRampLimits(maxRate = 40.0, acceleration = 20.0)

        val motion = advanceTreadmillRamp(position = 920.0, rate = 30.0, target = 920, limits = limits, seconds = 0.5)

        assertEquals(920.0, motion.position)
        assertEquals(0.0, motion.rate)
    }

    @Test
    fun everyProfileSlowsDownMoreBrisklyThanItSpeedsUp() {
        assertNull(TreadmillRampProfile.DIRECT.limits(faster = true))
        assertNull(TreadmillRampProfile.DIRECT.limits(faster = false))
        listOf(TreadmillRampProfile.GENTLE, TreadmillRampProfile.VERY_GENTLE).forEach { profile ->
            val up = profile.limits(faster = true)!!
            val down = profile.limits(faster = false)!!
            assertTrue(down.maxRate > up.maxRate && down.acceleration > up.acceleration)
            // No faster than the 0.64 km/h per second a treadmill was seen to manage.
            assertTrue(down.maxRate <= 64.0)
        }
    }

    @Test
    fun anUnknownStoredProfileFallsBackToGentle() {
        assertEquals(TreadmillRampProfile.DIRECT, "DIRECT".toTreadmillRampProfile())
        assertEquals(TreadmillRampProfile.VERY_GENTLE, "VERY_GENTLE".toTreadmillRampProfile())
        assertEquals(TreadmillRampProfile.GENTLE, null.toTreadmillRampProfile())
        assertEquals(TreadmillRampProfile.GENTLE, "something".toTreadmillRampProfile())
    }
}
