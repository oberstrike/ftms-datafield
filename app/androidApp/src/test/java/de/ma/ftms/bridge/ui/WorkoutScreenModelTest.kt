package de.ma.ftms.bridge.ui

import de.ma.ftms.bridge.i18n.DeStrings
import de.ma.ftms.bridge.i18n.EnStrings
import de.ma.ftms.bridge.runtime.DashboardMetricKey
import de.ma.ftms.bridge.runtime.EquipmentType
import de.ma.ftms.bridge.runtime.TreadmillControlAvailability
import de.ma.ftms.bridge.runtime.TreadmillControlState
import de.ma.ftms.bridge.runtime.TreadmillSetting
import de.ma.ftms.bridge.runtime.TreadmillTargetState
import de.ma.ftms.bridge.runtime.WORKOUT_SCREEN_FIELD_OPTIONS
import de.ma.ftms.bridge.runtime.WorkoutFollowState
import de.ma.ftms.bridge.runtime.WorkoutStepKind
import de.ma.ftms.core.DistanceSource
import de.ma.ftms.core.FtmsSample
import de.ma.ftms.core.FtmsValueRange
import de.ma.ftms.core.SmoothedFtmsSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutScreenModelTest {
    private val sample = SmoothedFtmsSample(
        raw = FtmsSample(
            speedKmh = 9.2,
            inclinePct = 1.0,
            heartRateBpm = 152,
            powerW = 248,
            stepRateSpm = 169.6,
            elapsedS = 1146,
        ),
        distanceM = 3421.0,
        ascentM = 34.4,
        distanceSource = DistanceSource.SPEED_TIME,
    )

    private fun reading(key: DashboardMetricKey, of: SmoothedFtmsSample? = sample) =
        key.workoutFieldReading(of, DeStrings, EquipmentType.TREADMILL, activeDurationMillis = 1_200_000)

    @Test
    fun aFieldShowsTheNumberLargeAndTheUnitNextToIt() {
        assertEquals(WorkoutFieldReading("9,2", "km/h"), reading(DashboardMetricKey.SPEED))
        assertEquals(WorkoutFieldReading("6:31", "/km"), reading(DashboardMetricKey.PACE))
        assertEquals(WorkoutFieldReading("5:50", "/km"), reading(DashboardMetricKey.AVG_PACE))
        assertEquals(WorkoutFieldReading("152", "bpm"), reading(DashboardMetricKey.HEART_RATE))
        assertEquals(WorkoutFieldReading("3,42", "km"), reading(DashboardMetricKey.DISTANCE))
        assertEquals(WorkoutFieldReading("19:06", ""), reading(DashboardMetricKey.ELAPSED))
        assertEquals(WorkoutFieldReading("1,0", "%"), reading(DashboardMetricKey.INCLINE))
        assertEquals(WorkoutFieldReading("34", "m"), reading(DashboardMetricKey.ASCENT))
        assertEquals(WorkoutFieldReading("170", "spm"), reading(DashboardMetricKey.CADENCE_STEP))
        assertEquals(WorkoutFieldReading("248", "W"), reading(DashboardMetricKey.POWER))
    }

    @Test
    fun numbersFollowTheLanguage() {
        assertEquals(
            "9.2",
            DashboardMetricKey.SPEED.workoutFieldReading(sample, EnStrings, EquipmentType.TREADMILL, 0).value,
        )
    }

    @Test
    fun aValueThatIsNotThereShowsDashes() {
        val standing = sample.copy(raw = FtmsSample(speedKmh = 0.0), distanceM = 0.0)

        WORKOUT_SCREEN_FIELD_OPTIONS.forEach { key ->
            assertEquals("--", reading(key, of = null).value, key.name)
        }
        // A belt that stands has no pace, and nothing was run yet to average.
        assertEquals("--", reading(DashboardMetricKey.PACE, of = standing).value)
        assertEquals("--", reading(DashboardMetricKey.AVG_PACE, of = standing).value)
        assertEquals("--", reading(DashboardMetricKey.HEART_RATE, of = standing).value)
    }

    @Test
    fun theElapsedTimeIsThatOfTheSessionWhereTheEquipmentCountsNone() {
        val silent = sample.copy(raw = sample.raw.copy(elapsedS = null))

        assertEquals(WorkoutFieldReading("20:00", ""), reading(DashboardMetricKey.ELAPSED, of = silent))
    }

    @Test
    fun aClockShowsHoursOnlyWhenThereAreSome() {
        assertEquals("0:00", formatClock(0))
        assertEquals("1:05", formatClock(65))
        assertEquals("59:59", formatClock(3599))
        assertEquals("1:02:03", formatClock(3723))
        assertEquals("0:00", formatClock(-4))
    }

    @Test
    fun theRingFillsAsTheStepPasses() {
        val step = WorkoutFollowState(active = true, stepSeconds = 120, secondsLeft = 84, secondsInStep = 36)

        assertEquals(0.3f, workoutStepProgress(step)!!, 0.0001f)
        assertEquals(0f, workoutStepProgress(step.copy(secondsLeft = 120)))
        assertEquals(1f, workoutStepProgress(step.copy(secondsLeft = 0)))
        assertEquals("1:24", workoutStepClock(step))
    }

    @Test
    fun aStepThatDoesNotEndByTimeHasNoProgressAndCountsUp() {
        val open = WorkoutFollowState(active = true, secondsInStep = 192)

        assertNull(workoutStepProgress(open))
        assertEquals("3:12", workoutStepClock(open))
    }

    @Test
    fun theStepIsNamedWithWhatItIsFor() {
        val run = WorkoutFollowState(active = true, stepNumber = 5, currentKind = WorkoutStepKind.RUN)

        assertEquals("Lauf · Schritt 5", workoutStepTitle(run, DeStrings))
        assertEquals("Run · Step 5", workoutStepTitle(run, EnStrings))
        assertEquals("Schritt 5", workoutStepTitle(run.copy(currentKind = WorkoutStepKind.OTHER), DeStrings))
        assertEquals("Pausiert · Schritt 5", workoutStepTitle(run.copy(paused = true), DeStrings))
        assertEquals("Einlaufen · Schritt 1", workoutStepTitle(run.copy(stepNumber = 1, currentKind = WorkoutStepKind.WARM_UP), DeStrings))
        assertEquals("Auslaufen · Schritt 9", workoutStepTitle(run.copy(stepNumber = 9, currentKind = WorkoutStepKind.COOL_DOWN), DeStrings))
    }

    @Test
    fun theStepThatFollowsIsNamedWithItsLength() {
        val next = WorkoutFollowState(active = true, hasNext = true, nextKind = WorkoutStepKind.RECOVERY, nextSeconds = 60)

        assertEquals("Erholung · 1:00", workoutNextTitle(next, DeStrings))
        assertEquals("Erholung", workoutNextTitle(next.copy(nextSeconds = null), DeStrings))
        assertEquals("Weiterer Schritt · 1:00", workoutNextTitle(next.copy(nextKind = WorkoutStepKind.OTHER), DeStrings))
        assertNull(workoutNextTitle(WorkoutFollowState(active = true), DeStrings))
    }

    @Test
    fun aSpeedOfAStepHasOneDecimal() {
        assertEquals("9,2", formatWorkoutSpeed(920, DeStrings))
        assertEquals("6,0", formatWorkoutSpeed(600, DeStrings))
        assertEquals("12.0", formatWorkoutSpeed(1200, EnStrings))
    }

    private fun shown(
        state: WorkoutScreenState,
        landscape: Boolean,
        workoutActive: Boolean = true,
        available: Boolean = true,
    ) = isWorkoutScreenShown(available = available, workoutActive = workoutActive, landscape = landscape, state = state)

    @Test
    fun theScreenComesByItselfWhenTheWatchIsInAWorkoutAndThePhoneLiesOnItsSide() {
        val untouched = WorkoutScreenState()

        assertTrue(shown(untouched, landscape = true))
        assertFalse(shown(untouched, landscape = false))
        assertFalse(shown(untouched, landscape = true, workoutActive = false))
    }

    @Test
    fun closedItStaysAwayAndOpenedItIsThereAtAnyOrientation() {
        val opened = WorkoutScreenState().openedByHand()

        assertFalse(shown(WorkoutScreenState().closed(), landscape = true))
        assertTrue(shown(opened, landscape = false))
        // Opened, it shows the time of the session while the watch is in no workout.
        assertTrue(shown(opened, landscape = false, workoutActive = false))
        // Without a session with a treadmill there is nothing to show, whoever asked for it.
        assertFalse(shown(opened, landscape = true, available = false))
    }

    @Test
    fun aSessionTheWatchStartsOpensTheScreenAtOnce() {
        val started = WorkoutScreenState().after(available = true, startedByWatch = true, landscape = false)

        assertTrue(started.opened)
        assertTrue(shown(started, landscape = false, workoutActive = false))
        // A session started with a tap in the app stays on the dashboard.
        assertFalse(WorkoutScreenState().after(available = true, startedByWatch = false, landscape = false).opened)
    }

    @Test
    fun closedAfterAStartByTheWatchItDoesNotComeBackByThatStart() {
        val started = WorkoutScreenState().after(available = true, startedByWatch = true, landscape = true)
        val closed = started.closed().after(available = true, startedByWatch = true, landscape = true)

        assertFalse(closed.opened)
        assertFalse(shown(closed, landscape = true))
        // Upright once, and a workout on the watch brings it back when the phone lies on its side.
        val upright = closed.after(available = true, startedByWatch = true, landscape = false)
        assertFalse(shown(upright, landscape = false))
        assertTrue(shown(upright.after(available = true, startedByWatch = true, landscape = true), landscape = true))
    }

    @Test
    fun theNextSessionTheWatchStartsOpensItAgain() {
        val closed = WorkoutScreenState()
            .after(available = true, startedByWatch = true, landscape = true)
            .closed()
        val between = closed.after(available = false, startedByWatch = false, landscape = true)

        assertEquals(WorkoutScreenState(), between)
        assertTrue(between.after(available = true, startedByWatch = true, landscape = true).opened)
    }

    @Test
    fun openedByHandItStaysUpWhenTheWorkoutEnds() {
        val opened = WorkoutScreenState().openedByHand().after(available = true, startedByWatch = false, landscape = true)

        assertTrue(shown(opened, landscape = true, workoutActive = false))
    }

    @Test
    fun theDashboardOffersTheScreenForAWorkoutOrASessionTheWatchStarted() {
        assertTrue(isWorkoutScreenOffered(available = true, workoutActive = true, startedByWatch = false))
        assertTrue(isWorkoutScreenOffered(available = true, workoutActive = false, startedByWatch = true))
        assertFalse(isWorkoutScreenOffered(available = true, workoutActive = false, startedByWatch = false))
        assertFalse(isWorkoutScreenOffered(available = false, workoutActive = true, startedByWatch = true))
    }

    @Test
    fun withoutAWorkoutTheRingShowsTheSession() {
        val none = WorkoutFollowState()

        assertEquals("Freier Lauf", workoutRingTitle(none, sessionPaused = false, strings = DeStrings))
        assertEquals("Pausiert", workoutRingTitle(none, sessionPaused = true, strings = DeStrings))
        assertEquals("19:06", workoutRingClock(none, activeDurationMillis = 1_146_900))
        // With a workout it is the step that counts, whatever the session says.
        val step = WorkoutFollowState(active = true, stepNumber = 5, currentKind = WorkoutStepKind.RUN, secondsLeft = 84)
        assertEquals("Lauf · Schritt 5", workoutRingTitle(step, sessionPaused = false, strings = DeStrings))
        assertEquals("1:24", workoutRingClock(step, activeDurationMillis = 1_146_900))
    }

    private fun belt(
        speedTarget: Int? = null,
        inclineTarget: Int? = null,
        moving: Boolean = true,
        inclineSupported: Boolean = true,
    ) = TreadmillControlState(
        availability = TreadmillControlAvailability.READY,
        beltMoving = moving,
        speed = TreadmillTargetState(range = FtmsValueRange(min = 100, max = 1800, step = 10), measured = 600, target = speedTarget),
        incline = TreadmillTargetState(
            supported = inclineSupported,
            range = FtmsValueRange(min = 0, max = 150, step = 10),
            measured = 20,
            target = inclineTarget,
        ),
    )

    @Test
    fun theKeysAreThereWhereTheAppMaySteerTheTreadmill() {
        assertEquals(WorkoutKeys(enabled = true, step = 10), workoutKeys(TreadmillSetting.SPEED, steering = true, control = belt()))
        assertEquals(WorkoutKeys(enabled = true, step = 10), workoutKeys(TreadmillSetting.INCLINE, steering = true, control = belt()))
        // A belt that stands is not set: the keys are shown and do nothing.
        assertEquals(
            WorkoutKeys(enabled = false, step = 10),
            workoutKeys(TreadmillSetting.SPEED, steering = true, control = belt(moving = false)),
        )
    }

    @Test
    fun thereAreNoKeysWithoutSteeringOrForWhatTheTreadmillDoesNotTake() {
        assertNull(workoutKeys(TreadmillSetting.SPEED, steering = false, control = belt()))
        assertNull(workoutKeys(TreadmillSetting.SPEED, steering = true, control = TreadmillControlState()))
        assertNull(workoutKeys(TreadmillSetting.INCLINE, steering = true, control = belt(inclineSupported = false)))
        assertEquals(
            WorkoutKeys(enabled = true, step = 10),
            workoutKeys(TreadmillSetting.SPEED, steering = true, control = belt(inclineSupported = false)),
        )
    }

    @Test
    fun aSpeedSetByHandIsToldFromTheOneOfTheWorkout() {
        val run = WorkoutFollowState(active = true, currentSpeed = 920, nextSpeed = 600)

        assertNull(workoutHandSpeed(run, belt()))
        assertNull(workoutHandSpeed(run, belt(speedTarget = 920)))
        assertEquals(940, workoutHandSpeed(run, belt(speedTarget = 940)))
        // The treadmill takes steps of 0.1 km/h, so the step's 9.23 km/h was sent as 9.2.
        assertNull(workoutHandSpeed(run.copy(currentSpeed = 923), belt(speedTarget = 920)))
    }

    @Test
    fun speedingUpForTheNextStepIsNotByHand() {
        val recovery = WorkoutFollowState(active = true, currentSpeed = 600, nextSpeed = 920, speedingUpAhead = true)

        assertNull(workoutHandSpeed(recovery, belt(speedTarget = 920)))
        assertEquals(800, workoutHandSpeed(recovery, belt(speedTarget = 800)))
    }

    @Test
    fun withoutASpeedFromTheWorkoutEverySpeedAskedForIsByHand() {
        assertEquals(700, workoutHandSpeed(WorkoutFollowState(), belt(speedTarget = 700)))
        assertEquals(700, workoutHandSpeed(WorkoutFollowState(active = true), belt(speedTarget = 700)))
        // What a workout said before it ended does not count any more.
        assertEquals(920, workoutHandSpeed(WorkoutFollowState(currentSpeed = 920), belt(speedTarget = 920)))
    }

    @Test
    fun theInclineAskedForShowsUntilTheTreadmillIsThere() {
        assertNull(workoutInclineOnItsWay(belt()))
        assertEquals(40, workoutInclineOnItsWay(belt(inclineTarget = 40)))
        assertNull(workoutInclineOnItsWay(belt(inclineTarget = 20)))
    }

    @Test
    fun aLevelBeltThatReportsNoInclineShowsZeroWhereItCanBeSet() {
        val level = sample.copy(raw = sample.raw.copy(inclinePct = null))
        val flat = belt().let { it.copy(incline = it.incline.copy(measured = 0)) }

        assertEquals(WorkoutFieldReading("0,0", "%"), workoutInclineFromControl(level, flat, DeStrings))
        assertEquals(WorkoutFieldReading("0,0", "%"), workoutInclineFromControl(null, flat, DeStrings))
        // What the treadmill reports itself is shown as it is.
        assertNull(workoutInclineFromControl(sample, flat, DeStrings))
        // Without a connected treadmill the control knows nothing either.
        assertNull(workoutInclineFromControl(level, TreadmillControlState(), DeStrings))
    }

    @Test
    fun aHeldKeyGoesOnSteppingFromWhatWasAskedFor() {
        assertTrue(heldKeyStepsOn(TreadmillSetting.SPEED, delta = 10, startedFrom = 600, base = 600))
        assertTrue(heldKeyStepsOn(TreadmillSetting.SPEED, delta = 10, startedFrom = 600, base = 890))
        assertTrue(heldKeyStepsOn(TreadmillSetting.INCLINE, delta = 10, startedFrom = 0, base = 140))
        // Nothing to start from: the treadmill has reported no value yet.
        assertFalse(heldKeyStepsOn(TreadmillSetting.SPEED, delta = 10, startedFrom = null, base = null))
    }

    @Test
    fun oneHoldMakesTheBeltFasterByThreeKilometresPerHourAtMost() {
        assertFalse(heldKeyStepsOn(TreadmillSetting.SPEED, delta = 10, startedFrom = 600, base = 900))
        // Slower is not limited: the belt has its own lowest speed.
        assertTrue(heldKeyStepsOn(TreadmillSetting.SPEED, delta = -10, startedFrom = 1200, base = 300))
    }
}
