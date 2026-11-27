package de.ma.ftms.bridge.ui

import de.ma.ftms.bridge.i18n.Strings
import de.ma.ftms.bridge.i18n.fmt
import de.ma.ftms.bridge.runtime.DashboardMetricKey
import de.ma.ftms.bridge.runtime.EquipmentType
import de.ma.ftms.bridge.runtime.LARGE_SPEED_INCREASE
import de.ma.ftms.bridge.runtime.TreadmillControlState
import de.ma.ftms.bridge.runtime.TreadmillSetting
import de.ma.ftms.bridge.runtime.WorkoutFollowState
import de.ma.ftms.bridge.runtime.WorkoutStepKind
import de.ma.ftms.core.SmoothedTreadmillSample
import kotlin.math.roundToInt

/** A value of the workout screen: the number in large and its unit in small next to it. */
internal data class WorkoutFieldReading(val value: String, val unit: String)

/**
 * This value as a field of the workout screen shows it: shorter than on the dashboard, because it
 * is read from a distance while running. A speed has one decimal, a distance is in kilometres,
 * and the elapsed time is never missing in a session.
 */
internal fun DashboardMetricKey.workoutFieldReading(
    sample: SmoothedTreadmillSample?,
    strings: Strings,
    type: EquipmentType,
    activeDurationMillis: Long,
): WorkoutFieldReading {
    val raw = sample?.raw
    val missing = strings.common.missingValue
    return when (this) {
        DashboardMetricKey.SPEED ->
            WorkoutFieldReading(raw?.speedKmh?.let { strings.fmt("%.1f", it) } ?: missing, "km/h")
        DashboardMetricKey.PACE -> WorkoutFieldReading(
            raw?.speedKmh?.takeIf { it > 0.0 }?.let { formatPaceClock(3600.0 / it) } ?: missing,
            strings.workout.paceUnit,
        )
        DashboardMetricKey.AVG_PACE -> WorkoutFieldReading(
            sample?.distanceM?.let { formatAveragePaceClock(it, activeDurationMillis) } ?: missing,
            strings.workout.paceUnit,
        )
        DashboardMetricKey.HEART_RATE -> WorkoutFieldReading(raw?.heartRateBpm?.toString() ?: missing, "bpm")
        DashboardMetricKey.DISTANCE ->
            WorkoutFieldReading(sample?.distanceM?.let { strings.fmt("%.2f", it / 1000.0) } ?: missing, "km")
        // The time the equipment counts, and the time of the session where it counts none.
        DashboardMetricKey.ELAPSED -> WorkoutFieldReading(
            raw?.let { formatClock(it.elapsedS ?: (activeDurationMillis / 1000).toInt()) } ?: missing,
            "",
        )
        DashboardMetricKey.INCLINE ->
            WorkoutFieldReading(raw?.inclinePct?.let { strings.fmt("%.1f", it) } ?: missing, "%")
        DashboardMetricKey.ASCENT -> WorkoutFieldReading(sample?.ascentM?.let { strings.fmt("%.0f", it) } ?: missing, "m")
        DashboardMetricKey.CADENCE_STEP ->
            WorkoutFieldReading(raw?.cadenceOrStepRate()?.roundToInt()?.toString() ?: missing, cadenceUnit(type))
        DashboardMetricKey.POWER -> WorkoutFieldReading(raw?.powerW?.toString() ?: missing, "W")
        // Not offered on the workout screen; shown as the dashboard does should one get here.
        else -> WorkoutFieldReading(format(sample, strings, activeDurationMillis = activeDurationMillis), "")
    }
}

/** [seconds] as a clock: "1:05", and "1:02:03" from an hour on. */
internal fun formatClock(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    return if (total < 3600) {
        formatMinutesSeconds(total)
    } else {
        "${total / 3600}:${(total % 3600 / 60).toString().padStart(2, '0')}:${(total % 60).toString().padStart(2, '0')}"
    }
}

/** How much of the step the watch is in is over, from 0 to 1; null for a step that does not end by time. */
internal fun workoutStepProgress(follow: WorkoutFollowState): Float? {
    val all = follow.stepSeconds?.takeIf { it > 0 } ?: return null
    val left = follow.secondsLeft ?: return null
    return (1f - left.toFloat() / all).coerceIn(0f, 1f)
}

