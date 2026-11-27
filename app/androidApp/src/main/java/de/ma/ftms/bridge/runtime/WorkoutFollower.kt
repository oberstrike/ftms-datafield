package de.ma.ftms.bridge.runtime

import kotlin.math.roundToInt

/**
 * One step of the workout on the watch, as far as a treadmill can use it.
 *
 * @property speed the speed the step asks for, in hundredths of a km/h: the middle of its pace
 * range. Null for a step without a pace.
 * @property seconds how long the step lasts. Null for a step that ends another way, such as with
 * the lap key.
 * @property kind what the step is for, as the watch names it.
 * @property endsWithLapKey the step lasts until the lap key is pressed.
 */
data class WatchWorkoutStep(
    val speed: Int?,
    val seconds: Int?,
    val kind: WorkoutStepKind = WorkoutStepKind.OTHER,
    val endsWithLapKey: Boolean = false,
)

/** What a step of a workout is for. */
enum class WorkoutStepKind {
    WARM_UP,
    RUN,
    RECOVERY,
    COOL_DOWN,

    /** The watch does not say, or says something this app has no name for. */
    OTHER,
}

/**
 * What the watch says about the workout it is in.
 *
 * @property started how many workouts were started in this activity; zero while there is none.
 * @property stepsDone how many steps have ended.
 * @property stepElapsedMillis how long the step had lasted on the watch when the message was
 * sent, by the activity timer. Null from a data field that does not say, or one that was not
 * there when the step began.
 */
data class WatchWorkout(
    val started: Int,
    val stepsDone: Int,
    val current: WatchWorkoutStep?,
    val next: WatchWorkoutStep?,
    val stepElapsedMillis: Long? = null,
)

/**
 * Reads the data field's workout message, see `WorkoutStepPublisher.mc`.
 *
 * What a watch puts into a step is documented by its names only, so this takes what is known from
 * other developers and refuses what does not fit:
 *
 * - A pace is a target of the type "speed" with a low and a high value in metres per second. A
 *   workout file holds the same in millimetres per second, so that is read too; the two cannot be
 *   mistaken for one another at any speed a person runs.
 * - A step with the duration type "time" lasts its value in seconds. A value that would make a
 *   step shorter than five seconds or longer than six hours is not believed.
 * - A step of an interval set carries its active and its rest part; the intensity says which of
 *   the two the watch is in.
 * - The intensity also names the step: warm-up, run, recovery or cool-down.
 * - A step with the duration type "open" lasts until the lap key is pressed.
 */
internal fun watchWorkoutFrom(payload: Map<*, *>): WatchWorkout =
    WatchWorkout(
        started = payload["started"].asInt() ?: 0,
        stepsDone = payload["done"].asInt() ?: 0,
        current = (payload["cur"] as? Map<*, *>)?.let(::watchWorkoutStepFrom),
        next = (payload["next"] as? Map<*, *>)?.let(::watchWorkoutStepFrom),
        stepElapsedMillis = (payload["el"] as? Number)?.toLong()?.takeIf { it >= 0 },
    )

private fun watchWorkoutStepFrom(info: Map<*, *>): WatchWorkoutStep {
    val intensity = info["int"].asInt()
    val resting = intensity in RESTING_INTENSITIES
    val kind = when (intensity) {
        in RESTING_INTENSITIES -> WorkoutStepKind.RECOVERY
        in RUNNING_INTENSITIES -> WorkoutStepKind.RUN
        INTENSITY_WARM_UP -> WorkoutStepKind.WARM_UP
        INTENSITY_COOL_DOWN -> WorkoutStepKind.COOL_DOWN
        else -> WorkoutStepKind.OTHER
    }
    val detail = (info["step"] ?: info[if (resting) "rest" else "active"]) as? Map<*, *>
        ?: return WatchWorkoutStep(speed = null, seconds = null, kind = kind)

    val speed = if (detail["tt"].asInt() in SPEED_TARGET_TYPES) {
        val bounds = listOfNotNull(
            detail["lo"].asMetresPerSecond(),
            detail["hi"].asMetresPerSecond(),
        )
        bounds.takeIf { it.isNotEmpty() }?.let { (it.average() * 360).roundToInt() }
    } else {
        null
    }
    val seconds = if (detail["dt"].asInt() == DURATION_TYPE_TIME) {
        (detail["dv"] as? Number)?.toDouble()?.roundToInt()?.takeIf { it in BELIEVABLE_STEP_SECONDS }
    } else {
        null
    }
    return WatchWorkoutStep(
        speed = speed,
        seconds = seconds,
        kind = kind,
        endsWithLapKey = detail["dt"].asInt() == DURATION_TYPE_OPEN,
    )
}

