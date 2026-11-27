package de.ma.ftms.bridge

import de.ma.ftms.bridge.runtime.BridgeSettings
import de.ma.ftms.bridge.runtime.EquipmentType
import de.ma.ftms.bridge.runtime.TreadmillControlAvailability
import de.ma.ftms.bridge.runtime.TreadmillControlState
import de.ma.ftms.bridge.runtime.TreadmillRampProfile
import de.ma.ftms.bridge.runtime.TreadmillTargetState
import de.ma.ftms.bridge.runtime.TreadmillTransition
import de.ma.ftms.bridge.runtime.WatchWorkout
import de.ma.ftms.bridge.runtime.WatchWorkoutStep
import de.ma.ftms.bridge.runtime.WorkoutFollowState
import de.ma.ftms.bridge.runtime.WorkoutFollower
import de.ma.ftms.bridge.runtime.WorkoutStepKind
import de.ma.ftms.bridge.runtime.WorkoutTransitionMode
import de.ma.ftms.bridge.runtime.isWorkoutFollowed
import de.ma.ftms.bridge.runtime.toWorkoutTransitionMode
import de.ma.ftms.bridge.runtime.watchWorkoutFrom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Following a workout of 9.2 km/h runs and 6 km/h recoveries, as the watch reports it. */
class WorkoutFollowerTest {
    private data class Sent(val atSeconds: Double, val speed: Int, val transition: TreadmillTransition, val ahead: Boolean)

    private var settings = BridgeSettings(treadmillControl = true, treadmillFollowWorkout = true)
    private val sent = mutableListOf<Sent>()
    private var now = 0L
    private var stepBeganAt = 0L
    private var belt = 600
    private var target: Int? = null
    private var enabled = true
    private var sending = true
    private var recording = true

    private val follower = WorkoutFollower(
        settings = { settings },
        setSpeed = { speed, transition, ahead ->
            sent += Sent((now - stepBeganAt) / 1000.0, speed, transition, ahead)
            target = speed
            belt = speed
        },
        onStateChanged = {},
    )

    private val gentle = TreadmillTransition.Profile(TreadmillRampProfile.GENTLE)
    private val run = WatchWorkoutStep(speed = 920, seconds = 120, kind = WorkoutStepKind.RUN)
    private val recovery = WatchWorkoutStep(speed = 600, seconds = 60, kind = WorkoutStepKind.RECOVERY)
    private var stepsDone = 0

    private fun control() = TreadmillControlState(
        availability = TreadmillControlAvailability.READY,
        beltMoving = belt > 0,
        speed = TreadmillTargetState(measured = belt, target = target),
    )

    /** The watch enters [current]; [next] follows. */
    private fun watchEnters(current: WatchWorkoutStep?, next: WatchWorkoutStep?) {
        stepsDone += 1
        stepBeganAt = now
        follower.onWorkout(WatchWorkout(started = 1, stepsDone = stepsDone, current = current, next = next), now)
    }

    /** The treadmill reports twice a second for [seconds]. */
    private fun pass(seconds: Int) {
        repeat(seconds * 2) {
            now += 500
            follower.tick(now, enabled, sending, recording, control())
        }
    }

    @Test
    fun theSpeedOfAStepIsSentOnceWhenTheWatchEntersIt() {
        pass(1)
        watchEnters(run, next = null)

        pass(120)

        assertEquals(listOf(Sent(0.5, 920, gentle, ahead = false)), sent)
    }

    @Test
    fun aBeltThatRunsAtTheSpeedOfTheStepIsToldNothing() {
        belt = 920
        pass(1)
        watchEnters(run, next = null)

        pass(10)

        assertTrue(sent.isEmpty())
    }

    @Test
    fun aStepWithoutAPaceLeavesTheBeltAlone() {
        pass(1)
        watchEnters(WatchWorkoutStep(speed = null, seconds = null), next = null)

        pass(60)

        assertTrue(sent.isEmpty())
        // No countdown for a step that ends with the lap key, but the time it has lasted.
        assertEquals(WorkoutFollowState(active = true, sending = true, secondsInStep = 60, stepNumber = 2), follower.state)
    }