/** The large time of the step: what is left of it, or how long it has lasted if it does not end by time. */
internal fun workoutStepClock(follow: WorkoutFollowState): String =
    formatClock(follow.secondsLeft ?: follow.secondsInStep)

/** What a step is for in a word; null where the watch does not say. */
internal fun WorkoutStepKind.label(strings: Strings): String? =
    when (this) {
        WorkoutStepKind.WARM_UP -> strings.workout.warmUp
        WorkoutStepKind.RUN -> strings.workout.run
        WorkoutStepKind.RECOVERY -> strings.workout.recovery
        WorkoutStepKind.COOL_DOWN -> strings.workout.coolDown
        WorkoutStepKind.OTHER -> null
    }

/** The line above the time: "Lauf · Schritt 5", or "Pausiert · Schritt 5" while the watch does not record. */
internal fun workoutStepTitle(follow: WorkoutFollowState, strings: Strings): String =
    listOfNotNull(
        if (follow.paused) strings.workout.paused else follow.currentKind.label(strings),
        strings.workout.step(follow.stepNumber),
    ).joinToString(" · ")

/** The step that follows in a line, as in "Erholung · 1:00"; null if the watch names none. */
internal fun workoutNextTitle(follow: WorkoutFollowState, strings: Strings): String? =
    if (follow.hasNext) {
        listOfNotNull(
            follow.nextKind.label(strings) ?: strings.workout.anotherStep,
            follow.nextSeconds?.let(::formatClock),
        ).joinToString(" · ")
    } else {
        null
    }

/** A speed of a step as a number in km/h, as in "9,2". */
internal fun formatWorkoutSpeed(speedHundredths: Int, strings: Strings): String =
    formatTreadmillNumber(TreadmillSetting.SPEED, speedHundredths, strings.locale)

/** The title in the ring: the step of the workout, or what the session is without one. */
internal fun workoutRingTitle(follow: WorkoutFollowState, sessionPaused: Boolean, strings: Strings): String =
    when {
        follow.active -> workoutStepTitle(follow, strings)
        sessionPaused -> strings.workout.paused
        else -> strings.workout.freeRun
    }

/** The large time in the ring: that of the step, or that of the session while the watch is in no workout. */
internal fun workoutRingClock(follow: WorkoutFollowState, activeDurationMillis: Long): String =
    if (follow.active) workoutStepClock(follow) else formatClock((activeDurationMillis / 1000).toInt())

/**
 * The keys of the workout screen for [setting] of the treadmill; null where there are none,
 * because steering the treadmill is switched off or the treadmill does not take the setting.
 *
 * @param steering the setting that lets the app steer the treadmill.
 */
internal fun workoutKeys(setting: TreadmillSetting, steering: Boolean, control: TreadmillControlState): WorkoutKeys? =
    if (steering && control.available && control.of(setting).supported) {
        WorkoutKeys(enabled = control.canSet, step = control.fineStep(setting))
    } else {
        null
    }

/**
 * @property enabled the belt moves, so a key does something.
 * @property step what one tap changes, in the unit of the setting: the treadmill's smallest step.
 */
internal data class WorkoutKeys(val enabled: Boolean, val step: Int)

/**
 * The speed that was asked for by hand, on this screen or on the dashboard: what the belt was
 * last told where that is not what the workout says. Null while the belt was told nothing, or
 * what the step the watch is in asks for, or the step after it once the belt speeds up for that.
 */
internal fun workoutHandSpeed(follow: WorkoutFollowState, control: TreadmillControlState): Int? {
    val asked = control.speed.target ?: return null
    val ofWorkout = (if (follow.speedingUpAhead) follow.nextSpeed else follow.currentSpeed).takeIf { follow.active }
    return asked.takeIf { ofWorkout == null || it != control.acceptedValue(TreadmillSetting.SPEED, ofWorkout) }
}

/**
 * The incline for the field that has the arrows, where the treadmill reports none: what the
 * control goes by. A treadmill may leave the incline out while it is level, and the control
 * counts that as zero; the field then shows a level belt as the dashboard does, not as missing.
 * Null where the treadmill reports an incline itself, or the control has none either.
 */