private fun Any?.asInt(): Int? = (this as? Number)?.toInt()

private fun Any?.asMetresPerSecond(): Double? {
    val value = (this as? Number)?.toDouble() ?: return null
    return when (value) {
        in METRES_PER_SECOND -> value
        in MILLIMETRES_PER_SECOND -> value / 1000
        else -> null
    }
}

// Toybox.Activity: WORKOUT_STEP_TARGET_SPEED and WORKOUT_STEP_TARGET_SPEED_LAP.
private val SPEED_TARGET_TYPES = setOf(0, 12)

// WORKOUT_STEP_DURATION_TIME and WORKOUT_STEP_DURATION_OPEN.
private const val DURATION_TYPE_TIME = 0
private const val DURATION_TYPE_OPEN = 5

// WORKOUT_INTENSITY_REST and WORKOUT_INTENSITY_RECOVERY.
private val RESTING_INTENSITIES = setOf(1, 4)

// WORKOUT_INTENSITY_ACTIVE and WORKOUT_INTENSITY_INTERVAL.
private val RUNNING_INTENSITIES = setOf(0, 5)

// WORKOUT_INTENSITY_WARMUP and WORKOUT_INTENSITY_COOLDOWN.
private const val INTENSITY_WARM_UP = 2
private const val INTENSITY_COOL_DOWN = 3

// 0.7 to 43 km/h, in either unit.
private val METRES_PER_SECOND = 0.2..12.0
private val MILLIMETRES_PER_SECOND = 200.0..12_000.0
private val BELIEVABLE_STEP_SECONDS = 5..21_600

/** What the app shows of the workout on the watch, followed or not. */
data class WorkoutFollowState(
    /** A session with a treadmill runs and the watch is in a step of a workout. */
    val active: Boolean = false,
    /** Following is switched on: the speeds are sent, not only shown. */
    val sending: Boolean = false,
    /** The speed of the step the watch is in, null if it asks for none. */
    val currentSpeed: Int? = null,
    /** The speed of the step that follows, null if it asks for none or the watch names no step. */
    val nextSpeed: Int? = null,
    /** How long the step still lasts, null if it does not end by time, e.g. but with the lap key. */
    val secondsLeft: Int? = null,
    /** How long the step has lasted. */
    val secondsInStep: Int = 0,
    /**
     * The time of the step comes from the watch. Otherwise the app counted from the message that
     * reported the step, which is off by however late that message came.
     */
    val timeFromWatch: Boolean = false,
    /** The number of the step the watch is in, counted from one over the whole workout. */
    val stepNumber: Int = 0,
    /** What the step the watch is in is for. */
    val currentKind: WorkoutStepKind = WorkoutStepKind.OTHER,
    /** How long the step lasts in all, null if it does not end by time. */
    val stepSeconds: Int? = null,
    /** The step lasts until the lap key is pressed. */
    val endsWithLapKey: Boolean = false,
    /** The watch names a step that follows. */
    val hasNext: Boolean = false,
    /** What the step that follows is for. */
    val nextKind: WorkoutStepKind = WorkoutStepKind.OTHER,
    /** How long the step that follows lasts, null if it does not end by time. */
    val nextSeconds: Int? = null,
    /** The belt has been told the speed of the step that follows and is on its way there. */
    val speedingUpAhead: Boolean = false,
    /** The activity on the watch does not record, so the step stands still. */
    val paused: Boolean = false,
)

/**
 * Sets the speed of a treadmill to what the workout on the watch asks for, step by step.
 *
 * This is the one place where the app sends a speed without a tap, so it does little:
 *
 * - It sends once when the watch enters a step with a pace, and, with
 *   [WorkoutTransitionMode.SMART], once shortly before a faster step begins, so that the belt is
 *   up to speed when the step starts. The belt slows down only when the slower step has begun.
 *   Between these moments it sends nothing: a speed set by hand or on the console stays until the
 *   next step.
 * - A step without a pace leaves the belt alone.
 * - It never asks for more than the limit in the settings, sends nothing while the activity on
 *   the watch is not recording, and switching it on takes effect with the next step.
 * - Everything else is up to [TreadmillController], which refuses a belt that stands and a
 *   treadmill that has fallen silent.
 *
 * The time a step has lasted is what the watch says in its messages, counted on between them
 * while the activity records. A data field that does not say leaves the app to count from the
 * message that reported the step. Not thread-safe; all times are milliseconds of one monotonic
 * clock.
 *
 * @param setSpeed asks the treadmill for a speed; `ahead` is true for a speed sent before its
 * step began.
 */