    @Test
    fun aFasterStepIsPreparedSoThatTheBeltIsThereWhenItBegins() {
        pass(1)
        watchEnters(recovery, next = run)
        pass(60)

        // The gentle way from 6 to 9.2 km/h takes about ten seconds; the message is taken to be late.
        assertEquals(1, sent.size)
        val ahead = sent.single()
        assertEquals(920, ahead.speed)
        assertTrue(ahead.ahead)
        assertEquals(gentle, ahead.transition)
        assertTrue(ahead.atSeconds in 46.0..50.0, "sent after ${ahead.atSeconds} s")

        // The watch then reports the step: its speed is on its way already.
        watchEnters(run, next = recovery)
        pass(30)
        assertEquals(1, sent.size)
    }

    @Test
    fun theBeltSlowsDownOnlyWhenTheSlowerStepHasBegun() {
        belt = 920
        pass(1)
        watchEnters(run, next = recovery)
        pass(125)
        assertTrue(sent.isEmpty())

        watchEnters(recovery, next = run)
        pass(1)

        assertEquals(Sent(0.5, 600, gentle, ahead = false), sent.last())
    }

    @Test
    fun aSpeedSetByHandStaysUntilTheNextStep() {
        pass(1)
        watchEnters(run, next = recovery)
        pass(10)

        // Someone taps a lower speed.
        target = 800
        belt = 800
        pass(100)

        assertEquals(1, sent.size)
    }

    @Test
    fun smartLeavesAChangeInAShortStepToTheTreadmill() {
        val short = recovery.copy(seconds = 45)
        belt = 920
        pass(1)
        watchEnters(short, next = run)
        pass(45)

        assertEquals(2, sent.size)
        assertEquals(Sent(0.5, 600, TreadmillTransition.Direct, ahead = false), sent[0])
        assertEquals(TreadmillTransition.Direct, sent[1].transition)
        // The belt itself needs a good five seconds from 6 to 9.2 km/h.
        assertTrue(sent[1].atSeconds in 37.0..39.5, "sent after ${sent[1].atSeconds} s")
    }

    @Test
    fun whatCountsAsShortCanBeSet() {
        settings = settings.copy(treadmillFollowShortStepSeconds = 90)
        belt = 920
        pass(1)
        watchEnters(recovery, next = run)
        pass(2)
        assertEquals(TreadmillTransition.Direct, sent.single().transition)

        settings = settings.copy(treadmillFollowShortStepSeconds = 0)
        watchEnters(recovery.copy(seconds = 20, speed = 500), next = null)
        pass(2)
        assertEquals(gentle, sent.last().transition)
    }

    @Test
    fun gentleLeadsEveryChangeAndPreparesNothing() {
        settings = settings.copy(treadmillFollowMode = WorkoutTransitionMode.GENTLE)
        belt = 920
        pass(1)
        watchEnters(recovery.copy(seconds = 30), next = run)
        pass(30)

        assertEquals(listOf(Sent(0.5, 600, gentle, ahead = false)), sent)
    }

    @Test
    fun offSendsTheSpeedInOnePieceWhenTheStepBegins() {
        settings = settings.copy(treadmillFollowMode = WorkoutTransitionMode.OFF)
        belt = 920
        pass(1)
        watchEnters(recovery, next = run)
        pass(60)
        watchEnters(run, next = recovery)
        pass(1)

        assertEquals(
            listOf(
                Sent(0.5, 600, TreadmillTransition.Direct, ahead = false),
                Sent(0.5, 920, TreadmillTransition.Direct, ahead = false),
            ),
            sent,
        )
    }

    @Test
    fun aGentleChangeCanBeGivenItsTime() {
        settings = settings.copy(treadmillFollowTransitionSeconds = 20)
        pass(1)
        watchEnters(recovery, next = run)
        pass(60)

        val ahead = sent.single()
        assertEquals(TreadmillTransition.Seconds(20), ahead.transition)
        assertTrue(ahead.atSeconds in 35.0..40.0, "sent after ${ahead.atSeconds} s")
    }

    @Test
    fun preparingNeverTakesMoreThanHalfOfAStep() {
        settings = settings.copy(treadmillFollowTransitionSeconds = 30, treadmillFollowShortStepSeconds = 0)
        pass(1)
        watchEnters(recovery.copy(seconds = 40), next = run)
        pass(40)

        assertTrue(sent.single().atSeconds >= 20.0, "sent after ${sent.single().atSeconds} s")
    }