internal fun workoutInclineFromControl(
    sample: SmoothedTreadmillSample?,
    control: TreadmillControlState,
    strings: Strings,
): WorkoutFieldReading? =
    control.incline.measured
        ?.takeIf { sample?.raw?.inclinePct == null }
        ?.let { WorkoutFieldReading(strings.fmt("%.1f", it / 10.0), "%") }

/** The incline the treadmill was told and has not reached yet; null once it is there. */
internal fun workoutInclineOnItsWay(control: TreadmillControlState): Int? =
    control.incline.target?.takeIf { it != control.incline.measured }

/** A key that is held waits this long, then steps again and again: the steps add up. */
internal const val HOLD_BEFORE_REPEAT_MILLIS = 450L

/** How fast a held key steps. The incline is slower, as its steps are larger for what they do. */
internal val TreadmillSetting.holdRepeatMillis: Long
    get() = when (this) {
        TreadmillSetting.SPEED -> 150L
        TreadmillSetting.INCLINE -> 400L
    }

/**
 * Whether a key that is held steps once more. One hold makes the belt faster by no more than
 * [LARGE_SPEED_INCREASE]: a finger, or a drop of sweat, that stays on the key must not take the
 * belt to its top speed. Whoever wants more lets go and presses again.
 *
 * @param startedFrom what the setting was asked to be, or was, when the key went down.
 * @param base what the next step starts from.
 */
internal fun heldKeyStepsOn(setting: TreadmillSetting, delta: Int, startedFrom: Int?, base: Int?): Boolean {
    if (startedFrom == null || base == null) {
        return false
    }
    return setting != TreadmillSetting.SPEED || delta < 0 || base + delta <= startedFrom + LARGE_SPEED_INCREASE
}

/**
 * What decides whether the workout screen is up, as far as it is not read from the session.
 *
 * @property opened it was asked for: by a tap on the dashboard, or by the watch starting the
 * session. It then stays up at any orientation and turns the display on its side itself.
 * @property dismissed it was closed and the phone has not been upright since.
 * @property watchStartHandled the start of this session by the watch has opened it already, so
 * that it does not come back by that after it was closed.
 */
internal data class WorkoutScreenState(
    val opened: Boolean = false,
    val dismissed: Boolean = false,
    val watchStartHandled: Boolean = false,
) {
    /**
     * This state after a look at the session.
     *
     * @param available a session with a treadmill runs; without one there is no screen.
     * @param startedByWatch the session began with the activity on the watch.
     */
    fun after(available: Boolean, startedByWatch: Boolean, landscape: Boolean): WorkoutScreenState {
        if (!available) {
            return WorkoutScreenState()
        }

        var state = this
        if (!startedByWatch) {
            state = state.copy(watchStartHandled = false)
        } else if (!state.watchStartHandled) {
            // Whoever starts on the watch stands on the treadmill and wants this screen, not the dashboard.
            state = state.copy(opened = true, dismissed = false, watchStartHandled = true)
        }
        if (!landscape && !state.opened) {
            // Upright again: the next time the phone lies on its side, the screen comes back.
            state = state.copy(dismissed = false)
        }
        return state
    }

    fun openedByHand(): WorkoutScreenState = copy(opened = true, dismissed = false)

    fun closed(): WorkoutScreenState = copy(opened = false, dismissed = true)
}

/**
 * Whether the workout screen is up. It opens when the watch starts the session and when it is
 * asked for on the dashboard, at any orientation. It also comes by itself when the watch is in a
 * workout and the phone lies on its side; closed, it then stays away until the phone has been
 * upright again.
 *
 * @param available a session with a treadmill runs.
 * @param workoutActive the watch is in a step of a workout.
 */
internal fun isWorkoutScreenShown(
    available: Boolean,
    workoutActive: Boolean,
    landscape: Boolean,
    state: WorkoutScreenState,
): Boolean =
    available && (state.opened || (workoutActive && landscape && !state.dismissed))

/** Whether the dashboard offers the workout screen: there is a workout to show, or the watch started the session. */
internal fun isWorkoutScreenOffered(available: Boolean, workoutActive: Boolean, startedByWatch: Boolean): Boolean =
    available && (workoutActive || startedByWatch)