internal class WorkoutFollower(
    private val settings: () -> BridgeSettings,
    private val setSpeed: (speed: Int, transition: TreadmillTransition, ahead: Boolean) -> Unit,
    private val onStateChanged: (WorkoutFollowState) -> Unit,
) {
    var state = WorkoutFollowState()
        private set

    private var workout: WatchWorkout? = null
    private var stepElapsedMillis = 0L
    private var tickedAtMillis: Long? = null

    /** The watch said how long the step has lasted. */
    private var timeFromWatch = false

    /** The step the watch is in has been dealt with: its speed was sent or there was none to send. */
    private var entered = true

    /** The speed of the next step was sent ahead, or it is too late for that. */
    private var aheadDone = false

    /** The speed sent ahead for the step that follows. */
    private var sentAhead: Int? = null

    /** Takes in what the watch reports; null for a message that could not be read. */
    fun onWorkout(reported: WatchWorkout?, nowMillis: Long) {
        val previous = workout
        workout = reported
        if (reported == null || reported.started == 0 || reported.current == null) {
            entered = true
            sentAhead = null
            return
        }

        val newStep = previous == null ||
            previous.started != reported.started ||
            previous.stepsDone != reported.stepsDone ||
            previous.current != reported.current
        if (newStep) {
            stepElapsedMillis = 0
            timeFromWatch = false
            entered = false
            aheadDone = false
        }
        val elapsed = reported.stepElapsedMillis
        if (elapsed != null) {
            // The watch's own count replaces the app's: it does not depend on when messages arrive.
            stepElapsedMillis = elapsed + MESSAGE_TRANSIT_MILLIS
            timeFromWatch = true
            tickedAtMillis = nowMillis
        }
    }

    /** Forgets the workout, e.g. because the session ended. */
    fun reset() {
        workout = null
        entered = true
        aheadDone = false
        sentAhead = null
        stepElapsedMillis = 0
        timeFromWatch = false
        tickedAtMillis = null
        publish(WorkoutFollowState())
    }

    /**
     * Called with every packet of the treadmill.
     *
     * @param enabled a session with a treadmill runs; without that the workout is of no use.
     * @param sending following is switched on. Without it the workout is only shown, which lets
     * one check what the watch reports before the belt follows it.
     * @param recording the activity on the watch records.
     */
    fun tick(nowMillis: Long, enabled: Boolean, sending: Boolean, recording: Boolean, control: TreadmillControlState) {
        val current = workout?.takeIf { it.started > 0 }?.current
        val lastTick = tickedAtMillis
        tickedAtMillis = nowMillis
        if (!enabled || current == null) {
            entered = true
            sentAhead = null
            publish(WorkoutFollowState())
            return
        }
        if (!sending) {
            // Switched on later, it starts with the next step and not in the middle of this one.
            entered = true
            aheadDone = true
            sentAhead = null
        }
        if (recording && lastTick != null) {
            stepElapsedMillis += (nowMillis - lastTick).coerceIn(0, MAX_TICK_MILLIS)
        }

        val settings = settings()
        val limit = settings.treadmillFollowMaxSpeed
        val next = workout?.next
        if (recording && sending) {
            if (!entered) {
                entered = true
                val speed = current.speed?.coerceAtMost(limit)
                val ahead = sentAhead
                sentAhead = null
                if (speed != null && speed != ahead && control.canSet && !control.runsAt(speed)) {
                    setSpeed(speed, settings.followTransition(current.seconds), false)
                }
            }
            sendAhead(current, next, settings, control)
        }

        publish(
            WorkoutFollowState(
                active = true,
                sending = sending,
                currentSpeed = current.speed?.coerceAtMost(limit),
                nextSpeed = next?.speed?.coerceAtMost(limit),
                secondsLeft = current.seconds?.let { (it - stepElapsedMillis / 1000).toInt().coerceAtLeast(0) },
                secondsInStep = (stepElapsedMillis / 1000).toInt(),
                timeFromWatch = timeFromWatch,
                stepNumber = (workout?.stepsDone ?: 0) + 1,
                currentKind = current.kind,
                stepSeconds = current.seconds,
                endsWithLapKey = current.endsWithLapKey,
                hasNext = next != null,
                nextKind = next?.kind ?: WorkoutStepKind.OTHER,
                nextSeconds = next?.seconds,
                speedingUpAhead = sentAhead != null,
                paused = !recording,
            ),
        )
    }

    /** Sends the speed of a faster next step early enough for the belt to be there when it begins. */
    private fun sendAhead(
        current: WatchWorkoutStep,
        next: WatchWorkoutStep?,
        settings: BridgeSettings,
        control: TreadmillControlState,
    ) {
        val stepMillis = current.seconds?.times(1000L)
        val target = next?.speed?.coerceAtMost(settings.treadmillFollowMaxSpeed)
        if (settings.treadmillFollowMode != WorkoutTransitionMode.SMART ||
            aheadDone || stepMillis == null || target == null || !control.canSet
        ) {
            return
        }
        if (stepElapsedMillis >= stepMillis) {
            // The step should be over; whatever comes now is sent when the watch reports it.
            aheadDone = true
            return
        }

        val base = control.speed.target ?: control.speed.measured ?: return
        if (target <= base) {
            return
        }

        // The change takes its time from the step the watch is in.
        val transition = settings.followTransition(current.seconds)
        val leadMillis = minOf(
            (treadmillTransitionSeconds(base, target, transition) * 1000).toLong() +
                if (timeFromWatch) 0 else MESSAGE_DELAY_MILLIS,
            stepMillis / 2,
        )
        if (stepElapsedMillis >= stepMillis - leadMillis) {
            aheadDone = true
            sentAhead = target
            setSpeed(target, transition, true)
        }
    }

    /** The belt was last told [speed], or runs at it with nothing else asked for. */
    private fun TreadmillControlState.runsAt(speed: Int): Boolean =
        (this.speed.target ?: this.speed.measured) == acceptedValue(TreadmillSetting.SPEED, speed)

    private fun publish(newState: WorkoutFollowState) {
        if (newState == state) {
            return
        }

        state = newState
        onStateChanged(newState)
    }
}