    @Test
    fun gentleStaysGentleWhenTheTreadmillControlIsSetToDirect() {
        settings = settings.copy(treadmillRamp = TreadmillRampProfile.DIRECT)
        pass(1)
        watchEnters(run, next = null)
        pass(1)

        assertEquals(gentle, sent.single().transition)
    }

    @Test
    fun aWorkoutNeverAsksForMoreThanTheLimit() {
        pass(1)
        watchEnters(WatchWorkoutStep(speed = 1500, seconds = 120), next = WatchWorkoutStep(speed = 1700, seconds = 60))
        pass(2)

        assertEquals(1200, sent.single().speed)
        assertEquals(1200, follower.state.currentSpeed)
        assertEquals(1200, follower.state.nextSpeed)
    }

    @Test
    fun nothingIsSentWhileTheActivityDoesNotRecord() {
        recording = false
        pass(1)
        watchEnters(recovery, next = run)
        pass(120)
        assertTrue(sent.isEmpty())
        assertEquals(60, follower.state.secondsLeft)

        // Recording again, the step has all its time left.
        recording = true
        belt = 700
        pass(40)
        assertEquals(listOf(600), sent.map { it.speed })
        pass(20)
        assertEquals(listOf(600, 920), sent.map { it.speed })
    }

    @Test
    fun switchedOnInTheMiddleOfAStepItStartsWithTheNext() {
        sending = false
        pass(1)
        watchEnters(run, next = recovery)
        pass(10)
        // The workout is shown, so that it can be checked before the belt follows it.
        assertEquals(
            WorkoutFollowState(
                active = true,
                currentSpeed = 920,
                nextSpeed = 600,
                secondsLeft = 110,
                secondsInStep = 10,
                stepNumber = 2,
                currentKind = WorkoutStepKind.RUN,
                stepSeconds = 120,
                hasNext = true,
                nextKind = WorkoutStepKind.RECOVERY,
                nextSeconds = 60,
            ),
            follower.state,
        )

        sending = true
        pass(100)
        assertTrue(sent.isEmpty())

        belt = 920
        watchEnters(recovery, next = run)
        pass(1)
        assertEquals(listOf(600), sent.map { it.speed })
    }

    @Test
    fun aBeltThatStandsWhenAStepBeginsIsNotGivenItsSpeedLater() {
        belt = 0
        pass(1)
        watchEnters(run, next = null)
        pass(5)

        belt = 500
        pass(60)

        assertTrue(sent.isEmpty())
    }

    @Test
    fun withoutASessionOnATreadmillTheWorkoutIsOfNoUse() {
        enabled = false
        pass(1)
        watchEnters(run, next = recovery)
        pass(10)

        assertEquals(WorkoutFollowState(), follower.state)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun aStepThatEndsWithTheLapKeyCannotBePrepared() {
        pass(1)
        watchEnters(WatchWorkoutStep(speed = null, seconds = null), next = run)
        pass(300)

        assertTrue(sent.isEmpty())
    }

    @Test
    fun theDashboardIsToldWhatComesAndWhen() {
        pass(1)
        watchEnters(recovery, next = run)
        pass(20)

        assertEquals(
            WorkoutFollowState(
                active = true,
                sending = true,
                currentSpeed = 600,
                nextSpeed = 920,
                secondsLeft = 40,
                secondsInStep = 20,
                stepNumber = 2,
                currentKind = WorkoutStepKind.RECOVERY,
                stepSeconds = 60,
                hasNext = true,
                nextKind = WorkoutStepKind.RUN,
                nextSeconds = 120,
            ),
            follower.state,
        )

        follower.reset()
        assertEquals(WorkoutFollowState(), follower.state)
        pass(60)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun theEndOfTheWorkoutEndsTheFollowing() {
        pass(1)
        watchEnters(run, next = null)
        pass(2)

        follower.onWorkout(WatchWorkout(started = 1, stepsDone = 9, current = null, next = null), now)
        pass(2)

        assertEquals(WorkoutFollowState(), follower.state)
        assertEquals(1, sent.size)
    }

    @Test
    fun theWatchSaysHowLongTheStepHasLasted() {
        pass(1)
        // The app joins 25 seconds into the recovery, as the watch's activity timer counts it.
        follower.onWorkout(
            WatchWorkout(started = 1, stepsDone = 4, current = recovery, next = run, stepElapsedMillis = 25_000),
            now,
        )
        stepBeganAt = now - 25_000
        pass(5)

        assertEquals(30, follower.state.secondsInStep)
        assertEquals(30, follower.state.secondsLeft)
        assertTrue(follower.state.timeFromWatch)

        // A repeated message corrects what the app counted in between, e.g. after a gap.
        now += 8_000
        follower.onWorkout(
            WatchWorkout(started = 1, stepsDone = 4, current = recovery, next = run, stepElapsedMillis = 38_000),
            now,
        )
        pass(1)
        assertEquals(39, follower.state.secondsInStep)
        assertTrue(sent.isEmpty())

        // With the watch's time the faster step is prepared on the second: about 10.5 s before it begins.
        pass(21)
        assertEquals(1, sent.size)
        assertTrue(sent.last().ahead)
        assertTrue(sent.last().atSeconds in 48.5..50.5, "sent after ${sent.last().atSeconds} s")
    }

    @Test
    fun withoutATimeFromTheWatchTheAppCountsItself() {
        pass(1)
        watchEnters(recovery, next = run)
        pass(10)

        assertFalse(follower.state.timeFromWatch)
        assertEquals(10, follower.state.secondsInStep)
    }

    @Test
    fun readsAPaceInMetresPerSecondAndATimeInSeconds() {
        // 6:30 to 6:32 per km, then 9:59 to 10:01 per km.
        val workout = watchWorkoutFrom(
            mapOf(
                "type" to "garmin_workout",
                "started" to 1,
                "done" to 3,
                "el" to 41_250,
                "cur" to mapOf("int" to 0, "step" to mapOf("dt" to 0, "dv" to 120, "tt" to 0, "lo" to 2.5510f, "hi" to 2.5641f)),
                "next" to mapOf("int" to 4, "step" to mapOf("dt" to 0, "dv" to 60, "tt" to 0, "lo" to 1.6639f, "hi" to 1.6694f)),
            ),
        )

        assertEquals(
            WatchWorkout(
                started = 1,
                stepsDone = 3,
                current = WatchWorkoutStep(921, 120, WorkoutStepKind.RUN),
                next = WatchWorkoutStep(600, 60, WorkoutStepKind.RECOVERY),
                stepElapsedMillis = 41_250,
            ),
            workout,
        )
    }

    @Test
    fun readsAPaceInMillimetresPerSecondToo() {
        val workout = watchWorkoutFrom(
            mapOf("started" to 1, "done" to 0, "cur" to mapOf("step" to mapOf("dt" to 0, "dv" to 120, "tt" to 0, "lo" to 2551, "hi" to 2564))),
        )

        assertEquals(WatchWorkoutStep(921, 120), workout.current)
        assertNull(workout.next)
    }

    @Test
    fun aStepWithoutPaceOrTimeIsReadAsSuch() {
        // Warm-up until the lap key, no target; then a heart rate target.
        val open = watchWorkoutFrom(mapOf("started" to 1, "cur" to mapOf("int" to 2, "step" to mapOf("dt" to 5, "tt" to 2))))
        val heartRate = watchWorkoutFrom(
            mapOf("started" to 1, "cur" to mapOf("step" to mapOf("dt" to 0, "dv" to 300, "tt" to 1, "lo" to 140, "hi" to 150))),
        )

        assertEquals(
            WatchWorkoutStep(speed = null, seconds = null, kind = WorkoutStepKind.WARM_UP, endsWithLapKey = true),
            open.current,
        )
        assertEquals(WatchWorkoutStep(speed = null, seconds = 300), heartRate.current)
    }

    @Test
    fun whatDoesNotFitIsNotBelieved() {
        // A speed no one runs, and a time that would be 33 hours.
        val workout = watchWorkoutFrom(
            mapOf("started" to 1, "cur" to mapOf("step" to mapOf("dt" to 0, "dv" to 120_000, "tt" to 0, "lo" to 50, "hi" to 60))),
        )

        assertEquals(WatchWorkoutStep(speed = null, seconds = null), workout.current)
        assertEquals(WatchWorkout(started = 0, stepsDone = 0, current = null, next = null), watchWorkoutFrom(emptyMap<String, Any>()))
    }

    @Test
    fun aStepOfAnIntervalSetIsReadByItsIntensity() {
        val active = mapOf("dt" to 0, "dv" to 120, "tt" to 0, "lo" to 2.5510, "hi" to 2.5641)
        val rest = mapOf("dt" to 0, "dv" to 60, "tt" to 0, "lo" to 1.6639, "hi" to 1.6694)

        val running = watchWorkoutFrom(mapOf("started" to 1, "cur" to mapOf("int" to 0, "rep" to 1, "active" to active, "rest" to rest)))
        val resting = watchWorkoutFrom(mapOf("started" to 1, "cur" to mapOf("int" to 1, "rep" to 1, "active" to active, "rest" to rest)))

        assertEquals(WatchWorkoutStep(921, 120, WorkoutStepKind.RUN), running.current)
        assertEquals(WatchWorkoutStep(600, 60, WorkoutStepKind.RECOVERY), resting.current)
    }

    @Test
    fun theIntensityNamesTheStep() {
        fun kindOf(intensity: Int?) = watchWorkoutFrom(
            mapOf("started" to 1, "cur" to mapOf("int" to intensity, "step" to mapOf("dt" to 0, "dv" to 60))),
        ).current?.kind

        assertEquals(WorkoutStepKind.RUN, kindOf(0))
        assertEquals(WorkoutStepKind.RECOVERY, kindOf(1))
        assertEquals(WorkoutStepKind.WARM_UP, kindOf(2))
        assertEquals(WorkoutStepKind.COOL_DOWN, kindOf(3))
        assertEquals(WorkoutStepKind.RECOVERY, kindOf(4))
        assertEquals(WorkoutStepKind.RUN, kindOf(5))
        // An intensity without a name here, and a watch that gives none.
        assertEquals(WorkoutStepKind.OTHER, kindOf(6))
        assertEquals(WorkoutStepKind.OTHER, kindOf(null))
    }

    @Test
    fun theScreenIsToldThatTheBeltIsAlreadyOnItsWayToTheNextStep() {
        pass(1)
        watchEnters(recovery, next = run)
        pass(20)
        assertFalse(follower.state.speedingUpAhead)

        pass(39)
        assertEquals(listOf(920), sent.map { it.speed })
        assertTrue(follower.state.speedingUpAhead)

        watchEnters(run, next = recovery)
        pass(1)
        assertFalse(follower.state.speedingUpAhead)
        assertEquals(3, follower.state.stepNumber)
    }

    @Test
    fun aStepStandsStillWhileTheWatchDoesNotRecord() {
        pass(1)
        watchEnters(run, next = recovery)
        pass(30)
        assertFalse(follower.state.paused)

        recording = false
        pass(30)

        assertTrue(follower.state.paused)
        assertEquals(90, follower.state.secondsLeft)
    }

    @Test
    fun aStepUntilTheLapKeyIsToldApartFromOneThatEndsAnotherWay() {
        pass(1)
        watchEnters(WatchWorkoutStep(speed = null, seconds = null, kind = WorkoutStepKind.WARM_UP, endsWithLapKey = true), next = run)
        pass(1)
        assertTrue(follower.state.endsWithLapKey)
        assertNull(follower.state.stepSeconds)

        watchEnters(WatchWorkoutStep(speed = 920, seconds = null), next = null)
        pass(1)
        assertFalse(follower.state.endsWithLapKey)
        assertFalse(follower.state.hasNext)
    }

    @Test
    fun followingNeedsTheSwitchTheTreadmillControlAndATreadmill() {
        val on = BridgeSettings(treadmillControl = true, treadmillFollowWorkout = true)

        assertTrue(isWorkoutFollowed(on, EquipmentType.TREADMILL))
        assertFalse(isWorkoutFollowed(on, EquipmentType.INDOOR_BIKE))
        assertFalse(isWorkoutFollowed(on.copy(treadmillControl = false), EquipmentType.TREADMILL))
        assertFalse(isWorkoutFollowed(BridgeSettings(treadmillControl = true), EquipmentType.TREADMILL))
    }

    @Test
    fun anUnknownStoredModeFallsBackToSmart() {
        assertEquals(WorkoutTransitionMode.OFF, "OFF".toWorkoutTransitionMode())
        assertEquals(WorkoutTransitionMode.GENTLE, "GENTLE".toWorkoutTransitionMode())
        assertEquals(WorkoutTransitionMode.SMART, null.toWorkoutTransitionMode())
        assertEquals(WorkoutTransitionMode.SMART, "something".toWorkoutTransitionMode())
    }
}