/** How the belt gets from the speed of one step of a workout to that of the next. */
enum class WorkoutTransitionMode {
    /** No transition of the app's own: the treadmill is told the speed and goes there as it does. */
    OFF,

    /** Every change is led softly, beginning when the step begins. */
    GENTLE,

    /**
     * Led softly where there is time for it and in one piece where a step is short, and a faster
     * step is prepared: the belt speeds up before the step begins.
     */
    SMART,
}

/**
 * How a speed of the workout is reached when its change falls into a step of [stepSeconds].
 *
 * A soft change takes the time from the settings, or has the pace of the treadmill control's
 * transition if no time is set (and a gentle one if that is switched off). [WorkoutTransitionMode.SMART]
 * leaves a change that falls into a short step to the treadmill: there the time is better spent
 * at the speed asked for.
 */
internal fun BridgeSettings.followTransition(stepSeconds: Int?): TreadmillTransition {
    val soft = if (treadmillFollowTransitionSeconds > 0) {
        TreadmillTransition.Seconds(treadmillFollowTransitionSeconds)
    } else {
        TreadmillTransition.Profile(
            treadmillRamp.takeUnless { it == TreadmillRampProfile.DIRECT } ?: TreadmillRampProfile.GENTLE,
        )
    }
    return when (treadmillFollowMode) {
        WorkoutTransitionMode.OFF -> TreadmillTransition.Direct
        WorkoutTransitionMode.GENTLE -> soft
        WorkoutTransitionMode.SMART ->
            if (stepSeconds != null && stepSeconds < treadmillFollowShortStepSeconds) TreadmillTransition.Direct else soft
    }
}

/** Whether the app follows the workout on the watch: it steers a treadmill and following is on. */
internal fun isWorkoutFollowed(settings: BridgeSettings, type: EquipmentType): Boolean =
    settings.treadmillFollowWorkout && isTreadmillControlWanted(settings, type)

/** A step that lasts less than this takes its changes of speed in one piece; 0 for never. */
val TREADMILL_FOLLOW_SHORT_STEP_OPTIONS: List<Int> = listOf(0, 30, 60, 90, 120)
const val DEFAULT_TREADMILL_FOLLOW_SHORT_STEP_SECONDS = 60

/** How long a change of speed takes when following a workout; 0 for the transition of the treadmill control. */
val TREADMILL_FOLLOW_TRANSITION_OPTIONS: List<Int> = listOf(0, 5, 10, 15, 20, 30)

/** The most a workout may ask of the belt, in hundredths of a km/h. */
val TREADMILL_FOLLOW_MAX_SPEED_OPTIONS: List<Int> = listOf(1000, 1200, 1400, 1600, 1800)
const val DEFAULT_TREADMILL_FOLLOW_MAX_SPEED = 1200

/**
 * Without a time from the watch, the message that reported a step is taken to be this old when it
 * arrives: the data field notices a step within a second and the message then travels.
 */
private const val MESSAGE_DELAY_MILLIS = 1_500L

/** A message with the watch's own time is taken to have travelled this long. */
private const val MESSAGE_TRANSIT_MILLIS = 500L

/** A gap between two packets counts as no more than this towards the time a step has lasted. */
private const val MAX_TICK_MILLIS = 5_000L
